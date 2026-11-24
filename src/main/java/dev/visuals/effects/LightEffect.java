package dev.visuals.effects;

import dev.visuals.VisualsMod;
import dev.visuals.config.OptionDef;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Licht: tönt die Himmelslicht-Farbe der Lichtkarte nach Tageszeit (Tag, Morgen, Abend, Nacht) und skaliert das
 * Mondlicht. Wirkt nur, wenn ein Wert vom Standard abweicht, sonst bleibt das Licht wie in Minecraft.
 * Nicht enthalten: dynamisches Licht (Fackeln in der Hand leuchten nicht).
 */
public final class LightEffect {
    private static final String[] COLOR_IDS = {"light.dayColor", "light.dawnColor", "light.duskColor", "light.nightColor"};
    private static int errors;

    private LightEffect() {
    }

    private static int defaultColor(String id) {
        OptionDef<?> def = VisualsMod.registry().get(id);
        return def != null && def.defaultValue() instanceof Integer i ? i : 0xFFFFFFFF;
    }

    private static boolean modified() {
        for (String id : COLOR_IDS) {
            if (VisualsMod.config().isModified(id)) {
                return true;
            }
        }
        return VisualsMod.config().isModified("light.moonStrength");
    }

    private static double nightFactor(double hour) {
        if (hour >= 7 && hour <= 17) {
            return 0;
        }
        if (hour <= 4.5 || hour >= 20.5) {
            return 1;
        }
        return hour < 7 ? (7 - hour) / 2.5 : (hour - 17) / 3.5;
    }

    /** Wird mit der Himmelslicht-Farbe aufgerufen und liefert die getönte Farbe. */
    public static Vector3fc tint(Vector3fc color) {
        Minecraft mc = Minecraft.getInstance();
        if (errors >= 5 || mc.level == null || !Opts.active("light") || !modified()) {
            return color;
        }
        try {
            long dayTime = mc.level.getDayTime();
            double hour = (((dayTime % 24000L) / 1000.0) + 6.0) % 24.0;
            int custom = SkyColors.at(hour, Opts.color("light.nightColor"), Opts.color("light.dawnColor"),
                    Opts.color("light.dayColor"), Opts.color("light.duskColor"));
            int base = SkyColors.at(hour, defaultColor("light.nightColor"), defaultColor("light.dawnColor"),
                    defaultColor("light.dayColor"), defaultColor("light.duskColor"));
            float r = ratio((custom >> 16) & 255, (base >> 16) & 255);
            float g = ratio((custom >> 8) & 255, (base >> 8) & 255);
            float b = ratio(custom & 255, base & 255);
            double night = nightFactor(hour);
            double moon = 1.0;
            if (VisualsMod.config().isModified("light.moonStrength")) {
                moon = 1.0 + (Opts.num("light.moonStrength") / 0.6 - 1.0) * night;
                if (Opts.bool("light.moonByPhase")) {
                    double phase = Math.floorMod(Math.floorDiv(dayTime, 24000L), 8L);
                    moon *= 0.35 + 0.65 * (1.0 + Math.cos(phase * Math.PI / 4.0)) / 2.0 * (night > 0.5 ? 1.0 : 1.0);
                }
            }
            float m = (float) Math.max(0.0, Math.min(3.0, moon));
            return new Vector3f(color.x() * r * m, color.y() * g * m, color.z() * b * m);
        } catch (RuntimeException e) {
            errors++;
            return color;
        }
    }

    private static float ratio(int custom, int base) {
        return base <= 0 ? 1f : Math.max(0f, Math.min(2.5f, custom / (float) base));
    }
}
