package dev.visuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.visuals.effects.AfkEffect;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blendet Namensschilder im AFK-Kino-Modus aus. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true, require = 0)
    private void visuals$hideNames(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                   CameraRenderState camera, CallbackInfo ci) {
        if (AfkEffect.hideNames()) {
            ci.cancel();
        }
    }
}
