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
