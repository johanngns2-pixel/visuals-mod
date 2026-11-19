package dev.visuals.effects;

import dev.visuals.util.Log;
import net.minecraft.client.Minecraft;

/**
 * Einstiegspunkt der Welt-Effekte, einmal pro Client-Tick. Jeder Effekt läuft in einem Schutz:
 * Wirft er mehrfach Fehler, wird er für die Sitzung abgeschaltet, statt das Spiel zu stören.
 */
public final class Effects {
    private static final int MAX_ERRORS = 3;
    private static int fireflyErrors;
    private static int leafErrors;
    private static int puddleErrors;

    private Effects() {
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (fireflyErrors < MAX_ERRORS) {
            try {
                FireflyEffect.tick(mc);
            } catch (RuntimeException e) {
                fireflyErrors++;
                Log.error("Glühwürmchen-Effekt Fehler " + fireflyErrors + "/" + MAX_ERRORS, e);
            }
        }
        if (puddleErrors < MAX_ERRORS) {
            try {
                PuddleEffect.tick(mc);
            } catch (RuntimeException e) {
                puddleErrors++;
                Log.error("Pfützen-Effekt Fehler " + puddleErrors + "/" + MAX_ERRORS, e);
            }
        }
        if (leafErrors < MAX_ERRORS) {
            try {
                LeafEffect.tick(mc);
            } catch (RuntimeException e) {
                leafErrors++;
                Log.error("Laub-Effekt Fehler " + leafErrors + "/" + MAX_ERRORS, e);
            }
        }
    }
}
