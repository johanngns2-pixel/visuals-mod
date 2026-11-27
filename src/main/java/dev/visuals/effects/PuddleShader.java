package dev.visuals.effects;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.visuals.util.Log;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Bildnachbearbeitung der Welt: Pfützen mit Spiegelung und Jahreszeiten-Farben (Laub, liegendes Laub, Schnee, Blüten).
 *
 * <p>Der Shader läuft direkt nach dem Zeichnen des festen Geländes und vor Entities, Namensschildern, Wasser, Glas
 * und Partikeln. So bleiben diese unberührt, und auf Seen entstehen keine Pfützen. Die Spiegelung ("Screen-Space-
 * Reflection": Strahlen laufen durch das Tiefenbild) entsteht in kleinerer Auflösung und wird dann in voller Auflösung
 * mit dem Bild zusammengesetzt, das spart viel Leistung. Spiegel-Teil und Aufbau folgen dem Welt-Shader von Versefall
 * (MIT-Lizenz), siehe THIRD_PARTY_NOTICES.md. Es werden keine Blöcke verändert. Schlägt etwas fehl, schaltet sich der
 * Effekt ab, und die einfachen Partikel-Pfützen übernehmen.
 */
public final class PuddleShader {
    private static final String VERTEX = """
            #version 330 core
            out vec2 uv;
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                uv = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String COMMON = """
            #version 330 core
            uniform sampler2D ColorTex;
            uniform sampler2D DepthTex;
            uniform sampler2D ReflTex;
            uniform mat4 Proj;
            uniform mat4 InvProj;
            uniform mat3 ViewRot;
            uniform vec3 CamPos;
            uniform vec2 OutSize;
            uniform vec2 FullSize;
            uniform vec4 Params;   // Flächendeckung, Spiegelstärke, Größe, Qualität (0 niedrig, 1 mittel, 2 hoch)
            uniform vec4 Env;      // Himmelsfarbe rgb, Regen
            uniform vec4 SeasonW;  // Gewichte Frühling, Sommer, Herbst, Winter
            uniform vec4 SeasonO;  // Laubfarben, liegendes Laub, Schneedecke, Blüten (je 0 bis 1)
            uniform float Time;
            uniform float Damp;
            uniform float UsePuddles;
            in vec2 uv;
            out vec4 fragColor;

            float hash(vec2 p) {
                vec3 q = fract(vec3(p.xyx) * 0.1031);
                q += dot(q, q.yzx + 33.33);
                return fract((q.x + q.y) * q.z);
            }
            float noise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
                           mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
            }
            float fbm(vec2 p) {
                float s = 0.0;
                float a = 0.5;
                for (int i = 0; i < 4; i++) {
                    s += a * noise(p);
                    p = p * 2.03 + 11.7;
                    a *= 0.5;
                }
                return s;
            }
            float depthAt(vec2 st) {
                return texture(DepthTex, st).r;
            }
            vec3 viewPos(vec2 st, float d) {
                vec4 p = InvProj * vec4(st * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
                return p.xyz / p.w;
            }
            vec2 project(vec3 v) {
                vec4 c = Proj * vec4(v, 1.0);
                return c.xy / c.w * 0.5 + 0.5;
            }
            // Flächennormale aus dem Tiefenbild; je Achse der glattere Nachbar, damit Kanten sauber bleiben
            vec3 normalAt(vec2 st, vec3 p) {
                vec2 px = 1.0 / FullSize;
                vec3 r = viewPos(st + vec2(px.x, 0.0), depthAt(st + vec2(px.x, 0.0)));
                vec3 l = viewPos(st - vec2(px.x, 0.0), depthAt(st - vec2(px.x, 0.0)));
                vec3 u = viewPos(st + vec2(0.0, px.y), depthAt(st + vec2(0.0, px.y)));
                vec3 d = viewPos(st - vec2(0.0, px.y), depthAt(st - vec2(0.0, px.y)));
                vec3 dx = abs(r.z - p.z) < abs(l.z - p.z) ? r - p : p - l;
                vec3 dy = abs(u.z - p.z) < abs(d.z - p.z) ? u - p : p - d;
                vec3 n = normalize(cross(dx, dy));
                return dot(n, -p) < 0.0 ? -n : n;
            }
            vec3 skyAt(vec3 dirWorld) {
                float t = clamp(dirWorld.y, 0.0, 1.0);
                vec3 horizon = mix(Env.rgb, vec3(1.0), 0.35);
                return mix(horizon, Env.rgb * 0.85, sqrt(t));
            }

            // ---------------------------------------------------------------- Jahreszeiten
            vec3 rgb2hsv(vec3 c) {
                vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
                vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
                vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
                float d = q.x - min(q.w, q.y);
                float e = 1.0e-10;
                return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
            }
            vec3 hsv2rgb(vec3 c) {
                vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
                return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
            }
            // wie "pflanzengrün" ist eine Farbe (Gras, Laub): 1 = eindeutig grün
            float greenness(vec3 hsv) {
                float g = smoothstep(0.17, 0.22, hsv.x) * (1.0 - smoothstep(0.45, 0.52, hsv.x));
                return g * smoothstep(0.10, 0.28, hsv.y) * smoothstep(0.06, 0.18, hsv.z);
            }
            // Laub und Gras ändern ihre Farbe mit der Jahreszeit
            vec3 seasonFoliage(vec3 c, vec3 wp) {
                float strength = SeasonO.x;
                if (strength < 0.01) {
                    return c;
                }
                vec3 hsv = rgb2hsv(c);
                float gr = greenness(hsv);
                if (gr < 0.01) {
                    return c;
                }
                float n = fbm(wp.xz * 0.16 + wp.y * 0.07);
                // Herbst: Orange, Gelb und Rot, je Baumgruppe verschieden
                vec3 autumn = hsv2rgb(vec3(mix(0.012, 0.125, n), clamp(hsv.y * 0.9 + 0.32, 0.0, 1.0), min(1.0, hsv.z * 1.35 + 0.12)));
                // Winter: verschneit
                vec3 winter = mix(vec3(0.86, 0.90, 0.95), vec3(0.60, 0.67, 0.74), n * 0.6) * (0.70 + 0.45 * hsv.z);
                // Frühling: frisches, helleres Grün
                vec3 spring = hsv2rgb(vec3(mix(hsv.x, 0.27, 0.40), min(1.0, hsv.y * 1.05), min(1.0, hsv.z * 1.12)));
                // Sommer: satteres Grün
                vec3 summer = hsv2rgb(vec3(hsv.x, min(1.0, hsv.y * 1.12), hsv.z));
                vec3 target = spring * SeasonW.x + summer * SeasonW.y + autumn * SeasonW.z + winter * SeasonW.w;
                return mix(c, target, clamp(strength * gr, 0.0, 1.0));
            }
            """;

    private static final String FRAGMENT_R = """
            // ---------------------------------------------------------------- Spiegelung (kleinere Auflösung)
            void main() {
                float d = depthAt(uv);
                if (d >= 1.0) {
                    fragColor = vec4(0.0);
                    return;
                }
                vec3 p = viewPos(uv, d);
                float dist = length(p);
                if (dist > 110.0) {
                    fragColor = vec4(0.0);
                    return;
                }
                vec3 v = normalize(p);
                vec3 n = normalAt(uv, p);
                mat3 toWorld = transpose(ViewRot);
                vec3 nw = toWorld * n;
                vec3 pw = toWorld * p;

                float upFacing = smoothstep(0.80, 0.97, nw.y);
                float wet = upFacing * clamp(Params.y, 0.0, 1.0);
                if (wet < 0.01) {
                    fragColor = vec4(0.0);
                    return;
                }
                float scale = max(0.5, Params.z);
                vec2 ground = mod(pw.xz + CamPos.xz, 4096.0);
                float shape = fbm(ground * (0.28 / scale)) * 0.75 + fbm(ground * (1.3 / scale)) * 0.25;
                float coverage = clamp(Params.x + Env.a * 0.2, 0.0, 1.0);
                float puddle = smoothstep(1.0 - coverage - 0.08, 1.0 - coverage + 0.08, shape);
                if (coverage > 0.985) {
                    puddle = 1.0;
                }
                float distFade = 1.0 - smoothstep(70.0, 110.0, dist);
                // grobe Vorabprüfung ohne Normalenkorrektur: nur nasser Boden und Pfützen ohne Wert überspringen
                if (wet * mix(0.32 * Damp, 0.75, puddle) * distFade < 0.02) {
                    fragColor = vec4(0.0);
                    return;
                }

                if (Params.w > 1.5 && Env.a > 0.05) {
                    vec2 q = ground * 6.0;
                    vec3 rip = vec3(noise(q + Time * 2.1) - 0.5, 0.0, noise(q * 1.3 - Time * 1.7) - 0.5);
                    nw = normalize(nw + rip * 0.12 * Env.a * puddle);
                    n = normalize(ViewRot * nw);
                }

                vec3 rw = toWorld * reflect(v, n);
                vec3 skyRefl = skyAt(rw);
                vec3 refl = skyRefl;
                float found = 0.0;
                if (Params.w > 0.5 && puddle + Damp > 0.05) {
                    int steps = Params.w > 1.5 ? 36 : 22;
                    vec3 r = normalize(reflect(v, n));
                    vec3 pos = p + n * 0.03;
                    float stepLen = 0.14 + dist * 0.014;
                    vec2 hitUv = vec2(0.0);
                    for (int i = 0; i < steps; i++) {
                        vec3 prev = pos;
                        pos += r * stepLen;
                        stepLen *= 1.12;
                        vec2 suv = project(pos);
                        if (suv.x < 0.0 || suv.x > 1.0 || suv.y < 0.0 || suv.y > 1.0 || pos.z > -0.05) {
                            break;
                        }
                        float sd = depthAt(suv);
                        if (sd >= 1.0) {
                            continue;
                        }
                        float diff = viewPos(suv, sd).z - pos.z;
                        if (diff > 0.0 && diff < stepLen * 2.5 + 0.3) {
                            vec3 a = prev;
                            vec3 b = pos;
                            for (int k = 0; k < 4; k++) {
                                vec3 m = (a + b) * 0.5;
                                vec2 muv = project(m);
                                float md = viewPos(muv, depthAt(muv)).z;
                                if (md - m.z > 0.0) {
                                    b = m;
                                } else {
                                    a = m;
                                }
                            }
                            hitUv = project(b);
                            vec2 edge = min(hitUv, 1.0 - hitUv);
                            found = smoothstep(0.0, 0.08, min(edge.x, edge.y)) * (1.0 - float(i) / float(steps));
                            break;
                        }
                    }
                    if (found > 0.0) {
                        // das gespiegelte Bild bekommt dieselben Jahreszeiten-Farben wie die Welt selbst
                        vec3 hitView = viewPos(hitUv, depthAt(hitUv));
                        vec3 hitWorld = toWorld * hitView + vec3(CamPos.x, CamPos.y, CamPos.z);
                        vec3 hitCol = seasonFoliage(texture(ColorTex, hitUv).rgb, vec3(mod(hitWorld.x, 4096.0), hitWorld.y, mod(hitWorld.z, 4096.0)));
                        refl = mix(skyRefl, hitCol, found);
                    }
                }
                float fresnel = 0.02 + 0.98 * pow(1.0 - max(dot(-v, n), 0.0), 5.0);
                float amount = wet * mix((0.32 + 0.4 * fresnel) * Damp, 0.75, puddle) * distFade;
                amount = clamp(amount, 0.0, 0.78);
                fragColor = vec4(refl * amount, amount);
            }
            """;

    private static final String FRAGMENT_C = """
            // ---------------------------------------------------------------- Zusammensetzen (volle Auflösung)
            void main() {
                vec3 base = texture(ColorTex, uv).rgb;
                float d = depthAt(uv);
                if (d >= 1.0) {
                    fragColor = vec4(base, 1.0);
                    return;
                }
                vec3 p = viewPos(uv, d);
                float dist = length(p);
                mat3 toWorld = transpose(ViewRot);
                vec3 pw = toWorld * p;
                vec3 wp = vec3(mod(pw.x + CamPos.x, 4096.0), pw.y + CamPos.y, mod(pw.z + CamPos.z, 4096.0));

                vec3 col = seasonFoliage(base, wp);

                bool groundWork = (SeasonO.y + SeasonO.z + SeasonO.w) > 0.01 || UsePuddles > 0.5;
                float up = 0.0;
                if (groundWork) {
                    vec3 n = normalAt(uv, p);
                    up = smoothstep(0.80, 0.97, (toWorld * n).y);
                }
                if (up > 0.01 && (SeasonO.y + SeasonO.z + SeasonO.w) > 0.01) {
                    vec3 hsv = rgb2hsv(base);
                    float isGreen = greenness(hsv);
                    float isDirt = smoothstep(0.03, 0.08, hsv.x) * (1.0 - smoothstep(0.12, 0.18, hsv.x)) * smoothstep(0.2, 0.4, hsv.y) * (1.0 - smoothstep(0.55, 0.75, hsv.z));
                    float ground = clamp(isGreen + isDirt, 0.0, 1.0);
                    float near = 1.0 - smoothstep(35.0, 75.0, dist);
                    vec2 g = wp.xz;
                    // Herbst: liegendes Laub in Flecken
                    float litterW = SeasonW.z * SeasonO.y * ground * up;
                    if (litterW > 0.01) {
                        float patch = smoothstep(0.38, 0.62, fbm(g * 0.35));
                        vec2 cell = floor(g * 3.2);
                        vec2 inCell = fract(g * 3.2) - 0.5;
                        float leaf = (1.0 - smoothstep(0.16, 0.30, length(inCell + (vec2(hash(cell), hash(cell + 7.1)) - 0.5) * 0.4)))
                                   * step(0.30, hash(cell + 3.3));
                        float h = hash(cell + 9.7);
                        vec3 leafCol = h < 0.35 ? vec3(0.78, 0.30, 0.08) : (h < 0.7 ? vec3(0.86, 0.58, 0.12) : vec3(0.52, 0.22, 0.08));
                        float m = clamp(leaf * (0.45 + 0.55 * patch) * litterW * near * 1.4, 0.0, 0.92);
                        col = mix(col, leafCol * (0.8 + 0.4 * hash(cell + 1.7)), m);
                    }
                    // Winter: Schneedecke
                    float snowW = SeasonW.w * SeasonO.z * up * (1.0 - smoothstep(0.55, 0.85, hsv.y) * 0.0);
                    if (snowW > 0.01) {
                        float lum = dot(base, vec3(0.299, 0.587, 0.114));
                        float sand = smoothstep(0.55, 0.7, hsv.z) * smoothstep(0.02, 0.07, hsv.x) * (1.0 - smoothstep(0.11, 0.16, hsv.x)) * (1.0 - smoothstep(0.35, 0.5, hsv.y));
                        float amt = clamp(snowW * (0.82 + 0.18 * noise(g * 2.0)) * (1.0 - sand * 0.9), 0.0, 0.95);
                        vec3 snow = vec3(0.93, 0.95, 1.0) * (0.82 + 0.25 * lum);
                        col = mix(col, snow, amt * (1.0 - 0.35 * (1.0 - ground)));
                    }
                    // Frühling: Blüten im Gras
                    float bloomW = SeasonW.x * SeasonO.w * isGreen * up;
                    if (bloomW > 0.01) {
                        vec2 cell = floor(g * 2.6);
                        vec2 inCell = fract(g * 2.6) - 0.5;
                        float on = step(0.80, hash(cell + 4.4));
                        float dot2 = 1.0 - smoothstep(0.07, 0.15, length(inCell + (vec2(hash(cell), hash(cell + 2.2)) - 0.5) * 0.5));
                        float h = hash(cell + 8.8);
                        vec3 flowerCol = h < 0.3 ? vec3(1.0, 0.55, 0.75) : (h < 0.6 ? vec3(1.0, 0.98, 0.92) : (h < 0.85 ? vec3(1.0, 0.86, 0.25) : vec3(0.55, 0.65, 1.0)));
                        col = mix(col, flowerCol, clamp(on * dot2 * bloomW * near * 1.2, 0.0, 0.95));
                    }
                }
                if (UsePuddles > 0.5) {
                    vec4 r = texture(ReflTex, uv);
                    float a = r.a * up;
                    vec3 darkened = col * mix(1.0, 0.8, clamp(a * 1.3, 0.0, 1.0));
                    col = darkened * (1.0 - a) + r.rgb * up;
                }
                fragColor = vec4(col, 1.0);
            }
            """;

    private static final class Prog {
        int id;
        final Map<String, Integer> locs = new HashMap<>();

        int loc(String name) {
            return locs.computeIfAbsent(name, k -> GL20.glGetUniformLocation(id, k));
        }
    }

    private static Prog progR;
    private static Prog progC;
    private static int vao;
    private static int samplerNearest;
    private static int samplerLinear;
    private static int outFbo;
    private static int outTex;
    private static int outW;
    private static int outH;
    private static int reflFbo;
    private static int reflTex;
    private static int reflW;
    private static int reflH;
    private static int mainFbo;
    private static int mainFboColor;
    private static boolean failed;
    private static int errors;
    private static boolean loggedStart;
    private static boolean midHookSeen;
    private static final Matrix4f SAVED_VIEW = new Matrix4f();
    private static final Matrix4f SAVED_PROJECTION = new Matrix4f();
    private static boolean savedValid;

    private PuddleShader() {
    }

    // ------------------------------------------------------------------ Schalter

    /** Sind die Spiegel-Pfützen per Shader aktiv? (Dann pausieren die einfachen Partikel-Pfützen.) */
    public static boolean working() {
        return puddlesOn();
    }

    private static boolean puddlesOn() {
        return !failed && Opts.active("puddles") && "reflect".equals(Opts.text("puddles.mode"))
                && Opts.num("puddles.reflection") > 0.001;
    }

    private static boolean seasonsOn() {
        return !failed && Opts.active("seasons") && (Opts.bool("seasons.foliageColors") || Opts.bool("seasons.groundLitter")
                || Opts.bool("seasons.snowCover") || Opts.bool("seasons.blossoms"));
    }

    // ------------------------------------------------------------------ Einstiegspunkte

    /** Zu Beginn von renderLevel: Kamera-Matrizen kopieren, weil sie im Lauf verändert werden können. */
    public static void begin(Matrix4f view, Matrix4f projection) {
        SAVED_VIEW.set(view);
        SAVED_PROJECTION.set(projection);
        savedValid = true;
    }

    /** Direkt nach dem festen Gelände (bevorzugter Zeitpunkt). */
    public static void afterTerrain() {
        midHookSeen = true;
        run();
    }

    /** Ende von renderLevel: nur als Ersatz, falls der frühere Zeitpunkt nicht greift. */
    public static void atEnd() {
        if (!midHookSeen) {
            run();
        }
    }

    private static void run() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!savedValid || level == null || mc.player == null || level.dimension() != Level.OVERWORLD
                || (!puddlesOn() && !seasonsOn())) {
            return;
        }
        try {
            draw(mc, level, mc.gameRenderer.getMainCamera(), SAVED_VIEW, SAVED_PROJECTION);
        } catch (Throwable t) {
            errors++;
            Log.error("Pfützen-Shader Fehler " + errors + "/3: " + t.getMessage(), t);
            if (errors == 1 && mc.player != null) {
                String msg = String.valueOf(t.getMessage()).replace('\n', ' ');
                mc.player.displayClientMessage(Component.translatable("visuals.puddles.failed",
                        msg.length() > 150 ? msg.substring(0, 150) : msg), false);
            }
            if (errors >= 3) {
                failed = true;
            }
        }
    }

    // ------------------------------------------------------------------ Zeichnen

    private static void draw(Minecraft mc, ClientLevel level, Camera camera, Matrix4f view, Matrix4f projection) {
        RenderTarget main = mc.getMainRenderTarget();
        if (main.getColorTexture() == null || main.getDepthTexture() == null) {
            return;
        }
        int w = main.width;
        int h = main.height;
        int colorId = ((GlTexture) main.getColorTexture()).glId();
        int depthId = ((GlTexture) main.getDepthTexture()).glId();
        boolean puddles = puddlesOn();
        double res = switch (Opts.text("puddles.resolution")) {
            case "full" -> 1.0;
            case "third" -> 0.34;
            default -> 0.5;
        };
        int rw = Math.max(1, (int) Math.round(w * res));
        int rh = Math.max(1, (int) Math.round(h * res));

        // aktuellen GL-Zustand merken
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] prevTex = new int[3];
        for (int u = 0; u < 3; u++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + u);
            prevTex[u] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        ByteBuffer maskBuf = BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, maskBuf);
        try {
            if (progC == null) {
                build();
            }
            ensureOut(w, h);
            ensureMainFbo(colorId);
            if (puddles) {
                ensureRefl(rw, rh);
            }
            GL11.glColorMask(true, true, true, true);
            GlStateManager._disableBlend();
            GlStateManager._disableDepthTest();
            GlStateManager._disableCull();
            GlStateManager._disableScissorTest();
            GL30.glBindVertexArray(vao);

            // Samplerbindungen: Einheit 0 und 1 = Bild und Tiefe (nächster Nachbar), 2 = Spiegelung (weich)
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(colorId);
            GL33.glBindSampler(0, samplerNearest);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            GlStateManager._bindTexture(depthId);
            GL33.glBindSampler(1, samplerNearest);

            if (puddles) {
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, reflFbo);
                GlStateManager._viewport(0, 0, rw, rh);
                GlStateManager._glUseProgram(progR.id);
                setUniforms(progR, camera, view, projection, level, rw, rh, w, h, true);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                GlStateManager._activeTexture(GL13.GL_TEXTURE2);
                GlStateManager._bindTexture(reflTex);
                GL33.glBindSampler(2, samplerLinear);
            }

            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, outFbo);
            GlStateManager._viewport(0, 0, w, h);
            GlStateManager._glUseProgram(progC.id);
            setUniforms(progC, camera, view, projection, level, w, h, w, h, puddles);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            if (!loggedStart) {
                loggedStart = true;
                Log.warn("Pfützen-Shader läuft ({}x{}, Spiegelung {}, Jahreszeiten {})", w, h, puddles, seasonsOn());
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("visuals.puddles.running"), true);
                }
            }

            // Ergebnis zurück in das Hauptbild kopieren
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, outFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainFbo);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            GL30.glBindVertexArray(prevVao);
            GL11.glColorMask(maskBuf.get(0) != 0, maskBuf.get(1) != 0, maskBuf.get(2) != 0, maskBuf.get(3) != 0);
            for (int u = 2; u >= 0; u--) {
                GL33.glBindSampler(u, 0);
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + u);
                GlStateManager._bindTexture(prevTex[u]);
            }
            GlStateManager._activeTexture(prevActive);
            GlStateManager._glUseProgram(prevProgram);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GlStateManager._viewport(vp[0], vp[1], vp[2], vp[3]);
            if (blend) {
                GlStateManager._enableBlend();
            }
            if (depthTest) {
                GlStateManager._enableDepthTest();
            }
            if (cull) {
                GlStateManager._enableCull();
            }
            if (scissor) {
                GlStateManager._enableScissorTest();
            }
        }
    }

    private static void setUniforms(Prog p, Camera camera, Matrix4f view, Matrix4f projection, ClientLevel level,
                                    int outW, int outH, int fullW, int fullH, boolean usePuddles) {
        float[] m = new float[16];
        GL20.glUniformMatrix4fv(p.loc("Proj"), false, projection.get(m));
        GL20.glUniformMatrix4fv(p.loc("InvProj"), false, new Matrix4f(projection).invert().get(m));
        GL20.glUniformMatrix3fv(p.loc("ViewRot"), false, new Matrix3f(view).get(new float[9]));
        Vec3 cam = camera.position();
        GL20.glUniform3f(p.loc("CamPos"), (float) (cam.x % 4096.0), (float) cam.y, (float) (cam.z % 4096.0));
        GL20.glUniform2f(p.loc("OutSize"), outW, outH);
        GL20.glUniform2f(p.loc("FullSize"), fullW, fullH);
        String q = Opts.text("puddles.quality");
        float quality = "low".equals(q) ? 0f : "high".equals(q) ? 2f : 1f;
        GL20.glUniform4f(p.loc("Params"), Opts.integer("puddles.coverage") / 100f, (float) Opts.num("puddles.reflection"),
                (float) Opts.num("puddles.size"), quality);
        int sky = PuddleLook.skyColor(level.getDayTime());
        GL20.glUniform4f(p.loc("Env"), ((sky >> 16) & 255) / 255f, ((sky >> 8) & 255) / 255f, (sky & 255) / 255f,
                level.getRainLevel(1.0f));
        boolean on = seasonsOn();
        float[] sw = on ? SeasonClock.weights() : new float[4];
        GL20.glUniform4f(p.loc("SeasonW"), sw[0], sw[1], sw[2], sw[3]);
        GL20.glUniform4f(p.loc("SeasonO"),
                on && Opts.bool("seasons.foliageColors") ? (float) Opts.num("seasons.foliageStrength") : 0f,
                on && Opts.bool("seasons.groundLitter") ? (float) Opts.num("seasons.litterAmount") : 0f,
                on && Opts.bool("seasons.snowCover") ? (float) Opts.num("seasons.snowAmount") : 0f,
                on && Opts.bool("seasons.blossoms") ? 1f : 0f);
        GL20.glUniform1f(p.loc("Time"), (float) (System.nanoTime() / 1.0e9 % 1000.0));
        GL20.glUniform1f(p.loc("Damp"), (float) Opts.num("puddles.damp"));
        GL20.glUniform1f(p.loc("UsePuddles"), usePuddles ? 1f : 0f);
        GL20.glUniform1i(p.loc("ColorTex"), 0);
        GL20.glUniform1i(p.loc("DepthTex"), 1);
        GL20.glUniform1i(p.loc("ReflTex"), 2);
    }

    // ------------------------------------------------------------------ Aufbau

    private static int compile(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(s);
            GL20.glDeleteShader(s);
            throw new IllegalStateException("Shader-Fehler: " + log);
        }
        return s;
    }

    private static Prog program(String fragmentBody) {
        int vs = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, COMMON + "\n" + fragmentBody);
        int id = GL20.glCreateProgram();
        GL20.glAttachShader(id, vs);
        GL20.glAttachShader(id, fs);
        GL20.glLinkProgram(id);
        if (GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(id);
            GL20.glDeleteProgram(id);
            throw new IllegalStateException("Programm-Fehler: " + log);
        }
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        Prog p = new Prog();
        p.id = id;
        return p;
    }

    private static void build() {
        Prog r = program(FRAGMENT_R);
        Prog c = program(FRAGMENT_C);
        vao = GL30.glGenVertexArrays();
        samplerNearest = sampler(GL11.GL_NEAREST);
        samplerLinear = sampler(GL11.GL_LINEAR);
        progR = r;
        progC = c;
    }

    private static int sampler(int filter) {
        int s = GL33.glGenSamplers();
        GL33.glSamplerParameteri(s, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL33.glSamplerParameteri(s, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL33.glSamplerParameteri(s, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
        GL33.glSamplerParameteri(s, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
        return s;
    }

    private static int newTexture(int w, int h) {
        int t = GL11.glGenTextures();
        GlStateManager._bindTexture(t);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        return t;
    }

    private static int newFbo(int tex) {
        int f = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, f);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        return f;
    }

    private static void ensureOut(int w, int h) {
        if (outFbo != 0 && outW == w && outH == h) {
            return;
        }
        if (outFbo != 0) {
            GL30.glDeleteFramebuffers(outFbo);
            GL11.glDeleteTextures(outTex);
        }
        outTex = newTexture(w, h);
        outFbo = newFbo(outTex);
        outW = w;
        outH = h;
    }

    private static void ensureRefl(int w, int h) {
        if (reflFbo != 0 && reflW == w && reflH == h) {
            return;
        }
        if (reflFbo != 0) {
            GL30.glDeleteFramebuffers(reflFbo);
            GL11.glDeleteTextures(reflTex);
        }
        reflTex = newTexture(w, h);
        reflFbo = newFbo(reflTex);
        reflW = w;
        reflH = h;
    }

    private static void ensureMainFbo(int colorId) {
        if (mainFbo != 0 && mainFboColor == colorId) {
            return;
        }
        if (mainFbo != 0) {
            GL30.glDeleteFramebuffers(mainFbo);
        }
        mainFbo = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorId, 0);
        mainFboColor = colorId;
    }
}
