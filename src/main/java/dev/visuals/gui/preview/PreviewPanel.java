package dev.visuals.gui.preview;

import dev.visuals.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorschau-Bildschirm: Szene plus Regler für Tageszeit, Jahreszeit, Wetter und Wind (nur für die Vorschau),
 * dazu "Vollbild" und "Vorher/Nachher" (gedrückt halten).
 */
public final class PreviewPanel {
    private static final int CONTROLS_H = 122;

    @FunctionalInterface
    private interface Drag {
        void on(double mx);
    }

    private record Hit(int x, int y, int w, int h, Runnable click, Drag drag) {
    }

    private final PreviewState state = new PreviewState();
    private final List<Hit> hits = new ArrayList<>();
    private Drag activeDrag;
    private boolean fullscreenRequested;

    public PreviewState state() {
        return state;
    }

    public boolean consumeFullscreenRequest() {
        boolean r = fullscreenRequested;
        fullscreenRequested = false;
        return r;
    }

    public void renderSceneOnly(GuiGraphics g, int x, int y, int w, int h) {
        ScenePreview.render(g, x, y, w, h, state);
    }

    public void render(GuiGraphics g, Font font, int mx, int my, int x, int y, int w, int h) {
        hits.clear();
        int sceneH = Math.max(60, h - CONTROLS_H);
        g.fill(x - 1, y - 1, x + w + 1, y + sceneH + 1, 0xFF000000);
        ScenePreview.render(g, x, y, w, sceneH, state);
        if (state.compare) {
            String label = I18n.get("visuals.gui.preview.vanilla");
            g.fill(x + 4, y + 4, x + 10 + font.width(label), y + 16, 0xB0000000);
            g.drawString(font, label, x + 7, y + 6, Theme.WHITE, true);
        }

        int cy = y + sceneH + 8;
        int cw = Math.min(w, 310);
        int lx = x + (w - cw) / 2;

        slider(g, font, mx, my, lx, cy, cw, I18n.get("visuals.gui.preview.time"), state.hour / 24.0,
                String.format(Locale.ROOT, "%02d:%02d", (int) state.hour, (int) ((state.hour % 1.0) * 60)),
                f -> state.hour = Math.round(f * 96.0) / 4.0);
        cy += 22;
        segmented(g, font, mx, my, lx, cy, cw, state.season,
                new String[]{"visuals.gui.season.spring", "visuals.gui.season.summer", "visuals.gui.season.autumn",
                        "visuals.gui.season.winter"}, i -> state.season = i);
        cy += 22;
        segmented(g, font, mx, my, lx, cy, cw, state.weather,
                new String[]{"visuals.gui.weather.clear", "visuals.gui.weather.rain", "visuals.gui.weather.snow",
                        "visuals.gui.weather.storm"}, i -> state.weather = i);
        cy += 22;
        slider(g, font, mx, my, lx, cy, cw, I18n.get("visuals.gui.preview.wind"), state.wind,
                String.format(Locale.ROOT, "%d %%", (int) Math.round(state.wind * 100)),
                f -> state.wind = Math.round(f * 20.0) / 20.0);
        cy += 22;

        int bw = (cw - 4) / 2;
        button(g, font, mx, my, lx, cy, bw, I18n.get("visuals.gui.preview.fullscreen"), false,
                () -> fullscreenRequested = true);
        button(g, font, mx, my, lx + bw + 4, cy, bw, I18n.get("visuals.gui.preview.compare"), state.compare, null);
        hits.add(new Hit(lx + bw + 4, cy, bw, 20, () -> {
            state.compare = true;
            activeDrag = m -> {
            };
        }, null));
    }

    private void slider(GuiGraphics g, Font font, int mx, int my, int x, int y, int w, String label, double frac,
                        String valueText, java.util.function.DoubleConsumer set) {
        g.drawString(font, label, x + 2, y + 6, Theme.WHITE, true);
        int tx = x + 70;
        int tw = w - 70;
        boolean hov = Theme.inside(mx, my, tx, y, tw, 20);
        Theme.track(g, tx, y, tw, 20, hov);
        int hx = tx + 1 + (int) Math.round(frac * (tw - 10));
        Theme.button(g, font, hx, y, 8, 20, "", hov, true, Theme.WHITE);
        g.drawString(font, valueText, tx + (tw - font.width(valueText)) / 2, y + 6, Theme.WHITE, true);
        Drag drag = m -> set.accept(Math.max(0, Math.min(1, (m - tx - 5) / (tw - 10.0))));
        hits.add(new Hit(tx, y, tw, 20, () -> {
        }, drag));
    }

    private void segmented(GuiGraphics g, Font font, int mx, int my, int x, int y, int w, int selected, String[] keys,
                           java.util.function.IntConsumer set) {
        int n = keys.length;
        int sw = (w - (n - 1) * 2) / n;
        for (int i = 0; i < n; i++) {
            int bx = x + i * (sw + 2);
            boolean sel = i == selected;
            boolean hov = Theme.inside(mx, my, bx, y, sw, 20);
            Theme.button(g, font, bx, y, sw, 20, I18n.get(keys[i]), hov, true, sel ? 0xFFFFFF55 : Theme.WHITE);
            if (sel) {
                g.fill(bx + 2, y + 17, bx + sw - 2, y + 18, 0xFFFFFF55);
            }
            final int idx = i;
            hits.add(new Hit(bx, y, sw, 20, () -> set.accept(idx), null));
        }
    }

    private void button(GuiGraphics g, Font font, int mx, int my, int x, int y, int w, String label, boolean pressed,
                        Runnable click) {
        boolean hov = Theme.inside(mx, my, x, y, w, 20);
        Theme.button(g, font, x, y, w, 20, label, hov || pressed, true, pressed ? 0xFFFFFF55 : Theme.WHITE);
        if (click != null) {
            hits.add(new Hit(x, y, w, 20, click, null));
        }
    }

    public boolean mouseClicked(double mx, double my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (Theme.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                hit.click().run();
                if (hit.drag() != null) {
                    activeDrag = hit.drag();
                    activeDrag.on(mx);
                }
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx) {
        if (activeDrag == null) {
            return false;
        }
        activeDrag.on(mx);
        return true;
    }

    public void mouseReleased() {
        activeDrag = null;
        state.compare = false;
    }
}
