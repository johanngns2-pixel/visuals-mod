package dev.visuals.hud;

import dev.visuals.effects.AfkEffect;
import dev.visuals.effects.Opts;
import dev.visuals.spotify.Lyrics;
import dev.visuals.spotify.SpotifyClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Anzeige über dem Bild: Cinema-Balken und Ortseinblendung (AFK) sowie Songtext (Spotify). */
public final class VisualsHud {
    private VisualsHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        try {
            lyrics(g, mc);
            afk(g, mc);
        } catch (RuntimeException e) {
            // Anzeige darf das Spiel nie stören
        }
    }

    private static void afk(GuiGraphics g, Minecraft mc) {
        if (!AfkEffect.bars()) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        float p = Math.min(1f, AfkEffect.afkTicks() / 15f);
        int bar = (int) (h * 0.11f * p);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        if (Opts.bool("afk.locationTitle") && AfkEffect.afkTicks() > 20 && AfkEffect.afkTicks() < 190) {
            int t = AfkEffect.afkTicks() - 20;
            float a = Math.min(1f, Math.min(t / 20f, (170 - t) / 20f));
            int alpha = (int) (255 * Math.max(0f, a));
            if (alpha > 4) {
                Component name = mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                        .map(k -> (Component) Component.translatable("biome." + k.identifier().getNamespace() + "."
                                + k.identifier().getPath()))
                        .orElse(Component.empty());
                Font font = mc.font;
                int tw = font.width(name);
                g.drawString(font, name, (w - tw) / 2, h - bar / 2 - 4, (alpha << 24) | 0xFFFFFF, true);
            }
        }
    }

    private static void lyrics(GuiGraphics g, Minecraft mc) {
        if (!Opts.active("spotify") || !Opts.bool("spotify.lyrics") || !SpotifyClient.connected()) {
            return;
        }
        List<Lyrics.Line> lines = SpotifyClient.lyrics();
        String text;
        long sinceStart = 10_000;
        if (lines.isEmpty()) {
            if (!SpotifyClient.lyricsLoaded() || !"title".equals(Opts.text("spotify.noLyrics"))
                    || SpotifyClient.title().isEmpty()) {
                return;
            }
            text = SpotifyClient.artist().isEmpty() ? SpotifyClient.title() : SpotifyClient.artist() + " \u2013 " + SpotifyClient.title();
        } else {
            long pos = SpotifyClient.positionMs();
            int idx = Lyrics.indexAt(lines, pos);
            if (idx < 0 || lines.get(idx).text().isBlank()) {
                return;
            }
            text = lines.get(idx).text();
            sinceStart = pos - lines.get(idx).timeMs();
        }
        Font font = mc.font;
        float size = (float) Opts.num("spotify.size");
        int w = g.guiWidth();
        int h = g.guiHeight();
        String transition = Opts.text("spotify.transition");
        float ease = Math.min(1f, sinceStart / 320f);
        float alphaF = "none".equals(transition) ? 1f : ease;
        float slide = "slide".equals(transition) ? (1f - ease) * 10f : 0f;
        int base = Opts.color("spotify.color") & 0xFFFFFF;
        int alpha = (int) (255 * alphaF);
        if (alpha <= 4) {
            return;
        }
        int y = switch (Opts.text("spotify.position")) {
            case "center" -> h / 2 - 20;
            case "bottom" -> h - 78;
            default -> 30;
        };
        String style = Opts.text("spotify.style");
        g.pose().pushMatrix();
        g.pose().translate(w / 2f, y + slide);
        g.pose().scale(size, size);
        int tw = font.width(text);
        int x = -tw / 2;
        if ("glow".equals(style)) {
            int glow = ((alpha / 3) << 24) | base;
            for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}}) {
                g.drawString(font, text, x + d[0], d[1], glow, false);
            }
        }
        g.drawString(font, text, x, 0, (alpha << 24) | base, "shadow".equals(style));
        g.pose().popMatrix();
    }
}
