package com.richardsenger.piratesnships.combat.cannon;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.content.CombatContent;
import com.richardsenger.piratesnships.combat.content.CombatSounds;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Server side of the swivel gun (docs/design.md §8.2, P2): loading, aiming with the view, firing on release, on land or
 * on a ship. Called by {@link SwivelGunBlock}, {@link SwivelReleasePayload} and {@link SwivelStation}. The aim is
 * computed by {@link SwivelRules}; loading follows {@link CannonRules#load}.
 *
 * <p>Aiming: a use with an empty hand makes the player the gun's operator and aims it at once; every server tick the
 * gun then follows the operator's view (the look direction turned into the ship's frame on a ship), until the client
 * reports the release ({@link #release}: fire when loaded) or the operator leaves ({@code cannons.swivel.aim_reach}),
 * dies or logs out. Firing works like the cannon's ({@link CannonService#fire}): the shot leaves the world-space muzzle
 * with the ship's velocity at that point added and pushes the ship back.
 */
public final class SwivelService {

    public static final String KEY_PREFIX = "message." + Constants.MOD_ID + ".swivel.";

    /** What a use of the swivel gun did. */
    public enum Outcome {
        POWDER_IN, SHOT_IN, NEEDS_POWDER_FIRST, ALREADY_POWDERED, ALREADY_LOADED, RELOADING, NOT_ENOUGH_AMMO,
        AIMING, AIMING_UNLOADED, OCCUPIED, FIRED, NOT_LOADED, DISABLED, NOT_A_SWIVEL;

        public String key() {
            return KEY_PREFIX + name().toLowerCase(Locale.ROOT);
        }
    }

    /** The outcome, the action-bar message for the user, and the shot when one was fired. */
    public record Use(Outcome outcome, Component message, @Nullable CannonballEntity ball) {
        static Use of(Outcome outcome, Object... args) {
            return new Use(outcome, Component.translatable(outcome.key(), args), null);
        }
    }

    private SwivelService() {
    }

    private static @Nullable SwivelGunBlockEntity swivel(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof SwivelGunBlock
                && level.getBlockEntity(pos) instanceof SwivelGunBlockEntity be ? be : null;
    }

    /** The configured ammo item. */
    public static Item ammoItem() {
        return switch (CannonConfig.SWIVEL_AMMO.get()) {
            case LEAD_SHOT -> CombatContent.LEAD_SHOT.get();
            case CANNONBALL -> CombatContent.CANNONBALL.get();
        };
    }

    /**
     * What a broken gun with the load {@code load} gives back (Q2): one gunpowder once powdered and, when loaded, the
     * shot recorded in {@code be} (or the configured ammo for a gun loaded before the shot was recorded).
     */
    static List<ItemStack> loadContents(CannonLoad load, @Nullable SwivelGunBlockEntity be) {
        List<ItemStack> out = new ArrayList<>();
        if (load == CannonLoad.EMPTY) return out;
        out.add(new ItemStack(Items.GUNPOWDER));
        if (load == CannonLoad.LOADED) {
            ItemStack shot = be != null ? be.shot() : ItemStack.EMPTY;
            out.add(shot.isEmpty() ? new ItemStack(ammoItem(), CannonConfig.SWIVEL_AMMO_COUNT.get()) : shot.copy());
        }
        return out;
    }

    public static boolean isAmmo(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ammoItem());
    }

    private static String seconds(long ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    // ---- loading --------------------------------------------------------------------------------------------------

    /**
     * Puts the held gunpowder or ammo into the gun: one powder, then {@code cannons.swivel.ammo_count} of the ammo item
     * (nothing is taken from a player with infinite materials). Refused in the wrong order, when already loaded, during
     * the reload cooldown, or with too few ammo items in the stack.
     */
    public static Use load(ServerLevel level, BlockPos pos, @Nullable Player player, ItemStack stack) {
        SwivelGunBlockEntity be = swivel(level, pos);
        if (be == null) return Use.of(Outcome.NOT_A_SWIVEL);
        if (!CannonConfig.SWIVEL_ENABLED.get()) return Use.of(Outcome.DISABLED);
        CannonRules.Charge charge;
        int count;
        if (CannonBlock.isPowder(stack)) {
            charge = CannonRules.Charge.POWDER;
            count = 1;
        } else if (isAmmo(stack)) {
            charge = CannonRules.Charge.BALL;
            count = CannonConfig.SWIVEL_AMMO_COUNT.get();
        } else {
            return status(level, pos);
        }
        BlockState state = level.getBlockState(pos);
        CannonLoad load = state.getValue(SwivelGunBlock.LOAD);
        long left = CannonRules.reloadLeft(be.reloadUntil(), level.getGameTime());
        CannonRules.LoadOutcome result = CannonRules.load(load, charge, left > 0);
        if (!result.accepted()) {
            return switch (result) {
                case RELOADING -> Use.of(Outcome.RELOADING, seconds(left));
                case NEEDS_POWDER_FIRST -> Use.of(Outcome.NEEDS_POWDER_FIRST);
                case ALREADY_POWDERED -> Use.of(Outcome.ALREADY_POWDERED);
                default -> Use.of(Outcome.ALREADY_LOADED);
            };
        }
        boolean free = player != null && player.hasInfiniteMaterials();
        if (!free && stack.getCount() < count) {
            return Use.of(Outcome.NOT_ENOUGH_AMMO, count, stack.getHoverName());
        }
        if (charge == CannonRules.Charge.BALL) {
            be.setShot(stack.copyWithCount(count)); // given back when the gun is broken loaded
        }
        if (!free) {
            stack.shrink(count);
        }
        level.setBlock(pos, state.setValue(SwivelGunBlock.LOAD, CannonRules.after(result, load)), Block.UPDATE_ALL);
        Vec3 at = CannonService.worldPoint(level, pos, Vec3.atCenterOf(pos));
        if (result == CannonRules.LoadOutcome.POWDER_IN) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.6f, 1.4f);
            return Use.of(Outcome.POWDER_IN);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.5f, 1.2f);
        return Use.of(Outcome.SHOT_IN);
    }

    /** What the gun needs next. */
    public static Use status(ServerLevel level, BlockPos pos) {
        SwivelGunBlockEntity be = swivel(level, pos);
        if (be == null) return Use.of(Outcome.NOT_A_SWIVEL);
        if (!CannonConfig.SWIVEL_ENABLED.get()) return Use.of(Outcome.DISABLED);
        long left = CannonRules.reloadLeft(be.reloadUntil(), level.getGameTime());
        return switch (level.getBlockState(pos).getValue(SwivelGunBlock.LOAD)) {
            case EMPTY -> left > 0 ? Use.of(Outcome.RELOADING, seconds(left)) : Use.of(Outcome.NOT_LOADED);
            case POWDER -> Use.of(Outcome.ALREADY_POWDERED);
            case LOADED -> Use.of(Outcome.ALREADY_LOADED);
        };
    }

    // ---- aiming ---------------------------------------------------------------------------------------------------

    /**
     * The aim along {@code viewer}'s look direction in the gun's block frame: on a ship the world look is turned back
     * by the ship's orientation, so the gun follows the view however the hull lies. Elevation is clamped to the
     * configured limits.
     */
    public static SwivelRules.Aim aimFor(ServerLevel level, BlockPos pos, SwivelGunBlockEntity be, Entity viewer) {
        // the camera's rotation (a living entity's view vector uses the head yaw, which lags behind on the server)
        Vec3 look = SwivelRules.lookVector(viewer.getYRot(), viewer.getXRot());
        ShipBody ship = SableShips.containing(level, pos);
        Vec3 local = ship == null ? look : CannonService.rotateInverse(ship.orientation(), look);
        return SwivelRules.aimFromLook(local, be.yaw(), CannonConfig.SWIVEL_MIN_ELEVATION.get(),
                CannonConfig.SWIVEL_MAX_ELEVATION.get());
    }

    /**
     * {@code player} takes the gun (a use with an empty hand): it becomes the operator and the gun turns to its view at
     * once. Refused while another player aims it.
     */
    public static Use startAim(ServerLevel level, BlockPos pos, Player player) {
        SwivelGunBlockEntity be = swivel(level, pos);
        if (be == null) return Use.of(Outcome.NOT_A_SWIVEL);
        if (!CannonConfig.SWIVEL_ENABLED.get()) return Use.of(Outcome.DISABLED);
        UUID current = be.operator();
        if (current != null && !current.equals(player.getUUID()) && level.getPlayerByUUID(current) != null) {
            return Use.of(Outcome.OCCUPIED);
        }
        be.setOperator(player.getUUID());
        be.setAim(aimFor(level, pos, be, player));
        return CannonRules.canFire(level.getBlockState(pos).getValue(SwivelGunBlock.LOAD))
                ? Use.of(Outcome.AIMING) : Use.of(Outcome.AIMING_UNLOADED);
    }

    /**
     * {@code player} lets go of the gun (the client's release of the use button): fires when loaded. Ignored (null)
     * when the player does not aim this gun.
     */
    public static @Nullable Use release(ServerLevel level, BlockPos pos, Player player) {
        SwivelGunBlockEntity be = swivel(level, pos);
        if (be == null || !player.getUUID().equals(be.operator())) return null;
        be.setOperator(null);
        if (!CannonRules.canFire(level.getBlockState(pos).getValue(SwivelGunBlock.LOAD))) return status(level, pos);
        return fire(level, pos, player);
    }

    /** Every server tick: the gun follows its operator's view, or lets go of an operator that left. */
    static void serverTick(Level level, BlockPos pos, BlockState state, SwivelGunBlockEntity be) {
        UUID id = be.operator();
        if (id == null || !(level instanceof ServerLevel server)) return;
        Player p = server.getPlayerByUUID(id);
        Vec3 at = CannonService.worldPoint(server, pos, Vec3.atCenterOf(pos));
        double reach = CannonConfig.SWIVEL_AIM_REACH.get();
        if (p == null || !p.isAlive() || p.isSpectator() || p.position().add(0, p.getEyeHeight(), 0).distanceToSqr(at) > reach * reach
                || !CannonConfig.SWIVEL_ENABLED.get()) {
            be.setOperator(null);
            return;
        }
        be.setAim(aimFor(server, pos, be, p));
    }

    // ---- firing ---------------------------------------------------------------------------------------------------

    /** Where the shot of the gun at {@code pos} leaves from and which way, in the block (plot) frame. */
    public static CannonService.Barrel barrel(BlockPos pos, SwivelRules.Aim aim) {
        return new CannonService.Barrel(SwivelRules.muzzle(pos.getX(), pos.getY(), pos.getZ(), aim.yawDegrees(), aim.elevationDegrees()),
                SwivelRules.direction(aim.yawDegrees(), aim.elevationDegrees()));
    }

    /**
     * Fires a loaded swivel gun along its aim: one shot ({@link CannonballEntity} with the swivel's damage, block
     * damage and impulse, drawn as the ammo item) leaves the world-space muzzle with the ship's velocity at the muzzle
     * added, owned by {@code owner} (null for crew), with sound and smoke and a small recoil impulse on the carrying
     * ship. The gun is empty afterwards and the reload starts. An unloaded gun only says what it needs.
     */
    public static Use fire(ServerLevel level, BlockPos pos, @Nullable Entity owner) {
        SwivelGunBlockEntity be = swivel(level, pos);
        if (be == null) return Use.of(Outcome.NOT_A_SWIVEL);
        if (!CannonConfig.SWIVEL_ENABLED.get()) return Use.of(Outcome.DISABLED);
        BlockState state = level.getBlockState(pos);
        if (!CannonRules.canFire(state.getValue(SwivelGunBlock.LOAD))) return status(level, pos);

        CannonService.Barrel local = barrel(pos, be.aim());
        ShipBody ship = SableShips.containing(level, pos);
        Vec3 muzzle = local.muzzle();
        Vec3 direction = local.direction();
        Vec3 carrier = Vec3.ZERO;
        if (ship != null) {
            muzzle = ship.toWorld(local.muzzle());
            direction = CannonService.rotate(ship.orientation(), local.direction());
            carrier = ship.velocityAt(local.muzzle());
        }
        Vec3 velocity = CannonRules.ballVelocity(direction, CannonConfig.SWIVEL_VELOCITY.get(), carrier);
        CannonballEntity ball = new CannonballEntity(level, muzzle, velocity, CannonConfig.swivelDamage(),
                CannonConfig.SWIVEL_BALL_LIFETIME_TICKS.get(), CannonConfig.swivelBlocksPerHit(),
                CannonConfig.SWIVEL_IMPACT_IMPULSE.get());
        ball.setItem(new ItemStack(ammoItem()));
        ball.setOwner(owner);
        level.addFreshEntity(ball);

        level.setBlock(pos, state.setValue(SwivelGunBlock.LOAD, CannonLoad.EMPTY), Block.UPDATE_ALL);
        be.setShot(ItemStack.EMPTY);
        be.setReloadUntil(level.getGameTime() + CannonConfig.SWIVEL_RELOAD_TICKS.get());

        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, CombatSounds.CANNON_SHOT.get(), SoundSource.BLOCKS,
                2.0f, 1.4f + level.getRandom().nextFloat() * 0.2f);
        CannonService.smokeAndFlash(level, muzzle, direction);
        double recoil = CannonConfig.SWIVEL_RECOIL_IMPULSE.get();
        if (ship != null && recoil > 0) {
            ship.applyImpulseNow(Vec3.atCenterOf(pos), CannonRules.recoilImpulse(local.direction(), recoil));
        }
        return new Use(Outcome.FIRED, Component.translatable(Outcome.FIRED.key()), ball);
    }
}
