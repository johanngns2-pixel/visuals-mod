package dev.visuals.effects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Flacher Abdruck auf dem Boden oder Wasser: Fußspuren, Schneedellen, Wasserringe. Blendet aus und wächst optional. */
public final class DecalParticle extends SingleQuadParticle {
    private static final float HALF_PI = 1.5707964f;

    private float yaw;
    private float baseSize = 0.3f;
    private float maxAlpha = 0.6f;
    private float grow;
    private final FacingCameraMode mode = (rotation, camera, partialTick) -> rotation.rotationY(yaw).rotateX(-HALF_PI);

    public DecalParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.hasPhysics = false;
        this.gravity = 0;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.lifetime = 100;
        this.alpha = 0f;
    }

    public DecalParticle setup(int rgb, float size, int lifetime, float alpha, float yawRad, float grow) {
        this.rCol = ((rgb >> 16) & 255) / 255f;
        this.gCol = ((rgb >> 8) & 255) / 255f;
        this.bCol = (rgb & 255) / 255f;
        this.baseSize = size;
        this.quadSize = size;
        this.lifetime = lifetime;
        this.maxAlpha = alpha;
        this.yaw = yawRad;
        this.grow = grow;
        return this;
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
        float t = this.age / (float) this.lifetime;
        float fadeIn = Math.min(1f, this.age / 4f);
        float fadeOut = Math.min(1f, (1f - t) / 0.35f);
        this.alpha = maxAlpha * Math.max(0f, Math.min(fadeIn, fadeOut));
        this.quadSize = baseSize * (1f + grow * t);
    }

    @Override
    public FacingCameraMode getFacingCameraMode() {
        return mode;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
