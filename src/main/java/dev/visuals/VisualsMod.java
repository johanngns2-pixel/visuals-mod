package dev.visuals;

import dev.visuals.config.ConfigManager;
import dev.visuals.config.OptionRegistry;
import dev.visuals.config.PresetManager;
import dev.visuals.config.UndoStack;
import dev.visuals.config.def.GeneralOptions;
import dev.visuals.config.def.MoreOptions;
import dev.visuals.config.def.SkyOptions;
import dev.visuals.config.def.Tabs;
import com.mojang.blaze3d.platform.InputConstants;
import dev.visuals.effects.Effects;
import dev.visuals.effects.PuddleEffect;
import dev.visuals.effects.PuddleParticle;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import dev.visuals.gui.VisualsScreen;
import dev.visuals.util.Log;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Einstiegspunkt. Läuft nur auf dem Client. */
public final class VisualsMod implements ClientModInitializer {
    public static final String MOD_ID = "visuals";

    private static OptionRegistry registry;
    private static ConfigManager config;
    private static KeyMapping openKey;
    private static UndoStack undo;
    private static PresetManager presets;

    @Override
    public void onInitializeClient() {
        registry = new OptionRegistry();
        Tabs.registerAll(registry);
        GeneralOptions.register(registry);
        SkyOptions.register(registry);
        MoreOptions.register(registry);
        // Weitere Reiter-Optionen werden hier registriert, sobald sie existieren.
        registry.freeze();

        config = new ConfigManager(
                FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json"), registry);
        config.load();
        undo = new UndoStack(config);
        presets = new PresetManager(registry, config, undo,
                FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("presets"));

        // Pfützen-Partikel (nur auf dem Client, kein Server nötig)
        SimpleParticleType puddle = FabricParticleTypes.simple();
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "puddle"), puddle);
        ParticleFactoryRegistry.getInstance().register(puddle, sprites ->
                (options, level, x, y, z, xd, yd, zd, random) ->
                        new PuddleParticle(level, x, y, z, sprites.get(random), 0.5f));
        PuddleEffect.type = puddle;

        // Taste zum Öffnen (Standard: Rechts-Shift, in den Steuerungs-Einstellungen änderbar)
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.visuals.open", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Effects.tick(client);
            while (openKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new VisualsScreen(null));
                }
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.close());
        Log.info("Visuals geladen: {} Reiter, {} Optionen", registry.tabs().size(), registry.options().size());
    }

    public static OptionRegistry registry() {
        return registry;
    }

    public static UndoStack undo() {
        return undo;
    }

    public static PresetManager presets() {
        return presets;
    }

    public static ConfigManager config() {
        return config;
    }
}
