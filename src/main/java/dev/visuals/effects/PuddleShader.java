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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/**
 * Pfützen mit Spiegelung: Nach dem Zeichnen der Welt läuft ein Vollbild-Shader über das fertige Bild (Technik und Aufbau nach dem Welt-Shader von Versefall, MIT-Lizenz).
 * Er liest Bild und Tiefe, erkennt waagerechte Flächen, legt per Rauschen Pfützen darauf und spiegelt die Szene
 * darin ("Screen-Space-Reflection": Strahlen laufen durch das Tiefenbild). Was nicht im Bild ist, wird durch die
 * Himmelsfarbe ersetzt. Es werden keine Blöcke verändert. Schlägt etwas fehl, schaltet sich der Effekt ab und die
 * einfachen Partikel-Pfützen übernehmen.
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

    // Der Spiegel-Teil dieses Shaders (nasser Boden, Pfützen aus Rauschen, Strahlen durch das Tiefenbild) folgt dem
    // Welt-Shader von Versefall (MIT-Lizenz, VerseFall-Development), siehe THIRD_PARTY_NOTICES.md.
    private static final String FRAGMENT = """
            #version 330 core
            uniform sampler2D ColorTex;
            uniform sampler2D DepthTex;
            uniform mat4 Proj;
            uniform mat4 InvProj;
            uniform mat3 ViewRot;
            uniform vec3 CamPos;
            uniform vec2 OutSize;
            uniform vec4 Params;   // Flächendeckung, Spiegelstärke, Größe, Qualität (0 niedrig, 1 mittel, 2 hoch)
            uniform vec4 Env;      // Himmelsfarbe rgb, Regen
            uniform float Time;
            uniform float Damp;    // wie stark der nasse Boden außerhalb der Pfützen spiegelt
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
                vec2 px = 1.0 / OutSize;
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

            void main() {
                vec3 base = texture(ColorTex, uv).rgb;
                float d = depthAt(uv);
                if (d >= 1.0) {
                    fragColor = vec4(base, 1.0);
                    return;
                }
                vec3 p = viewPos(uv, d);
                vec3 v = normalize(p);
                vec3 n = normalAt(uv, p);
                mat3 toWorld = transpose(ViewRot);
                vec3 nw = toWorld * n;
                vec3 pw = toWorld * p;

                // nass: alles, was nach oben zeigt; Pfützen dort, wo das Rauschen hoch ist
                float upFacing = smoothstep(0.80, 0.97, nw.y);
                float wet = upFacing * clamp(Params.y, 0.0, 1.0);
                if (wet < 0.01) {
                    fragColor = vec4(base, 1.0);
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

                // Regen kräuselt die Oberfläche in den Pfützen leicht
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
                if (Params.w > 0.5) {
                    int steps = Params.w > 1.5 ? 44 : 28;
                    vec3 r = normalize(reflect(v, n));
                    vec3 pos = p + n * 0.03;
                    float stepLen = 0.12 + length(p) * 0.012;
                    vec2 hitUv = vec2(0.0);
                    for (int i = 0; i < steps; i++) {
                        vec3 prev = pos;
                        pos += r * stepLen;
                        stepLen *= 1.1;
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
                            for (int k = 0; k < 5; k++) {
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
                        refl = mix(skyRefl, texture(ColorTex, hitUv).rgb, found);
                    }
                }
                float fresnel = 0.02 + 0.98 * pow(1.0 - max(dot(-v, n), 0.0), 5.0);
                // feuchter Boden glänzt nur in flachen Winkeln, Pfützen sind echte Spiegel
                float amount = wet * mix((0.32 + 0.4 * fresnel) * Damp, 0.75, puddle);
                vec3 darkened = base * mix(1.0, 0.8, wet);
                fragColor = vec4(mix(darkened, refl, clamp(amount, 0.0, 0.78)), 1.0);
            }
            """;

    private static int program;
    private static int vao;
    private static int outFbo;
    private static int outTex;
    private static int outW;
    private static int outH;
    private static int mainFbo;
    private static int mainFboColor;
    private static int sampler;
    private static boolean failed;
    private static int errors;
    private static int[] loc;

    private PuddleShader() {
    }

    /** Läuft der Shader-Effekt (also müssen die Partikel-Pfützen pausieren)? */
    public static boolean working() {
        return !failed && Opts.active("puddles") && "reflect".equals(Opts.text("puddles.mode"));
    }

    private static final Matrix4f SAVED_VIEW = new Matrix4f();
    private static final Matrix4f SAVED_PROJECTION = new Matrix4f();
    private static boolean savedValid;
    private static boolean loggedStart;

    /** Zu Beginn von renderLevel: Kamera-Matrizen kopieren, weil sie im Lauf verändert werden können. */
    public static void begin(Matrix4f view, Matrix4f projection) {
        SAVED_VIEW.set(view);
        SAVED_PROJECTION.set(projection);
        savedValid = true;
    }

    /** Wird nach dem Zeichnen der Welt aufgerufen. */
    public static void apply(Camera camera, Matrix4f view, Matrix4f projection) {
        if (savedValid) {
            view = SAVED_VIEW;
            projection = SAVED_PROJECTION;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!working() || level == null || level.dimension() != Level.OVERWORLD || mc.player == null) {
            return;
        }
        double coverage = Opts.integer("puddles.coverage") / 100.0;
        if (Opts.num("puddles.reflection") <= 0.001) {
            return;
        }
        try {
            draw(mc, level, camera, view, projection, coverage);
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

    private static void draw(Minecraft mc, ClientLevel level, Camera camera, Matrix4f view, Matrix4f projection,
                             double coverage) {
        RenderTarget main = mc.getMainRenderTarget();
        if (main.getColorTexture() == null || main.getDepthTexture() == null) {
            return;
        }
        int w = main.width;
        int h = main.height;
        int colorId = ((GlTexture) main.getColorTexture()).glId();
        int depthId = ((GlTexture) main.getDepthTexture()).glId();
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
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int prevTex1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        java.nio.ByteBuffer maskBuf = org.lwjgl.BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, maskBuf);
        try {
            if (program == 0) {
                build();
            }
            ensureTargets(w, h, colorId);
            GL11.glColorMask(true, true, true, true);
            GlStateManager._disableBlend();
            GlStateManager._disableDepthTest();
            GlStateManager._disableCull();
            GlStateManager._disableScissorTest();

            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, outFbo);
            GlStateManager._viewport(0, 0, w, h);
            GlStateManager._glUseProgram(program);

            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(colorId);
            GL33.glBindSampler(0, sampler);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            GlStateManager._bindTexture(depthId);
            GL33.glBindSampler(1, sampler);

            Matrix4f inv = new Matrix4f(projection).invert();
            float[] m = new float[16];
            GL20.glUniformMatrix4fv(loc[0], false, projection.get(m));
            GL20.glUniformMatrix4fv(loc[1], false, inv.get(m));
            GL20.glUniformMatrix3fv(loc[2], false, new Matrix3f(view).get(new float[9]));
            Vec3 cam = camera.position();
            GL20.glUniform3f(loc[3], (float) (cam.x % 4096.0), (float) cam.y, (float) (cam.z % 4096.0));
            GL20.glUniform2f(loc[4], w, h);
            String q = Opts.text("puddles.quality");
            float quality = "low".equals(q) ? 0f : "high".equals(q) ? 2f : 1f;
            GL20.glUniform4f(loc[5], (float) coverage, (float) Opts.num("puddles.reflection"),
                    (float) Opts.num("puddles.size"), quality);
            int sky = PuddleLook.skyColor(level.getDayTime());
            GL20.glUniform4f(loc[6], ((sky >> 16) & 255) / 255f, ((sky >> 8) & 255) / 255f, (sky & 255) / 255f,
                    level.getRainLevel(1.0f));
            GL20.glUniform1f(loc[7], (System.nanoTime() / 1_000_000_000.0 % 1000.0) > 0 ? (float) (System.nanoTime() / 1.0e9 % 1000.0) : 0f);
            GL20.glUniform1i(loc[8], 0);
            GL20.glUniform1i(loc[9], 1);
            GL20.glUniform1f(loc[10], (float) Opts.num("puddles.damp"));

            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            if (!loggedStart) {
                loggedStart = true;
                Log.warn("Pfützen-Shader läuft ({}x{}, Flächendeckung {} %)", w, h, (int) Math.round(coverage * 100));
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
            GL33.glBindSampler(1, 0);
            GL33.glBindSampler(0, 0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            GlStateManager._bindTexture(prevTex1);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(prevTex0);
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

    private static void build() {
        int vs = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(p);
            GL20.glDeleteProgram(p);
            throw new IllegalStateException("Programm-Fehler: " + log);
        }
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        String[] names = {"Proj", "InvProj", "ViewRot", "CamPos", "OutSize", "Params", "Env", "Time", "ColorTex", "DepthTex", "Damp"};
        loc = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            loc[i] = GL20.glGetUniformLocation(p, names[i]);
        }
        vao = GL30.glGenVertexArrays();
        sampler = GL33.glGenSamplers();
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
        program = p;
    }

    private static void ensureTargets(int w, int h, int colorId) {
        if (outFbo == 0 || outW != w || outH != h) {
            if (outFbo != 0) {
                GL30.glDeleteFramebuffers(outFbo);
                GL11.glDeleteTextures(outTex);
            }
            outTex = GL11.glGenTextures();
            GlStateManager._bindTexture(outTex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            outFbo = GL30.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, outFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, outTex, 0);
            outW = w;
            outH = h;
        }
        if (mainFbo == 0 || mainFboColor != colorId) {
            if (mainFbo != 0) {
                GL30.glDeleteFramebuffers(mainFbo);
            }
            mainFbo = GL30.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorId, 0);
            mainFboColor = colorId;
        }
    }
}
