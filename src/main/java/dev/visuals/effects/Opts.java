package dev.visuals.effects;

import dev.visuals.VisualsMod;
import dev.visuals.config.OptionDef;

/** Kurzer Zugriff auf Config-Werte per ID für die Effekte. Fällt bei Problemen auf den Standardwert zurück. */
public final class Opts {
    private Opts() {
    }

    private static Object raw(String id) {
        Object v = VisualsMod.config().getRaw(id);
        if (v == null) {
            OptionDef<?> def = VisualsMod.registry().get(id);
            v = def == null ? null : def.defaultValue();
        }
        return v;
    }

    public static boolean bool(String id) {
        return Boolean.TRUE.equals(raw(id));
    }

    public static double num(String id) {
        return raw(id) instanceof Number n ? n.doubleValue() : 0.0;
    }

    public static int integer(String id) {
        return raw(id) instanceof Number n ? n.intValue() : 0;
    }

    public static int color(String id) {
        return raw(id) instanceof Integer i ? i : 0xFFFFFFFF;
    }

    public static String text(String id) {
        Object v = raw(id);
        return v == null ? "" : v.toString();
    }

    /** Ist der Reiter aktiv (Mod an und Master-Schalter an)? */
    public static boolean active(String tabId) {
        return VisualsMod.config().isEffectActive(tabId);
    }
}
