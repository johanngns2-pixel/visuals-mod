package dev.visuals.effects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Weich leuchtender Punkt, immer zur Kamera gedreht: Meteore, Polarlicht, Regenbogen. Voll hell, ohne Physik. */
public final class GlowParticle extends SingleQuadParticle {
    public interface Mover {
        void update(GlowParticle p, int age);
    }

    private float baseAlpha = 1f;
    private float alphaScale = 1f;
    private int fadeIn = 10;
    private int fadeOut = 10;
    private Mover mover;

    public GlowParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.hasPhysics = false;
        this.gravity = 0;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.lifetime = 20;
        this.alpha = 0f;
    }

    public GlowParticle setup(int rgb, float size, int lifetime, float alpha, double vx, double vy, double vz,
                              int fadeIn, int fadeOut) {
        this.rCol = ((rgb >> 16) & 255) / 255f;
        this.gCol = ((rgb >> 8) & 255) / 255f;
        this.bCol = (rgb & 255) / 255f;
        this.quadSize = size;
        this.lifetime = lifetime;
        this.baseAlpha = alpha;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.fadeIn = fadeIn;
        this.fadeOut = fadeOut;
        return this;
    }

    public GlowParticle mover(Mover mover) {
        this.mover = mover;
        return this;
    }

    public void moveTo(double nx, double ny, double nz) {
        this.x = nx;
        this.y = ny;
        this.z = nz;
    }

    public double px() {
        return this.x;
    }

    public double py() {
        return this.y;
    }

    public double pz() {
        return this.z;
    }

    public void alphaScale(float scale) {
        this.alphaScale = scale;
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
        this.x += this.xd;
        this.y += this.yd;
        this.z += this.zd;
        if (mover != null) {
            mover.update(this, this.age);
        }
        float in = fadeIn <= 0 ? 1f : Math.min(1f, this.age / (float) fadeIn);
        float out = fadeOut <= 0 ? 1f : Math.min(1f, (this.lifetime - this.age) / (float) fadeOut);
        this.alpha = baseAlpha * alphaScale * Math.max(0f, Math.min(in, out));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
