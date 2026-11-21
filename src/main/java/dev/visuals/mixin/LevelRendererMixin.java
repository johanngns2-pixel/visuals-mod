package dev.visuals.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.visuals.effects.PuddleShader;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hängt den Pfützen-Spiegel-Shader an das Ende des Welt-Renderings. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void visuals$afterLevel(GraphicsResourceAllocator allocator, DeltaTracker tracker, boolean outline,
                                    Camera camera, Matrix4f view, Matrix4f projection, Matrix4f cullProjection,
                                    GpuBufferSlice fog, Vector4f fogColor, boolean sky, CallbackInfo ci) {
        PuddleShader.apply(camera, view, projection);
    }
}
