package dev.visuals.gui;

import dev.visuals.VisualsMod;
import dev.visuals.config.ConfigManager;
import dev.visuals.config.OptionDef;
import dev.visuals.config.OptionRegistry;
import dev.visuals.config.OptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Optionsliste einer Kategorie im Stil der Minecraft-Optionen: pro Option eine Zeile mit Name links und einem
 * Bedienelement rechts (Schalter-Knopf, Regler, Wechsel-Knopf, Farbknopf, Textfeld) plus Zurücksetzen-Knopf.
 * Dazu Tooltips, Rechtsklick-Menü (Zurücksetzen, Kopieren, Einfügen, Wert eingeben) und Markierung ungültiger Eingaben.
 *
 * <p>Immediate Mode: beim Zeichnen werden die klickbaren Flächen gesammelt.
 */
public final class OptionPanel {
    public static final int ROW_H = 22;
    public static final int GROUP_H = 26;
    private static final int WIDGET_H = 20;
    private static final int CONTROL_W = 140;
    private static final int RESET_W = 20;
    private static final int POPUP_ROW_H = 16;

    @FunctionalInterface
    private interface Click {
        void on(double mx, double my, int button, boolean shift);
    }

    @FunctionalInterface
    private interface Drag {
        void on(double mx, boolean shift);
    }

    private record Hit(int x, int y, int w, int h, Click click) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> popupHits = new ArrayList<>();
    private final List<Hit> ctxHits = new ArrayList<>();
    private Drag activeDrag;
    private int clipTop;
    private int clipBottom;

    private String editId;
    private OptionDef<?> editDef;
    private boolean editHex;
    private final StringBuilder editBuf = new StringBuilder();

    private String colorId;
    private int colorX;
    private int colorY;
    private final float[] hsv = new float[3];
    private String ctxId;
    private int ctxX;
    private int ctxY;

    private final Map<String, Long> invalid = new HashMap<>();
    private String hoverId;
    private long hoverSince;
    private final Map<String, Integer> rowOffsets = new HashMap<>();
    private String highlightId;
    private long highlightUntil;
    private Consumer<OptionDef<?>> valueRequest = d -> {
    };

    /** Der Screen legt fest, was bei "Wert eingeben" passiert (er öffnet ein Eingabefenster). */
    public void setValueRequest(Consumer<OptionDef<?>> valueRequest) {
        this.valueRequest = valueRequest;
    }

    // ------------------------------------------------------------------ Zustand

    public boolean popupOpen() {
        return colorId != null || ctxId != null;
    }

    public boolean editing() {
        return editId != null;
    }

    public boolean blocksInput() {
        return popupOpen() || editing();
    }

    public Integer offsetOf(String id) {
        return rowOffsets.get(id);
    }

    public void highlight(String id) {
        highlightId = id;
        highlightUntil = System.currentTimeMillis() + 1800;
    }

    public static String valueText(OptionDef<?> def, Object value) {
        return toText(def, value);
    }

    public void closeAll() {
        editId = null;
        colorId = null;
        ctxId = null;
        activeDrag = null;
    }

    // ------------------------------------------------------------------ Liste zeichnen

    /** Zeichnet alle Gruppen und Optionen ab {@code y} und liefert das y-Ende des Inhalts. */
    public int render(GuiGraphics g, Font font, int mx, int my, int x, int y, int w,
                      int clipTop, int clipBottom, String tabId) {
        hits.clear();
        ctxHits.clear();
        this.clipTop = clipTop;
        this.clipBottom = clipBottom;
        ConfigManager cfg = VisualsMod.config();
        OptionRegistry reg = VisualsMod.registry();
        boolean popup = popupOpen();
        String hovered = null;
        int originY = y;

        for (String group : reg.groupsOf(tabId)) {
            String heading = I18n.get("visuals.group." + tabId + "." + group);
            g.drawString(font, heading, x + (w - font.width(heading)) / 2, y + 9, Theme.WHITE, true);
            y += GROUP_H;
            for (OptionDef<?> def : reg.optionsOf(tabId)) {
                if (!def.groupId().equals(group)) {
                    continue;
                }
                rowOffsets.put(def.id(), y - originY);
                if (y + ROW_H > clipTop && y < clipBottom) {
                    if (row(g, font, mx, my, x, y, w, def, cfg, popup)) {
                        hovered = def.id();
                    }
                }
                y += ROW_H;
            }
            y += 6;
        }
        if (!Objects.equals(hovered, hoverId)) {
            hoverId = hovered;
            hoverSince = System.currentTimeMillis();
        }
        return y;
    }

    private boolean row(GuiGraphics g, Font font, int mx, int my, int x, int y, int w, OptionDef<?> def,
                        ConfigManager cfg, boolean popup) {
        String id = def.id();
        boolean enabled = cfg.isOptionEnabled(id);
        boolean inRow = my >= clipTop && my < clipBottom && Theme.inside(mx, my, x, y, w, WIDGET_H);
        boolean rowHover = enabled && !popup && inRow;
        if (rowHover) {
            g.fill(x, y, x + w, y + WIDGET_H, 0x20FFFFFF);
        }
        if (id.equals(highlightId) && System.currentTimeMillis() < highlightUntil) {
            long left = highlightUntil - System.currentTimeMillis();
            int a = (int) (0x60 * Math.min(1.0, left / 600.0));
            g.fill(x, y, x + w, y + WIDGET_H, (a << 24) | 0xFFFF55);
        }
        if (invalid.getOrDefault(id, 0L) > System.currentTimeMillis()) {
            outline(g, x, y, w, WIDGET_H, 0xFFFF5555);
        }
        Object value = cfg.getRaw(id);
        if (value == null) {
            value = def.defaultValue();
        }

        int cx = x + w - RESET_W - 4 - CONTROL_W;
        boolean ctrlHover = enabled && !popup && inRow && mx >= cx && mx < cx + CONTROL_W;
        int textColor = enabled ? Theme.WHITE : Theme.GRAY_TEXT;
        long now = System.currentTimeMillis();

        switch (def.type()) {
            case TOGGLE -> {
                boolean on = Boolean.TRUE.equals(value);
                Theme.button(g, font, cx, y, CONTROL_W, WIDGET_H, I18n.get(on ? "visuals.gui.on" : "visuals.gui.off"),
                        ctrlHover, enabled, enabled ? (on ? Theme.OK_GREEN : Theme.OFF_RED) : Theme.GRAY_TEXT);
                add(cx, y, CONTROL_W, WIDGET_H, enabled, (px, py, b, s) -> {
                    if (b == 0) {
                        cfg.set(id, !on);
                    }
                });
            }
            case SLIDER, INT_SLIDER -> {
                double v = ((Number) value).doubleValue();
                double frac = Math.max(0, Math.min(1, (v - def.min()) / (def.max() - def.min())));
                Theme.track(g, cx, y, CONTROL_W, WIDGET_H, ctrlHover);
                int hx = cx + 1 + (int) Math.round(frac * (CONTROL_W - 10));
                Theme.button(g, font, hx, y, 8, WIDGET_H, "", ctrlHover, enabled, textColor);
                String shown = numberText(def, v) + (def.unit().isEmpty() ? "" : " " + def.unit());
                g.drawString(font, shown, cx + (CONTROL_W - font.width(shown)) / 2, y + 6, textColor, true);
                add(cx, y, CONTROL_W, WIDGET_H, enabled, (px, py, b, s) -> {
                    if (b == 0) {
                        Drag d = (m, sh) -> slide(def, cx, CONTROL_W, m, sh);
                        activeDrag = d;
                        d.on(px, s);
                    }
                });
            }
            case DROPDOWN -> {
                String shown = I18n.get(def.choiceKey(String.valueOf(value)));
                Theme.button(g, font, cx, y, CONTROL_W, WIDGET_H, shown, ctrlHover, enabled, textColor);
                add(cx, y, CONTROL_W, WIDGET_H, enabled, (px, py, b, s) -> {
                    if (b == 0) {
                        cycle(def, s ? -1 : 1);
                    }
                });
            }
            case COLOR -> {
                int argb = (Integer) value;
                Theme.button(g, font, cx, y, CONTROL_W, WIDGET_H, "", ctrlHover, enabled, textColor);
                g.fill(cx + 4, y + 4, cx + 22, y + 16, 0xFF000000);
                g.fill(cx + 5, y + 5, cx + 21, y + 15, 0xFF000000 | argb);
                g.drawString(font, String.format("#%06X", argb & 0xFFFFFF), cx + 28, y + 6, textColor, true);
                add(cx, y, CONTROL_W, WIDGET_H, enabled, (px, py, b, s) -> {
                    if (b == 0) {
                        closeAll();
                        colorId = id;
                        colorX = cx;
                        colorY = y + WIDGET_H;
                        rgbToHsv(argb, hsv);
                    }
                });
            }
            default -> { // TEXT
                boolean editingThis = id.equals(editId);
                Theme.field(g, cx, y, CONTROL_W, WIDGET_H, editingThis);
                String shown = editingThis ? editText(now) : String.valueOf(value);
                g.drawString(font, font.plainSubstrByWidth(shown, CONTROL_W - 10), cx + 5, y + 6,
                        enabled ? Theme.WHITE : Theme.GRAY_TEXT, true);
                add(cx, y, CONTROL_W, WIDGET_H, enabled, (px, py, b, s) -> {
                    if (b == 0) {
                        startEdit(def, false, String.valueOf(cfg.getRaw(id)));
                    }
                });
            }
        }

        // Name
        String name = font.plainSubstrByWidth(I18n.get(def.nameKey()), Math.max(20, cx - (x + 4) - 8));
        g.drawString(font, name, x + 4, y + 6, textColor, true);

        // Zurücksetzen
        if (cfg.isModified(id) && enabled) {
            int rx = x + w - RESET_W;
            boolean over = !popup && Theme.inside(mx, my, rx, y, RESET_W, WIDGET_H) && my >= clipTop && my < clipBottom;
            Theme.button(g, font, rx, y, RESET_W, WIDGET_H, "\u21BA", over, true, Theme.WHITE);
            add(rx, y, RESET_W, WIDGET_H, true, (px, py, b, s) -> {
                if (b == 0) {
                    cfg.reset(id);
                }
            });
        }
        if (enabled && y + ROW_H > clipTop && y < clipBottom) {
            ctxHits.add(new Hit(x, y, w, WIDGET_H, (px, py, b, s) -> {
                closeAll();
                ctxId = id;
                ctxX = (int) px;
                ctxY = (int) py;
            }));
        }
        return rowHover;
    }

    private void add(int x, int y, int w, int h, boolean enabled, Click click) {
        if (enabled && y + h > clipTop && y < clipBottom) {
            hits.add(new Hit(x, y, w, h, click));
        }
    }

    private void cycle(OptionDef<?> def, int dir) {
        List<String> choices = def.choices();
        if (choices.isEmpty()) {
            return;
        }
        int i = choices.indexOf(String.valueOf(VisualsMod.config().getRaw(def.id())));
        int next = Math.floorMod(i + dir, choices.size());
        VisualsMod.config().set(def.id(), choices.get(next));
    }

    // ------------------------------------------------------------------ Overlays

    public void renderOverlay(GuiGraphics g, Font font, int mx, int my, int sw, int sh) {
        popupHits.clear();
        ConfigManager cfg = VisualsMod.config();
        OptionRegistry reg = VisualsMod.registry();
        if (colorId != null) {
            OptionDef<?> def = reg.get(colorId);
            if (def != null) {
                renderColor(g, font, mx, my, sw, sh, def, cfg);
            }
        }
        if (ctxId != null) {
            OptionDef<?> def = reg.get(ctxId);
            if (def != null) {
                renderContext(g, font, mx, my, sw, sh, def, cfg);
            }
        }
        renderTooltip(g, font, mx, my, sw, sh, reg);
    }

    private void renderColor(GuiGraphics g, Font font, int mx, int my, int sw, int sh, OptionDef<?> def, ConfigManager cfg) {
        int w = 150;
        int h = 112;
        int x = Math.max(4, Math.min(colorX, sw - w - 4));
        int y = Math.max(4, Math.min(colorY, sh - h - 4));
        String id = def.id();
        int argb = cfg.getRaw(id) instanceof Integer i ? i : 0xFFFFFFFF;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFFFFFFF);
        g.fill(x, y, x + w, y + h, 0xFF101010);
        g.fill(x + 8, y + 8, x + w - 8, y + 24, 0xFF000000);
        g.fill(x + 9, y + 9, x + w - 9, y + 23, 0xFF000000 | argb);

        int bx = x + 22;
        int bw = w - 32;
        String[] labels = {"H", "S", "V"};
        for (int k = 0; k < 3; k++) {
            int by = y + 34 + k * 17;
            g.drawString(font, labels[k], x + 9, by + 1, Theme.GRAY_TEXT, true);
            for (int i = 0; i < bw; i += 2) {
                float t = i / (float) bw;
                int col = switch (k) {
                    case 0 -> hsvToRgb(t, 1f, 1f);
                    case 1 -> hsvToRgb(hsv[0], t, hsv[2]);
                    default -> hsvToRgb(hsv[0], hsv[1], t);
                };
                g.fill(bx + i, by, bx + Math.min(i + 2, bw), by + 10, 0xFF000000 | col);
            }
            int mxk = bx + Math.round(hsv[k] * (bw - 2));
            g.fill(mxk - 1, by - 2, mxk + 3, by + 12, 0xFFFFFFFF);
            final int kk = k;
            popupHits.add(new Hit(bx, by - 2, bw, 14, (cx, cy, b, s) -> {
                if (b == 0) {
                    Drag d = (m, sf) -> setHsv(id, kk, (float) ((m - bx) / (bw - 2.0)));
                    activeDrag = d;
                    d.on(cx, s);
                }
            }));
        }
        int hy = y + 88;
        boolean editingHex = editHex && id.equals(editId);
        Theme.field(g, x + 8, hy, w - 16, 16, editingHex);
        String shown = editingHex ? editText(System.currentTimeMillis()) : String.format("#%06X", argb & 0xFFFFFF);
        g.drawString(font, shown, x + 13, hy + 4, Theme.WHITE, true);
        popupHits.add(new Hit(x + 8, hy, w - 16, 16, (cx, cy, b, s) -> {
            if (b == 0) {
                startEdit(def, true, String.format("#%06X", argb & 0xFFFFFF));
            }
        }));
        popupHits.add(0, new Hit(x, y, w, h, (cx, cy, b, s) -> {
        }));
    }

    private void setHsv(String id, int component, float value) {
        hsv[component] = Math.max(0f, Math.min(1f, value));
        ConfigManager cfg = VisualsMod.config();
        int alpha = cfg.getRaw(id) instanceof Integer i ? (i & 0xFF000000) : 0xFF000000;
        cfg.set(id, alpha | hsvToRgb(hsv[0], hsv[1], hsv[2]));
    }

    private void renderContext(GuiGraphics g, Font font, int mx, int my, int sw, int sh, OptionDef<?> def, ConfigManager cfg) {
        boolean numeric = def.type() == OptionType.SLIDER || def.type() == OptionType.INT_SLIDER;
        List<String> labels = new ArrayList<>();
        labels.add(I18n.get("visuals.gui.ctx.reset"));
        labels.add(I18n.get("visuals.gui.ctx.copy"));
        labels.add(I18n.get("visuals.gui.ctx.paste"));
        if (numeric) {
            labels.add(I18n.get("visuals.gui.ctx.enter"));
        }
        int w = 140;
        int h = labels.size() * POPUP_ROW_H + 4;
        int x = Math.min(ctxX, sw - w - 4);
        int y = Math.min(ctxY, sh - h - 4);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFFFFFFF);
        g.fill(x, y, x + w, y + h, 0xFF101010);
        String id = def.id();
        for (int i = 0; i < labels.size(); i++) {
            int ry = y + 2 + i * POPUP_ROW_H;
            if (Theme.inside(mx, my, x, ry, w, POPUP_ROW_H)) {
                g.fill(x + 1, ry, x + w - 1, ry + POPUP_ROW_H, 0xFF4A4F6E);
            }
            g.drawString(font, labels.get(i), x + 8, ry + 4, Theme.WHITE, true);
            final int idx = i;
            popupHits.add(new Hit(x, ry, w, POPUP_ROW_H, (cx, cy, b, s) -> {
                if (b != 0) {
                    return;
                }
                closeAll();
                switch (idx) {
                    case 0 -> cfg.reset(id);
                    case 1 -> Minecraft.getInstance().keyboardHandler.setClipboard(toText(def, cfg.getRaw(id)));
                    case 2 -> applyText(def, Minecraft.getInstance().keyboardHandler.getClipboard().trim());
                    default -> valueRequest.accept(def);
                }
            }));
        }
    }

    private void renderTooltip(GuiGraphics g, Font font, int mx, int my, int sw, int sh, OptionRegistry reg) {
        if (hoverId == null || popupOpen() || editing() || System.currentTimeMillis() - hoverSince < 450) {
            return;
        }
        OptionDef<?> def = reg.get(hoverId);
        if (def == null) {
            return;
        }
        String tip = I18n.get(def.tooltipKey());
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        if (!tip.equals(def.tooltipKey())) {
            lines.addAll(font.split(Component.literal(tip), 200));
        }
        lines.addAll(font.split(Component.literal(I18n.get("visuals.gui.default") + ": " + toText(def, def.defaultValue())
                + (def.unit().isEmpty() ? "" : " " + def.unit())), 200));
        int w = 0;
        for (var l : lines) {
            w = Math.max(w, font.width(l));
        }
        int h = lines.size() * 10;
        int x = Math.max(8, Math.min(mx + 12, sw - w - 8));
        int y = Math.min(my + 16, sh - h - 8);
        Theme.tooltipBox(g, x, y, w, h);
        int ty = y;
        for (var l : lines) {
            g.drawString(font, l, x, ty, Theme.WHITE, true);
            ty += 10;
        }
    }

    // ------------------------------------------------------------------ Eingaben

    public boolean mouseClicked(double mx, double my, int button, boolean shift) {
        if (editing()) {
            commitEdit();
        }
        if (popupOpen()) {
            for (int i = popupHits.size() - 1; i >= 0; i--) {
                Hit hit = popupHits.get(i);
                if (Theme.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.click().on(mx, my, button, shift);
                    return true;
                }
            }
            closeAll();
            return true;
        }
        if (my < clipTop || my >= clipBottom) {
            return false;
        }
        if (button == 1) {
            for (Hit hit : ctxHits) {
                if (Theme.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.click().on(mx, my, button, shift);
                    return true;
                }
            }
            return false;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (Theme.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                hit.click().on(mx, my, button, shift);
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx, boolean shift) {
        if (activeDrag == null) {
            return false;
        }
        activeDrag.on(mx, shift);
        return true;
    }

    public void mouseReleased() {
        activeDrag = null;
    }

    public boolean keyPressed(int key, boolean ctrl) {
        if (editing()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitEdit();
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                editId = null;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (editBuf.length() > 0) {
                    editBuf.setLength(editBuf.length() - 1);
                }
            } else if (ctrl && key == GLFW.GLFW_KEY_V) {
                for (char c : Minecraft.getInstance().keyboardHandler.getClipboard().toCharArray()) {
                    typeChar(c);
                }
            }
            return true;
        }
        if (popupOpen() && key == GLFW.GLFW_KEY_ESCAPE) {
            closeAll();
            return true;
        }
        return false;
    }

    public boolean charTyped(int codepoint) {
        if (!editing()) {
            return false;
        }
        for (char c : Character.toChars(codepoint)) {
            typeChar(c);
        }
        return true;
    }

    private void typeChar(char c) {
        if (editDef == null) {
            return;
        }
        boolean ok;
        int max;
        if (editHex || editDef.type() == OptionType.COLOR) {
            ok = c == '#' || Character.digit(c, 16) >= 0;
            max = 9;
        } else if (editDef.type() == OptionType.TEXT) {
            ok = c >= 32 && c != 127;
            max = (int) editDef.max();
        } else {
            ok = (c >= '0' && c <= '9') || c == '.' || c == ',' || c == '-';
            max = 12;
        }
        if (ok && editBuf.length() < max) {
            editBuf.append(c);
        }
    }

    // ------------------------------------------------------------------ Bearbeiten und Werte

    private void startEdit(OptionDef<?> def, boolean hex, String initial) {
        if (!hex) {
            closeAll();
        } else {
            editId = null;
        }
        editId = def.id();
        editDef = def;
        editHex = hex;
        editBuf.setLength(0);
        editBuf.append(initial);
    }

    private String editText(long now) {
        return editBuf + ((now / 500) % 2 == 0 ? "_" : "");
    }

    private void commitEdit() {
        if (editId == null || editDef == null) {
            return;
        }
        OptionDef<?> def = editDef;
        String text = editBuf.toString().trim();
        editId = null;
        if (applyText(def, text) && def.type() == OptionType.COLOR && def.id().equals(colorId)
                && VisualsMod.config().getRaw(def.id()) instanceof Integer i) {
            rgbToHsv(i, hsv);
        }
    }

    /** Wandelt Text in einen Wert um und setzt ihn. Bei Fehlern oder Korrekturen wird die Zeile kurz rot markiert. */
    public boolean applyText(OptionDef<?> def, String text) {
        ConfigManager cfg = VisualsMod.config();
        String id = def.id();
        boolean ok = false;
        try {
            switch (def.type()) {
                case TOGGLE -> {
                    String t = text.toLowerCase(Locale.ROOT);
                    if (t.equals("true") || t.equals("1") || t.equals("an")) {
                        ok = cfg.set(id, true);
                    } else if (t.equals("false") || t.equals("0") || t.equals("aus")) {
                        ok = cfg.set(id, false);
                    }
                }
                case SLIDER -> ok = cfg.set(id, Double.parseDouble(text.replace(',', '.')));
                case INT_SLIDER -> ok = cfg.set(id, (int) Math.round(Double.parseDouble(text.replace(',', '.'))));
                case DROPDOWN -> {
                    if (def.choices().contains(text)) {
                        ok = cfg.set(id, text);
                    }
                }
                case COLOR -> {
                    String hex = text.startsWith("#") ? text.substring(1) : text;
                    int alpha = cfg.getRaw(id) instanceof Integer i ? (i & 0xFF000000) : 0xFF000000;
                    if (hex.length() == 6) {
                        ok = cfg.set(id, alpha | Integer.parseInt(hex, 16));
                    } else if (hex.length() == 8) {
                        ok = cfg.set(id, (int) Long.parseLong(hex, 16));
                    }
                }
                default -> ok = cfg.set(id, text);
            }
        } catch (NumberFormatException e) {
            ok = false;
        }
        if (!ok) {
            invalid.put(id, System.currentTimeMillis() + 1200);
        }
        return ok;
    }

    private void slide(OptionDef<?> def, int trackX, int trackW, double mx, boolean shift) {
        double frac = Math.max(0, Math.min(1, (mx - trackX - 5) / (trackW - 10.0)));
        double v = def.min() + frac * (def.max() - def.min());
        double step = shift ? Math.max(def.step() / 10.0, 0.0001) : def.step();
        v = Math.round(v / step) * step;
        v = Math.round(v * 1e6) / 1e6;
        v = Math.max(def.min(), Math.min(def.max(), v));
        ConfigManager cfg = VisualsMod.config();
        if (def.type() == OptionType.INT_SLIDER) {
            cfg.set(def.id(), (int) Math.round(v));
        } else {
            cfg.set(def.id(), v);
        }
    }

    /** Aktueller Wert als Text, zum Vorbelegen des Eingabefensters. */
    public static String plainValue(OptionDef<?> def, Object value) {
        if (value instanceof Number n && def.type() == OptionType.INT_SLIDER) {
            return String.valueOf(n.intValue());
        }
        if (value instanceof Number n) {
            return numberText(def, n.doubleValue());
        }
        return String.valueOf(value);
    }

    private static String numberText(OptionDef<?> def, double v) {
        if (def.type() == OptionType.INT_SLIDER) {
            return String.valueOf((int) Math.round(v));
        }
        double step = def.step();
        int decimals = step >= 1 ? 0 : step >= 0.1 ? 1 : step >= 0.01 ? 2 : 3;
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    private static String toText(OptionDef<?> def, Object value) {
        if (value == null) {
            return "";
        }
        return switch (def.type()) {
            case TOGGLE -> Boolean.TRUE.equals(value) ? I18n.get("visuals.gui.on") : I18n.get("visuals.gui.off");
            case SLIDER, INT_SLIDER -> numberText(def, ((Number) value).doubleValue());
            case COLOR -> String.format("#%06X", ((Integer) value) & 0xFFFFFF);
            case DROPDOWN -> I18n.get(def.choiceKey(String.valueOf(value)));
            default -> String.valueOf(value);
        };
    }

    // ------------------------------------------------------------------ Bausteine

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    static int hsvToRgb(float h, float s, float v) {
        h = Math.max(0f, Math.min(1f, h)) * 6f;
        float c = v * s;
        float x = c * (1 - Math.abs(h % 2 - 1));
        float m = v - c;
        float r;
        float gr;
        float b;
        switch ((int) h % 6) {
            case 0 -> { r = c; gr = x; b = 0; }
            case 1 -> { r = x; gr = c; b = 0; }
            case 2 -> { r = 0; gr = c; b = x; }
            case 3 -> { r = 0; gr = x; b = c; }
            case 4 -> { r = x; gr = 0; b = c; }
            default -> { r = c; gr = 0; b = x; }
        }
        int ri = Math.round((r + m) * 255);
        int gi = Math.round((gr + m) * 255);
        int bi = Math.round((b + m) * 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    static void rgbToHsv(int rgb, float[] out) {
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) {
            h = 0;
        } else if (max == r) {
            h = ((g - b) / d) % 6;
        } else if (max == g) {
            h = (b - r) / d + 2;
        } else {
            h = (r - g) / d + 4;
        }
        h /= 6f;
        if (h < 0) {
            h += 1f;
        }
        out[0] = h;
        out[1] = max == 0 ? 0 : d / max;
        out[2] = max;
    }
}
