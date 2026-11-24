package dev.visuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.visuals.effects.SkyEffect;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hängt die Himmel-Einstellungen in den Himmel-Renderer von Minecraft ein. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Shadow
    private int starIndexCount;

    @Unique
    private int visuals$originalStarCount = -1;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void visuals$afterExtract(ClientLevel level, float partialTick, Camera camera, SkyRenderState state,
                                      CallbackInfo ci) {
        SkyEffect.apply(level, partialTick, state);
    }

    @Inject(method = "renderStars", at = @At("HEAD"))
    private void visuals$starsBefore(float brightness, PoseStack pose, CallbackInfo ci) {
        visuals$originalStarCount = starIndexCount;
        starIndexCount = SkyEffect.starIndexCount(starIndexCount);
    }

    @Inject(method = "renderStars", at = @At("RETURN"))
    private void visuals$starsAfter(float brightness, PoseStack pose, CallbackInfo ci) {
        if (visuals$originalStarCount >= 0) {
            starIndexCount = visuals$originalStarCount;
            visuals$originalStarCount = -1;
        }
    }

    @Inject(method = "renderSun", at = @At("HEAD"))
    private void visuals$sunBefore(float angle, PoseStack pose, CallbackInfo ci) {
        SkyEffect.pushScaled(pose, SkyEffect.sunScale());
    }

    @Inject(method = "renderSun", at = @At("RETURN"))
    private void visuals$sunAfter(float angle, PoseStack pose, CallbackInfo ci) {
        pose.popPose();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"))
    private void visuals$moonBefore(MoonPhase phase, float angle, PoseStack pose, CallbackInfo ci) {
        SkyEffect.pushScaled(pose, SkyEffect.moonScale());
    }

    @Inject(method = "renderMoon", at = @At("RETURN"))
    private void visuals$moonAfter(MoonPhase phase, float angle, PoseStack pose, CallbackInfo ci) {
        pose.popPose();
    }

    @ModifyArg(method = "renderSun", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 1, require = 0)
    private Vector4fc visuals$sunColor(Vector4fc color) {
        return SkyEffect.tint(color, "sky.sunColor");
    }

    @ModifyArg(method = "renderMoon", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 1, require = 0)
    private Vector4fc visuals$moonColor(Vector4fc color) {
        return SkyEffect.tint(color, "sky.moonColor");
    }

    @ModifyArg(method = "renderStars", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 1, require = 0)
    private Vector4fc visuals$starColor(Vector4fc color) {
        return SkyEffect.tint(color, "sky.starColor");
    }
}
