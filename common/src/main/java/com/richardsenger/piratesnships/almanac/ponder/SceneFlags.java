package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleBlockEntity;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleMachine;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleState;
import com.richardsenger.piratesnships.ship.decor.flag.Flags;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Flags in Ponder scenes (PND2): a pole head's real {@link FlagpoleState}, changed by the real {@link FlagpoleMachine}
 * and written into the block entity the way the server's sync does ({@link SceneKit#editBlockEntity}), so the game's
 * cloth renderer draws the flag, its kind, its banner and its run up or down the pole. Ponder pauses the game in
 * single player, so a pending action is re-timed every scene tick to have started that many ticks ago: the hoisting
 * animation then runs with the scene whether or not the game clock moves.
 */
final class SceneFlags {

    /** Who works the pole in a scene (the flag items go nowhere: there is no player). */
    static final UUID ACTOR = new UUID(0L, 0x5ce9eL);

    private SceneFlags() {
    }

    /** The state of the pole head at {@code head}, or empty. */
    static FlagpoleState state(Level level, BlockPos head) {
        return level.getBlockEntity(head) instanceof FlagpoleBlockEntity be ? be.state() : FlagpoleState.EMPTY;
    }

    /** A flag of {@code kind} flying at {@code head} at once (a pole the scene starts with). */
    static void fly(SceneBuilder scene, BlockPos head, FlagKind kind) {
        SceneKit.once(scene, s -> write(s, head, new FlagpoleState(kind, Flags.stackFor(kind), false, Optional.empty(),
                Optional.empty())));
    }

    /** Hoists {@code kind} (its flag item) over {@code ticks}. */
    static void hoist(SceneBuilder scene, BlockPos head, FlagKind kind, int ticks) {
        act(scene, head, level -> new FlagpoleMachine.Input.Hoist(kind, Flags.stackFor(kind)), ticks);
    }

    /** Hoists a banner, a custom flag, over {@code ticks}: blue with a white skull and a white border. */
    static void hoistBanner(SceneBuilder scene, BlockPos head, int ticks) {
        act(scene, head, level -> new FlagpoleMachine.Input.Hoist(FlagKind.CUSTOM, banner(level)), ticks);
    }

    /** Strikes or raises the colours (an empty hand on the pole) over {@code ticks}. */
    static void toggle(SceneBuilder scene, BlockPos head, int ticks) {
        act(scene, head, level -> new FlagpoleMachine.Input.Toggle(), ticks);
    }

    /** The banner the scene flies. */
    static ItemStack banner(Level level) {
        ItemStack stack = new ItemStack(Items.BLUE_BANNER);
        var patterns = level.registryAccess().registryOrThrow(Registries.BANNER_PATTERN);
        BannerPatternLayers.Builder layers = new BannerPatternLayers.Builder();
        patterns.getHolder(BannerPatterns.SKULL).ifPresent(h -> layers.add(h, DyeColor.WHITE));
        patterns.getHolder(BannerPatterns.BORDER).ifPresent(h -> layers.add(h, DyeColor.WHITE));
        stack.set(DataComponents.BANNER_PATTERNS, layers.build());
        return stack;
    }

    /**
     * One action at the pole: {@link FlagpoleMachine#use} with the input, the pending action shown running for
     * {@code ticks}, then completed by {@link FlagpoleMachine#tick}.
     */
    private static void act(SceneBuilder scene, BlockPos head, Function<Level, FlagpoleMachine.Input> input, int ticks) {
        FlagpoleState[] started = new FlagpoleState[1];
        SceneKit.everyTick(scene, ticks, (s, t, total) -> {
            Level level = s.getWorld();
            long now = level.getGameTime();
            if (t == 0 || started[0] == null) {
                started[0] = FlagpoleMachine.use(state(level, head), input.apply(level), ACTOR, now, total).state();
            }
            FlagpoleState running = retimed(started[0], now - t, total);
            if (t < total - 1) {
                write(s, head, running);
            } else {
                write(s, head, FlagpoleMachine.tick(running, Long.MAX_VALUE).state());
            }
        });
    }

    /** {@code state} with its pending action started at {@code start} and lasting {@code ticks}. */
    static FlagpoleState retimed(FlagpoleState state, long start, int ticks) {
        return state.withPending(state.pending().map(p -> new FlagpoleState.Pending(p.action(), p.actor(), start,
                start + ticks, p.kind(), p.item())));
    }

    /**
     * Writes {@code state} into the pole head's block entity, as the server's sync does. The cloth is drawn from it;
     * the block state's {@code flag} (what the server's other systems read) is left as it is, since a block change
     * in Ponder's level would replace the block entity.
     */
    static void write(PonderScene scene, BlockPos head, FlagpoleState state) {
        Level level = scene.getWorld();
        String key = SceneSync.key(SceneSync.FLAGPOLE_STATE, "flagpole");
        SceneKit.editBlockEntity(scene, head, tag -> FlagpoleState.CODEC
                .encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), state)
                .result().ifPresent(t -> tag.put(key, t)));
    }
}
