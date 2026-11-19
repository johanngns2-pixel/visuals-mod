package dev.visuals.effects;

import dev.visuals.spotify.SpotifyClient;
import net.minecraft.client.Minecraft;

/** Hält den Schalter "Mit Spotify verbunden" und die tatsächliche Verbindung im Gleichklang. */
public final class SpotifyEffect {
    private static boolean autoTried;

    private SpotifyEffect() {
    }

    public static void tick(Minecraft mc) {
        if (!Opts.active("spotify")) {
            return;
        }
        boolean wanted = Opts.bool("spotify.connected");
        if (wanted && !SpotifyClient.busy()) {
            SpotifyClient.connect();
        } else if (!wanted && SpotifyClient.busy()) {
            SpotifyClient.disconnect();
        }
    }
}
