package dev.visuals.effects;

import dev.visuals.util.Log;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/**
 * Einstiegspunkt der Welt-Effekte, einmal pro Client-Tick. Jeder Effekt läuft in einem Schutz:
 * Wirft er mehrfach Fehler, wird er für die Sitzung abgeschaltet, statt das Spiel zu stören.
 */
public final class Effects {
    private static final int MAX_ERRORS = 3;

    private static final class Guard {
        private final String name;
        private final Consumer<Minecraft> tick;
        private int errors;

        Guard(String name, Consumer<Minecraft> tick) {
            this.name = name;
            this.tick = tick;
        }

        void run(Minecraft mc) {
            if (errors >= MAX_ERRORS) {
                return;
            }
            try {
                tick.accept(mc);
            } catch (RuntimeException e) {
                errors++;
                Log.error("Effekt " + name + " Fehler " + errors + "/" + MAX_ERRORS, e);
            }
        }
    }

    private static final Guard[] GUARDS = {
            new Guard("Glühwürmchen", FireflyEffect::tick),
            new Guard("Pfützen", PuddleEffect::tick),
            new Guard("Laub", LeafEffect::tick),
            new Guard("Tiere", CritterEffect::tick),
            new Guard("Berührung", TouchEffect::tick),
            new Guard("Weltereignisse", EventEffect::tick),
    };

    private Effects() {
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        for (Guard g : GUARDS) {
            g.run(mc);
        }
    }
}
