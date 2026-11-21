package dev.visuals.effects;

import dev.visuals.util.Log;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Schaltet für den AFK-Modus vorübergehend ein Shader-Paket in Iris ein und danach wieder zurück.
 * Iris wird nur per Reflection angesprochen, die Mod läuft also auch ohne Iris. Ohne Iris (oder bei Fehlern)
 * liefert {@link #start} {@code false}, und der eingebaute Kino-Shader übernimmt.
 */
public final class IrisBridge {
    public static final String MELLOW = "Mellow_Shader_v3_1_1.zip";
    public static final String BLISS = "Bliss_v2_1_1__Chocapic13_Shaders_edit_.zip";

    private static boolean active;
    private static boolean prevEnabled;
    private static String prevPack;

    private IrisBridge() {
    }

    public static boolean installed() {
        return FabricLoader.getInstance().isModLoaded("iris");
    }

    public static boolean active() {
        return active;
    }

    /** Aktiviert das Paket. {@code true}, wenn Iris jetzt dieses Paket verwendet. */
    public static boolean start(String packFile) {
        if (!installed() || active) {
            return false;
        }
        try {
            if (!ensureInstalled(packFile)) {
                return false;
            }
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object config = iris.getMethod("getIrisConfig").invoke(null);
            Class<?> cc = config.getClass();
            prevEnabled = (Boolean) cc.getMethod("areShadersEnabled").invoke(config);
            Object name = cc.getMethod("getShaderPackName").invoke(config);
            prevPack = name instanceof Optional<?> o && o.isPresent() ? String.valueOf(o.get()) : null;
            if (prevEnabled && packFile.equals(prevPack)) {
                return false; // läuft schon, nichts zu tun und nichts zurückzusetzen
            }
            cc.getMethod("setShaderPackName", String.class).invoke(config, packFile);
            cc.getMethod("setShadersEnabled", boolean.class).invoke(config, true);
            iris.getMethod("reload").invoke(null);
            active = true;
            Log.warn("AFK: Iris-Shader {} aktiviert", packFile);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.warn("AFK: Iris-Shader konnte nicht aktiviert werden: {}", String.valueOf(e.getCause() != null ? e.getCause() : e));
            return false;
        }
    }

    /** Stellt den früheren Iris-Zustand wieder her. */
    public static void restore() {
        if (!active) {
            return;
        }
        active = false;
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object config = iris.getMethod("getIrisConfig").invoke(null);
            Class<?> cc = config.getClass();
            if (prevPack != null) {
                cc.getMethod("setShaderPackName", String.class).invoke(config, prevPack);
            }
            cc.getMethod("setShadersEnabled", boolean.class).invoke(config, prevEnabled);
            Method reload = iris.getMethod("reload");
            reload.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.warn("AFK: Iris-Zustand konnte nicht zurückgesetzt werden: {}", String.valueOf(e.getCause() != null ? e.getCause() : e));
        }
    }

    /** Legt das Paket aus der Mod in den shaderpacks-Ordner, falls es dort noch fehlt. */
    private static boolean ensureInstalled(String packFile) {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
        Path target = dir.resolve(packFile);
        if (Files.isRegularFile(target)) {
            return true;
        }
        try (InputStream in = IrisBridge.class.getResourceAsStream("/assets/visuals/shaderpacks/" + packFile)) {
            if (in == null) {
                Log.warn("AFK: Shader-Paket {} fehlt im Ordner shaderpacks und ist nicht in der Mod enthalten", packFile);
                return false;
            }
            Files.createDirectories(dir);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            Log.warn("AFK: Shader-Paket konnte nicht kopiert werden: {}", e.getMessage());
            return false;
        }
    }
}
