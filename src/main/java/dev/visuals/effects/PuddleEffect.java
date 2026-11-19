package dev.visuals.effects;

import dev.visuals.config.def.Tabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Pfützen: Während es regnet, sammelt sich "Nässe". Solange es nass ist, entstehen rund um den Spieler Pfützen
 * auf ebenem, vollem Boden unter freiem Himmel. Danach trocknen sie innerhalb der eingestellten Trocknungszeit.
 */
public final class PuddleEffect {
    public static SimpleParticleType type;

    private static float wetness;
    private static long tick;
    private static final Map<Long, Long> occupiedUntil = new HashMap<>();

    private PuddleEffect() {
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (type == null || level == null || player == null || mc.isPaused()) {
            return;
        }
        tick++;
        if (!Opts.active(Tabs.PUDDLES)) {
            return;
        }
        double dryTicks = Math.max(200.0, Opts.integer("puddles.dryTime") * 20.0);
        if (level.getRainLevel(1.0f) > 0.2f) {
            wetness = Math.min(1f, wetness + 1f / 500f);
        } else {
            wetness = Math.max(0f, wetness - (float) (1.0 / dryTicks));
        }
        if (tick % 40 == 0) {
            Iterator<Map.Entry<Long, Long>> it = occupiedUntil.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue() < tick) {
                    it.remove();
                }
            }
        }
        double amount = Opts.num("puddles.amount");
        if (wetness < 0.15f || amount <= 0.0) {
            return;
        }
        int maxActive = (int) (90 * amount);
        if (occupiedUntil.size() >= maxActive) {
            return;
        }
        RandomSource rnd = level.random;
        double expected = 0.35 * amount * wetness;
        int tries = (int) expected;
        if (rnd.nextDouble() < expected - tries) {
            tries++;
        }
        for (int i = 0; i < tries * 3; i++) {
            if (trySpawn(mc, level, player.getX(), player.getZ(), rnd, dryTicks)) {
                tries--;
                if (tries <= 0) {
                    break;
                }
            }
        }
    }

    private static boolean trySpawn(Minecraft mc, ClientLevel level, double px, double pz, RandomSource rnd,
                                    double dryTicks) {
        double angle = rnd.nextDouble() * Math.PI * 2;
        double dist = 2 + rnd.nextDouble() * 22;
        double x = px + Math.cos(angle) * dist;
        double z = pz + Math.sin(angle) * dist;
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        BlockPos below = new BlockPos(bx, top - 1, bz);
        BlockState ground = level.getBlockState(below);
        if (ground.isAir() || ground.is(BlockTags.LEAVES) || !ground.isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        BlockState above = level.getBlockState(below.above());
        if (!above.isAir()) {
            return false; // z. B. Gras, Schnee oder Blumen liegen darauf
        }
        long cell = (((long) (bx >> 1)) << 32) ^ (bz >> 1 & 0xFFFFFFFFL);
        if (occupiedUntil.containsKey(cell)) {
            return false;
        }
        float size = (float) ((0.7 + rnd.nextDouble() * 1.1) * Opts.num("puddles.size"));
        int lifetime = (int) (dryTicks * (0.5 + 0.5 * rnd.nextDouble() * wetness)) + 60;
        Particle p = mc.particleEngine.createParticle(type, x, top + 0.012, z, 0.0, 0.0, 0.0);
        if (p instanceof PuddleParticle) {
            // Größe und Lebensdauer setzt der Anbieter über Standardwerte, hier feinjustieren
            p.scale(size / 0.5f);
            p.setLifetime(lifetime);
        }
        occupiedUntil.put(cell, tick + lifetime);
        return p != null;
    }
}
