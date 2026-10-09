package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.Constants;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.createmod.ponder.foundation.instruction.ReplaceBlocksInstruction;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    /**
     * Walks the entity in {@code ref} from {@code from} to {@code to} over {@code ticks}, facing the way it goes (its legs
     * move, so a crew member plays its walk). Non-blocking.
     */
    static void walk(SceneBuilder scene, AtomicReference<? extends Entity> ref, Vec3 from, Vec3 to, int ticks) {
        float yaw = yaw(to.x - from.x, to.z - from.z);
        everyTick(scene, ticks, (s, t, total) -> {
            Entity e = ref.get();
            if (e != null) {
                Vec3 p = lerp(from, to, (t + 1) / (double) total);
                e.setPos(p.x, p.y, p.z);
                e.setYRot(yaw);
                if (e instanceof LivingEntity living) {
                    living.yBodyRot = yaw;
                    living.yHeadRot = yaw;
                }
            }
        });
    }

    /** Moves the entity in {@code ref} along a parabola {@code height} above the line from {@code from} to {@code to}. */
    static void fly(SceneBuilder scene, AtomicReference<? extends Entity> ref, Vec3 from, Vec3 to, double height, int ticks) {
        everyTick(scene, ticks, (s, t, total) -> {
            Entity e = ref.get();
            if (e != null) {
                double f = (t + 1) / (double) total;
                Vec3 p = lerp(from, to, f).add(0, height * 4 * f * (1 - f), 0);
                e.setPos(p.x, p.y, p.z);
                e.setDeltaMovement(Vec3.ZERO);
            }
        });
    }

    /** Runs {@code action} once, on the next scene tick (an instruction for code without a Ponder instruction). */
    static void once(SceneBuilder scene, Consumer<PonderScene> action) {
        scene.addInstruction(action);
    }

    /**
     * Rewrites the saved data of the block entity at {@code pos} the way a sync from the server does: its full tag is
     * saved, changed by {@code edit} and loaded back (for state the server decides and a client only receives, such
     * as a flag being hoisted or a bell's last ring).
     */
    static void editBlockEntity(PonderScene scene, BlockPos pos, Consumer<CompoundTag> edit) {
        BlockEntity be = scene.getWorld().getBlockEntity(pos);
        if (be == null) {
            return;
        }
        HolderLookup.Provider registries = scene.getWorld().registryAccess();
        CompoundTag tag = be.saveWithoutMetadata(registries);
        edit.accept(tag);
        be.loadWithComponents(tag, registries);
    }

    /** A dropped item that stays where it is put (the flying hook, a shot); spawned by {@code createEntity}. */
    static ItemEntity floatingItem(Level level, Vec3 at, ItemStack stack, AtomicReference<ItemEntity> ref) {
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack);
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
        item.setPickUpDelay(Short.MAX_VALUE);
        ref.set(item);
        return item;
    }

    /**
     * Sets the blocks of {@code selection} to the state {@code state} gives when the instruction runs, not when the
     * storyboard is built: our blocks' states are only looked up in the client (a headless build has no registry).
     */
    static void setLater(SceneBuilder scene, Selection selection, Supplier<BlockState> state, boolean particles) {
        scene.addInstruction(new ReplaceBlocksInstruction(selection, prev -> state.get(), true, particles));
    }

    /** Removes the entity in {@code ref} from the scene (once). */
    static void discard(SceneBuilder scene, AtomicReference<? extends Entity> ref) {
        once(scene, s -> {
            Entity e = ref.get();
            if (e != null) {
                e.discard();
            }
        });
    }

    /**
     * Declares a point the watcher must see at this moment of the scene (PND3): where something happens that no text
     * points at and no click shows (an entity at work, a block changing in a model's state). It does nothing in the
     * game; {@code SceneViewTest} reads it from the schedule and fails when a solid block hides the point from the
     * default camera.
     */
    static void focus(SceneBuilder scene, Vec3 point) {
        scene.addInstruction(new Focus(point, null));
    }

    /** {@link #focus(SceneBuilder, Vec3)} for a whole block: seen when its centre or a face toward the camera is. */
    static void focus(SceneBuilder scene, BlockPos block) {
        scene.addInstruction(new Focus(null, block));
    }

    /**
     * Hides {@code selection} (Ponder's {@code hideSection}, the wall on the watcher's side cut away) and tells the
     * headless view check that those blocks are gone from the picture (Ponder's own instruction erases them through a
     * callback the check cannot read).
     */
    static void hide(SceneBuilder scene, Selection selection, net.minecraft.core.Direction fadeOutTo) {
        scene.addInstruction(new Hidden(selection));
        scene.world().hideSection(selection, fadeOutTo);
    }

    /**
     * Cuts a hull wall on the watcher's side away (PND3: Ponder's default camera looks from the north-west, so a wall on
     * the north or west side of a hold stands between the watcher and what happens inside): the wall fades out
     * ({@link #hide}), then its cells become air, so the water behind them draws its face toward the watcher like a
     * cross-section instead of hiding it. Blocks for 15 ticks.
     */
    static void cutAway(SceneBuilder scene, Selection wall) {
        hide(scene, wall, net.minecraft.core.Direction.UP);
        scene.idle(15);
        scene.world().setBlocks(wall, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), false);
    }

    /** A point of interest of the scene ({@link #focus}): a marker only, complete at once. */
    static final class Focus extends PonderInstruction {
        final Vec3 point;
        final BlockPos block;

        Focus(Vec3 point, BlockPos block) {
            this.point = point;
            this.block = block;
        }

        @Override
        public boolean isComplete() {
            return true;
        }

        @Override
        public void tick(PonderScene scene) {
        }
    }

    /** The blocks {@link #hide} takes out of the picture: a marker only, complete at once. */
    static final class Hidden extends PonderInstruction {
        final Selection selection;

        Hidden(Selection selection) {
            this.selection = selection;
        }

        @Override
        public boolean isComplete() {
            return true;
        }

        @Override
        public void tick(PonderScene scene) {
        }
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
