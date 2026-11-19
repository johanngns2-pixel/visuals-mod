package dev.visuals.spotify;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Synchronisierte Songtexte im LRC-Format: "[mm:ss.xx] Text". */
public final class Lyrics {
    public record Line(long timeMs, String text) {
    }

    private static final Pattern LINE = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]\\s*(.*)");

    private Lyrics() {
    }

    public static List<Line> parse(String lrc) {
        List<Line> lines = new ArrayList<>();
        if (lrc == null) {
            return lines;
        }
        for (String raw : lrc.split("\\R")) {
            Matcher m = LINE.matcher(raw.trim());
            if (!m.matches()) {
                continue;
            }
            long min = Long.parseLong(m.group(1));
            long sec = Long.parseLong(m.group(2));
            long frac = 0;
            if (m.group(3) != null) {
                String f = (m.group(3) + "00").substring(0, 3);
                frac = Long.parseLong(f);
            }
            lines.add(new Line(min * 60_000L + sec * 1000L + frac, m.group(4)));
        }
        return lines;
    }

    /** Index der Zeile, die zur Zeit {@code ms} läuft, oder -1 vor der ersten Zeile. */
    public static int indexAt(List<Line> lines, long ms) {
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs() <= ms) {
                idx = i;
            } else {
                break;
            }
        }
        return idx;
    }
}
