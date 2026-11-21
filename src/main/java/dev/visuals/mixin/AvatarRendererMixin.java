package dev.visuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.visuals.effects.AfkEffect;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Namensschilder von Spielern im AFK-Kino-Modus ausblenden (Spieler haben eine eigene Variante). */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true, require = 0)
    private void visuals$hideNames(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                   CameraRenderState camera, CallbackInfo ci) {
        if (AfkEffect.hideNames()) {
            ci.cancel();
        }
    }
}
