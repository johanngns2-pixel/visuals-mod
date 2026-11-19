package dev.visuals.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Berührung: Fußspuren auf Sand, Schlamm und Erde, Dellen im Schnee, Wasserringe, Staub beim Rennen und
 * aufstiebendes Laub. Alles rein optisch (Partikel), es wird kein Block verändert.
 * Nicht enthalten: sich biegendes Gras und Blumen (das braucht Eingriffe in die Block-Modelle).
 */
public final class TouchEffect {
    private static double distance;
    private static boolean leftFoot;
    private static int tick;
    private static Vec3 last;

    private TouchEffect() {
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused() || !Opts.active("touch") || !SeasonClock.allows("touch")) {
            last = null;
            return;
        }
        tick++;
        Vec3 pos = player.position();
        double moved = last == null ? 0 : Math.sqrt((pos.x - last.x) * (pos.x - last.x) + (pos.z - last.z) * (pos.z - last.z));
        last = pos;
        boolean moving = moved > 0.02;

        if (player.isInWater()) {
            if (Opts.bool("touch.waterRings") && tick % (moving ? 10 : 26) == 0) {
                ring(mc, level, player);
            }
            return;
        }
        if (!player.onGround() || !moving) {
            return;
        }
        BlockPos feet = player.blockPosition();
        BlockPos belowPos = feet.below();
        BlockState below = level.getBlockState(belowPos);
        BlockState at = level.getBlockState(feet);

        distance += moved;
        if (distance >= 0.95) {
            distance = 0;
            footprint(mc, level, player, below, at);
        }
        if (player.isSprinting() && Opts.bool("touch.dust") && tick % 5 == 0 && isDry(below)) {
            mc.particleEngine.createParticle(ParticleTypes.POOF, pos.x, pos.y + 0.1, pos.z, 0.0, 0.01, 0.0);
        }
        if (Opts.bool("touch.leafPiles") && tick % 6 == 0 && below.is(Blocks.GRASS_BLOCK) && SeasonClock.allows("leaves")
                && leavesNear(level, feet)) {
            int rgb = 0xFF000000 | (Opts.bool("foliage.biomeColor") ? 0xB0802A : Opts.color("foliage.color") & 0xFFFFFF);
            for (int i = 0; i < 2; i++) {
                mc.particleEngine.createParticle(ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, rgb),
                        pos.x + (level.random.nextDouble() - 0.5) * 0.6, pos.y + 0.1,
                        pos.z + (level.random.nextDouble() - 0.5) * 0.6, 0.0, 0.1, 0.0);
            }
        }
    }

    private static boolean isDry(BlockState s) {
        return s.is(BlockTags.SAND) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.DIRT_PATH);
    }

    private static boolean leavesNear(ClientLevel level, BlockPos feet) {
        for (int dx = -5; dx <= 5; dx += 2) {
            for (int dz = -5; dz <= 5; dz += 2) {
                for (int dy = 2; dy <= 7; dy += 2) {
                    if (level.getBlockState(feet.offset(dx, dy, dz)).is(BlockTags.LEAVES)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void footprint(Minecraft mc, ClientLevel level, LocalPlayer player, BlockState below, BlockState at) {
        int rgb;
        boolean snow = at.is(Blocks.SNOW) || below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.SNOW) || below.is(Blocks.POWDER_SNOW);
        if (snow) {
            if (!Opts.bool("touch.snowDents")) {
                return;
            }
            rgb = 0x9AA8BC;
        } else if (!Opts.bool("touch.footprints")) {
            return;
        } else if (below.is(BlockTags.SAND)) {
            rgb = 0x8C7A52;
        } else if (below.is(Blocks.MUD)) {
            rgb = 0x2E2418;
        } else if (below.is(Blocks.DIRT) || below.is(Blocks.COARSE_DIRT) || below.is(Blocks.DIRT_PATH)
                || below.is(Blocks.GRASS_BLOCK)) {
            rgb = 0x3A2A1C;
            if (below.is(Blocks.GRASS_BLOCK)) {
                return; // auf Gras bleiben keine Spuren
            }
        } else {
            return;
        }
        float yawDeg = player.getYRot();
        double yaw = Math.toRadians(yawDeg);
        double side = leftFoot ? 0.13 : -0.13;
        leftFoot = !leftFoot;
        double px = player.getX() + Math.cos(yaw) * side;
        double pz = player.getZ() + Math.sin(yaw) * side;
        double py = player.getY() + (snow && at.is(Blocks.SNOW) ? 0.1 : 0.012);
        int life = Math.max(20, Opts.integer("touch.footprintDuration") * 20);
        float strength = (float) Opts.num("touch.footprintStrength") * 0.75f;
        Particle p = mc.particleEngine.createParticle(Particles.footprint, px, py, pz, 0.0, 0.0, 0.0);
        if (p instanceof DecalParticle d) {
            d.setup(rgb, 0.32f, life, strength, (float) Math.toRadians(-yawDeg + 180.0), 0f);
        }
    }

    private static void ring(Minecraft mc, ClientLevel level, LocalPlayer player) {
        BlockPos pos = player.blockPosition();
        for (int i = 0; i < 4; i++) {
            if (!level.getFluidState(pos.above(i)).is(FluidTags.WATER)) {
                double y = pos.getY() + i - 0.1;
                if (i == 0) {
                    return;
                }
                Particle p = mc.particleEngine.createParticle(Particles.ring, player.getX(), y, player.getZ(), 0.0, 0.0, 0.0);
                if (p instanceof DecalParticle d) {
                    d.setup(0xDDEEFF, 0.35f, 36, 0.55f, 0f, 3.2f);
                }
                return;
            }
        }
    }
}
