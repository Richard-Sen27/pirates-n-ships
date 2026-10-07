package com.richardsenger.piratesnships.ship.decor.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import com.richardsenger.piratesnships.ship.decor.flag.FlagTint;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleBlockEntity;

/** Client setup of the {@code ship.decor} module (physical client only, from {@code ShipDecorModule.initClient()}). */
public final class ShipDecorClient {

    private ShipDecorClient() {
    }

    public static void init() {
        // The ship's name on the nameplate's board (G8)
        ClientEvents.registerBlockEntityRenderer(ShipDecor.NAMEPLATE_BLOCK_ENTITY, NameplateRenderer::new);
        // Custom banner flags: the cloth's tinted faces take the banner's base colour from the flagpole's block entity
        ClientEvents.registerBlockColor((state, level, pos, tintIndex) ->
                tintIndex == FlagTint.TINT_INDEX && level != null && pos != null
                        && level.getBlockEntity(pos) instanceof FlagpoleBlockEntity be ? be.clothTint() : FlagTint.NONE,
                ShipDecor.FLAGPOLE);
    }
}
