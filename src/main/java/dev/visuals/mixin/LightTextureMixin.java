package dev.visuals.mixin;

import dev.visuals.effects.LightEffect;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Tönt die Himmelslicht-Farbe der Lichtkarte (Lichtfarben nach Tageszeit, Mondlicht). */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @ModifyArg(method = "updateLightTexture",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;"),
            index = 0, require = 0)
    private Vector3fc visuals$skyLightColor(Vector3fc color) {
        return LightEffect.tint(color);
    }
}
