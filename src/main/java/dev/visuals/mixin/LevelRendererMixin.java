package dev.visuals.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import dev.visuals.effects.PuddleShader;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hängt die Welt-Nachbearbeitung (Pfützen-Spiegelung, Jahreszeiten-Farben) ein: am besten direkt nach dem festen
 * Gelände, damit Entities, Namensschilder, Wasser und Partikel unberührt bleiben. Als Ersatz läuft sie am Ende des
 * Bildes, falls der frühere Zeitpunkt nicht greift.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void visuals$beforeLevel(GraphicsResourceAllocator allocator, DeltaTracker tracker, boolean outline,
                                     Camera camera, Matrix4f view, Matrix4f projection, Matrix4f cullProjection,
                                     GpuBufferSlice fog, Vector4f fogColor, boolean sky, CallbackInfo ci) {
        PuddleShader.begin(view, projection);
    }

    /** Der Haupt-Durchgang: direkt nach dem Zeichnen des undurchsichtigen Geländes. */
    @Inject(method = "method_62214",
            at = @At(value = "INVOKE", ordinal = 0, shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V"),
            require = 0)
    private void visuals$afterTerrain(GpuBufferSlice fog, LevelRenderState state, ProfilerFiller profiler,
                                      Matrix4f view, ResourceHandle<?> a, ResourceHandle<?> b, boolean flag,
                                      ResourceHandle<?> c, ResourceHandle<?> d, CallbackInfo ci) {
        PuddleShader.afterTerrain();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void visuals$afterLevel(GraphicsResourceAllocator allocator, DeltaTracker tracker, boolean outline,
                                    Camera camera, Matrix4f view, Matrix4f projection, Matrix4f cullProjection,
                                    GpuBufferSlice fog, Vector4f fogColor, boolean sky, CallbackInfo ci) {
        PuddleShader.atEnd();
    }
}
