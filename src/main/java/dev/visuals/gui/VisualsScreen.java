package dev.visuals.gui;

import com.google.gson.JsonObject;
import dev.visuals.VisualsMod;
import dev.visuals.config.ConfigManager;
import dev.visuals.config.OptionDef;
import dev.visuals.config.OptionRegistry;
import dev.visuals.config.Preset;
import dev.visuals.config.PresetIO;
import dev.visuals.config.PresetManager;
import dev.visuals.config.TabDef;
import dev.visuals.config.UndoStack;
import dev.visuals.gui.preview.PreviewPanel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Das Einstellungs-Menü im Stil der Minecraft-Optionen.
 *
 * <p>Drei Ansichten: <b>Übersicht</b> (Suchfeld und Kategorien als Knöpfe), <b>Kategorie</b> (die Optionen einer
 * Kategorie als schmale Liste) und <b>Vorschau</b> (Beispielszene mit Reglern). "Fertig" speichert und schließt,
 * wie in den Vanilla-Optionen. Strg+Z und Strg+Y machen Änderungen rückgängig und wiederholen sie.
 *
 * <p>Gezeichnet wird im "Immediate Mode": Beim Zeichnen sammelt {@link #hits} alle Knöpfe.
 */
public final class VisualsScreen extends Screen {
    private enum Mode { MAIN, CATEGORY, PREVIEW }

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    private record Result(String tabId, String optionId, String label) {
    }

    private static final int COLUMN_W = 310;

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private final OptionPanel panel = new OptionPanel();
    private final PreviewPanel preview = new PreviewPanel();
    private final StringBuilder query = new StringBuilder();

    private Mode mode = Mode.MAIN;
    private String activeTab = "general";
    private int scroll;
    private int maxScroll;
    private int listTop;
    private int listBottom;
    private boolean previewFullscreen;
    private String pendingJump;
    private ModalDialog modal;

    public VisualsScreen(Screen parent) {
        super(Component.translatable("visuals.gui.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panel.setValueRequest(this::askValue);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ Geometrie

    private int colW() {
        return Math.min(COLUMN_W, this.width - 12);
    }

    private int colX() {
        return (this.width - colW()) / 2;
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        hits.clear();
        VisualsMod.presets().tick();
        // Der unscharfe Hintergrund wird von Minecraft selbst gezeichnet; ein zweiter Aufruf stürzt ab ("blur once per frame").
        if (previewFullscreen) {
            preview.renderSceneOnly(g, 0, 0, this.width, this.height);
            String hint = I18n.get("visuals.gui.preview.exitHint");
            g.fill(0, this.height - 16, this.width, this.height, 0x90000000);
            g.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2, this.height - 12, Theme.WHITE, true);
            return;
        }
        g.fill(0, 0, this.width, this.height, 0x70000000);

        switch (mode) {
            case MAIN -> renderMain(g, mx, my);
            case CATEGORY -> renderCategory(g, mx, my);
            default -> renderPreviewScreen(g, mx, my);
        }

        if (modal == null && mode == Mode.CATEGORY) {
            panel.renderOverlay(g, this.font, mx, my, this.width, this.height);
        }
        if (modal != null) {
            modal.render(g, this.font, mx, my, this.width, this.height);
        }
    }

    private void title(GuiGraphics g, String text, int y) {
        g.drawString(this.font, text, (this.width - this.font.width(text)) / 2, y, Theme.WHITE, true);
    }

    /** Dunkler Listenbereich mit Trennlinien oben und unten, wie in den Vanilla-Listen. */
    private void listBackground(GuiGraphics g, int top, int bottom) {
        g.fill(0, top, this.width, bottom, 0x66000000);
        g.fill(0, top - 2, this.width, top - 1, 0xFF000000);
        g.fill(0, top - 1, this.width, top, 0xFF373737);
        g.fill(0, bottom, this.width, bottom + 1, 0xFF000000);
        g.fill(0, bottom + 1, this.width, bottom + 2, 0xFF373737);
    }

    private void scrollbar(GuiGraphics g, int x, int top, int bottom, int contentH) {
        if (maxScroll <= 0) {
            return;
        }
        int trackH = bottom - top;
        int handleH = Math.max(16, (int) ((long) trackH * trackH / Math.max(trackH, contentH)));
        int handleY = top + (int) ((long) (trackH - handleH) * scroll / maxScroll);
        g.fill(x, top, x + 6, bottom, 0xFF000000);
        g.fill(x, handleY, x + 6, handleY + handleH, 0xFF808080);
        g.fill(x, handleY, x + 5, handleY + handleH - 1, 0xFFC0C0C0);
    }

    // ---- Übersicht

    private void renderMain(GuiGraphics g, int mx, int my) {
        ConfigManager cfg = VisualsMod.config();
        OptionRegistry reg = VisualsMod.registry();
        int w = colW();
        int x = colX();

        title(g, I18n.get("visuals.gui.title"), 10);
        Theme.field(g, x, 24, w, 20, true);
        String shown = query.length() == 0 ? I18n.get("visuals.gui.search")
                : query + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        g.drawString(this.font, this.font.plainSubstrByWidth(shown, w - 12), x + 5, 30,
                query.length() == 0 ? Theme.GRAY_TEXT : Theme.WHITE, true);

        listTop = 54;
        listBottom = this.height - 62;
        listBackground(g, listTop, listBottom);

        g.enableScissor(0, listTop, this.width, listBottom);
        int y = listTop + 6 - scroll;
        int contentH;
        if (query.length() == 0) {
            int bw = (w - 6) / 2;
            int i = 0;
            for (TabDef tab : reg.tabs()) {
                int bx = x + (i % 2) * (bw + 6);
                int by = y + (i / 2) * 24;
                boolean hov = my >= listTop && my < listBottom && Theme.inside(mx, my, bx, by, bw, 20);
                Theme.button(g, this.font, bx, by, bw, 20, I18n.get(tab.nameKey()), hov, true, Theme.WHITE);
                boolean on = cfg.isEffectActive(tab.id());
                g.fill(bx + bw - 12, by + 7, bx + bw - 6, by + 13, 0xFF000000);
                g.fill(bx + bw - 11, by + 8, bx + bw - 7, by + 12, on ? Theme.OK_GREEN : 0xFF707070);
                if (by + 20 > listTop && by < listBottom) {
                    final String id = tab.id();
                    hits.add(new Hit(bx, by, bw, 20, () -> openCategory(id, null)));
                }
                i++;
            }
            contentH = ((i + 1) / 2) * 24 + 12;
        } else {
            List<Result> results = searchResults();
            if (results.isEmpty()) {
                String none = I18n.get("visuals.gui.search.none");
                g.drawString(this.font, none, (this.width - this.font.width(none)) / 2, y + 12, Theme.GRAY_TEXT, true);
            }
            for (int i = 0; i < results.size(); i++) {
                Result r = results.get(i);
                int by = y + i * 24;
                boolean hov = my >= listTop && my < listBottom && Theme.inside(mx, my, x, by, w, 20);
                Theme.button(g, this.font, x, by, w, 20, r.label(), hov, true, Theme.WHITE);
                if (by + 20 > listTop && by < listBottom) {
                    hits.add(new Hit(x, by, w, 20, () -> openCategory(r.tabId(), r.optionId())));
                }
            }
            contentH = results.size() * 24 + 12;
        }
        g.disableScissor();
        maxScroll = Math.max(0, contentH - (listBottom - listTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        scrollbar(g, x + w + 4, listTop, listBottom, contentH);

        // Fußleiste: zwei Reihen mit je drei Knöpfen
        int bw = (w - 8) / 3;
        int yA = this.height - 54;
        int yB = this.height - 30;
        PresetManager presets = VisualsMod.presets();
        String presetLabel = presets.activeName().isEmpty()
                ? I18n.get("visuals.gui.preset")
                : presetLabel(presets.activeName(), presets.activeIsBuiltin());
        footer(g, mx, my, x, yA, bw, I18n.get("visuals.gui.preset") + ": " + presetLabel, this::openPresetMenu);
        footer(g, mx, my, x + bw + 4, yA, bw, I18n.get("visuals.gui.preset.save"), this::openSavePreset);
        footer(g, mx, my, x + (bw + 4) * 2, yA, bw, I18n.get("visuals.gui.io"), this::openIoMenu);
        footer(g, mx, my, x, yB, bw, I18n.get("visuals.gui.previewOpen"), () -> setMode(Mode.PREVIEW));
        footer(g, mx, my, x + bw + 4, yB, bw, I18n.get("visuals.gui.resetAll"), this::askResetAll);
        footer(g, mx, my, x + (bw + 4) * 2, yB, bw, I18n.get("visuals.gui.done"), this::finish);
    }

    private void footer(GuiGraphics g, int mx, int my, int x, int y, int w, String label, Runnable action) {
        Theme.button(g, this.font, x, y, w, 20, label, Theme.inside(mx, my, x, y, w, 20), true, Theme.WHITE);
        hits.add(new Hit(x, y, w, 20, action));
    }

    private static String presetLabel(String name, boolean builtin) {
        return builtin ? I18n.get("visuals.preset." + name) : name;
    }

    // ---- Kategorie

    private void renderCategory(GuiGraphics g, int mx, int my) {
        ConfigManager cfg = VisualsMod.config();
        OptionRegistry reg = VisualsMod.registry();
        TabDef tab = reg.tab(activeTab);
        int w = colW();
        int x = colX();

        title(g, I18n.get(tab.nameKey()), 8);
        String desc = this.font.plainSubstrByWidth(I18n.get(tab.descKey()), w + 40);
        g.drawString(this.font, desc, (this.width - this.font.width(desc)) / 2, 20, Theme.GRAY_TEXT, true);

        int top = 34;
        if (tab.hasMaster()) {
            OptionDef<Boolean> master = reg.masterOf(tab.id()).orElseThrow();
            boolean on = cfg.get(master);
            String label = I18n.get(tab.nameKey()) + ": " + I18n.get(on ? "visuals.gui.on" : "visuals.gui.off");
            Theme.button(g, this.font, x, top, w, 20, label, Theme.inside(mx, my, x, top, w, 20), true,
                    on ? Theme.OK_GREEN : Theme.OFF_RED);
            hits.add(new Hit(x, top, w, 20, () -> cfg.set(master, !on)));
            top += 26;
        }

        listTop = top + 4;
        listBottom = this.height - 34;
        listBackground(g, listTop, listBottom);
        g.enableScissor(0, listTop, this.width, listBottom);
        int startY = listTop + 4 - scroll;
        int endY = panel.render(g, this.font, mx, my, x, startY, w, listTop, listBottom, tab.id());
        g.disableScissor();
        int contentH = endY - startY + 8;
        maxScroll = Math.max(0, contentH - (listBottom - listTop));
        if (pendingJump != null) {
            Integer off = panel.offsetOf(pendingJump);
            if (off != null) {
                scroll = off - 40;
                panel.highlight(pendingJump);
                pendingJump = null;
            }
        }
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        scrollbar(g, x + w + 4, listTop, listBottom, contentH);

        int y = this.height - 28;
        int bw = (w - 4) / 2;
        footer(g, mx, my, x, y, bw, I18n.get("visuals.gui.resetTab"),
                () -> VisualsMod.undo().group(() -> cfg.resetTab(activeTab)));
        footer(g, mx, my, x + bw + 4, y, bw, I18n.get("visuals.gui.done"), () -> setMode(Mode.MAIN));
    }

    // ---- Vorschau

    private void renderPreviewScreen(GuiGraphics g, int mx, int my) {
        title(g, I18n.get("visuals.gui.preview"), 8);
        int m = Math.max(8, (this.width - 520) / 2);
        preview.render(g, this.font, mx, my, m, 24, this.width - m * 2, this.height - 24 - 34);
        if (preview.consumeFullscreenRequest()) {
            previewFullscreen = true;
        }
        int w = Math.min(200, colW());
        footer(g, mx, my, (this.width - w) / 2, this.height - 28, w, I18n.get("visuals.gui.done"),
                () -> setMode(Mode.MAIN));
    }

    // ------------------------------------------------------------------ Navigation

    private void setMode(Mode m) {
        mode = m;
        scroll = 0;
        panel.closeAll();
    }

    private void openCategory(String tabId, String optionId) {
        activeTab = tabId;
        query.setLength(0);
        setMode(Mode.CATEGORY);
        pendingJump = optionId;
    }

    /** "Fertig" in der Übersicht: speichern und schließen, wie in den Vanilla-Optionen. */
    private void finish() {
        VisualsMod.presets().finishNow();
        VisualsMod.config().commit();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        VisualsMod.presets().finishNow();
        super.removed();
    }

    @Override
    public void onClose() {
        finish();
    }

    // ------------------------------------------------------------------ Suche

    private List<Result> searchResults() {
        List<Result> out = new ArrayList<>();
        String q = query.toString().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return out;
        }
        OptionRegistry reg = VisualsMod.registry();
        for (TabDef tab : reg.tabs()) {
            String tabName = I18n.get(tab.nameKey());
            if (tabName.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Result(tab.id(), null, tabName));
            }
            for (OptionDef<?> def : reg.optionsOf(tab.id())) {
                if (def.isMaster()) {
                    continue;
                }
                String name = I18n.get(def.nameKey());
                String tip = I18n.get(def.tooltipKey());
                if (name.toLowerCase(Locale.ROOT).contains(q) || (!tip.equals(def.tooltipKey())
                        && tip.toLowerCase(Locale.ROOT).contains(q))) {
                    out.add(new Result(tab.id(), def.id(), tabName + " \u203A " + name));
                }
            }
        }
        return out.size() > 30 ? out.subList(0, 30) : out;
    }

    // ------------------------------------------------------------------ Modale Fenster

    private void info(String... lines) {
        modal = ModalDialog.message(I18n.get("visuals.gui.title"), List.of(lines), I18n.get("visuals.gui.ok"), () -> {
        });
    }

    private void askValue(OptionDef<?> def) {
        ConfigManager cfg = VisualsMod.config();
        String prompt = String.format(Locale.ROOT, I18n.get("visuals.gui.enterValue"),
                OptionPanel.plainValue(def, def.min()), OptionPanel.plainValue(def, def.max()));
        modal = ModalDialog.input(I18n.get(def.nameKey()), prompt, 12,
                OptionPanel.plainValue(def, cfg.getRaw(def.id())), I18n.get("visuals.gui.ok"),
                text -> panel.applyText(def, text), I18n.get("visuals.gui.cancel"), () -> {
                });
    }

    private void askResetAll() {
        modal = ModalDialog.confirm(I18n.get("visuals.gui.resetAll"), List.of(I18n.get("visuals.gui.resetAll.ask")),
                I18n.get("visuals.gui.resetAll"), () -> VisualsMod.undo().group(VisualsMod.config()::resetAll),
                I18n.get("visuals.gui.cancel"), () -> {
                });
    }

    private void openPresetMenu() {
        PresetManager presets = VisualsMod.presets();
        List<ModalDialog.Button> entries = new ArrayList<>();
        for (Preset p : presets.all()) {
            boolean active = p.name().equals(presets.activeName()) && p.builtin() == presets.activeIsBuiltin();
            String label = (active ? "\u2713 " : "") + presetLabel(p.name(), p.builtin());
            entries.add(new ModalDialog.Button(label, Theme.GRAY, () -> presets.apply(p)));
        }
        entries.add(new ModalDialog.Button(I18n.get("visuals.gui.cancel"), Theme.GRAY, () -> {
        }));
        modal = ModalDialog.menu(I18n.get("visuals.gui.preset.choose"), entries, () -> {
        });
    }

    private void openSavePreset() {
        modal = ModalDialog.input(I18n.get("visuals.gui.preset.save"), I18n.get("visuals.gui.preset.save.prompt"), 32,
                I18n.get("visuals.gui.confirm.save"), name -> {
                    boolean ok = VisualsMod.presets().saveCurrent(name);
                    info(ok ? I18n.get("visuals.gui.preset.saved") : I18n.get("visuals.gui.preset.saveFailed"));
                }, I18n.get("visuals.gui.cancel"), () -> {
                });
    }

    private Path dataDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(VisualsMod.MOD_ID);
    }

    private void openIoMenu() {
        List<ModalDialog.Button> entries = new ArrayList<>();
        entries.add(new ModalDialog.Button(I18n.get("visuals.gui.io.copyCode"), Theme.GRAY, this::exportCode));
        entries.add(new ModalDialog.Button(I18n.get("visuals.gui.io.exportFile"), Theme.GRAY, this::exportFile));
        entries.add(new ModalDialog.Button(I18n.get("visuals.gui.io.importCode"), Theme.GRAY, () ->
                importText(Minecraft.getInstance().keyboardHandler.getClipboard())));
        entries.add(new ModalDialog.Button(I18n.get("visuals.gui.io.importFile"), Theme.GRAY, this::importFile));
        entries.add(new ModalDialog.Button(I18n.get("visuals.gui.cancel"), Theme.GRAY, () -> {
        }));
        modal = ModalDialog.menu(I18n.get("visuals.gui.io.title"), entries, () -> {
        });
    }

    private JsonObject currentExport() {
        return PresetIO.toJson("", VisualsMod.presets().currentModified(), VisualsMod.registry());
    }

    private void exportCode() {
        Minecraft.getInstance().keyboardHandler.setClipboard(PresetIO.toCode(currentExport()));
        info(I18n.get("visuals.gui.io.copied"));
    }

    private void exportFile() {
        Path dir = dataDir().resolve("exports");
        Path file = dir.resolve("visuals-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, PresetIO.toPrettyJson(currentExport()), StandardCharsets.UTF_8);
            info(String.format(I18n.get("visuals.gui.io.exported"), file.toAbsolutePath()));
        } catch (IOException e) {
            info(I18n.get("visuals.gui.io.writeFailed"));
        }
    }

    private void importFile() {
        Path file = dataDir().resolve("import.json");
        if (!Files.isRegularFile(file)) {
            info(String.format(I18n.get("visuals.gui.io.noFile"), file.toAbsolutePath()));
            return;
        }
        try {
            importText(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            info(I18n.get("visuals.gui.io.invalid"));
        }
    }

    /** Prüft den Text und zeigt eine Vorschau der Änderungen, bevor etwas angewendet wird. */
    private void importText(String text) {
        OptionRegistry reg = VisualsMod.registry();
        PresetIO.Parsed parsed;
        try {
            parsed = PresetIO.fromJson(PresetIO.parseText(text), reg);
        } catch (IllegalArgumentException e) {
            info(I18n.get("visuals.gui.io.invalid"));
            return;
        }
        List<UndoStack.Change> diff = VisualsMod.presets().diff(parsed.values());
        if (diff.isEmpty()) {
            info(I18n.get("visuals.gui.io.nothing"));
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(String.format(I18n.get("visuals.gui.io.preview"), diff.size()));
        int shown = Math.min(8, diff.size());
        for (int i = 0; i < shown; i++) {
            UndoStack.Change c = diff.get(i);
            OptionDef<?> def = reg.get(c.id());
            lines.add(I18n.get(def.nameKey()) + ": " + OptionPanel.valueText(def, c.oldValue()) + " \u2192 "
                    + OptionPanel.valueText(def, c.newValue()));
        }
        if (diff.size() > shown) {
            lines.add(String.format(I18n.get("visuals.gui.io.more"), diff.size() - shown));
        }
        if (parsed.skipped() > 0) {
            lines.add(String.format(I18n.get("visuals.gui.io.skipped"), parsed.skipped()));
        }
        if (parsed.corrected() > 0) {
            lines.add(String.format(I18n.get("visuals.gui.io.corrected"), parsed.corrected()));
        }
        Map<String, Object> values = parsed.values();
        modal = ModalDialog.confirm(I18n.get("visuals.gui.io.title"), lines, I18n.get("visuals.gui.io.apply"),
                () -> VisualsMod.presets().applyValues(values), I18n.get("visuals.gui.cancel"), () -> {
                });
    }

    private void runModal(Runnable action) {
        if (action != null) {
            modal = null;
            action.run();
        }
    }

    // ------------------------------------------------------------------ Eingaben

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int button = event.button();
        double mx = event.x();
        double my = event.y();
        boolean shift = event.hasShiftDown();
        if (modal != null) {
            if (button == 0) {
                runModal(modal.click(mx, my));
            }
            return true;
        }
        if (previewFullscreen) {
            previewFullscreen = false;
            return true;
        }
        if (mode == Mode.CATEGORY && (panel.popupOpen() || panel.editing())) {
            if (panel.mouseClicked(mx, my, button, shift)) {
                return true;
            }
        }
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (Theme.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.action().run();
                    return true;
                }
            }
            if (mode == Mode.PREVIEW && preview.mouseClicked(mx, my)) {
                return true;
            }
        }
        if (mode == Mode.CATEGORY && my >= listTop && my < listBottom) {
            if (panel.mouseClicked(mx, my, button, shift)) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (panel.mouseDragged(event.x(), event.hasShiftDown()) || preview.mouseDragged(event.x())) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        panel.mouseReleased();
        preview.mouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (modal != null) {
            return true;
        }
        if (mode != Mode.PREVIEW) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (scrollY * 22)));
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (modal != null) {
            modal.charTyped(event.codepoint());
            return true;
        }
        if (mode == Mode.MAIN) {
            if (query.length() < 40 && event.codepoint() >= 32) {
                query.append(Character.toChars(event.codepoint()));
                scroll = 0;
            }
            return true;
        }
        if (mode == Mode.CATEGORY && panel.charTyped(event.codepoint())) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean ctrl = event.hasControlDown();
        if (modal != null) {
            runModal(modal.key(key, ctrl));
            return true;
        }
        if (previewFullscreen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                previewFullscreen = false;
            }
            return true;
        }
        if (mode == Mode.CATEGORY && panel.keyPressed(key, ctrl)) {
            return true;
        }
        if (ctrl && (key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_Y)) {
            VisualsMod.presets().finishNow();
            if (key == GLFW.GLFW_KEY_Y || event.hasShiftDown()) {
                VisualsMod.undo().redo();
            } else {
                VisualsMod.undo().undo();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (mode != Mode.MAIN) {
                setMode(Mode.MAIN);
            } else if (query.length() > 0) {
                query.setLength(0);
                scroll = 0;
            } else {
                finish();
            }
            return true;
        }
        if (mode == Mode.MAIN) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && query.length() > 0) {
                query.setLength(query.length() - 1);
                scroll = 0;
                return true;
            }
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && query.length() > 0) {
                List<Result> results = searchResults();
                if (!results.isEmpty()) {
                    openCategory(results.get(0).tabId(), results.get(0).optionId());
                }
                return true;
            }
        }
        return super.keyPressed(event);
    }
}
