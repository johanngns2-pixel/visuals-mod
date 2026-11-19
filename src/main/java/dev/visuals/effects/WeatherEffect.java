package dev.visuals.effects;

import dev.visuals.VisualsMod;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.WeatherRenderState;

/**
 * Wetter und Nebel: skaliert Regen- und Schneestärke, verkürzt oder färbt den Nebel um.
 * Alles gilt nur, wenn ein Wert vom Standard abweicht, auf Standardwerten bleibt Minecraft unverändert.
 */
public final class WeatherEffect {
    private static int errors;

    private WeatherEffect() {
    }

    private static boolean on() {
        return errors < 5 && Opts.active("weather");
    }

    public static void applyWeather(WeatherRenderState state) {
        if (!on()) {
            return;
        }
        try {
            boolean snow = !state.snowColumns.isEmpty();
            double a = Opts.num(snow ? "weather.snowAmount" : "weather.rainAmount");
            if (Math.abs(a - 1.0) > 0.001) {
                state.intensity = (float) Math.max(0.0, Math.min(1.0, state.intensity * a));
            }
        } catch (RuntimeException e) {
            errors++;
        }
    }

    public static void applyFog(FogData data) {
        if (!on()) {
            return;
        }
        try {
            double d = Opts.num("weather.fogDensity");
            if (d > 1.001) {
                float end = Math.min(data.environmentalEnd, data.renderDistanceEnd) / (float) d;
                data.environmentalEnd = Math.min(data.environmentalEnd, end);
                data.environmentalStart = Math.min(data.environmentalStart, end * 0.3f);
            }
        } catch (RuntimeException e) {
            errors++;
        }
    }

    public static int fogColor(int base) {
        if (!on() || !VisualsMod.config().isModified("weather.fogColor")) {
            return base;
        }
        try {
            int custom = Opts.color("weather.fogColor");
            double lum = (0.3 * ((base >> 16) & 255) + 0.59 * ((base >> 8) & 255) + 0.11 * (base & 255)) / 255.0;
            double k = Math.max(0.12, Math.min(1.0, lum / 0.75));
            int rgb = SkyColors.mix(base & 0xFFFFFF,
                    (((int) (((custom >> 16) & 255) * k)) << 16) | (((int) (((custom >> 8) & 255) * k)) << 8)
                            | (int) ((custom & 255) * k), 0.65);
            return (base & 0xFF000000) | rgb;
        } catch (RuntimeException e) {
            errors++;
            return base;
        }
    }
}
