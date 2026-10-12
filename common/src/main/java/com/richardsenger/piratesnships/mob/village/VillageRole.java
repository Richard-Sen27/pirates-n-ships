package com.richardsenger.piratesnships.mob.village;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.trade.stall.StallKind;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * What a villager of a seafarer village does for a living (LIFE1, docs/design.md §10.1 "Village life"): the role picks
 * the outfit ({@link VillagerOutfit}), the workstations of the building it belongs to ({@link ResidentPosts}) and the
 * day it keeps ({@link VillageRoutine}). Pure: no world access.
 *
 * <ul>
 *   <li>{@link #VILLAGER}: the plain villager of a house (the four houses), at home at its bench and in the lanes;</li>
 *   <li>{@link #FISHWIFE}: the fisher's cottage; guts the catch at the drying rack in her yard, then helps at the fish
 *       market;</li>
 *   <li>{@link #NET_MENDER}: the net loft; mends the nets drying under it;</li>
 *   <li>{@link #DOCKHAND}: the warehouse; shifts its crates, then the cargo on the pier;</li>
 *   <li>{@link #SHIPWRIGHT}: the shipwright's shed or the slipway; sells hull patches and the toolkit;</li>
 *   <li>{@link #INNKEEPER}: the tavern or the inn; keeps the bar from mid-morning to late at night;</li>
 *   <li>{@link #LAMPLIGHTER}: the beacon tower; walks the lamp posts with a lantern at dusk and at dawn;</li>
 *   <li>{@link #VENDOR}: a stall of the fish market or the market square; sells and buys its wares.</li>
 * </ul>
 */
public enum VillageRole implements StringRepresentable {
    VILLAGER,
    FISHWIFE,
    NET_MENDER,
    DOCKHAND,
    SHIPWRIGHT,
    INNKEEPER,
    LAMPLIGHTER,
    VENDOR;

    public static final Codec<VillageRole> CODEC = StringRepresentable.fromEnum(VillageRole::values);

    /** The role's id: {@code fishwife}, {@code net_mender}, ... (config, commands, lang keys, texture names). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    /**
     * The entity texture of the role ({@code textures/entity/<name>.png}, on the sailor's geometry): {@code villager}
     * for the plain villager, {@code villager_<role>} for the others.
     */
    public String textureName() {
        return this == VILLAGER ? "villager" : "villager_" + id();
    }

    /** The stall a villager of this role keeps when the plan gives none ({@code null}: the role keeps no stall). */
    public @Nullable StallKind defaultStall() {
        return this == SHIPWRIGHT ? StallKind.SHIPWRIGHT : null;
    }

    /** The role with the synced id {@code ordinal}; {@link #VILLAGER} for an unknown one. */
    public static VillageRole byOrdinal(int ordinal) {
        VillageRole[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : VILLAGER;
    }

    public static Optional<VillageRole> byId(String id) {
        for (VillageRole r : values()) if (r.id().equals(id)) return Optional.of(r);
        return Optional.empty();
    }
}
