package dev.visuals;

import dev.visuals.config.ConfigManager;
import dev.visuals.config.OptionRegistry;
import dev.visuals.config.def.GeneralOptions;
import dev.visuals.config.def.Tabs;
import dev.visuals.util.Log;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Einstiegspunkt. Läuft nur auf dem Client. */
public final class VisualsMod implements ClientModInitializer {
    public static final String MOD_ID = "visuals";

    private static OptionRegistry registry;
    private static ConfigManager config;

    @Override
    public void onInitializeClient() {
        registry = new OptionRegistry();
        Tabs.registerAll(registry);
        GeneralOptions.register(registry);
        // Weitere Reiter-Optionen werden hier registriert, sobald sie existieren.
        registry.freeze();

        config = new ConfigManager(
                FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json"), registry);
        config.load();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.close());
        Log.info("Visuals geladen: {} Reiter, {} Optionen", registry.tabs().size(), registry.options().size());
    }

    public static OptionRegistry registry() {
        return registry;
    }

    public static ConfigManager config() {
        return config;
    }
}
