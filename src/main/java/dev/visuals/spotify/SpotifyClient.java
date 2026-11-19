package dev.visuals.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.visuals.VisualsMod;
import dev.visuals.effects.Opts;
import dev.visuals.util.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Spotify-Anbindung: Anmeldung (PKCE, ohne Client-Secret), Abfrage des laufenden Titels und Laden synchronisierter
 * Songtexte von LRCLIB. Gesprochen wird nur mit accounts.spotify.com, api.spotify.com und lrclib.net.
 * Der Refresh-Token liegt in config/visuals/spotify.json. Die Redirect-URI in der Spotify-App muss
 * {@value #REDIRECT} lauten.
 */
public final class SpotifyClient {
    public static final String REDIRECT = "http://127.0.0.1:43822/callback";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile boolean connected;
    private static volatile boolean connecting;
    private static volatile boolean running;
    private static volatile String accessToken;
    private static volatile long accessExpiry;
    private static volatile String refreshToken;
    private static volatile String clientId = "";

    private static volatile String trackId = "";
    private static volatile String title = "";
    private static volatile String artist = "";
    private static volatile boolean playing;
    private static volatile long progressMs;
    private static volatile long snapshotNanos;
    private static volatile List<Lyrics.Line> lyrics = List.of();
    private static volatile boolean lyricsLoaded;

    private SpotifyClient() {
    }

    // ------------------------------------------------------------------ Zustand für die Anzeige

    public static boolean connected() {
        return connected;
    }

    public static boolean busy() {
        return connecting || connected;
    }

    public static String title() {
        return title;
    }

    public static String artist() {
        return artist;
    }

    public static boolean lyricsLoaded() {
        return lyricsLoaded;
    }

    public static List<Lyrics.Line> lyrics() {
        return lyrics;
    }

    /** Aktuelle Abspielposition in Millisekunden, hochgerechnet seit der letzten Abfrage. */
    public static long positionMs() {
        long add = playing ? (System.nanoTime() - snapshotNanos) / 1_000_000L : 0L;
        return progressMs + add;
    }

    public static boolean isPlaying() {
        return playing;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(VisualsMod.MOD_ID).resolve("spotify.json");
    }

    private static void tell(String key) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable(key), false);
            }
        });
    }

    private static void setOption(boolean on) {
        Minecraft.getInstance().execute(() -> VisualsMod.config().set("spotify.connected", on));
    }

    // ------------------------------------------------------------------ Verbinden und Trennen

    /** Startet die Anmeldung im Browser. Ist schon ein Token gespeichert, wird er still erneuert. */
    public static synchronized void connect() {
        if (connecting || connected) {
            return;
        }
        clientId = Opts.text("spotify.clientId").trim();
        if (clientId.isEmpty()) {
            tell("visuals.spotify.noClientId");
            setOption(false);
            return;
        }
        connecting = true;
        Thread t = new Thread(() -> {
            try {
                if (loadRefresh() && refresh()) {
                    finishConnect();
                    return;
                }
                login();
            } catch (Exception e) {
                Log.warn("Spotify-Anmeldung fehlgeschlagen: {}", e.getMessage());
                tell("visuals.spotify.failed");
                connecting = false;
                setOption(false);
            }
        }, "visuals-spotify-login");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized void disconnect() {
        running = false;
        connected = false;
        connecting = false;
        accessToken = null;
        refreshToken = null;
        trackId = "";
        title = "";
        artist = "";
        lyrics = List.of();
        lyricsLoaded = false;
        try {
            Files.deleteIfExists(file());
        } catch (IOException e) {
            Log.warn("spotify.json konnte nicht gelöscht werden: {}", e.getMessage());
        }
    }

    private static void finishConnect() {
        connected = true;
        connecting = false;
        setOption(true);
        tell("visuals.spotify.connected");
        startPolling();
    }

    private static boolean loadRefresh() {
        try {
            Path f = file();
            if (!Files.isRegularFile(f)) {
                return false;
            }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!o.has("refresh") || !o.has("clientId") || !o.get("clientId").getAsString().equals(clientId)) {
                return false;
            }
            refreshToken = o.get("refresh").getAsString();
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static void saveRefresh() {
        try {
            Files.createDirectories(file().getParent());
            JsonObject o = new JsonObject();
            o.addProperty("clientId", clientId);
            o.addProperty("refresh", refreshToken);
            Files.writeString(file(), o.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.warn("spotify.json konnte nicht gespeichert werden: {}", e.getMessage());
        }
    }

    private static String randomString(int n) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static void login() throws Exception {
        String verifier = randomString(64);
        String challenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String state = randomString(16);
        String url = "https://accounts.spotify.com/authorize?response_type=code&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(REDIRECT) + "&code_challenge_method=S256&code_challenge=" + challenge
                + "&scope=" + enc("user-read-currently-playing user-read-playback-state") + "&state=" + state;
        try (ServerSocket server = new ServerSocket(43822, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(180_000);
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                mc.keyboardHandler.setClipboard(url);
                try {
                    net.minecraft.Util.getPlatform().openUri(URI.create(url));
                } catch (RuntimeException e) {
                    Log.warn("Browser konnte nicht geöffnet werden: {}", e.getMessage());
                }
            });
            tell("visuals.spotify.browser");
            String code = null;
            try (Socket s = server.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String request = in.readLine();
                String body = "Verbunden. Du kannst dieses Fenster schliessen.";
                if (request != null && request.startsWith("GET /callback")) {
                    String query = request.substring(request.indexOf('?') + 1, request.lastIndexOf(' '));
                    String gotState = null;
                    for (String part : query.split("&")) {
                        String[] kv = part.split("=", 2);
                        if (kv.length == 2) {
                            String v = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                            if (kv[0].equals("code")) {
                                code = v;
                            } else if (kv[0].equals("state")) {
                                gotState = v;
                            }
                        }
                    }
                    if (!state.equals(gotState)) {
                        code = null;
                    }
                    if (code == null) {
                        body = "Anmeldung abgebrochen.";
                    }
                }
                OutputStream out = s.getOutputStream();
                byte[] b = ("<html><body style=\"font-family:sans-serif;text-align:center;margin-top:20vh\"><h2>" + body
                        + "</h2></body></html>").getBytes(StandardCharsets.UTF_8);
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + b.length
                        + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(b);
                out.flush();
            }
            if (code == null) {
                throw new IOException("kein Code erhalten");
            }
            JsonObject tok = post("grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT)
                    + "&client_id=" + enc(clientId) + "&code_verifier=" + enc(verifier));
            readToken(tok);
            saveRefresh();
            finishConnect();
        }
    }

    private static JsonObject post(String form) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded").timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("Token-Antwort " + res.statusCode());
        }
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private static void readToken(JsonObject o) {
        accessToken = o.get("access_token").getAsString();
        accessExpiry = System.currentTimeMillis() + (o.get("expires_in").getAsLong() - 60) * 1000L;
        if (o.has("refresh_token")) {
            refreshToken = o.get("refresh_token").getAsString();
        }
    }

    private static boolean refresh() {
        try {
            readToken(post("grant_type=refresh_token&refresh_token=" + enc(refreshToken) + "&client_id=" + enc(clientId)));
            saveRefresh();
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ------------------------------------------------------------------ Abfragen

    private static void startPolling() {
        if (running) {
            return;
        }
        running = true;
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    if (System.currentTimeMillis() > accessExpiry && !refresh()) {
                        Thread.sleep(5000);
                        continue;
                    }
                    poll();
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    return;
                } catch (Exception e) {
                    Log.warn("Spotify-Abfrage: {}", e.getMessage());
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException ie) {
                        return;
                    }
                }
            }
        }, "visuals-spotify-poll");
        t.setDaemon(true);
        t.start();
    }

    private static void poll() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
                .header("Authorization", "Bearer " + accessToken).timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 204 || res.body() == null || res.body().isBlank()) {
            playing = false;
            return;
        }
        if (res.statusCode() == 401) {
            accessExpiry = 0;
            return;
        }
        if (res.statusCode() != 200) {
            return;
        }
        JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!o.has("item") || o.get("item").isJsonNull()) {
            playing = false;
            return;
        }
        JsonObject item = o.getAsJsonObject("item");
        String id = item.has("id") && !item.get("id").isJsonNull() ? item.get("id").getAsString() : item.get("name").getAsString();
        progressMs = o.has("progress_ms") && !o.get("progress_ms").isJsonNull() ? o.get("progress_ms").getAsLong() : 0L;
        snapshotNanos = System.nanoTime();
        playing = o.has("is_playing") && o.get("is_playing").getAsBoolean();
        if (!id.equals(trackId)) {
            trackId = id;
            title = item.get("name").getAsString();
            artist = item.getAsJsonArray("artists").size() > 0
                    ? item.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString() : "";
            lyrics = List.of();
            lyricsLoaded = false;
            long dur = item.has("duration_ms") ? item.get("duration_ms").getAsLong() / 1000L : 0L;
            fetchLyrics(id, title, artist, dur);
        }
    }

    private static void fetchLyrics(String id, String name, String by, long seconds) {
        if (!"lrclib".equals(Opts.text("spotify.source"))) {
            lyricsLoaded = true;
            return;
        }
        Thread t = new Thread(() -> {
            try {
                String url = "https://lrclib.net/api/get?track_name=" + enc(name) + "&artist_name=" + enc(by)
                        + (seconds > 0 ? "&duration=" + seconds : "");
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "visuals-mod")
                        .timeout(Duration.ofSeconds(10)).GET().build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200 && id.equals(trackId)) {
                    JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                    String synced = o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()
                            ? o.get("syncedLyrics").getAsString() : null;
                    lyrics = Lyrics.parse(synced);
                }
            } catch (Exception e) {
                Log.warn("Songtext konnte nicht geladen werden: {}", e.getMessage());
            } finally {
                if (id.equals(trackId)) {
                    lyricsLoaded = true;
                }
            }
        }, "visuals-spotify-lyrics");
        t.setDaemon(true);
        t.start();
    }
}
