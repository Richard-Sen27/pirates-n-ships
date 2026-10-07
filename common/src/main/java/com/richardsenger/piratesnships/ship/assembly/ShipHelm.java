package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.HelmRules.Role;
import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The steering helm of a ship (HL1, docs/design.md §4.1; rules in {@link HelmRules}). Its plot position is kept as
 * {@code helm} in the ship pointer ({@link ShipAssembler#USER_DATA_KEY} in the sub-level's user data, saved with the
 * body). It is checked lazily: a remembered position that holds no helm any more means the ship is helmless, so a
 * broken helm needs no hook, and a ship assembled before HL1 (no {@code helm} yet) takes the first helm placed or used.
 */
public final class ShipHelm {

    public static final String TAG_HELM = "helm";
    public static final String KEY_ATTACHED = Constants.MOD_ID + ".assembly.helm_attached";
    public static final String KEY_SECOND = Constants.MOD_ID + ".assembly.second_helm";
    public static final String KEY_UNNAMED = Constants.MOD_ID + ".assembly.this_ship";

    private ShipHelm() {
    }

    /** Whether {@code ship} carries our ship pointer (it was assembled, adopted or split from one of our ships). */
    public static boolean isOurShip(ShipBody ship) {
        return ship.userData(ShipAssembler.USER_DATA_KEY).hasUUID("ship");
    }

    /** The plot position of the ship's steering helm, or null when the ship is helmless. */
    public static @Nullable BlockPos steering(ShipBody ship) {
        CompoundTag pointer = ship.userData(ShipAssembler.USER_DATA_KEY);
        if (!pointer.contains(TAG_HELM)) {
            return null;
        }
        BlockPos p = BlockPos.of(pointer.getLong(TAG_HELM));
        return isHelm(ship.level(), p) ? p : null;
    }

    /** What the helm at {@code pos} (plot) is to {@code ship}. */
    public static Role role(ShipBody ship, BlockPos pos) {
        CompoundTag pointer = ship.userData(ShipAssembler.USER_DATA_KEY);
        boolean ours = pointer.hasUUID("ship");
        BlockPos recorded = pointer.contains(TAG_HELM) ? BlockPos.of(pointer.getLong(TAG_HELM)) : null;
        return HelmRules.role(ours, recorded, recorded != null && isHelm(ship.level(), recorded), pos.immutable());
    }

    /**
     * The helm at {@code pos} is placed or used: on a helmless ship it becomes the steering helm. Returns its role as it
     * was before (so {@link Role#ATTACHES} tells that it attached just now).
     */
    public static Role claim(ShipBody ship, BlockPos pos) {
        Role role = role(ship, pos);
        if (role == Role.ATTACHES && isHelm(ship.level(), pos)) {
            setSteering(ship, pos);
        }
        return role;
    }

    /** Remembers {@code pos} (plot) as the steering helm of {@code ship}, or forgets it ({@code null}). */
    public static void setSteering(ShipBody ship, @Nullable BlockPos pos) {
        CompoundTag pointer = ship.userData(ShipAssembler.USER_DATA_KEY);
        writeHelm(pointer, pos);
        ship.setUserData(ShipAssembler.USER_DATA_KEY, pointer);
    }

    static void writeHelm(CompoundTag pointer, @Nullable BlockPos pos) {
        if (pos == null) {
            pointer.remove(TAG_HELM);
        } else {
            pointer.putLong(TAG_HELM, pos.asLong());
        }
    }

    /** The remembered helm position in a pointer tag (valid or not), or null. */
    static @Nullable BlockPos recorded(CompoundTag pointer) {
        return pointer.contains(TAG_HELM) ? BlockPos.of(pointer.getLong(TAG_HELM)) : null;
    }

    private static boolean isHelm(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof HelmBlock;
    }

    /**
     * Makes a Sable body that is not one of our ships a ship, with the helm at {@code helm} (plot) as its steering helm:
     * a new record (owner: {@code player}) and our pointer. This recovers a body that lost its record (an orphan left by
     * a split before HL1) and lets a helm turn any sub-level into a ship. Assembly's toggle applies.
     */
    public static AssemblyResult adopt(ShipBody ship, BlockPos helm, @Nullable Player player) {
        if (!AssemblyConfig.ENABLED.get()) {
            return AssemblyResult.of(AssemblyResult.Outcome.DISABLED);
        }
        ServerLevel level = ship.level();
        ShipRegistry registry = ShipRegistry.get(level.getServer());
        UUID id = ship.id();
        ShipData data = registry.find(id).orElseGet(() ->
                ShipData.create(id, Optional.ofNullable(player).map(Player::getUUID), level.dimension().location()));
        registry.put(data);
        CompoundTag pointer = ship.userData(ShipAssembler.USER_DATA_KEY);
        pointer.putUUID("ship", id);
        if (!pointer.contains("version")) {
            pointer.putInt("version", 1);
        }
        writeHelm(pointer, helm);
        ship.setUserData(ShipAssembler.USER_DATA_KEY, pointer);
        HullRuntimes.onAssembled(ship);
        return new AssemblyResult(AssemblyResult.Outcome.ADOPTED, ship.plotBlocks().size(), id, null, 0);
    }
}
