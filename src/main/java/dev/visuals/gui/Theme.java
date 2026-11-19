package dev.visuals.gui;

import dev.visuals.VisualsMod;
import dev.visuals.config.def.GeneralOptions;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Farben des Menüs. Die Akzentfarbe kommt aus der Config. */
public final class Theme {
    public static final int OVERLAY = 0x80000000;
    public static final int PANEL = 0xF0151518;
    public static final int PANEL_LIGHT = 0xF01E1E23;
    public static final int ROW = 0xFF202026;
    public static final int ROW_HOVER = 0xFF2A2A32;
    public static final int BORDER = 0xFF33333B;
    public static final int TEXT = 0xFFE8E8EC;
    public static final int TEXT_DIM = 0xFF9A9AA4;
    public static final int TEXT_DISABLED = 0xFF5E5E66;
    public static final int GREEN = 0xFF4CC468;
    public static final int GRAY = 0xFF6B6B73;

    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY_TEXT = 0xFFA0A0A0;
    public static final int OK_GREEN = 0xFF55FF55;
    public static final int OFF_RED = 0xFFFF5555;

    private Theme() {
    }

    /** Knopf im Stil der Minecraft-Optionen: schwarzer Rand, Grauton mit Licht oben und Schatten unten. */
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hovered,
                              boolean enabled, int textColor) {
        int border = hovered && enabled ? 0xFFFFFFFF : 0xFF000000;
        int face = !enabled ? 0xFF3A3A3A : hovered ? 0xFF7A7F9A : 0xFF6B6B6B;
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        if (enabled) {
            int light = hovered ? 0xFFB8BDD6 : 0xFFA3A3A3;
            g.fill(x + 1, y + 1, x + w - 1, y + 2, light);
            g.fill(x + 1, y + 1, x + 2, y + h - 1, light);
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF3B3B3B);
            g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0xFF3B3B3B);
        }
        if (label != null && !label.isEmpty()) {
            String t = font.plainSubstrByWidth(label, w - 8);
            g.drawString(font, t, x + (w - font.width(t)) / 2, y + (h - 8) / 2, enabled ? textColor : GRAY_TEXT, true);
        }
    }

    /** Dunkles Feld mit hellem Rand: Eingabefeld und Regler-Schiene. */
    public static void field(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        g.fill(x, y, x + w, y + h, focused ? 0xFFFFFFFF : 0xFFA0A0A0);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000);
    }

    /** Dunkles Feld, hover-fähig, für Regler. */
    public static void track(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        g.fill(x, y, x + w, y + h, hovered ? 0xFFFFFFFF : 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF202020);
    }

    /** Tooltip-Kasten wie in Vanilla (dunkel mit violettem Rand). */
    public static void tooltipBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 3, y - 4, x + w + 3, y + h + 4, 0xF0100010);
        g.fill(x - 3, y - 3, x + w + 3, y - 4 + 1, 0xFF3C0F6B);
        g.fill(x - 3, y + h + 3, x + w + 3, y + h + 4, 0xFF28007F);
        g.fill(x - 3, y - 3, x - 2, y + h + 3, 0xFF3C0F6B);
        g.fill(x + w + 2, y - 3, x + w + 3, y + h + 3, 0xFF28007F);
    }

    public static int accent() {
        if (VisualsMod.config() == null) {
            return GeneralOptions.ACCENT_COLOR.defaultValue();
        }
        return VisualsMod.config().get(GeneralOptions.ACCENT_COLOR) | 0xFF000000;
    }

    /** Mischt zwei ARGB-Farben, t = 0 ergibt a, t = 1 ergibt b. */
    public static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
