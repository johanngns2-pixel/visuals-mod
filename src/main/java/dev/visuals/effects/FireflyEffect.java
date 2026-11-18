package dev.visuals.effects;

import dev.visuals.config.def.Tabs;
import dev.visuals.mixin.SingleQuadParticleAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Glühwürmchen: erzeugt nachts rein clientseitig Partikel des Typs "firefly" rund um den Spieler.
 * Das ist derselbe Partikel, den Vanilla über Glühwürmchen-Büschen nutzt (leuchtet weich, blendet ein und aus),
 * hier mit eigener Farbe, Größe und Menge. Es werden keine Entities und keine Blöcke verändert.
 */
public final class FireflyEffect {
    private static final double BASE_RATE_PER_TICK = 0.4;

    private FireflyEffect() {
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused() || !Opts.active(Tabs.FIREFLIES)
                || !SeasonClock.allows("fireflies")) {
            return;
        }
        if (Opts.bool("fireflies.onlyAtNight") && !isNight(level)) {
            return;
        }
        if (level.getRainLevel(1.0f) > 0.3f) {
            return; // bei Regen sind sie weg
        }
        double amount = Opts.num("fireflies.amount");
        if (amount <= 0.0) {
            return;
        }
        RandomSource rnd = level.random;
        double expected = BASE_RATE_PER_TICK * amount;
        int spawns = (int) expected;
        if (rnd.nextDouble() < expected - spawns) {
            spawns++;
        }
        double swarm = Opts.num("fireflies.swarm");
        for (int i = 0; i < spawns; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dist = 5 + rnd.nextDouble() * 22;
            double cx = player.getX() + Math.cos(angle) * dist;
            double cz = player.getZ() + Math.sin(angle) * dist;
            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(cx), (int) Math.floor(cz));
            if (player.getY() < groundY - 8) {
                continue; // Spieler ist tief unter der Oberfläche
            }
            int group = rnd.nextDouble() < swarm ? 2 + rnd.nextInt(3) : 1;
            for (int g = 0; g < group; g++) {
                double x = cx + (rnd.nextDouble() - 0.5) * 3.0;
                double z = cz + (rnd.nextDouble() - 0.5) * 3.0;
                double y = groundY + 0.4 + rnd.nextDouble() * 2.4;
                if (Opts.bool("fireflies.avoidPlayer") && player.distanceToSqr(x, y, z) < 9.0) {
                    continue;
                }
                spawn(mc, x, y, z, rnd);
            }
        }
    }

    private static boolean isNight(ClientLevel level) {
        long t = level.getDayTime() % 24000L;
        return t >= 12600L && t <= 23400L;
    }

    private static void spawn(Minecraft mc, double x, double y, double z, RandomSource rnd) {
        Particle p = mc.particleEngine.createParticle(ParticleTypes.FIREFLY, x, y, z, 0.0, 0.0, 0.0);
        if (p == null) {
            return;
        }
        int c1 = Opts.color("fireflies.color");
        int c2 = Opts.color("fireflies.color2");
        float t = rnd.nextFloat();
        float r = mix((c1 >> 16) & 255, (c2 >> 16) & 255, t) / 255f;
        float g = mix((c1 >> 8) & 255, (c2 >> 8) & 255, t) / 255f;
        float b = mix(c1 & 255, c2 & 255, t) / 255f;
        if (p instanceof SingleQuadParticleAccessor acc) {
            acc.visuals$setRed(r);
            acc.visuals$setGreen(g);
            acc.visuals$setBlue(b);
        }
        p.scale((float) Opts.num("fireflies.size"));
        if ("flicker".equals(Opts.text("fireflies.style"))) {
            p.setLifetime(40 + rnd.nextInt(50));
        }
    }

    private static float mix(int a, int b, float t) {
        return a + (b - a) * t;
    }
}
