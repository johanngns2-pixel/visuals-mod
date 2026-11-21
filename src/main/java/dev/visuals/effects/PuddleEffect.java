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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Pfützen: Wer den Schalter einschaltet, hat sie. Kein Regen, kein Timer, kein Trocknen.
 *
 * <p>Die Welt wird in feste Felder eingeteilt. Für jedes Feld entscheidet ein fester Zufallswert aus den
 * Koordinaten, ob dort eine Pfütze liegt, wo und wie groß. Dadurch sind die Pfützen an jedem Ort immer
 * dieselben, auch nach dem Wiederkommen. Rund um den Spieler werden sie angezeigt, weit weg wieder entfernt.
 */
public final class PuddleEffect {
    public static SimpleParticleType type;

    private static final int CELL = 6;
    private static final int RADIUS_CELLS = 5;
    private static final long SALT = 0x5EED_1234_ABCDL;

    private static final Map<Long, PuddleParticle> active = new HashMap<>();
    private static final Map<Long, Integer> checkedUntil = new HashMap<>();
    private static ClientLevel lastLevel;
    private static int tick;

    private PuddleEffect() {
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (type == null || level == null || player == null) {
            reset();
            return;
        }
        if (level != lastLevel) {
            reset();
            lastLevel = level;
        }
        tick++;
        boolean enabled = Opts.active(Tabs.PUDDLES) && level.dimension() == Level.OVERWORLD && !PuddleShader.working();
        if (!enabled) {
            retireAll();
            return;
        }
        if (tick % 5 != 0) {
            return;
        }
        double chance = Math.max(0.0, Math.min(0.75, Opts.integer("puddles.coverage") / 100.0 * 0.6));
        double sizeOpt = Opts.num("puddles.size");
        int pcx = Math.floorDiv((int) Math.floor(player.getX()), CELL);
        int pcz = Math.floorDiv((int) Math.floor(player.getZ()), CELL);

        // weit entfernte oder tote Pfützen entfernen
        Iterator<Map.Entry<Long, PuddleParticle>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, PuddleParticle> e = it.next();
            int cx = (int) (e.getKey() >> 32);
            int cz = (int) (long) e.getKey();
            PuddleParticle p = e.getValue();
            if (!p.isAlive()) {
                it.remove();
            } else if (Math.abs(cx - pcx) > RADIUS_CELLS + 2 || Math.abs(cz - pcz) > RADIUS_CELLS + 2
                    || chance <= 0.0) {
                p.retire();
                it.remove();
            }
        }
        checkedUntil.values().removeIf(until -> until < tick);
        if (chance <= 0.0) {
            return;
        }

        int created = 0;
        for (int dx = -RADIUS_CELLS; dx <= RADIUS_CELLS && created < 6; dx++) {
            for (int dz = -RADIUS_CELLS; dz <= RADIUS_CELLS && created < 6; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
                if (active.containsKey(key) || checkedUntil.containsKey(key)) {
                    continue;
                }
                if (hash(cx, cz, 1) >= chance) {
                    checkedUntil.put(key, tick + 2000);
                    continue;
                }
                double x = (cx + 0.15 + 0.7 * hash(cx, cz, 2)) * CELL;
                double z = (cz + 0.15 + 0.7 * hash(cx, cz, 3)) * CELL;
                float size = (float) ((0.8 + 1.1 * hash(cx, cz, 4)) * sizeOpt);
                PuddleParticle p = create(mc, level, x, z, size);
                if (p != null) {
                    active.put(key, p);
                    created++;
                } else {
                    checkedUntil.put(key, tick + 100); // Chunk fehlt oder Boden passt nicht: später nochmal
                }
            }
        }
    }

    private static PuddleParticle create(Minecraft mc, ClientLevel level, double x, double z, float size) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!level.hasChunkAt(new BlockPos(bx, 64, bz))) {
            return null;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        BlockPos below = new BlockPos(bx, top - 1, bz);
        BlockState ground = level.getBlockState(below);
        if (ground.isAir() || ground.is(BlockTags.LEAVES) || !ground.isFaceSturdy(level, below, Direction.UP)) {
            return null;
        }
        if (!level.getBlockState(below.above()).isAir()) {
            return null; // Gras, Schnee oder Blumen liegen darauf
        }
        Particle p = mc.particleEngine.createParticle(type, x, top + 0.012, z, 0.0, 0.0, 0.0);
        if (p instanceof PuddleParticle puddle) {
            puddle.scale(size / 0.5f);
            return puddle;
        }
        return null;
    }

    private static void retireAll() {
        for (PuddleParticle p : active.values()) {
            p.retire();
        }
        active.clear();
        checkedUntil.clear();
    }

    private static void reset() {
        active.clear();
        checkedUntil.clear();
        lastLevel = null;
    }

    /** Fester Zufallswert zwischen 0 und 1 aus Feldkoordinaten, immer gleich für denselben Ort. */
    private static double hash(int cx, int cz, int salt) {
        long h = SALT + cx * 0x9E3779B97F4A7C15L + cz * 0xBF58476D1CE4E5B9L + salt * 0x94D049BB133111EBL;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * (1.0 / (1L << 53));
    }
}
