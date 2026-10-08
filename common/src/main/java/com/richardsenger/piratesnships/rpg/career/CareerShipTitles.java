package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * The captain's title on the ship (docs/design.md §15, HON1): the name stored when a player names a ship with a name
 * tag at the helm ({@code HelmBlock.useItemOn} calls {@link #forNaming(Player, ShipBody, String)} before
 * {@code ShipAssembler.name}). The nameplate and the ship HUD show the stored name, so they show the title too.
 *
 * <p>Rule, with {@code careers.title_on_ship} and {@code careers.enabled} on: any title typed into the name tag is
 * removed ({@link CareerTitles#stripTitle}), then the namer's own title goes in front when the namer owns the ship
 * (or it has no owner). The title is fixed at naming: after a promotion the owner renames the ship (any name tag,
 * with or without the old title) and the new title replaces the old one. With the toggle off the typed name is
 * stored unchanged.
 */
public final class CareerShipTitles {

    private CareerShipTitles() {
    }

    public static boolean enabled() {
        return CareerConfig.TITLE_ON_SHIP.get() && CareerConfig.ENABLED.get();
    }

    /** The name to store when {@code player} names the ship {@code ship} {@code typed}. */
    public static String forNaming(Player player, ShipBody ship, String typed) {
        if (!enabled() || player.getServer() == null) return typed;
        Optional<UUID> owner = ShipRegistry.get(player.getServer()).find(ship.id()).flatMap(ShipData::owner);
        return forNaming(player, owner, typed);
    }

    /** {@link #forNaming(Player, ShipBody, String)} for a ship owned by {@code owner} (empty: nobody). */
    public static String forNaming(Player player, Optional<UUID> owner, String typed) {
        if (!enabled()) return typed;
        boolean captain = owner.isEmpty() || owner.get().equals(player.getUUID());
        return CareerTitles.shipName(captain ? CareerTitles.of(Careers.record(player)) : Optional.empty(), typed);
    }
}
