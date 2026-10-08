package com.richardsenger.piratesnships.crew.hammock;

import com.richardsenger.piratesnships.crew.hammock.PlayerSleepRules.Refusal;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Players sleep aboard (SLP1, docs/design.md §7.1): in a hammock (on land or on a ship) and in the sea cot on an
 * assembled ship. Vanilla's bed handling cannot do this alone: on a ship the bunk is a block in Sable's far-away plot,
 * and vanilla would lay the sleeper there once ({@code LivingEntity#startSleeping}; Sable 2.0.6 projects that spot into
 * the world, {@code refs/sable/common/src/main/java/dev/ryanhcode/sable/mixin/respawn_point/sleeping/LivingEntityMixin.java},
 * but the sleeper then stays behind when the ship sails on), and it checks monsters around the plot position.
 * <p>
 * So the player lies on a {@link HammockSeat} in the plot at the bunk's head half, which Sable carries with the ship like
 * the crew's seats (sable-notes §9.0e), and vanilla does the rest wherever it can:
 * <ul>
 *   <li><b>Lying down</b> ({@link #use}): our checks in vanilla's order ({@link PlayerSleepRules}), measured where they
 *       make sense: reach in the bunk's frame, monsters around the bunk's world position. Then vanilla's
 *       {@code startSleeping} at the head half (sleeping position, pose, the cot's {@code OCCUPIED}, phantom timer),
 *       the seat, stats, advancement and {@code updateSleepingPlayerList}. The player counts for the night skip
 *       through vanilla's sleeping list; vanilla's {@code Player#tick} counts the sleep timer.</li>
 *   <li><b>The respawn point</b> is set as vanilla does at the bed, at the head half, unforced. On a ship that is a
 *       plot position, which Sable 2.0.6 turns into a tracking point that moves with the ship
 *       ({@code refs/sable/common/src/main/java/dev/ryanhcode/sable/mixin/respawn_point/ServerPlayerMixin.java}
 *       {@code sable$setRespawnPosition}, {@code sable$findRespawnPosition}); on land the hammock finds the spot
 *       beside it ({@code HammockBlock#getRespawnPosition}).</li>
 *   <li><b>Waking</b>: vanilla wakes the sleeper (dawn, "Leave Bed", the night skipped, hurt). Just before, in
 *       {@link #onWakeUp} ({@code CommonEvents.PLAYER_WAKE_UP}, NeoForge's {@code PlayerWakeUpEvent}), we take the
 *       player off the seat beside the bunk ({@link #standUp}), free the cot and clear the sleeping position, so
 *       vanilla's own stand-up (only at the bed's height, else on top of the head half: inside the deck above a hammock
 *       hung in a hold) is skipped. Sneaking off the seat, or losing it, wakes the player ({@link #onPlayerTick}).</li>
 * </ul>
 * A hammock with a player's seat is not free for the crew ({@link ShipBunks#isFree}).
 */
public final class PlayerSleep {

    public static final String KEY_CREW_IN_IT = "message.pirates_n_ships.hammock.crew_in_it";
    public static final String KEY_NOT_HERE = "message.pirates_n_ships.bunk.not_here";

    /** Vanilla's monster search round a bed: 8 blocks across, 5 up and down. */
    private static final double MONSTER_XZ = 8.0, MONSTER_Y = 5.0;

    private PlayerSleep() {
    }

    /**
     * {@code player} uses the bunk half {@code state} at {@code pos}: lies down, or is told why not (action bar).
     * Returns why not, {@link Refusal#NONE} when the player now sleeps.
     */
    public static Refusal use(ServerPlayer player, BlockPos pos, BlockState state) {
        Refusal r = tryLieDown(player, pos, state);
        Component message = message(r);
        if (message != null) {
            player.displayClientMessage(message, true);
        }
        return r;
    }

    /** {@link #use} without the message. */
    public static Refusal tryLieDown(ServerPlayer player, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof Bunk bunk)) {
            return Refusal.OTHER;
        }
        ServerLevel level = player.serverLevel();
        BlockPos foot = Bunk.foot(state, pos);
        BlockPos head = Bunk.head(state, pos);
        ShipBody ship = SableShips.containing(level, foot);

        boolean crewInIt = false, occupied = false;
        for (HammockSeat s : HammockSeat.at(level, foot)) {
            Entity rider = s.getFirstPassenger();
            if (rider instanceof CrewMember) crewInIt = true;
            else if (rider != null) occupied = true;
        }
        BlockState headState = level.getBlockState(head);
        if (headState.hasProperty(BedBlock.OCCUPIED) && headState.getValue(BedBlock.OCCUPIED)) {
            occupied = true;
        }
        Vec3 inBunkFrame = ship == null ? player.position() : ship.toPlot(player.position());
        boolean inReach = reaches(inBunkFrame, head) || reaches(inBunkFrame, foot);
        boolean obstructed = bunk.needsHeadroom() && (suffocates(level, head.above()) || suffocates(level, foot.above()));
        Vec3 bunkInWorld = world(ship, Vec3.atBottomCenterOf(head));
        boolean monsters = !level.getEntitiesOfClass(Monster.class, new AABB(bunkInWorld, bunkInWorld)
                .inflate(MONSTER_XZ, MONSTER_Y, MONSTER_XZ), m -> m.isPreventingPlayerRest(player)).isEmpty();
        PlayerSleepRules.Check check = new PlayerSleepRules.Check(player.isSleeping() || !player.isAlive(), crewInIt,
                occupied, level.dimensionType().natural() && BedBlock.canSetSpawn(level), inReach, obstructed,
                level.isDay(), monsters, player.isCreative());
        Refusal r = PlayerSleepRules.refusal(check);
        if (PlayerSleepRules.setsSpawn(r)) {
            player.setRespawnPosition(level.dimension(), head, player.getYRot(), false, true);
        }
        if (r != Refusal.NONE) {
            return r;
        }
        lieDown(player, level, foot, head, Bunk.facing(state), bunk);
        return Refusal.NONE;
    }

    private static void lieDown(ServerPlayer player, ServerLevel level, BlockPos foot, BlockPos head, Direction facing, Bunk bunk) {
        HammockSeat seat = HammockSeat.spawnForPlayer(level, foot, lyingSpot(head, bunk), facing);
        player.stopRiding();
        player.startSleeping(head); // vanilla: sleeping position, pose, the cot's OCCUPIED, TIME_SINCE_REST
        if (!player.startRiding(seat, true)) {
            seat.discard();
            player.stopSleepInBed(true, true);
            return;
        }
        player.setPose(Pose.SLEEPING); // startRiding stands it up
        seat.positionRider(player); // place it now (Sable maps it to world space)
        player.awardStat(Stats.SLEEP_IN_BED);
        CriteriaTriggers.SLEPT_IN_BED.trigger(player);
        if (!level.canSleepThroughNights()) {
            player.displayClientMessage(Component.translatable("sleep.not_possible"), true);
        }
        level.updateSleepingPlayerList();
    }

    /** Plot (or world) position of a sleeper's feet in the bunk with head half {@code head}. */
    public static Vec3 lyingSpot(BlockPos head, Bunk bunk) {
        return Vec3.atBottomCenterOf(head).add(0, bunk.lyingHeight() / 16.0, 0);
    }

    /**
     * {@code CommonEvents.PLAYER_WAKE_UP}: a player sleeping on one of our seats wakes. It gets off the seat beside the
     * bunk, the cot is free again and the sleeping position is cleared before vanilla's stand-up could run.
     */
    public static void onWakeUp(Player p) {
        if (!(p instanceof ServerPlayer player) || p.level().isClientSide) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Optional<BlockPos> at = player.getSleepingPos();
        boolean onSeat = player.getVehicle() instanceof HammockSeat seat && seat.forPlayer();
        if (at.isEmpty() || !(onSeat || sleepsOnSeat(level, at.get()))) {
            return; // a bed on land: vanilla's
        }
        BlockState state = level.getBlockState(at.get());
        if (state.hasProperty(BedBlock.OCCUPIED) && state.getValue(BedBlock.OCCUPIED)) {
            level.setBlock(at.get(), state.setValue(BedBlock.OCCUPIED, false), Block.UPDATE_ALL);
        }
        player.clearSleepingPos();
        if (player.getVehicle() instanceof HammockSeat seat && seat.forPlayer()) {
            player.stopRiding(); // dismounts at HammockSeat#getDismountLocationForPassenger: standUp
            seat.discard();
        }
        // vanilla now stands it up where it is, updates the sleeping list and teleports the client there
    }

    /**
     * End of every player tick (both sides; acts on the server): wakes a player sleeping in a bunk of ours that is no
     * longer on its seat (it sneaked off, or the seat is gone), and gets a player off a seat it rides awake (after a
     * reconnect, or when the wake event did not reach us).
     */
    public static void onPlayerTick(Player p) {
        if (!(p instanceof ServerPlayer player) || p.level().isClientSide) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (player.getVehicle() instanceof HammockSeat seat && seat.forPlayer()) {
            if (!player.isSleeping()) {
                getOff(player, seat);
            }
            return;
        }
        Optional<BlockPos> at = player.getSleepingPos();
        if (at.isPresent() && sleepsOnSeat(level, at.get())) {
            BlockState state = level.getBlockState(at.get());
            player.stopSleepInBed(false, true); // onWakeUp, then vanilla: sleeping list, client teleport
            for (HammockSeat s : HammockSeat.at(level, Bunk.foot(state, at.get()))) {
                if (s.forPlayer() && !s.isVehicle()) s.discard();
            }
        }
    }

    /** Whether a sleeper with sleeping position {@code pos} sleeps on one of our seats. */
    public static boolean sleepsOnSeat(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof Bunk bunk && bunk.sleepsOnSeat(level, pos);
    }

    /** Takes a woken {@code player} off {@code seat} to the spot beside the bunk, and removes the seat. */
    static void getOff(ServerPlayer player, HammockSeat seat) {
        player.stopRiding(); // dismounts at HammockSeat#getDismountLocationForPassenger: standUp
        Vec3 at = player.position();
        player.teleportTo(at.x, at.y, at.z); // a server player's own dismount does not tell its client
        seat.discard();
    }

    /**
     * World position where a sleeper gets up from the bunk with foot half {@code foot}: vanilla's stand-up spot beside a
     * bed ({@link BedBlock#findStandUpPosition}), looked for in the bunk's frame (on a ship the deck is in the plot) at
     * the bunk's height, then one and two blocks lower (a hammock hangs above the floor). A spot beside the bunk wins
     * over one on the bunk itself (vanilla's last resort, which for a hammock means standing on the canvas); with
     * neither, on top of the head half as vanilla does. Taken into the world.
     */
    public static Vec3 standUp(ServerLevel level, BlockPos foot, float yRot) {
        BlockState state = level.getBlockState(foot);
        Direction facing = state.getBlock() instanceof Bunk ? Bunk.facing(state) : Direction.NORTH;
        Vec3 spot = standUpInFrame(level, foot, facing, yRot)
                .orElseGet(() -> Vec3.atBottomCenterOf(foot.relative(facing).above()).add(0, 0.1, 0));
        return world(SableShips.containing(level, foot), spot);
    }

    /**
     * {@link #standUp} in the bunk's own frame, without the last fallback: empty when there is no spot beside the bunk
     * or on it. Also the hammock's respawn spot on land.
     */
    public static Optional<Vec3> standUpInFrame(LevelReader level, BlockPos foot, Direction facing, float yRot) {
        BlockPos head = foot.relative(facing);
        Vec3 onBunk = null;
        for (int down = 0; down <= STAND_UP_SEARCH_DOWN; down++) {
            Vec3 v = BedBlock.findStandUpPosition(EntityType.PLAYER, level, head.below(down), facing, yRot).orElse(null);
            if (v == null) continue;
            BlockPos at = BlockPos.containing(v);
            if (!at.equals(head) && !at.equals(foot)) return Optional.of(v);
            if (onBunk == null) onBunk = v;
        }
        return Optional.ofNullable(onBunk);
    }

    /** How many blocks below a bunk {@link #standUp} looks for the floor. */
    static final int STAND_UP_SEARCH_DOWN = 2;

    /** The message for refusal {@code r}, or null (none, or silent). */
    public static @Nullable Component message(Refusal r) {
        return switch (r) {
            case NONE, OTHER -> null;
            case CREW_IN_IT -> Component.translatable(KEY_CREW_IN_IT);
            case OCCUPIED -> Component.translatable("block.minecraft.bed.occupied");
            case NOT_HERE -> Component.translatable(KEY_NOT_HERE);
            case TOO_FAR_AWAY -> Player.BedSleepingProblem.TOO_FAR_AWAY.getMessage();
            case OBSTRUCTED -> Player.BedSleepingProblem.OBSTRUCTED.getMessage();
            case NOT_NOW -> Player.BedSleepingProblem.NOT_POSSIBLE_NOW.getMessage();
            case NOT_SAFE -> Player.BedSleepingProblem.NOT_SAFE.getMessage();
        };
    }

    private static boolean reaches(Vec3 from, BlockPos half) {
        Vec3 b = Vec3.atBottomCenterOf(half);
        return PlayerSleepRules.inReach(from.x - b.x, from.y - b.y, from.z - b.z);
    }

    private static boolean suffocates(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isSuffocating(level, pos);
    }

    private static Vec3 world(@Nullable ShipBody ship, Vec3 p) {
        return ship == null ? p : ship.toWorld(p);
    }
}
