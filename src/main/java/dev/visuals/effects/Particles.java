package dev.visuals.effects;

import dev.visuals.VisualsMod;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Registriert die eigenen Partikel der Mod (nur auf dem Client, kein Server nötig). */
public final class Particles {
    public static SimpleParticleType glow;
    public static SimpleParticleType butterfly;
    public static SimpleParticleType bird;
    public static SimpleParticleType dragonfly;
    public static SimpleParticleType bat;
    public static SimpleParticleType footprint;
    public static SimpleParticleType ring;

    private Particles() {
    }

    private static SimpleParticleType type(String name) {
        SimpleParticleType t = FabricParticleTypes.simple();
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(VisualsMod.MOD_ID, name), t);
        return t;
    }

    public static void register() {
        ParticleFactoryRegistry reg = ParticleFactoryRegistry.getInstance();
        glow = type("glow");
        reg.register(glow, sprites -> (o, level, x, y, z, xd, yd, zd, random) ->
                new GlowParticle(level, x, y, z, sprites.get(random)));
        footprint = type("footprint");
        reg.register(footprint, sprites -> (o, level, x, y, z, xd, yd, zd, random) ->
                new DecalParticle(level, x, y, z, sprites.get(random)));
        ring = type("ring");
        reg.register(ring, sprites -> (o, level, x, y, z, xd, yd, zd, random) ->
                new DecalParticle(level, x, y, z, sprites.get(random)));
        butterfly = type("butterfly");
        reg.register(butterfly, sprites -> (o, level, x, y, z, xd, yd, zd, random) ->
                new CritterParticle(level, x, y, z, sprites, CritterParticle.Kind.BUTTERFLY));
        bird = type("bird");
        reg.register(bird, sprites -> (o, level, x, y, z, xd, yd, zd, random) ->
                new CritterParticle(level, x, y, z, sprites, CritterParticle.Kind.BIRD));
        dragonfly = type("dragonfly");
        reg.register(dragonfly, sprites -> (o, level, x, y, z, xd, yd, zd, random) ->
                new CritterParticle(level, x, y, z, sprites, CritterParticle.Kind.DRAGONFLY));
        bat = type("bat");
        reg.register(bat, sprites -> (o, level, x, y, z, xd, yd, zd, random) ->
                new CritterParticle(level, x, y, z, sprites, CritterParticle.Kind.BAT));
    }
}
