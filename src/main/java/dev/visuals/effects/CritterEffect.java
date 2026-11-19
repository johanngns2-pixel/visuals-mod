package dev.visuals.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiere: lässt Schmetterlinge, Vögel, Libellen und Fledermäuse rund um den Spieler erscheinen und Motten um
 * Fackeln kreisen. Tag-Nacht-Rhythmus, Menge und Arten kommen aus dem Reiter "Tiere". Rein optisch.
 */
public final class CritterEffect {
    private static final List<Particle> BUTTERFLIES = new ArrayList<>();
    private static final List<Particle> BIRDS = new ArrayList<>();
    private static final List<Particle> DRAGONFLIES = new ArrayList<>();
    private static final List<Particle> BATS = new ArrayList<>();
    private static final List<Particle> MOTHS = new ArrayList<>();
    private static int tick;

    private CritterEffect() {
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused() || level.dimension() != Level.OVERWORLD
                || !Opts.active("wildlife") || !SeasonClock.allows("wildlife")) {
            return;
        }
        tick++;
        for (List<Particle> l : List.of(BUTTERFLIES, BIRDS, DRAGONFLIES, BATS, MOTHS)) {
            l.removeIf(p -> !p.isAlive());
        }
        if (level.getRainLevel(1.0f) > 0.4f) {
            return;
        }
        double amount = Opts.num("wildlife.amount");
        if (amount <= 0.0) {
            return;
        }
        long t = level.getDayTime() % 24000L;
        boolean rhythm = Opts.bool("wildlife.rhythm");
        boolean day = t < 12300L || t > 23500L;
        boolean night = t >= 13200L && t <= 22800L;
        RandomSource rnd = level.random;
        double rate = 0.035 * amount;

        if (Opts.bool("wildlife.butterflies") && (day || !rhythm) && BUTTERFLIES.size() < 8 * amount
                && rnd.nextDouble() < rate) {
            spawnOnGrass(mc, level, player, Particles.butterfly, BUTTERFLIES, 0.6 + rnd.nextDouble() * 1.2, 1500, rnd);
        }
        if (Opts.bool("wildlife.birds") && (day || !rhythm) && BIRDS.size() < 4 * amount && rnd.nextDouble() < rate * 0.5) {
            spawnHigh(mc, level, player, Particles.bird, BIRDS, 18 + rnd.nextDouble() * 14, 1400, rnd, false);
        }
        if (Opts.bool("wildlife.dragonflies") && (day || !rhythm) && DRAGONFLIES.size() < 5 * amount
                && rnd.nextDouble() < rate) {
            spawnNearWater(mc, level, player, rnd);
        }
        if (Opts.bool("wildlife.bats") && (night || !rhythm) && BATS.size() < 6 * amount && rnd.nextDouble() < rate) {
            spawnHigh(mc, level, player, Particles.bat, BATS, 2.5 + rnd.nextDouble() * 5, 1200, rnd, true);
        }
        if (Opts.bool("wildlife.torchInsects") && (night || !rhythm) && MOTHS.size() < 6 && tick % 15 == 0) {
            spawnMoth(mc, level, player, rnd);
        }
    }

    private static double[] ring(LocalPlayer player, RandomSource rnd, double min, double max) {
        double a = rnd.nextDouble() * Math.PI * 2;
        double d = min + rnd.nextDouble() * (max - min);
        return new double[]{player.getX() + Math.cos(a) * d, player.getZ() + Math.sin(a) * d};
    }

    private static void register(Minecraft mc, SimpleParticleType type, List<Particle> list, double x, double y,
                                 double z, int life) {
        Particle p = mc.particleEngine.createParticle(type, x, y, z, 0.0, 0.0, 0.0);
        if (p instanceof CritterParticle c) {
            c.life(life);
            list.add(p);
        }
    }

    private static void spawnOnGrass(Minecraft mc, ClientLevel level, LocalPlayer player, SimpleParticleType type,
                                     List<Particle> list, double height, int life, RandomSource rnd) {
        double[] xz = ring(player, rnd, 6, 26);
        int bx = (int) Math.floor(xz[0]);
        int bz = (int) Math.floor(xz[1]);
        if (!level.hasChunkAt(new BlockPos(bx, 64, bz))) {
            return;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        if (!level.getBlockState(new BlockPos(bx, top - 1, bz)).is(Blocks.GRASS_BLOCK)) {
            return;
        }
        register(mc, type, list, xz[0], top + height, xz[1], life);
    }

    private static void spawnHigh(Minecraft mc, ClientLevel level, LocalPlayer player, SimpleParticleType type,
                                  List<Particle> list, double height, int life, RandomSource rnd, boolean needGround) {
        double[] xz = ring(player, rnd, 10, 30);
        int bx = (int) Math.floor(xz[0]);
        int bz = (int) Math.floor(xz[1]);
        if (!level.hasChunkAt(new BlockPos(bx, 64, bz))) {
            return;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        if (needGround && level.getBlockState(new BlockPos(bx, top - 1, bz)).isAir()) {
            return;
        }
        register(mc, type, list, xz[0], top + height, xz[1], life);
    }

    private static void spawnNearWater(Minecraft mc, ClientLevel level, LocalPlayer player, RandomSource rnd) {
        double[] xz = ring(player, rnd, 5, 28);
        int bx = (int) Math.floor(xz[0]);
        int bz = (int) Math.floor(xz[1]);
        if (!level.hasChunkAt(new BlockPos(bx, 64, bz))) {
            return;
        }
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
        if (!level.getFluidState(new BlockPos(bx, top - 1, bz)).is(FluidTags.WATER)) {
            return;
        }
        register(mc, Particles.dragonfly, DRAGONFLIES, xz[0], top + 0.7 + rnd.nextDouble() * 0.8, xz[1], 1200);
    }

    private static void spawnMoth(Minecraft mc, ClientLevel level, LocalPlayer player, RandomSource rnd) {
        for (int i = 0; i < 6; i++) {
            BlockPos pos = player.blockPosition().offset(rnd.nextInt(25) - 12, rnd.nextInt(9) - 4, rnd.nextInt(25) - 12);
            BlockState s = level.getBlockState(pos);
            if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_TORCH)) {
                Particle p = mc.particleEngine.createParticle(Particles.butterfly, pos.getX() + 0.5, pos.getY() + 0.6,
                        pos.getZ() + 0.5, 0.0, 0.0, 0.0);
                if (p instanceof CritterParticle c) {
                    c.life(900).moth(pos);
                    MOTHS.add(p);
                }
                return;
            }
        }
    }
}
