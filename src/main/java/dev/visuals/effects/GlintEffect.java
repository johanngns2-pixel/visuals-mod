package dev.visuals.effects;

import com.mojang.blaze3d.platform.NativeImage;
import dev.visuals.VisualsMod;
import dev.visuals.config.def.GlintExamples;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Glint: ersetzt die Glint-Texturen für Items und Rüstung durch eine selbst erzeugte, passend eingefärbte Version
 * (einfarbig, Verlauf oder Regenbogen; Streifen, Funkeln oder Wellen; verschiedene Richtungen).
 * Gilt global für alle Verzauberungen. Pro Waffe, Werkzeug, Rüstung oder Verzauberung getrennt geht damit nicht,
 * und das Tempo bestimmt weiter Minecraft. Solange nichts geändert wurde, bleibt das Original unangetastet.
 */
public final class GlintEffect {
    private static final Identifier ITEM = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
    private static final Identifier ENTITY = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_entity.png");
    private static final String[] IDS = {"glint.colorMode", "glint.color", "glint.color2", "glint.pattern", "glint.direction"};
    private static final int SIZE = 128;

    private static DynamicTexture dynItem;
    private static DynamicTexture dynEntity;
    private static AbstractTexture origItem;
    private static AbstractTexture origEntity;
    private static boolean applied;
    private static String lastKey = "";
    private static int tick;

    private GlintEffect() {
    }

    private static boolean modified() {
        for (String id : IDS) {
            if (VisualsMod.config().isModified(id)) {
                return true;
            }
        }
        return false;
    }

    public static void tick(Minecraft mc) {
        tick++;
        applyExample();
        boolean want = Opts.active("glint") && modified();
        TextureManager tm = mc.getTextureManager();
        if (!want) {
            if (applied) {
                restore(tm);
            }
            return;
        }
        String mode = Opts.text("glint.colorMode");
        String key = mode + Opts.color("glint.color") + Opts.color("glint.color2") + Opts.text("glint.pattern")
                + Opts.text("glint.direction");
        boolean rainbow = "rainbow".equals(mode);
        if (applied && key.equals(lastKey) && (!rainbow || tick % 3 != 0)) {
            return;
        }
        if (!applied) {
            origItem = tm.getTexture(ITEM);
            origEntity = tm.getTexture(ENTITY);
            dynItem = new DynamicTexture(() -> "visuals_glint_item", new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, false));
            dynEntity = new DynamicTexture(() -> "visuals_glint_entity", new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, false));
            tm.register(ITEM, dynItem);
            tm.register(ENTITY, dynEntity);
            applied = true;
        }
        lastKey = key;
        paint(dynItem);
        paint(dynEntity);
    }

    /** Wählt der Spieler einen Beispiel-Glint, werden dessen Werte (weich überblendet) übernommen. */
    private static void applyExample() {
        String ex = Opts.text("glint.example");
        if ("custom".equals(ex) || !Opts.active("glint")) {
            return;
        }
        GlintExamples.Example e = GlintExamples.ALL.get(ex);
        if (e != null) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("glint.colorMode", e.mode());
            v.put("glint.color", e.color());
            v.put("glint.color2", e.color2());
            v.put("glint.pattern", e.pattern());
            v.put("glint.direction", e.direction());
            VisualsMod.presets().applyValues(v);
        }
        VisualsMod.config().set("glint.example", "custom");
    }

    private static void restore(TextureManager tm) {
        applied = false;
        lastKey = "";
        if (origItem != null) {
            tm.register(ITEM, origItem);
        }
        if (origEntity != null) {
            tm.register(ENTITY, origEntity);
        }
        dynItem = null;
        dynEntity = null;
    }

    private static void paint(DynamicTexture tex) {
        NativeImage img = tex.getPixels();
        if (img == null) {
            return;
        }
        String mode = Opts.text("glint.colorMode");
        String pattern = Opts.text("glint.pattern");
        String dir = Opts.text("glint.direction");
        int c1 = Opts.color("glint.color");
        int c2 = Opts.color("glint.color2");
        double time = System.currentTimeMillis() / 1000.0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double u = axis(dir, x, y);
                double i = intensity(pattern, x, y, u);
                int rgb;
                switch (mode) {
                    case "gradient" -> rgb = lerp(c1, c2, 0.5 + 0.5 * Math.sin(u * Math.PI * 2));
                    case "rainbow" -> rgb = hue((u * 2 + time * 0.15) % 1.0);
                    default -> rgb = c1 & 0xFFFFFF;
                }
                int r = (int) (((rgb >> 16) & 255) * i);
                int g = (int) (((rgb >> 8) & 255) * i);
                int b = (int) ((rgb & 255) * i);
                int a = (int) (255 * i);
                img.setPixel(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        tex.upload();
    }

    /** Position entlang der Laufrichtung, 0 bis 1 über die Kachel (nahtlos wiederholbar). */
    private static double axis(String dir, int x, int y) {
        return switch (dir) {
            case "horizontal" -> y / (double) SIZE;
            case "vertical" -> x / (double) SIZE;
            case "reverse" -> ((x - y) % SIZE + SIZE) % SIZE / (double) SIZE;
            default -> ((x + y) % SIZE) / (double) SIZE;
        };
    }

    private static double intensity(String pattern, int x, int y, double u) {
        switch (pattern) {
            case "sparkles" -> {
                long h = (x * 73856093L) ^ (y * 19349663L);
                h = (h ^ (h >>> 13)) * 1274126177L;
                double r = ((h >>> 8) & 0xFFFF) / 65535.0;
                double base = r > 0.985 ? 1.0 : 0.0;
                // weiche Nachbarschaft: vier Nachbarn
                double glow = 0;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    long hh = (((x + d[0] + SIZE) % SIZE) * 73856093L) ^ (((y + d[1] + SIZE) % SIZE) * 19349663L);
                    hh = (hh ^ (hh >>> 13)) * 1274126177L;
                    if ((((hh >>> 8) & 0xFFFF) / 65535.0) > 0.985) {
                        glow += 0.25;
                    }
                }
                return Math.min(1.0, base + glow * 0.6);
            }
            case "waves" -> {
                double v = (y + 7 * Math.sin(2 * Math.PI * x / SIZE * 2)) / SIZE;
                double s = Math.sin(2 * Math.PI * v * 4);
                return s > 0.3 ? (s - 0.3) / 0.7 : 0.0;
            }
            default -> {
                double s = Math.sin(2 * Math.PI * u * 3);
                return s > 0.45 ? (s - 0.45) / 0.55 : 0.0;
            }
        }
    }

    private static int lerp(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int hue(double h) {
        double hh = h * 6;
        int i = (int) hh;
        double f = hh - i;
        double q = 1 - f;
        double r;
        double g;
        double b;
        switch (i % 6) {
            case 0 -> { r = 1; g = f; b = 0; }
            case 1 -> { r = q; g = 1; b = 0; }
            case 2 -> { r = 0; g = 1; b = f; }
            case 3 -> { r = 0; g = q; b = 1; }
            case 4 -> { r = f; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
