package dev.visuals.mixin;

import dev.visuals.effects.AfkEffect;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kino-Kamera im AFK-Modus. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private Vec3 position;

    @Shadow
    private boolean detached;

    @Shadow
    @Final
    private BlockPos.MutableBlockPos blockPosition;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void visuals$afk(Level level, Entity entity, boolean detachedIn, boolean mirror, float partialTick,
                             CallbackInfo ci) {
        double[] c = AfkEffect.cameraFor(level, entity, partialTick);
        if (c != null) {
            this.position = new Vec3(c[0], c[1], c[2]);
            this.blockPosition.set(c[0], c[1], c[2]);
            this.detached = true;
            this.setRotation((float) c[3], (float) c[4]);
        }
    }
}
