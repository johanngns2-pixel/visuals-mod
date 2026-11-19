package dev.visuals.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Kleine Tiere als Partikel: Schmetterling, Vogel, Libelle, Fledermaus (und Motten an Fackeln).
 * Rein optisch, keine Entity, der Server weiß nichts davon.
 */
public final class CritterParticle extends SingleQuadParticle {
    public enum Kind {
        BUTTERFLY(0.30f, 2, 3),
        BIRD(0.95f, 3, 4),
        DRAGONFLY(0.38f, 2, 2),
        BAT(0.45f, 2, 3);

        final float size;
        final int frames;
        final int framePeriod;

        Kind(float size, int frames, int framePeriod) {
            this.size = size;
            this.frames = frames;
            this.framePeriod = framePeriod;
        }
    }

    private final SpriteSet sprites;
    private final Kind kind;
    private double heading;
    private double baseY;
    private int frame;
    private boolean flock;
    private double flockVx;
    private double flockVz;
    private BlockPos orbit;
    private double orbitAngle;
    private double orbitRadius;

    public CritterParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, Kind kind) {
        super(level, x, y, z, sprites.get(0, 1));
        this.sprites = sprites;
        this.kind = kind;
        this.hasPhysics = false;
        this.gravity = 0;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.quadSize = kind.size;
        this.lifetime = 1200;
        this.alpha = 0f;
        this.heading = level.random.nextDouble() * Math.PI * 2;
        this.baseY = y;
        RandomSource rnd = level.random;
        if (kind == Kind.BUTTERFLY) {
            float[] c = pastel(rnd.nextFloat());
            this.rCol = c[0];
            this.gCol = c[1];
            this.bCol = c[2];
        } else if (kind == Kind.DRAGONFLY) {
            this.rCol = 0.55f + rnd.nextFloat() * 0.2f;
            this.gCol = 0.9f;
            this.bCol = 0.95f;
        }
    }

    public CritterParticle life(int ticks) {
        this.lifetime = ticks;
        return this;
    }

    /** Bewegt sich geradeaus (Vogelschwarm). */
    public CritterParticle flock(double vx, double vz) {
        this.flock = true;
        this.flockVx = vx;
        this.flockVz = vz;
        return this;
    }

    /** Umkreist einen Block wie eine Motte eine Lampe. */
    public CritterParticle moth(BlockPos center) {
        this.orbit = center;
        this.orbitRadius = 0.5 + this.level.random.nextDouble() * 0.5;
        this.orbitAngle = this.level.random.nextDouble() * Math.PI * 2;
        this.quadSize = 0.16f;
        this.rCol = 0.85f;
        this.gCol = 0.8f;
        this.bCol = 0.7f;
        return this;
    }

    private static float[] pastel(float h) {
        float s = 0.45f;
        float v = 1.0f;
        float hh = (h % 1f) * 6f;
        int i = (int) hh;
        float f = hh - i;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        return switch (i % 6) {
            case 0 -> new float[]{v, t, p};
            case 1 -> new float[]{q, v, p};
            case 2 -> new float[]{p, v, t};
            case 3 -> new float[]{p, q, v};
            case 4 -> new float[]{t, p, v};
            default -> new float[]{v, p, q};
        };
    }

    private boolean blocked(double nx, double ny, double nz) {
        BlockPos pos = BlockPos.containing(nx, ny, nz);
        BlockState state = this.level.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(this.level, pos).isEmpty();
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.distanceToSqr(this.x, this.y, this.z) > 70.0 * 70.0) {
            this.remove();
            return;
        }
        RandomSource rnd = this.level.random;
        double vx;
        double vy;
        double vz;
        if (orbit != null) {
            orbitAngle += 0.17;
            double tx = orbit.getX() + 0.5 + Math.cos(orbitAngle) * orbitRadius;
            double ty = orbit.getY() + 0.6 + Math.sin(orbitAngle * 1.7) * 0.35;
            double tz = orbit.getZ() + 0.5 + Math.sin(orbitAngle) * orbitRadius;
            this.x = tx;
            this.y = ty;
            this.z = tz;
            vx = 0;
            vy = 0;
            vz = 0;
        } else if (flock) {
            vx = flockVx;
            vy = 0;
            vz = flockVz;
        } else {
            double speed;
            switch (kind) {
                case BUTTERFLY -> {
                    heading += (rnd.nextDouble() - 0.5) * 0.5;
                    speed = 0.04 + 0.02 * Math.sin(age * 0.2);
                    vy = Math.sin(age * 0.35) * 0.025 + (baseY - this.y) * 0.03;
                }
                case DRAGONFLY -> {
                    if (age % 50 < 8) {
                        heading += (rnd.nextDouble() - 0.5) * 2.0;
                        speed = 0.13;
                    } else {
                        speed = 0.008;
                    }
                    vy = Math.sin(age * 0.5) * 0.01 + (baseY - this.y) * 0.05;
                }
                case BAT -> {
                    heading += (rnd.nextDouble() - 0.5) * 0.9;
                    speed = 0.085;
                    vy = (rnd.nextDouble() - 0.5) * 0.08 + (baseY - this.y) * 0.02;
                }
                default -> { // BIRD
                    heading += (rnd.nextDouble() - 0.5) * 0.04;
                    speed = 0.13;
                    vy = Math.sin(age * 0.08) * 0.01 + (baseY - this.y) * 0.01;
                }
            }
            vx = Math.cos(heading) * speed;
            vz = Math.sin(heading) * speed;
        }
        if (orbit == null) {
            double nx = this.x + vx;
            double ny = this.y + vy;
            double nz = this.z + vz;
            if (!flock && blocked(nx, ny, nz)) {
                heading += Math.PI * (0.5 + rnd.nextDouble() * 0.5);
            } else {
                this.x = nx;
                this.y = ny;
                this.z = nz;
            }
        }
        if (age % kind.framePeriod == 0) {
            frame = (frame + 1) % kind.frames;
            this.setSprite(sprites.get(frame, kind.frames - 1));
        }
        float in = Math.min(1f, this.age / 20f);
        float out = Math.min(1f, (this.lifetime - this.age) / 30f);
        this.alpha = Math.max(0f, Math.min(in, out));
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
