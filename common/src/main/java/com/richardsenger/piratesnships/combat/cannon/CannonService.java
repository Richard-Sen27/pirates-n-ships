package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.content.CombatSounds;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;

/**
 * Server side of the cannon (docs/design.md §8.2): loading, aiming and firing a cannon block, on land or on a ship.
 * Called by {@link CannonBlock} for players and by {@link CannonStation} for crew. All decisions are in
 * {@link CannonRules}; this class reads and writes the world. Every call takes either half of the two-block cannon and
 * acts on its master (front) block.
 *
 * <p>On a ship the cannon block sits in the ship's plot, so its position, facing and the barrel direction are in the
 * plot (= body) frame. Firing converts the muzzle point to world space with {@link ShipBody#toWorld(Vec3)}, rotates the
 * barrel direction by the ship's orientation, and adds the ship's velocity at the muzzle point
 * ({@link ShipBody#velocityAt}, linear plus angular). The ball itself is an ordinary world entity.
 */
public final class CannonService {

    public static final String KEY_PREFIX = "message." + Constants.MOD_ID + ".cannon.";

    /** What a use of the cannon did. */
    public enum Outcome {
        POWDER_IN, BALL_IN, NEEDS_POWDER_FIRST, ALREADY_POWDERED, ALREADY_LOADED, RELOADING,
        AIMED, FIRED, NOT_LOADED, DISABLED, NOT_A_CANNON, SHOT_DISABLED;

        public String key() {
            return KEY_PREFIX + name().toLowerCase(Locale.ROOT);
        }
    }

    /** The outcome, the action-bar message for the user, and the ball when one was fired. */
    public record Use(Outcome outcome, Component message, @Nullable CannonballEntity ball) {
        static Use of(Outcome outcome, Object... args) {
            return new Use(outcome, Component.translatable(outcome.key(), args), null);
        }
    }

    private CannonService() {
    }

    /** The master block of the cannon half at {@code pos}; {@code pos} itself when it is no cannon. */
    public static BlockPos master(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof CannonBlock ? CannonBlock.masterPos(state, pos) : pos;
    }

    private static @Nullable CannonBlockEntity cannon(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CannonBlock
                && level.getBlockEntity(pos) instanceof CannonBlockEntity be ? be : null;
    }

    private static String seconds(long ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    private static String degrees(double deg) {
        return String.format(Locale.ROOT, "%+.0f", deg);
    }

    // ---- loading --------------------------------------------------------------------------------------------------

    /**
     * Puts the held gunpowder or shot (a cannonball, chain shot or grapeshot, CAN3) into the cannon. One item is taken
     * unless the player has infinite materials (creative). Refused in the wrong order, when already loaded, during the
     * reload cooldown, or for a shot whose server toggle is off. The shot is kept in {@link CannonBlock#SHOT}.
     */
    public static Use load(ServerLevel level, BlockPos pos, @Nullable Player player, ItemStack stack) {
        pos = master(level, pos);
        CannonBlockEntity be = cannon(level, pos);
        if (be == null) return Use.of(Outcome.NOT_A_CANNON);
        if (!CannonConfig.ENABLED.get()) return Use.of(Outcome.DISABLED);
        CannonRules.Charge charge;
        ShotKind shot = CannonBlock.shotOf(stack);
        if (CannonBlock.isPowder(stack)) {
            charge = CannonRules.Charge.POWDER;
        } else if (shot != null) {
            if (!CannonConfig.allowed(shot)) return Use.of(Outcome.SHOT_DISABLED);
            charge = CannonRules.Charge.BALL;
        } else {
            return status(level, pos, be);
        }
        BlockState state = level.getBlockState(pos);
        CannonLoad load = state.getValue(CannonBlock.LOAD);
        long left = CannonRules.reloadLeft(be.reloadUntil(), level.getGameTime());
        CannonRules.LoadOutcome result = CannonRules.load(load, charge, left > 0);
        if (!result.accepted()) {
            Outcome o = Outcome.valueOf(result.name());
            return o == Outcome.RELOADING ? Use.of(o, seconds(left)) : Use.of(o);
        }
        if (player == null || !player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        BlockState next = state.setValue(CannonBlock.LOAD, CannonRules.after(result, load));
        if (result == CannonRules.LoadOutcome.BALL_IN && shot != null) next = next.setValue(CannonBlock.SHOT, shot);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        Vec3 at = worldPoint(level, pos, Vec3.atCenterOf(pos));
        if (result == CannonRules.LoadOutcome.POWDER_IN) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
        } else {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.6f);
        }
        return Use.of(Outcome.valueOf(result.name()));
    }

    /** What the cannon needs next, for a use that loads or fires nothing. */
    private static Use status(ServerLevel level, BlockPos pos, CannonBlockEntity be) {
        long left = CannonRules.reloadLeft(be.reloadUntil(), level.getGameTime());
        return switch (level.getBlockState(pos).getValue(CannonBlock.LOAD)) {
            case EMPTY -> left > 0 ? Use.of(Outcome.RELOADING, seconds(left)) : Use.of(Outcome.NOT_LOADED);
            case POWDER -> Use.of(Outcome.ALREADY_POWDERED);
            case LOADED -> Use.of(Outcome.ALREADY_LOADED);
        };
    }

    // ---- aiming ---------------------------------------------------------------------------------------------------

    /** One elevation step up or down (clamped); the message names the new elevation. */
    public static Use aim(ServerLevel level, BlockPos pos, boolean up) {
        pos = master(level, pos);
        CannonBlockEntity be = cannon(level, pos);
        if (be == null) return Use.of(Outcome.NOT_A_CANNON);
        if (!CannonConfig.ENABLED.get()) return Use.of(Outcome.DISABLED);
        int step = CannonRules.stepElevation(be.elevationStep(), CannonConfig.ELEVATION_STEPS.get(), up);
        be.setElevationStep(step);
        Vec3 at = worldPoint(level, pos, Vec3.atCenterOf(pos));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6f, up ? 1.1f : 0.9f);
        return Use.of(Outcome.AIMED, degrees(CannonConfig.elevationDegrees(step)));
    }

    // ---- firing ---------------------------------------------------------------------------------------------------

    /** Where the ball of the cannon with its master at {@code pos} leaves from and which way, in the block (plot) frame. */
    public record Barrel(Vec3 muzzle, Vec3 direction) {
    }

    public static Barrel barrel(BlockPos pos, Direction facing, double elevationDegrees) {
        Vec3 dir = CannonRules.muzzleDirection(facing.getStepX(), facing.getStepZ(), elevationDegrees);
        return new Barrel(CannonRules.muzzle(pos.getX(), pos.getY(), pos.getZ(), dir), dir);
    }

    /** The barrel of the cannon at {@code pos} as it is aimed now, in the block (plot) frame. */
    public static @Nullable Barrel barrel(ServerLevel level, BlockPos pos) {
        pos = master(level, pos);
        CannonBlockEntity be = cannon(level, pos);
        if (be == null) return null;
        return barrel(pos, level.getBlockState(pos).getValue(CannonBlock.FACING), CannonConfig.elevationDegrees(be.elevationStep()));
    }

    /**
     * Fires a loaded cannon: one {@link CannonballEntity} (CAN3: of the loaded {@link ShotKind}; grapeshot as a cone of
     * pellets, the returned ball is the first) leaves the muzzle in world space with the ship's velocity at the muzzle
     * added, owned by {@code owner} (the firing player, for attribution; null for crew), with the shot sound,
     * smoke and a flash, and a recoil impulse on the carrying ship. The cannon is empty afterwards and the reload
     * cooldown starts. An unloaded cannon only says what it needs.
     */
    public static Use fire(ServerLevel level, BlockPos pos, @Nullable Entity owner) {
        return fire(level, pos, owner, 1.0);
    }

    /**
     * {@link #fire(ServerLevel, BlockPos, Entity)} with the ball's block damage scaled by {@code blockDamageFactor}
     * ({@link CannonRules#scaledBlocks}; WS4a: a crew firing by itself breaks {@code cannons.npc.npc_block_damage_multiplier}
     * times the blocks).
     */
    public static Use fire(ServerLevel level, BlockPos pos, @Nullable Entity owner, double blockDamageFactor) {
        pos = master(level, pos);
        CannonBlockEntity be = cannon(level, pos);
        if (be == null) return Use.of(Outcome.NOT_A_CANNON);
        if (!CannonConfig.ENABLED.get()) return Use.of(Outcome.DISABLED);
        BlockState state = level.getBlockState(pos);
        if (!CannonRules.canFire(state.getValue(CannonBlock.LOAD))) {
            return status(level, pos, be);
        }
        Barrel local = barrel(pos, state.getValue(CannonBlock.FACING), CannonConfig.elevationDegrees(be.elevationStep()));
        ShipBody ship = SableShips.containing(level, pos);
        Vec3 muzzle = local.muzzle();
        Vec3 direction = local.direction();
        Vec3 carrier = Vec3.ZERO;
        if (ship != null) {
            muzzle = ship.toWorld(local.muzzle());
            direction = rotate(ship.orientation(), local.direction());
            carrier = ship.velocityAt(local.muzzle());
        }
        ShotKind kind = state.getValue(CannonBlock.SHOT);
        CannonballEntity ball = null;
        for (Vec3 dir : shotDirections(level, kind, direction)) {
            Vec3 velocity = CannonRules.ballVelocity(dir, CannonConfig.muzzleVelocity(kind), carrier);
            CannonballEntity b = new CannonballEntity(level, muzzle, velocity, CannonConfig.entityDamage(kind),
                    CannonConfig.BALL_LIFETIME_TICKS.get());
            b.setKind(kind);
            b.setOwner(owner);
            b.setFiringShip(ship == null ? null : ship.id()); // FL2: who fired at whom
            b.setBlockDamageFactor(blockDamageFactor);
            level.addFreshEntity(b);
            if (ball == null) ball = b;
        }

        level.setBlock(pos, state.setValue(CannonBlock.LOAD, CannonLoad.EMPTY).setValue(CannonBlock.SHOT, ShotKind.BALL),
                Block.UPDATE_ALL);
        be.setReloadUntil(level.getGameTime() + CannonConfig.RELOAD_TICKS.get());

        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, CombatSounds.CANNON_SHOT.get(), SoundSource.BLOCKS,
                4.0f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        smokeAndFlash(level, muzzle, direction);
        double recoil = CannonConfig.RECOIL_IMPULSE.get();
        if (ship != null && recoil > 0) {
            ship.applyImpulseNow(Vec3.atCenterOf(pos), CannonRules.recoilImpulse(local.direction(), recoil));
        }
        return new Use(Outcome.FIRED, Component.translatable(Outcome.FIRED.key()), ball);
    }

    /**
     * The world directions the shot of {@code kind} leaves along, around the barrel's {@code direction} (CAN3): the ball
     * straight, chain shot turned off by up to {@code cannons.chain_shot.spread_degrees}, grapeshot as a cone of
     * {@code cannons.grapeshot.pellets} pellets ({@link ShotRules#cone}).
     */
    static List<Vec3> shotDirections(ServerLevel level, ShotKind kind, Vec3 direction) {
        return switch (kind) {
            case BALL -> List.of(direction.normalize());
            case CHAIN -> List.of(ShotRules.deviate(direction, CannonConfig.CHAIN_SPREAD_DEGREES.get(),
                    level.getRandom().nextDouble(), level.getRandom().nextDouble()));
            case GRAPE -> ShotRules.cone(direction, CannonConfig.GRAPE_PELLETS.get(), CannonConfig.GRAPE_SPREAD_DEGREES.get(),
                    level.getRandom().nextDouble() * 2.0 * Math.PI);
        };
    }

    /** A big puff of smoke along the shot and a short fire flash at the muzzle. */
    static void smokeAndFlash(ServerLevel level, Vec3 muzzle, Vec3 dir) {
        level.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 12, 0.1, 0.1, 0.1, 0.06);
        level.sendParticles(ParticleTypes.EXPLOSION, muzzle.x + dir.x * 0.5, muzzle.y + dir.y * 0.5, muzzle.z + dir.z * 0.5,
                1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 24, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.4, 0.2, 0.4, 0.01);
        level.sendParticles(ParticleTypes.POOF, muzzle.x, muzzle.y, muzzle.z, 16, dir.x * 0.4, dir.y * 0.4, dir.z * 0.4, 0.08);
    }

    /** A plot point in world space (unchanged off a ship), for sounds and particles. */
    static Vec3 worldPoint(ServerLevel level, BlockPos pos, Vec3 point) {
        ShipBody ship = SableShips.containing(level, pos);
        return ship == null ? point : ship.toWorld(point);
    }

    static Vec3 rotate(Quaterniond q, Vec3 v) {
        Vector3d r = q.transform(new Vector3d(v.x, v.y, v.z));
        return new Vec3(r.x, r.y, r.z);
    }

    static Vec3 rotateInverse(Quaterniond q, Vec3 v) {
        Vector3d r = q.transformInverse(new Vector3d(v.x, v.y, v.z));
        return new Vec3(r.x, r.y, r.z);
    }
}
