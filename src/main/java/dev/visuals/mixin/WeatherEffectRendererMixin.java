package dev.visuals.mixin;

import dev.visuals.effects.WeatherEffect;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.WeatherRenderState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Regen- und Schneestärke. */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void visuals$afterExtract(Level level, int ticks, float partialTick, Vec3 pos, WeatherRenderState state,
                                      CallbackInfo ci) {
        WeatherEffect.applyWeather(state);
    }
}
