package dev.visuals.gui;

import dev.visuals.VisualsMod;
import dev.visuals.config.ConfigManager;
import dev.visuals.config.OptionDef;
import dev.visuals.config.OptionRegistry;
import dev.visuals.config.OptionType;
import dev.visuals.config.TabDef;
import dev.visuals.config.def.GeneralOptions;
import dev.visuals.config.def.Tabs;
import dev.visuals.gui.widgets.TabBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Einstellungs-Menü. Layout: Reiter-Leiste links, Einstellungen in der Mitte, Vorschau rechts,
 * Suchleiste oben, Aktionsleiste unten.
 *
 * <p>Gezeichnet wird im "Immediate Mode": Beim Zeichnen sammelt {@link #hits} alle klickbaren Flächen,
 * {@link #mouseClicked} arbeitet sie von oben nach unten ab. Dadurch gibt es keine Widget-Klassen,
 * die zwischen Minecraft-Versionen brechen können.
 *
 * <p>Stand (Abschnitt 2): Layout, Reiter-Leiste, Kopfbereich mit Master-Schalter und Zurücksetzen,
 * Optionsliste mit Scrollen (Schalter sind bedienbar, andere Typen folgen mit den Widgets in Abschnitt 3),
 * Schließen mit Rückfrage. Suche, Vorschau, Presets und Rückgängig folgen in den Abschnitten 3 bis 5.
 */
public final class VisualsScreen extends Screen {
    private static final int TOP_H = 28;
    private static final int BOTTOM_H = 28;
    private static final int MIN_W_FOR_PREVIEW = 520;
    private static final int ROW_H = 20;
    private static final int GROUP_H = 22;

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private final Anim masterKnob = new Anim(0);

    private TabBar tabBar;
    private String activeTab = Tabs.GENERAL;
    private int scroll;
    private int maxScroll;
    private boolean previewCollapsed;
    private boolean confirmClose;

    public VisualsScreen(Screen parent) {
        super(Component.translatable("visuals.gui.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        OptionRegistry registry = VisualsMod.registry();
        tabBar = new TabBar(new ArrayList<>(registry.tabs()));
        tabBar.setHeight(this.height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ Geometrie

    private boolean previewVisible() {
        return !previewCollapsed && this.width >= MIN_W_FOR_PREVIEW;
    }

    private int previewW() {
        return previewVisible() ? (int) (this.width * 0.35) : 0;
    }

    private int contentLeft() {
        return TabBar.COLLAPSED_W;
    }

    private int contentRight() {
        return this.width - previewW();
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        ConfigManager cfg = VisualsMod.config();
        OptionRegistry reg = VisualsMod.registry();
        TabDef tab = reg.tab(activeTab);

        this.renderBackground(g, mx, my, delta);
        g.fill(0, 0, this.width, this.height, Theme.OVERLAY);
        tabBar.setHeight(this.height);

        int left = contentLeft();
        int right = contentRight();
        int top = TOP_H;
        int bottom = this.height - BOTTOM_H;

        // Flächen
        g.fill(left, 0, this.width, this.height, Theme.PANEL);
        g.fill(left, top - 1, this.width, top, Theme.BORDER);
        g.fill(left, bottom, this.width, bottom + 1, Theme.BORDER);

        renderTopBar(g, mx, my, left, cfg);
        renderContent(g, mx, my, left, right, top, bottom, tab, cfg, reg);
        renderPreview(g, right, top, bottom);
        renderBottomBar(g, mx, my, left, bottom, cfg);

        // Reiter-Leiste zuletzt, damit sie beim Aufklappen über dem Inhalt liegt
        tabBar.render(g, this.font, mx, my, activeTab);

        if (confirmClose) {
            renderConfirm(g, mx, my);
        }
    }

    private void renderTopBar(GuiGraphics g, int mx, int my, int left, ConfigManager cfg) {
        // Suchleiste (Platzhalter bis Abschnitt 5)
        int sx = left + 10;
        int sw = Math.max(80, Math.min(260, this.width - left - 200));
        g.fill(sx, 5, sx + sw, TOP_H - 5, Theme.ROW);
        g.drawString(this.font, I18n.get("visuals.gui.search"), sx + 6, 10, Theme.TEXT_DISABLED);

        // Notausschalter
        boolean on = cfg.get(GeneralOptions.MOD_ENABLED);
        String label = I18n.get(on ? "visuals.gui.killswitch.on" : "visuals.gui.killswitch.off");
        int bw = this.font.width(label) + 16;
        int bx = this.width - bw - 10;
        button(g, mx, my, bx, 4, bw, TOP_H - 8, label, on ? Theme.accent() : Theme.GRAY,
                () -> cfg.set(GeneralOptions.MOD_ENABLED, !on));
    }

    private void renderContent(GuiGraphics g, int mx, int my, int left, int right, int top, int bottom,
                               TabDef tab, ConfigManager cfg, OptionRegistry reg) {
        int pad = 12;
        int x = left + pad;
        int w = right - left - pad * 2;

        // Kopf: Titel, Beschreibung, Master-Schalter, Zurücksetzen
        g.drawString(this.font, I18n.get(tab.nameKey()), x, top + 10, Theme.TEXT);
        List<net.minecraft.util.FormattedCharSequence> desc =
                this.font.split(Component.translatable(tab.descKey()), Math.max(60, w - 150));
        int dy = top + 24;
        for (var line : desc) {
            g.drawString(this.font, line, x, dy, Theme.TEXT_DIM);
            dy += 10;
        }
        int headerH = (dy - top) + 8;

        int rx = right - pad;
        String reset = I18n.get("visuals.gui.resetTab");
        int rw = this.font.width(reset) + 14;
        rx -= rw;
        button(g, mx, my, rx, top + 8, rw, 16, reset, Theme.GRAY, () -> cfg.resetTab(activeTab));
        if (tab.hasMaster()) {
            OptionDef<Boolean> master = reg.masterOf(tab.id()).orElseThrow();
            boolean on = cfg.get(master);
            int sw = 30;
            rx -= sw + 10;
            toggle(g, rx, top + 9, on, masterKnob, true);
            hits.add(new Hit(rx, top + 9, sw, 14, () -> cfg.set(master, !on)));
        }

        // Optionsliste
        int listTop = top + headerH;
        g.fill(x, listTop - 1, x + w, listTop, Theme.BORDER);
        g.enableScissor(left, listTop, right, bottom);
        int y = listTop - scroll + 6;
        int startY = y;
        for (String group : reg.groupsOf(tab.id())) {
            g.drawString(this.font, I18n.get("visuals.group." + tab.id() + "." + group), x, y + 6, Theme.accent());
            y += GROUP_H;
            for (OptionDef<?> def : reg.optionsOf(tab.id())) {
                if (!def.groupId().equals(group)) {
                    continue;
                }
                renderOption(g, mx, my, x, y, w, def, cfg, listTop, bottom);
                y += ROW_H + 2;
            }
            y += 6;
        }
        g.disableScissor();
        maxScroll = Math.max(0, (y - startY) + 12 - (bottom - listTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
    }

    private <T> void renderOption(GuiGraphics g, int mx, int my, int x, int y, int w, OptionDef<T> def,
                                  ConfigManager cfg, int clipTop, int clipBottom) {
        boolean enabled = cfg.isOptionEnabled(def.id());
        boolean hovered = enabled && Theme.inside(mx, my, x, y, w, ROW_H) && my >= clipTop && my < clipBottom;
        g.fill(x, y, x + w, y + ROW_H, hovered ? Theme.ROW_HOVER : Theme.ROW);
        if (cfg.isModified(def.id())) {
            g.fill(x, y, x + 2, y + ROW_H, Theme.accent());
        }
        int textColor = enabled ? Theme.TEXT : Theme.TEXT_DISABLED;
        g.drawString(this.font, I18n.get(def.nameKey()), x + 8, y + 6, textColor);

        T value = cfg.get(def);
        if (def.type() == OptionType.TOGGLE) {
            boolean on = Boolean.TRUE.equals(value);
            toggle(g, x + w - 38, y + 3, on, null, enabled);
            if (enabled && clipTop <= y && y + ROW_H <= clipBottom) {
                hits.add(new Hit(x, y, w, ROW_H, () -> cfg.set(def.id(), !on)));
            }
        } else {
            String shown = formatValue(def, value);
            g.drawString(this.font, shown, x + w - 8 - this.font.width(shown), y + 6,
                    enabled ? Theme.TEXT_DIM : Theme.TEXT_DISABLED);
        }
    }

    private static String formatValue(OptionDef<?> def, Object value) {
        if (value instanceof Double d) {
            return String.format("%.2f%s", d, def.unit().isEmpty() ? "" : " " + def.unit());
        }
        if (value instanceof Integer i && def.type() == OptionType.COLOR) {
            return String.format("#%06X", i & 0xFFFFFF);
        }
        if (value instanceof Integer i) {
            return i + (def.unit().isEmpty() ? "" : " " + def.unit());
        }
        if (def.type() == OptionType.DROPDOWN && value instanceof String s) {
            return I18n.get(def.choiceKey(s));
        }
        return String.valueOf(value);
    }

    private void renderPreview(GuiGraphics g, int right, int top, int bottom) {
        if (!previewVisible()) {
            return;
        }
        g.fill(right, top, this.width, bottom, Theme.PANEL_LIGHT);
        g.fill(right, top, right + 1, bottom, Theme.BORDER);
        String text = I18n.get("visuals.gui.previewSoon");
        g.drawString(this.font, text, right + (this.width - right) / 2 - this.font.width(text) / 2,
                (top + bottom) / 2, Theme.TEXT_DISABLED);
    }

    private void renderBottomBar(GuiGraphics g, int mx, int my, int left, int bottom, ConfigManager cfg) {
        int y = bottom + 4;
        int h = BOTTOM_H - 8;
        String resetAll = I18n.get("visuals.gui.resetAll");
        int rw = this.font.width(resetAll) + 16;
        button(g, mx, my, left + 10, y, rw, h, resetAll, Theme.GRAY, cfg::resetAll);

        String close = I18n.get("visuals.gui.close");
        String apply = I18n.get("visuals.gui.apply");
        int cw = this.font.width(close) + 20;
        int aw = this.font.width(apply) + 20;
        int cx = this.width - cw - 10;
        int ax = cx - aw - 8;
        button(g, mx, my, cx, y, cw, h, close, Theme.GRAY, this::requestClose);
        button(g, mx, my, ax, y, aw, h, apply, Theme.accent(), cfg::commit);

        if (this.width >= MIN_W_FOR_PREVIEW) {
            String label = I18n.get(previewCollapsed ? "visuals.gui.previewShow" : "visuals.gui.previewHide");
            int pw = this.font.width(label) + 16;
            button(g, mx, my, ax - pw - 8, y, pw, h, label, Theme.GRAY, () -> previewCollapsed = !previewCollapsed);
        }
    }

    private void renderConfirm(GuiGraphics g, int mx, int my) {
        hits.clear(); // modal: nur noch die Rückfrage ist klickbar
        g.fill(0, 0, this.width, this.height, 0xB0000000);
        int w = 260;
        int h = 80;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        g.fill(x, y, x + w, y + h, Theme.PANEL_LIGHT);
        g.fill(x, y, x + w, y + 1, Theme.accent());
        String title = I18n.get("visuals.gui.confirm.title");
        g.drawString(this.font, title, x + w / 2 - this.font.width(title) / 2, y + 14, Theme.TEXT);

        ConfigManager cfg = VisualsMod.config();
        String save = I18n.get("visuals.gui.confirm.save");
        String discard = I18n.get("visuals.gui.confirm.discard");
        String cancel = I18n.get("visuals.gui.confirm.cancel");
        int bw = 76;
        int by = y + h - 28;
        button(g, mx, my, x + 8, by, bw, 18, save, Theme.accent(), () -> {
            cfg.commit();
            closeNow();
        });
        button(g, mx, my, x + 8 + bw + 6, by, bw, 18, discard, Theme.GRAY, () -> {
            cfg.revert();
            closeNow();
        });
        button(g, mx, my, x + 8 + (bw + 6) * 2, by, bw - 4, 18, cancel, Theme.GRAY, () -> confirmClose = false);
    }

    // ------------------------------------------------------------------ Bausteine

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, int color,
                        Runnable action) {
        boolean hovered = Theme.inside(mx, my, x, y, w, h);
        int bg = Theme.mix(Theme.ROW, color, hovered ? 0.55 : 0.25);
        g.fill(x, y, x + w, y + h, bg);
        g.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2, Theme.TEXT);
        hits.add(new Hit(x, y, w, h, action));
    }

    /** Gleitender Schalter (30 x 14). {@code knob} darf null sein, dann springt er. */
    private void toggle(GuiGraphics g, int x, int y, boolean on, Anim knob, boolean enabled) {
        double t = knob != null ? knob.step(on ? 1 : 0) : (on ? 1 : 0);
        int track = enabled ? Theme.mix(Theme.GRAY, Theme.accent(), t) : Theme.mix(0xFF3A3A40, 0xFF55453A, t);
        g.fill(x, y, x + 30, y + 14, track);
        int kx = x + 2 + (int) Math.round(t * 16);
        g.fill(kx, y + 2, kx + 10, y + 12, enabled ? 0xFFF4F4F6 : 0xFF8A8A90);
    }

    // ------------------------------------------------------------------ Eingaben

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = event.x();
        double my = event.y();
        if (!confirmClose) {
            if (tabBar.contains(mx, my)) {
                String id = tabBar.clicked(mx, my);
                if (id != null) {
                    selectTab(id);
                }
                return true;
            }
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (Theme.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                hit.action().run();
                return true;
            }
        }
        return confirmClose || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (confirmClose) {
            return true;
        }
        if (tabBar.contains(mx, my)) {
            tabBar.scrolled(scrollY);
        } else {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (scrollY * 18)));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (confirmClose) {
                confirmClose = false;
            } else {
                requestClose();
            }
            return true;
        }
        if (confirmClose) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            selectTab(tabBar.neighbour(activeTab, key == GLFW.GLFW_KEY_DOWN ? 1 : -1));
            return true;
        }
        return super.keyPressed(event);
    }

    private void selectTab(String id) {
        if (!id.equals(activeTab)) {
            activeTab = id;
            scroll = 0;
        }
    }

    // ------------------------------------------------------------------ Schließen

    private void requestClose() {
        if (VisualsMod.config().isDirty()) {
            confirmClose = true;
        } else {
            closeNow();
        }
    }

    private void closeNow() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        requestClose();
    }
}
