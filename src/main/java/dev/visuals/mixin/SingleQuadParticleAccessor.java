package dev.visuals.mixin;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Erlaubt es, Farbe, Transparenz und Größe eines Partikels nach dem Erzeugen zu setzen. */
@Mixin(SingleQuadParticle.class)
public interface SingleQuadParticleAccessor {
    @Accessor("rCol")
    void visuals$setRed(float value);

    @Accessor("gCol")
    void visuals$setGreen(float value);

    @Accessor("bCol")
    void visuals$setBlue(float value);

    @Accessor("alpha")
    void visuals$setAlpha(float value);
}
