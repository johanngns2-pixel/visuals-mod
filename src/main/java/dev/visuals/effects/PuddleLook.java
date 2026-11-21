package dev.visuals.effects;

import net.fabricmc.loader.api.FabricLoader;

/** Aussehen der Pfützen: Spiegelfarbe aus dem Himmel, Glanz und Stärke aus den Einstellungen. */
final class PuddleLook {
    private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");

    private PuddleLook() {
    }

    /** Qualität: "high" gilt nur mit Iris, sonst wie "medium". */
    static String quality() {
        String q = Opts.text("puddles.quality");
        return "high".equals(q) && !IRIS ? "medium" : q;
    }

    static float alpha() {
        return (float) Math.max(0.0, Math.min(0.92, Opts.num("puddles.reflection") * 0.92));
    }

    /** Himmelsfarbe (RGB) zur Tageszeit, wie sie Pfützen und Shader spiegeln. */
    static int skyColor(long dayTime) {
        double hour = (((dayTime % 24000L) / 1000.0) + 6.0) % 24.0;
        boolean custom = Opts.bool("sky.skyCustom") && Opts.active("sky");
        int night = custom ? Opts.color("sky.skyNight") : 0xFF0B1030;
        int dawn = custom ? Opts.color("sky.skyDawn") : 0xFFE8946A;
        int day = custom ? Opts.color("sky.skyDay") : 0xFF78A7FF;
        int dusk = custom ? Opts.color("sky.skyDusk") : 0xFFC96B4F;
        return SkyColors.at(hour, night, dawn, day, dusk);
    }

    /** Farbe, die der Himmel gerade in den Pfützen spiegelt. */
    static int color(long dayTime, float rain, double seed, int age) {
        double hour = (((dayTime % 24000L) / 1000.0) + 6.0) % 24.0;
        boolean custom = Opts.bool("sky.skyCustom") && Opts.active("sky");
        int night = custom ? Opts.color("sky.skyNight") : 0xFF0B1030;
        int dawn = custom ? Opts.color("sky.skyDawn") : 0xFFE8946A;
        int day = custom ? Opts.color("sky.skyDay") : 0xFF78A7FF;
        int dusk = custom ? Opts.color("sky.skyDusk") : 0xFFC96B4F;
        int sky = SkyColors.at(hour, night, dawn, day, dusk);
        // Himmel nahe am Horizont ist heller und blasser, das spiegelt sich flach im Wasser
        int c = SkyColors.mix(sky, 0xFFFFFF, hourIsNight(hour) ? 0.10 : 0.35);
        // Bei Regen wird der Himmel grau und gleichmäßig
        c = SkyColors.mix(c, 0x8A8F96, Math.min(1.0, rain * 0.7));
        if (!"low".equals(quality())) {
            // leichtes Glitzern, als würde Licht über die Wasseroberfläche laufen
            double wave = 0.5 + 0.5 * Math.sin(age * 0.09 + seed) * Math.sin(age * 0.047 + seed * 1.7);
            c = SkyColors.mix(c, 0xFFFFFF, 0.14 * wave * (hourIsNight(hour) ? 0.4 : 1.0));
        }
        return c;
    }

    private static boolean hourIsNight(double hour) {
        return hour < 5.0 || hour > 19.5;
    }
}
