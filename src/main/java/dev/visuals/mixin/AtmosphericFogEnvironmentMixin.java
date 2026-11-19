package dev.visuals.mixin;

import dev.visuals.effects.WeatherEffect;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nebeldichte und Nebelfarbe. */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class AtmosphericFogEnvironmentMixin {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void visuals$afterSetup(FogData data, Camera camera, ClientLevel level, float renderDistance,
                                    DeltaTracker tracker, CallbackInfo ci) {
        WeatherEffect.applyFog(data);
    }

    @Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
    private void visuals$color(ClientLevel level, Camera camera, int renderDistance, float partialTick,
                               CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(WeatherEffect.fogColor(cir.getReturnValue()));
    }
}
