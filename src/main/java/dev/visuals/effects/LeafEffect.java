package dev.visuals.effects;

import dev.visuals.config.def.Tabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Fallendes Laub: erzeugt Laub-Partikel an der Unterseite von Blättern in der Nähe des Spielers.
 * Nutzt den Vanilla-Partikel "tinted_leaves" mit Farbe aus dem Biom (oder eigener Farbe).
 * Fallgeschwindigkeit und Drehung bestimmt Vanilla, diese beiden Regler wirken hier noch nicht.
 */
public final class LeafEffect {
    private static final double BASE_RATE_PER_TICK = 0.7;

    private LeafEffect() {
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused() || !Opts.active(Tabs.FOLIAGE)
                || !SeasonClock.allows("leaves")) {
            return;
        }
        double amount = Opts.num("foliage.amount");
        if (amount <= 0.0) {
            return;
        }
        if (level.getRainLevel(1.0f) > 0.6f) {
            return;
        }
        if (particleCount(mc) > Opts.integer("foliage.maxParticles")) {
            return;
        }
        RandomSource rnd = level.random;
        double expected = BASE_RATE_PER_TICK * amount;
        int attempts = (int) expected;
        if (rnd.nextDouble() < expected - attempts) {
            attempts++;
        }
        int range = Math.max(8, Opts.integer("foliage.range"));
        for (int i = 0; i < attempts * 3; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dist = rnd.nextDouble() * range;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            // von der Oberkante nach unten das erste Blatt mit freier Luft darunter suchen
            for (int y = top; y > top - 14 && y > level.getMinY(); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (state.is(BlockTags.LEAVES) && level.getBlockState(pos.below()).isAir()) {
                    spawn(mc, level, pos, state, rnd);
                    attempts--;
                    break;
                }
            }
            if (attempts <= 0) {
                break;
            }
        }
    }

    private static void spawn(Minecraft mc, ClientLevel level, BlockPos pos, BlockState state, RandomSource rnd) {
        int rgb;
        if (Opts.bool("foliage.biomeColor")) {
            int tint = mc.getBlockColors().getColor(state, level, pos, 0);
            rgb = tint == -1 ? Opts.color("foliage.color") : tint;
        } else {
            rgb = Opts.color("foliage.color");
        }
        double x = pos.getX() + rnd.nextDouble();
        double y = pos.getY() - 0.05;
        double z = pos.getZ() + rnd.nextDouble();
        mc.particleEngine.createParticle(ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF000000 | rgb),
                x, y, z, 0.0, 0.0, 0.0);
    }

    private static int particleCount(Minecraft mc) {
        try {
            String s = mc.particleEngine.countParticles();
            StringBuilder digits = new StringBuilder();
            for (char c : s.toCharArray()) {
                if (Character.isDigit(c)) {
                    digits.append(c);
                } else if (digits.length() > 0) {
                    break;
                }
            }
            return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
