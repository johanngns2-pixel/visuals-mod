package dev.visuals.effects;

import java.time.LocalDate;

/** Bestimmt die aktuelle Jahreszeit nach den Einstellungen des Reiters "Jahreszeiten". */
public final class SeasonClock {
    public static final String[] IDS = {"spring", "summer", "autumn", "winter"};

    private SeasonClock() {
    }

    /** Index in {@link #IDS}. */
    public static int current() {
        String mode = Opts.text("seasons.mode");
        switch (mode) {
            case "manual" -> {
                return indexOf(Opts.text("seasons.manual"));
            }
            case "cycle" -> {
                long minutes = Math.max(1, Opts.integer("seasons.cycleMinutes"));
                long slot = System.currentTimeMillis() / (minutes * 60_000L);
                return (int) (slot % 4);
            }
            default -> {
                int month = LocalDate.now().getMonthValue();
                if (month >= 3 && month <= 5) {
                    return 0;
                }
                if (month >= 6 && month <= 8) {
                    return 1;
                }
                if (month >= 9 && month <= 11) {
                    return 2;
                }
                return 3;
            }
        }
    }

    private static double smooth(double e0, double e1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /**
     * Gewichte der vier Jahreszeiten (Frühling, Sommer, Herbst, Winter), die zusammen 1 ergeben. Am Ende einer
     * Jahreszeit wird weich zur nächsten übergeblendet.
     */
    public static float[] weights() {
        float[] w = new float[4];
        switch (Opts.text("seasons.mode")) {
            case "manual" -> w[indexOf(Opts.text("seasons.manual"))] = 1f;
            case "cycle" -> {
                double periodMs = Math.max(1, Opts.integer("seasons.cycleMinutes")) * 60_000.0;
                double pos = (System.currentTimeMillis() / periodMs) % 4.0;
                int idx = (int) pos;
                double frac = pos - idx;
                double trans = Math.min(0.5, Opts.integer("seasons.transitionSeconds") * 1000.0 / periodMs);
                double t = trans <= 0.0 ? (frac > 0.9999 ? 1.0 : 0.0) : smooth(1.0 - trans, 1.0, frac);
                w[idx] = (float) (1.0 - t);
                w[(idx + 1) % 4] += (float) t;
            }
            default -> { // echtes Datum: Frühling ab 1. März, Übergang über etwa 10 Tage
                double rel = ((LocalDate.now().getDayOfYear() - 60) % 365 + 365) % 365;
                double pos = rel / 91.25;
                int idx = ((int) pos) % 4;
                double frac = pos - Math.floor(pos);
                double t = smooth(1.0 - 0.11, 1.0, frac);
                w[idx] = (float) (1.0 - t);
                w[(idx + 1) % 4] += (float) t;
            }
        }
        return w;
    }

    private static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) {
                return i;
            }
        }
        return 1;
    }

    /**
     * Darf der Effekt in der aktuellen Jahreszeit laufen? Ist der Reiter "Jahreszeiten" aus,
     * gilt der Effekt immer. Effekt-IDs: leaves, fireflies, wildlife, touch, fog, events.
     */
    public static boolean allows(String effect) {
        if (!Opts.active("seasons")) {
            return true;
        }
        String season = IDS[current()];
        String id = "seasons." + effect + Character.toUpperCase(season.charAt(0)) + season.substring(1);
        return Opts.bool(id);
    }
}
