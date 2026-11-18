package dev.visuals.effects;

/** Himmelsfarbe über den Tag: weicher Verlauf zwischen Nacht, Morgen, Tag und Abend. */
public final class SkyColors {
    private static final double[] AT = {0, 4.5, 6.5, 9, 16, 18, 20.5, 24};

    private SkyColors() {
    }

    /** Stunde 0 bis 24 (Minecraft: 6 Uhr = Sonnenaufgang, 18 Uhr = Sonnenuntergang). */
    public static int at(double hour, int night, int dawn, int day, int dusk) {
        int[] col = {night, night, dawn, day, day, dusk, night, night};
        for (int i = 0; i < AT.length - 1; i++) {
            if (hour <= AT[i + 1]) {
                double f = (hour - AT[i]) / (AT[i + 1] - AT[i]);
                return mix(col[i], col[i + 1], Math.max(0, Math.min(1, f)));
            }
        }
        return night & 0xFFFFFF;
    }

    /** Mischt zwei RGB-Farben; 0 ergibt a, 1 ergibt b. Ergebnis ohne Alpha. */
    public static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
