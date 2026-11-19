package dev.visuals.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * AFK: Wer eine Weile nichts tut, bekommt den Kino-Modus: die Kamera wechselt zwischen Seite, hinter dir, von oben
 * und Panorama, mit Orbit, Dolly und Kran, Kollisionsschutz, Cinema-Balken und Ortseinblendung (siehe VisualsHud).
 * Jede Eingabe beendet den Modus. Nicht enthalten: Charakter-Animationen (Atmen, Umschauen, Hinsetzen, Einschlafen).
 */
public final class AfkEffect {
    private static final String[] VIEWS = {"viewSide", "viewBehind", "viewTop", "viewPanorama"};

    private static boolean afk;
    private static int idleTicks;
    private static int afkTicks;
    private static int viewTicks;
    private static int viewIndex = -1;
    private static Vec3 lastPos = Vec3.ZERO;
    private static float lastYaw;
    private static float lastPitch;
    private static int savedFps = -1;
    private static boolean fpsLowered;

    private AfkEffect() {
    }

    public static boolean active() {
        return afk;
    }

    public static int afkTicks() {
        return afkTicks;
    }

    public static boolean bars() {
        return afk && Opts.bool("afk.bars");
    }

    public static boolean cinema() {
        return afk && Opts.bool("afk.cinema");
    }

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !Opts.active("afk")) {
            end(mc);
            return;
        }
        boolean activity = mc.screen != null || mc.isPaused() || moved(player) || keysDown(mc);
        lastPos = player.position();
        lastYaw = player.getYRot();
        lastPitch = player.getXRot();
        if (activity) {
            idleTicks = 0;
            if (afk) {
                end(mc);
            }
            return;
        }
        idleTicks++;
        if (!afk && idleTicks >= Math.max(5, Opts.integer("afk.waitSeconds")) * 20) {
            afk = true;
            afkTicks = 0;
            viewTicks = 0;
            viewIndex = -1;
            pickNextView();
        }
        if (afk) {
            afkTicks++;
            viewTicks++;
            if (viewTicks >= Math.max(3, Opts.integer("afk.switchInterval")) * 20) {
                viewTicks = 0;
                pickNextView();
            }
            if (Opts.bool("afk.powerSave") && afkTicks > 30 * 20 && !fpsLowered) {
                savedFps = mc.options.framerateLimit().get();
                mc.options.framerateLimit().set(Math.min(savedFps, 30));
                fpsLowered = true;
            }
        }
    }

    private static void end(Minecraft mc) {
        if (!afk && !fpsLowered) {
            idleTicks = 0;
            return;
        }
        if (fpsLowered && savedFps > 0) {
            mc.options.framerateLimit().set(savedFps);
        }
        fpsLowered = false;
        afk = false;
        idleTicks = 0;
        afkTicks = 0;
    }

    private static boolean moved(LocalPlayer p) {
        return p.position().distanceToSqr(lastPos) > 1.0E-5 || Math.abs(p.getYRot() - lastYaw) > 0.05f
                || Math.abs(p.getXRot() - lastPitch) > 0.05f;
    }

    private static boolean keysDown(Minecraft mc) {
        var o = mc.options;
        return o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown() || o.keyJump.isDown()
                || o.keyShift.isDown() || o.keySprint.isDown() || o.keyAttack.isDown() || o.keyUse.isDown();
    }

    private static void pickNextView() {
        List<Integer> enabled = new ArrayList<>();
        for (int i = 0; i < VIEWS.length; i++) {
            if (Opts.bool("afk." + VIEWS[i])) {
                enabled.add(i);
            }
        }
        if (enabled.isEmpty()) {
            viewIndex = -1;
            return;
        }
        int pos = enabled.indexOf(viewIndex);
        viewIndex = enabled.get((pos + 1) % enabled.size());
    }

    /** Kameraposition und -blickrichtung {x, y, z, yaw, pitch} oder {@code null}, wenn die Kamera normal bleibt. */
    public static double[] cameraFor(Level level, Entity entity, float partialTick) {
        if (!afk || !Opts.bool("afk.cinema") || viewIndex < 0 || !(entity instanceof LocalPlayer player)
                || !(level instanceof ClientLevel)) {
            return null;
        }
        double vt = (viewTicks + partialTick) / 20.0;
        double cx = player.getX();
        double cy = player.getY() + player.getEyeHeight() * 0.85;
        double cz = player.getZ();
        double psi = Math.toRadians(player.getYRot());
        double dolly = Opts.bool("afk.dolly") ? 1.0 + 0.28 * Math.sin(vt * 0.45) : 1.0;
        double crane = Opts.bool("afk.crane") ? 1.1 * Math.sin(vt * 0.3) : 0.0;
        double orbit = Opts.bool("afk.orbit") ? vt * 0.16 : 0.0;
        double px;
        double py;
        double pz;
        double yaw;
        double pitch;
        switch (VIEWS[viewIndex]) {
            case "viewSide" -> {
                double a = psi + orbit;
                double r = 3.4 * dolly;
                px = cx + Math.cos(a) * r;
                pz = cz + Math.sin(a) * r;
                py = cy + 0.3 + crane;
                return aim(level, player, cx, cy, cz, px, py, pz, true);
            }
            case "viewBehind" -> {
                double a = psi - Math.PI / 2 + orbit;
                double r = 3.9 * dolly;
                px = cx + Math.cos(a) * r;
                pz = cz + Math.sin(a) * r;
                py = cy + 0.7 + crane;
                return aim(level, player, cx, cy, cz, px, py, pz, true);
            }
            case "viewTop" -> {
                py = cy + 7.0 + crane * 2;
                yaw = Math.toDegrees(-psi) + Math.toDegrees(orbit) * 0.0 + player.getYRot() * 0.0 + vt * 6.0;
                return clipped(level, player, cx, cy, cz, cx + 0.001, py, cz, player.getYRot() + vt * 6.0, 89.0);
            }
            default -> { // Panorama
                yaw = player.getYRot() + vt * 11.0;
                pitch = -6.0 + Math.sin(vt * 0.3) * 4.0;
                return new double[]{cx, cy + 0.25 + crane * 0.3, cz, yaw, pitch};
            }
        }
    }

    private static double[] aim(Level level, LocalPlayer player, double cx, double cy, double cz, double px, double py,
                                double pz, boolean lookAtPlayer) {
        double dx = cx - px;
        double dy = cy - py;
        double dz = cz - pz;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return clipped(level, player, cx, cy, cz, px, py, pz, yaw, pitch);
    }

    /** Kollisionsschutz: Liegt ein Block zwischen Spieler und Kamera, rückt die Kamera davor. */
    private static double[] clipped(Level level, LocalPlayer player, double cx, double cy, double cz, double px,
                                    double py, double pz, double yaw, double pitch) {
        if (Opts.bool("afk.collisionGuard")) {
            Vec3 from = new Vec3(cx, cy, cz);
            Vec3 to = new Vec3(px, py, pz);
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 dir = to.subtract(from).normalize();
                Vec3 safe = hit.getLocation().subtract(dir.scale(0.35));
                px = safe.x;
                py = safe.y;
                pz = safe.z;
            }
        }
        return new double[]{px, py, pz, yaw, pitch};
    }
}
