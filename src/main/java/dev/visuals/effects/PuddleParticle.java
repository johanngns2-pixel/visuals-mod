package dev.visuals.effects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Eine Pfütze: ein flacher, glänzender Fleck auf dem Boden, der die Farbe des Himmels zurückwirft.
 * Sie bleibt dauerhaft stehen, bis {@link #retire()} aufgerufen wird (Schalter aus oder weit weg).
 * Rein optisch (ein Partikel), es wird kein Block verändert.
 */
public final class PuddleParticle extends SingleQuadParticle {
    private static final float HALF_PI = 1.5707964f;
    private static final FacingCameraMode FLAT = (rotation, camera, partialTick) -> rotation.rotationX(-HALF_PI);
    private static final int FADE_TICKS = 30;

    private final double shimmerSeed;
    private int retiredAge = -1;

    public PuddleParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, float size) {
        super(level, x, y, z, sprite);
        this.hasPhysics = false;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.gravity = 0;
        this.lifetime = Integer.MAX_VALUE;
        this.quadSize = size;
        this.alpha = 0f;
        this.shimmerSeed = level.random.nextDouble() * 100.0;
        applyLook();
    }

    /** Blendet die Pfütze aus und entfernt sie danach. */
    public void retire() {
        if (retiredAge < 0) {
            retiredAge = this.age;
        }
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.age++;
        if (retiredAge >= 0 && this.age - retiredAge >= FADE_TICKS) {
            this.remove();
            return;
        }
        applyLook();
    }

    private void applyLook() {
        long time = this.level.getDayTime();
        float rain = this.level.getRainLevel(1.0f);
        int rgb = PuddleLook.color(time, rain, shimmerSeed, this.age);
        this.rCol = ((rgb >> 16) & 255) / 255f;
        this.gCol = ((rgb >> 8) & 255) / 255f;
        this.bCol = (rgb & 255) / 255f;
        float fade = Math.min(1f, this.age / (float) FADE_TICKS);
        if (retiredAge >= 0) {
            fade = Math.min(fade, 1f - (this.age - retiredAge) / (float) FADE_TICKS);
        }
        this.alpha = PuddleLook.alpha() * Math.max(0f, fade);
    }

    @Override
    public FacingCameraMode getFacingCameraMode() {
        return FLAT;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
