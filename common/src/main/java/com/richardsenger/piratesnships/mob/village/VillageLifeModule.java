package com.richardsenger.piratesnships.mob.village;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.mob.MobContent;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.trade.stall.StallKind;

import java.util.List;

/**
 * Village life, layer one (work package LIFE1, docs/design.md §10.1 "Village life"): the seafarer villages get their
 * people. Each building's resident has a {@link VillageRole} with an outfit ({@link VillagerOutfit}) and a day
 * ({@link VillageRoutine}) anchored to the building's workstations ({@link ResidentPosts}, {@link VillagePlan}); world
 * generation places them ({@link Residents}, from {@code PortStructure.afterPlace}) and fills a slot again
 * {@code respawn_days} after it was lost; vendors and shipwrights keep stalls ({@code trade.stall}); any villager can be
 * hired as crew and keeps its outfit aboard ({@link ResidentInteractions}). The entity type and its config section live
 * with the other mobs ({@code MobContent.VILLAGER}, {@code mobs.villagers}, {@link VillagerConfig}).
 */
public final class VillageLifeModule implements ModModule {

    @Override
    public String id() {
        return "mob.village";
    }

    @Override
    public void registerConfig() {
        VillagerConfig.init();
    }

    @Override
    public void registerContent() {
        VillageLifeContent.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(Residents::onServerTick);
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> VillageCommands.register(dispatcher));
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> {
            lang.add(MobContent.VILLAGER.get().getDescriptionId(), "Villager")
                    .add(VillageLifeContent.BENCH_SEAT.get().getDescriptionId(), "Bench Seat")
                    .add(ResidentInteractions.NOT_NOW, "%s has no time for you now")
                    .add(ResidentInteractions.ASLEEP, "%s is fast asleep")
                    .add(ResidentInteractions.STALL_SHUT, "%s: \"The stall's shut till morning, captain.\"")
                    .add(ResidentInteractions.NO_MARKET, "%s: \"There's no market here to sell for.\"")
                    .add(ResidentInteractions.NO_VILLAGE, "%s belongs to no port and can't sign on here")
                    .add(ResidentInteractions.HIRE_OFFER,
                            "%s: \"Sign on as your hand? I'm a %s by trade. It's %s doubloons to the purser.\" Sneak and use again to hire.")
                    .add(ResidentInteractions.GREETING + VillageRole.VILLAGER.id(), "%s: \"Fair winds, captain. Mind the nets on the quay.\"")
                    .add(ResidentInteractions.GREETING + VillageRole.FISHWIFE.id(), "%s: \"Fresh off the boats this morning! Mind your boots, the rack drips.\"")
                    .add(ResidentInteractions.GREETING + VillageRole.NET_MENDER.id(), "%s: \"Every knot a fish saved. Sharks bite through the best of them.\"")
                    .add(ResidentInteractions.GREETING + VillageRole.DOCKHAND.id(), "%s: \"Crates to the pier, barrels to the hold. Need a strong back aboard?\"")
                    .add(ResidentInteractions.GREETING + VillageRole.SHIPWRIGHT.id(), "%s: \"Every hull leaks, captain. The good ones only a little.\"")
                    .add(ResidentInteractions.GREETING + VillageRole.INNKEEPER.id(), "%s: \"Rum, a bed or a rumour, captain? The tavern has all three.\"")
                    .add(ResidentInteractions.GREETING + VillageRole.LAMPLIGHTER.id(), "%s: \"Lamps at dusk, lamps at dawn, and the beacon all night.\"")
                    .add(ResidentInteractions.GREETING + VillageRole.VENDOR.id(), "%s: \"Come back when the stall is open, captain.\"")
                    .add(ResidentInteractions.STALL_OPEN + StallKind.FISH.id(), "%s: \"Cod, fresh or salted. Best on the coast.\"")
                    .add(ResidentInteractions.STALL_OPEN + StallKind.PROVISIONS.id(), "%s: \"Salt pork, hardtack and rum: stores for a long passage.\"")
                    .add(ResidentInteractions.STALL_OPEN + StallKind.CHANDLERY.id(), "%s: \"Rope, sailcloth, nails. A chandler's whole trade.\"")
                    .add(ResidentInteractions.STALL_OPEN + StallKind.SHIPWRIGHT.id(), "%s: \"Patches for your hull, or a toolkit to fit them yourself.\"")
                    .add(VillageCommands.SPAWNED, "Spawned %s, a %s")
                    .add(VillageCommands.UNKNOWN, "Unknown role: %s")
                    .add(VillageCommands.DISABLED, "Villagers are switched off (mobs.villagers.enabled)")
                    .add(VillageCommands.RESPAWNED, "%s villagers moved in");
            lang.add(ResidentInteractions.roleKey(VillageRole.VILLAGER), "villager")
                    .add(ResidentInteractions.roleKey(VillageRole.FISHWIFE), "fishwife")
                    .add(ResidentInteractions.roleKey(VillageRole.NET_MENDER), "net mender")
                    .add(ResidentInteractions.roleKey(VillageRole.DOCKHAND), "dockhand")
                    .add(ResidentInteractions.roleKey(VillageRole.SHIPWRIGHT), "shipwright")
                    .add(ResidentInteractions.roleKey(VillageRole.INNKEEPER), "innkeeper")
                    .add(ResidentInteractions.roleKey(VillageRole.LAMPLIGHTER), "lamplighter")
                    .add(ResidentInteractions.roleKey(VillageRole.VENDOR), "vendor");
        });
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.mob.village.client.VillageLifeClient.init();
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(VillageLifeGameTests.class);
    }
}
