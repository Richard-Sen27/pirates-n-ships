package com.richardsenger.piratesnships.mob.harbor;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.trade.desk.HarborDeskService;
import com.richardsenger.piratesnships.trade.market.PortKind;

import java.util.List;

/**
 * The harbor master (work package PRT1a, docs/design.md §10.3, plan {@code docs/plans/crew-and-ports.md}): a sailor
 * in a coat and hat behind every port's harbor desk, placed by world generation ({@link HarborMasters},
 * {@link HarborMasterPosts}), kept in {@link HarborMasterRegistry} and replaced {@code respawn_days} after his death;
 * talking to him opens the desk ({@link HarborMasterInteractions}). The entity type and its config section live with
 * the other mobs ({@code MobContent.HARBOR_MASTER}, {@code mobs.harbor_master}, {@link HarborMasterConfig}).
 */
public final class HarborMasterModule implements ModModule {

    @Override
    public String id() {
        return "mob.harbor";
    }

    @Override
    public void registerEvents() {
        CommonEvents.ENTITY_INTERACT.register(HarborMasterInteractions::onEntityInteract);
        CommonEvents.SERVER_TICK_END.register(HarborMasters::onServerTick);
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .add(MobContent.HARBOR_MASTER.get().getDescriptionId(), "Harbor Master")
                .add(HarborMasterInteractions.NOT_NOW, "The harbor master waves you off: not now!")
                .add(HarborMasterInteractions.NO_DESK, "\"I've no desk to serve you at here.\"")
                .add(HarborMasterInteractions.GREETING + PortKind.SEAFARER_VILLAGE.getSerializedName(),
                        "%s: \"Welcome ashore, captain. Goods, contracts or a new ship?\"")
                .add(HarborMasterInteractions.GREETING + PortKind.NAVY_OUTPOST.getSerializedName(),
                        "%s: \"State your business, captain. The Crown's ledger is open.\"")
                .add(HarborMasterInteractions.GREETING + PortKind.PIRATE_ISLAND.getSerializedName(),
                        "%s: \"Coin first, questions never. What've you got?\"")
                .add(HarborDeskService.Use.TALK_TO_MASTER.message(), "Talk to the harbor master"));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(HarborMasterGameTests.class);
    }
}
