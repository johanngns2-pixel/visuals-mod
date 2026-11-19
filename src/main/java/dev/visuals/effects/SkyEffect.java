package dev.visuals.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.visuals.config.def.SkyOptions;
import dev.visuals.config.def.Tabs;
import dev.visuals.util.Log;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.world.level.MoonPhase;

/**
 * Himmel: ändert die Werte, die Minecraft pro Frame für den Himmel berechnet (Himmelsfarbe, Sternhelligkeit,
 * Mondphase), und skaliert Sonne und Mond. Aufgerufen aus {@code SkyRendererMixin}.
 * Bei Fehlern schaltet sich der Effekt nach ein paar Versuchen selbst ab.
 */
public final class SkyEffect {
    private static final int MAX_ERRORS = 5;
    private static int errors;

    private SkyEffect() {
    }

    private static boolean enabled() {
        return errors < MAX_ERRORS && Opts.active(Tabs.SKY);
    }

    public static void apply(ClientLevel level, float partialTick, SkyRenderState state) {
        if (!enabled()) {
            return;
        }
        try {
            // Himmelsfarbe nach Tageszeit
            if (Opts.bool("sky.skyCustom")) {
                long t = level.getDayTime() % 24000L;
                double hour = ((t / 1000.0) + 6.0) % 24.0;
                int custom = SkyColors.at(hour, Opts.color("sky.skyNight"), Opts.color("sky.skyDawn"),
                        Opts.color("sky.skyDay"), Opts.color("sky.skyDusk"));
                // Regen und Gewitter dunkeln den Himmel weiter ab: dazu die Vanilla-Farbe beimischen
                double wet = Math.max(level.getRainLevel(partialTick), 0.0);
                int rgb = SkyColors.mix(custom, state.skyColor & 0xFFFFFF, Math.min(1.0, wet * 0.8));
                state.skyColor = (state.skyColor & 0xFF000000) | rgb;
                if (state.skyColor >>> 24 == 0) {
                    state.skyColor |= 0xFF000000;
                }
            }
            // Gewitter: dunkler Himmel (Reiter Wetter)
            if (Opts.active("weather") && level.isThundering()) {
                double dark = Math.min(1.0, Opts.num("weather.stormDarkness") * Math.max(level.getRainLevel(partialTick), 0f));
                if (dark > 0.001 && Math.abs(Opts.num("weather.stormDarkness") - 0.5) > 0.001) {
                    state.skyColor = (state.skyColor & 0xFF000000)
                            | SkyColors.mix(state.skyColor & 0xFFFFFF, 0x050508, Math.min(0.85, dark * 0.9));
                }
            }
            // Blutmond: rötlicher Nachthimmel, voller Mond, weniger Sterne
            double blood = EventEffect.bloodMoon(level);
            if (blood > 0.0) {
                state.skyColor = (state.skyColor & 0xFF000000) | SkyColors.mix(state.skyColor & 0xFFFFFF, 0x5A0A0A, blood * 0.85);
                state.moonPhase = MoonPhase.FULL_MOON;
                state.starBrightness *= (float) (1.0 - blood * 0.6);
            }
            // Sterne: Helligkeit und Funkeln
            double twinkle = Opts.num("sky.starTwinkle");
            double amount = Opts.num("sky.starAmount");
            float b = state.starBrightness;
            if (b > 0f) {
                double flick = 1.0 - twinkle * 0.45 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 350.0));
                b *= (float) (flick * Math.min(1.0, 0.35 + amount * 0.65));
                state.starBrightness = Math.max(0f, Math.min(1f, b));
            }
            if (!Opts.bool("sky.moonPhases")) {
                state.moonPhase = MoonPhase.FULL_MOON;
            }
        } catch (RuntimeException e) {
            errors++;
            Log.error("Himmel-Effekt Fehler " + errors + "/" + MAX_ERRORS, e);
        }
    }

    /** Wie viele der Sternflächen gezeichnet werden (je 6 Indizes pro Stern), 0 bis zum Original. */
    public static int starIndexCount(int original) {
        if (!enabled()) {
            return original;
        }
        double amount = Opts.num("sky.starAmount");
        double f = Math.max(0.0, Math.min(1.0, amount));
        return ((int) (original * f) / 6) * 6;
    }

    public static float sunScale() {
        return enabled() ? (float) Math.max(0.05, Opts.num(SkyOptions.SUN_SIZE.id())) : 1.0f;
    }

    public static float moonScale() {
        return enabled() ? (float) Math.max(0.05, Opts.num(SkyOptions.MOON_SIZE.id())) : 1.0f;
    }

    /** Skaliert in der Ebene des Himmelskörpers (x und z), die Höhe bleibt. */
    public static void pushScaled(PoseStack pose, float scale) {
        pose.pushPose();
        if (scale != 1.0f) {
            pose.scale(scale, 1.0f, scale);
        }
    }
}
