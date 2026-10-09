package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.Constants;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Small storyboard helpers shared by our Ponder scenes (PND1, docs/almanac.md "Scenes"): shared-text lines, arrows and
 * paths drawn with Ponder's lines, and a per-tick callback for what Ponder has no instruction for (an entity walking
 * a circle, a wheel turning by degrees, an anchor falling). Client only, like every storyboard.
 */
final class SceneKit {

    private SceneKit() {
    }

    /**
     * One of our items by its path, looked up in the item registry rather than through the content classes, so a
     * storyboard compiles without the mod's registration (the headless storyboard test; an unknown path is air).
     */
    static ItemStack item(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Constants.id(path)));
    }

    /** The default state of one of our blocks by its path (see {@link #item}). */
    static BlockState block(String path) {
        return BuiltInRegistries.BLOCK.get(Constants.id(path)).defaultBlockState();
    }

    /** Sets the window's text to {@code line} (Ponder shared text with an explicit key, see {@link SceneLine}). */
    static TextElementBuilder say(TextElementBuilder window, SceneLine line) {
        return window.sharedText(line.key());
    }

    /** A thick line from {@code from} to {@code to} with a two-stroke head at {@code to}. */
    static void arrow(SceneBuilder scene, PonderPalette color, Vec3 from, Vec3 to, int ticks) {
        scene.overlay().showBigLine(color, from, to, ticks);
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1e-6) {
            return;
        }
        Vec3 u = d.scale(1.0 / len);
        Vec3 side = Math.abs(u.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(-u.z, 0, u.x).normalize();
        double head = Math.min(0.45, len * 0.35);
        Vec3 back = to.subtract(u.scale(head));
        scene.overlay().showBigLine(color, to, back.add(side.scale(head * 0.6)), ticks);
        scene.overlay().showBigLine(color, to, back.subtract(side.scale(head * 0.6)), ticks);
    }

    /** Thin lines joining {@code points} in order. */
    static void path(SceneBuilder scene, PonderPalette color, List<Vec3> points, int ticks) {
        for (int i = 1; i < points.size(); i++) {
            scene.overlay().showLine(color, points.get(i - 1), points.get(i), ticks);
        }
    }

    /**
     * Points on a horizontal circle about {@code center} (at its height) of {@code radius}, from {@code fromDeg} to
     * {@code toDeg} in {@code segments} steps; an angle {@code a} is the point {@code center + r (cos a, 0, sin a)}, so
     * 0° is east and 90° south.
     */
    static List<Vec3> arc(Vec3 center, double radius, double fromDeg, double toDeg, int segments) {
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            double a = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / segments);
            out.add(center.add(radius * Math.cos(a), 0, radius * Math.sin(a)));
        }
        return out;
    }

    /** Minecraft's yaw (degrees) of a horizontal direction {@code (dx, dz)}. */
    static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Puts an entity at {@code pos} facing {@code yaw}, body and head included, without a lerp from its old spot. */
    static void place(Entity e, Vec3 pos, float yaw) {
        e.setPos(pos.x, pos.y, pos.z);
        e.setYRot(yaw);
        e.yRotO = yaw;
        if (e instanceof LivingEntity living) {
            living.yBodyRot = yaw;
            living.yBodyRotO = yaw;
            living.yHeadRot = yaw;
            living.yHeadRotO = yaw;
        }
    }

    /** What {@link #everyTick} runs: {@code tick} counts 0 .. {@code total - 1}. */
    @FunctionalInterface
    interface TickAction {
        void tick(PonderScene scene, int tick, int total);
    }

    /** Runs {@code action} on each of the next {@code ticks} scene ticks, alongside the instructions after it. */
    static void everyTick(SceneBuilder scene, int ticks, TickAction action) {
        scene.addInstruction(new EveryTick(ticks, action));
    }

    /** Linear interpolation of two points, {@code t} in 0..1. */
    static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.add(b.subtract(a).scale(t));
    }

    /** Non-blocking: calls its action once per tick for its duration. */
    private static final class EveryTick extends TickingInstruction {
        private final TickAction action;

        EveryTick(int ticks, TickAction action) {
            super(false, ticks);
            this.action = action;
        }

        @Override
        public void tick(PonderScene scene) {
            int tick = totalTicks - remainingTicks;
            super.tick(scene);
            if (tick < totalTicks) {
                action.tick(scene, tick, totalTicks);
            }
        }
    }
}
