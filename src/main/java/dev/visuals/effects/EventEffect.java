package dev.visuals.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Weltereignisse: Meteoritenschauer, Polarlicht, Blutmond, Regenbogen, Zugvögel.
 * Wann ein Ereignis stattfindet, wird aus der Weltzeit abgeleitet (feste Zufallswerte pro Tag). Mit der Option
 * "Zufall aus der Weltzeit" sehen alle Spieler auf einem Server dasselbe. Rein optisch, es wird nichts gesendet.
 */
public final class EventEffect {
    private static final int SPRING = 0;
    private static final int SUMMER = 1;
    private static final int AUTUMN = 2;
    private static final int WINTER = 3;

    private record Meteor(double[] pos, double[] vel, int[] ticks) {
    }

    private static final List<Meteor> METEORS = new ArrayList<>();
    private static ClientLevel lastLevel;
    private static long metDay = Long.MIN_VALUE;
    private static long aurDay = Long.MIN_VALUE;
    private static long bloodDay = Long.MIN_VALUE;
    private static long birdDay = Long.MIN_VALUE;
    private static float prevRain;
    private static long rainbowUntil;

    private EventEffect() {
    }

    // ------------------------------------------------------------------ Zeitfenster

    private static double hash(long salt, long n) {
        long h = salt * 0x9E3779B97F4A7C15L + n * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    /** Fortschritt 0 bis 1, wenn das Ereignis heute gerade läuft, sonst -1. */
    private static double window(ClientLevel level, LocalPlayer player, String id, double chance, int startMin,
                                 int startSpan, int durTicks) {
        long dt = level.getDayTime();
        long day = Math.floorDiv(dt, 24000L);
        long t = Math.floorMod(dt, 24000L);
        long salt = id.hashCode() + (Opts.bool("events.sharedRandom") ? 0L : player.getUUID().hashCode());
        if (hash(salt, day) >= chance) {
            return -1;
        }
        double start = startMin + hash(salt * 31 + 7, day) * startSpan;
        if (t < start || t > start + durTicks) {
            return -1;
        }
        return (t - start) / Math.max(1, durTicks);
    }

    private static boolean seasonOk(int... allowed) {
        if (!Opts.bool("events.seasonBound") || !Opts.active("seasons")) {
            return true;
        }
        int s = SeasonClock.current();
        for (int a : allowed) {
            if (a == s) {
                return true;
            }
        }
        return false;
    }

    private static double freq(String id) {
        return Opts.num("events." + id);
    }

    /** Stärke des Blutmonds 0 bis 1 (mit Ein- und Ausblenden), für den Himmel. */
    public static double bloodMoon(ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Opts.active("events") || !Opts.bool("events.bloodMoon")
                || !SeasonClock.allows("events")) {
            return 0;
        }
        double p = window(level, mc.player, "bloodMoon", Math.min(0.5, 0.14 * freq("bloodMoonFrequency")), 13000, 3000,
                Math.max(200, Opts.integer("events.bloodMoonDuration") * 20));
        if (p < 0) {
            return 0;
        }
        return Math.min(1.0, Math.min(p * 10, (1.0 - p) * 10));
    }

    // ------------------------------------------------------------------ Tick

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) {
            return;
        }
        if (level != lastLevel) {
            lastLevel = level;
            METEORS.clear();
            metDay = aurDay = bloodDay = birdDay = Long.MIN_VALUE;
            rainbowUntil = 0;
        }
        if (!Opts.active("events") || !SeasonClock.allows("events")
                || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }
        long day = Math.floorDiv(level.getDayTime(), 24000L);
        RandomSource rnd = level.random;

        // Meteoritenschauer
        if (Opts.bool("events.meteors") && seasonOk(SUMMER, AUTUMN)) {
            double p = window(level, player, "meteors", Math.min(0.9, 0.5 * freq("meteorsFrequency")), 13500, 5000,
                    Math.max(200, Opts.integer("events.meteorsDuration") * 20));
            if (p >= 0) {
                if (metDay != day) {
                    metDay = day;
                    announce(player, "meteors");
                }
                if (rnd.nextDouble() < 1.0 / 35.0) {
                    spawnMeteor(player, rnd);
                }
            }
        }
        tickMeteors(mc, level);

        // Polarlicht
        if (Opts.bool("events.aurora") && seasonOk(AUTUMN, WINTER)) {
            int dur = Math.max(200, Opts.integer("events.auroraDuration") * 20);
            double p = window(level, player, "aurora", Math.min(0.9, 0.4 * freq("auroraFrequency")), 14000, 5000, dur);
            if (p >= 0 && aurDay != day) {
                aurDay = day;
                announce(player, "aurora");
                spawnAurora(mc, level, player, (int) Math.max(120, dur * (1.0 - p)));
            }
        }

        // Blutmond: nur Ankündigung, die Farbe setzt der Himmel-Effekt
        if (Opts.bool("events.bloodMoon") && bloodMoon(level) > 0.05 && bloodDay != day) {
            bloodDay = day;
            announce(player, "bloodMoon");
        }

        // Regenbogen nach Regen
        float rain = level.getRainLevel(1.0f);
        long t = Math.floorMod(level.getDayTime(), 24000L);
        if (Opts.bool("events.rainbow") && seasonOk(SPRING, SUMMER, AUTUMN) && prevRain > 0.4f && rain < 0.15f
                && t < 11500L && level.getGameTime() > rainbowUntil
                && rnd.nextDouble() < Math.min(1.0, 0.6 * freq("rainbowFrequency"))) {
            spawnRainbow(mc, level, player);
            rainbowUntil = level.getGameTime() + 3000;
            announce(player, "rainbow");
        }
        prevRain = rain;

        // Zugvögel
        if (Opts.bool("events.birds") && seasonOk(SPRING, AUTUMN)) {
            double p = window(level, player, "birds", Math.min(0.95, 0.7 * freq("birdsFrequency")), 1500, 9000, 1600);
            if (p >= 0 && birdDay != day) {
                birdDay = day;
                announce(player, "birds");
                spawnFlock(mc, level, player, rnd);
            }
        }
    }

    private static void announce(LocalPlayer player, String id) {
        if (Opts.bool("events.announce")) {
            player.displayClientMessage(Component.translatable("visuals.event." + id), true);
        }
    }

    // ------------------------------------------------------------------ Meteore

    private static void spawnMeteor(LocalPlayer player, RandomSource rnd) {
        double a = rnd.nextDouble() * Math.PI * 2;
        double b = a + Math.PI / 2 + (rnd.nextDouble() - 0.5) * 1.2;
        double[] pos = {player.getX() + Math.cos(a) * 75, player.getY() + 55 + rnd.nextDouble() * 35,
                player.getZ() + Math.sin(a) * 75};
        double[] vel = {Math.cos(b) * 3.0, -0.9 - rnd.nextDouble() * 0.5, Math.sin(b) * 3.0};
        METEORS.add(new Meteor(pos, vel, new int[]{0}));
    }

    private static void tickMeteors(Minecraft mc, ClientLevel level) {
        for (int i = METEORS.size() - 1; i >= 0; i--) {
            Meteor m = METEORS.get(i);
            for (int k = 0; k < 3; k++) {
                m.pos()[k] += m.vel()[k];
            }
            m.ticks()[0]++;
            Particle head = mc.particleEngine.createParticle(Particles.glow, m.pos()[0], m.pos()[1], m.pos()[2], 0, 0, 0);
            if (head instanceof GlowParticle g) {
                g.setup(0xFFF4D0, 3.2f, 4, 1.0f, 0, 0, 0, 1, 2);
            }
            Particle tail = mc.particleEngine.createParticle(Particles.glow, m.pos()[0], m.pos()[1], m.pos()[2], 0, 0, 0);
            if (tail instanceof GlowParticle g) {
                g.setup(0xFFB060, 2.2f, 16, 0.7f, 0, 0, 0, 1, 14);
            }
            if (m.ticks()[0] > 26) {
                METEORS.remove(i);
            }
        }
    }

    // ------------------------------------------------------------------ Polarlicht

    private static void spawnAurora(Minecraft mc, ClientLevel level, LocalPlayer player, int life) {
        int[] colors = {0x4CFF9A, 0x40E0C8, 0x9A6BFF};
        double base = level.random.nextDouble() * Math.PI * 2;
        for (int layer = 0; layer < 3; layer++) {
            for (int i = 0; i < 26; i++) {
                double ang = base + (i / 25.0 - 0.5) * 2.2;
                double r = 105 + layer * 8;
                double x = player.getX() + Math.cos(ang) * r;
                double z = player.getZ() + Math.sin(ang) * r;
                double y = player.getY() + 62 + layer * 15;
                final int idx = i;
                final double y0 = y;
                Particle p = mc.particleEngine.createParticle(Particles.glow, x, y, z, 0, 0, 0);
                if (p instanceof GlowParticle g) {
                    g.setup(colors[layer], 26f, life, 0.17f, 0, 0, 0, 100, 100);
                    g.mover((gp, age) -> {
                        gp.moveTo(gp.px(), y0 + Math.sin(age * 0.02 + idx * 0.5) * 4.0, gp.pz());
                        gp.alphaScale((float) (0.7 + 0.3 * Math.sin(age * 0.03 + idx * 0.7)));
                    });
                }
            }
        }
    }

    // ------------------------------------------------------------------ Regenbogen

    private static void spawnRainbow(Minecraft mc, ClientLevel level, LocalPlayer player) {
        int[] colors = {0xFF3030, 0xFF8A30, 0xFFE030, 0x40D060, 0x3080FF, 0x4B40D0, 0x9040C0};
        double az = level.random.nextDouble() * Math.PI * 2;
        double hx = Math.cos(az);
        double hz = Math.sin(az);
        double dx = -hz;
        double dz = hx;
        for (int band = 0; band < 7; band++) {
            double radius = 78 - band * 3.2;
            for (int i = 0; i <= 26; i++) {
                double u = Math.PI * i / 26.0;
                double x = player.getX() + dx * 95 + hx * Math.cos(u) * radius;
                double z = player.getZ() + dz * 95 + hz * Math.cos(u) * radius;
                double y = player.getY() + Math.sin(u) * radius - 6;
                Particle p = mc.particleEngine.createParticle(Particles.glow, x, y, z, 0, 0, 0);
                if (p instanceof GlowParticle g) {
                    g.setup(colors[band], 11f, 2600, 0.20f, 0, 0, 0, 120, 160);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Zugvögel

    private static void spawnFlock(Minecraft mc, ClientLevel level, LocalPlayer player, RandomSource rnd) {
        double a = rnd.nextDouble() * Math.PI * 2;
        double fx = -Math.cos(a);
        double fz = -Math.sin(a);
        double offset = (rnd.nextDouble() - 0.5) * 50;
        double sx = player.getX() + Math.cos(a) * 110 - fz * offset;
        double sz = player.getZ() + Math.sin(a) * 110 + fx * offset;
        double sy = player.getY() + 38 + rnd.nextDouble() * 22;
        int n = 7 + rnd.nextInt(5);
        double speed = 0.25;
        for (int i = 0; i < n; i++) {
            int rank = (i + 1) / 2;
            double side = (i % 2 == 0 ? 1 : -1) * rank * 2.4;
            double back = rank * 2.0;
            double x = sx - fx * back + (-fz) * side;
            double z = sz - fz * back + fx * side;
            Particle p = mc.particleEngine.createParticle(Particles.bird, x, sy + rnd.nextDouble() * 0.6, z, 0, 0, 0);
            if (p instanceof CritterParticle c) {
                c.life(900).flock(fx * speed, fz * speed);
            }
        }
    }
}
