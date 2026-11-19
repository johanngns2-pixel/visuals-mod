package dev.visuals.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Ambient-Geräusche: Wind, Grillen, Schnee und zusätzliches Regenrauschen, jeweils mit eigener Lautstärke.
 * Die Klänge sind nahtlose Schleifen (12 s) und werden nacheinander abgespielt. Nicht enthalten: Raumhall.
 */
public final class SoundEffect {
    private static final SoundEvent WIND = event("ambient.wind");
    private static final SoundEvent RAIN = event("ambient.rain");
    private static final SoundEvent SNOW = event("ambient.snow");
    private static final SoundEvent CRICKETS = event("ambient.crickets");
    private static final int LOOP_TICKS = 236;

    private static int tick;
    private static int nextWind;
    private static int nextRain;
    private static int nextSnow;
    private static int nextCrickets;

    private SoundEffect() {
    }

    private static SoundEvent event(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("visuals", path));
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused() || !Opts.active("sound")
                || level.dimension() != Level.OVERWORLD) {
            return;
        }
        tick++;
        int bx = (int) Math.floor(player.getX());
        int bz = (int) Math.floor(player.getZ());
        boolean outdoors = player.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
        if (!outdoors) {
            return;
        }
        float rain = level.getRainLevel(1.0f);
        long t = level.getDayTime() % 24000L;
        boolean night = t >= 13000L && t <= 23000L;

        double windVol = Opts.num("sound.wind");
        if (windVol > 0 && tick >= nextWind) {
            double strength = Opts.active("weather") ? Opts.num("weather.windStrength") : 0.4;
            double v = windVol * Math.min(1.0, 0.3 + strength * 0.7 + rain * 0.3 + Math.max(0, (player.getY() - 90) / 120.0) * 0.4);
            play(level, player, WIND, v);
            nextWind = tick + LOOP_TICKS;
        }
        double rainVol = Opts.num("sound.rain");
        if (rainVol > 0 && rain > 0.2f && tick >= nextRain) {
            play(level, player, RAIN, rainVol * rain);
            nextRain = tick + LOOP_TICKS;
        }
        double snowVol = Opts.num("sound.snow");
        if (snowVol > 0 && SeasonClock.current() == 3 && rain < 0.5f && tick >= nextSnow) {
            play(level, player, SNOW, snowVol);
            nextSnow = tick + LOOP_TICKS;
        }
        double cricketVol = Opts.num("sound.crickets");
        if (cricketVol > 0 && night && rain < 0.2f && SeasonClock.current() != 3 && tick >= nextCrickets) {
            play(level, player, CRICKETS, cricketVol);
            nextCrickets = tick + LOOP_TICKS;
        }
    }

    private static void play(ClientLevel level, LocalPlayer player, SoundEvent sound, double volume) {
        level.playLocalSound(player.getX(), player.getY(), player.getZ(), sound, SoundSource.AMBIENT,
                (float) Math.max(0.0, Math.min(1.0, volume)), 1.0f, false);
    }
}
