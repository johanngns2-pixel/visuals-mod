package dev.visuals.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Erlaubt es, die Schwerkraft eines Partikels zu lesen und zu ändern (Fallgeschwindigkeit des Laubs). */
@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("gravity")
    float visuals$getGravity();

    @Accessor("gravity")
    void visuals$setGravity(float value);
}
