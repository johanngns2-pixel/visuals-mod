package dev.visuals.config.def;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fertige Beispiel-Glints (Farbmodus, Farben, Muster, Richtung). Erzeugt aus einer Tabelle. */
public final class GlintExamples {
    public record Example(String mode, int color, int color2, String pattern, String direction) {
    }

    public static final Map<String, Example> ALL = new LinkedHashMap<>();

    static {
        ALL.put("amethyst", new Example("solid", 0xFFA050FF, 0xFF50C8FF, "stripes", "diagonal"));
        ALL.put("ruby", new Example("solid", 0xFFFF2A3A, 0xFFFF8A8A, "stripes", "diagonal"));
        ALL.put("emerald", new Example("solid", 0xFF22E07A, 0xFFA0FFD0, "stripes", "diagonal"));
        ALL.put("sapphire", new Example("solid", 0xFF2A6BFF, 0xFF9AC4FF, "stripes", "diagonal"));
        ALL.put("gold", new Example("solid", 0xFFFFC93C, 0xFFFFF0B0, "stripes", "diagonal"));
        ALL.put("ice", new Example("gradient", 0xFF9FE8FF, 0xFFFFFFFF, "sparkles", "diagonal"));
        ALL.put("fire", new Example("gradient", 0xFFFF3B00, 0xFFFFD000, "waves", "vertical"));
        ALL.put("lava", new Example("gradient", 0xFFFF1E00, 0xFFFF8A00, "waves", "horizontal"));
        ALL.put("pink", new Example("solid", 0xFFFF4FD8, 0xFFFFB0F0, "stripes", "diagonal"));
        ALL.put("neon", new Example("solid", 0xFF39FF14, 0xFFB0FF9A, "sparkles", "diagonal"));
        ALL.put("cyan", new Example("solid", 0xFF00F0FF, 0xFFA0FFFF, "stripes", "horizontal"));
        ALL.put("orange", new Example("solid", 0xFFFF8A1E, 0xFFFFC890, "stripes", "reverse"));
        ALL.put("white", new Example("solid", 0xFFFFFFFF, 0xFFDDEEFF, "sparkles", "diagonal"));
        ALL.put("shadow", new Example("solid", 0xFF5A2D8C, 0xFF9A6BFF, "waves", "vertical"));
        ALL.put("rainbow", new Example("rainbow", 0xFFFFFFFF, 0xFFFFFFFF, "stripes", "diagonal"));
        ALL.put("rainbowsparkle", new Example("rainbow", 0xFFFFFFFF, 0xFFFFFFFF, "sparkles", "diagonal"));
        ALL.put("galaxy", new Example("gradient", 0xFF5B2BFF, 0xFFFF2BD6, "sparkles", "diagonal"));
        ALL.put("sunset", new Example("gradient", 0xFFFF5E3A, 0xFFFFC371, "waves", "horizontal"));
        ALL.put("ocean", new Example("gradient", 0xFF0077FF, 0xFF00E5C8, "waves", "horizontal"));
        ALL.put("forest", new Example("gradient", 0xFF1E8F3A, 0xFFB6F24A, "stripes", "vertical"));
        ALL.put("toxic", new Example("gradient", 0xFF8CFF00, 0xFF00FF9C, "sparkles", "diagonal"));
        ALL.put("candy", new Example("gradient", 0xFFFF7AD9, 0xFF7AE7FF, "stripes", "diagonal"));
        ALL.put("blood", new Example("solid", 0xFFB0001A, 0xFFFF4060, "waves", "vertical"));
        ALL.put("mint", new Example("solid", 0xFF7DFFC9, 0xFFD0FFEE, "stripes", "reverse"));
        ALL.put("lavender", new Example("solid", 0xFFC9A7FF, 0xFFEBDDFF, "stripes", "diagonal"));
        ALL.put("copper", new Example("gradient", 0xFFC46A2F, 0xFFFFB27A, "stripes", "diagonal"));
    }

    private GlintExamples() {
    }
}
