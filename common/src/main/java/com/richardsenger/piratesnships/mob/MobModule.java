package com.richardsenger.piratesnships.mob;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.law.world.LawTags;
import com.richardsenger.piratesnships.mob.kraken.KrakenContent;
import com.richardsenger.piratesnships.mob.kraken.KrakenGameTests;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.data.PackOutput;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.models.model.ModelTemplate;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;
import java.util.Optional;

/**
 * The humanoid mobs (work package M3, docs/design.md §9, §8.5 "NPC duelists"): pirate, sailor, navy soldier and navy
 * officer on the shared GeckoLib rig, with hostility rules, sword duelists through the melee engine, musketeers
 * through the firearm service, law crimes through {@code #pirates_n_ships:navy}, loot tables and
 * {@code /pirates mob spawn}. Since M4 also the shark (docs/design.md §12): a water creature that spawns naturally in
 * ocean biomes and hunts swimmers. Since K1a also the kraken ({@code mob.kraken}), a rare boss of the deep ocean.
 */
public final class MobModule implements ModModule {

    private static final ModelTemplate SPAWN_EGG = new ModelTemplate(
            Optional.of(ResourceLocation.withDefaultNamespace("item/template_spawn_egg")), Optional.empty());

    @Override
    public String id() {
        return "mob";
    }

    @Override
    public void registerConfig() {
        MobConfig.init();
    }

    @Override
    public void registerContent() {
        MobContent.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> MobCommands.register(dispatcher));
        KrakenContent.registerEvents();
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.mob.client.MobClient.init();
    }

    @Override
    public void gatherData(DataContributions data) {
        // the law keys its navy crimes (attack_navy, kill_navy) and navy hostility on this tag
        data.entityTypeTags(tags -> tags.tag(LawTags.NAVY).add(MobContent.NAVY_SOLDIER.get(), MobContent.NAVY_OFFICER.get()));
        data.models(m -> MobContent.eggs().values().forEach(egg ->
                SPAWN_EGG.create(ModelLocationUtils.getModelLocation(egg.get()), new TextureMapping(), m.models())));
        data.models(m -> SPAWN_EGG.create(ModelLocationUtils.getModelLocation(MobContent.SHARK_SPAWN_EGG.get()), new TextureMapping(), m.models()));
        lootTable(data, MobKind.PIRATE, MobLoot.pirate());
        lootTable(data, MobKind.NAVY_SOLDIER, MobLoot.navy());
        lootTable(data, MobKind.NAVY_OFFICER, MobLoot.navy());
        lootTable(data, MobKind.PIRATE_CAPTAIN, MobLoot.pirateCaptain());
        data.encoded(PackOutput.Target.DATA_PACK, "loot_table", Constants.id("entities/" + MobCommands.SHARK), LootTable.DIRECT_CODEC, MobLoot.shark());
        KrakenContent.gatherData(data);
        data.lang(lang -> {
            lang.add(MobContent.PIRATE.get().getDescriptionId(), "Pirate")
                    .add(MobContent.SAILOR.get().getDescriptionId(), "Sailor")
                    .add(MobContent.NAVY_SOLDIER.get().getDescriptionId(), "Navy Soldier")
                    .add(MobContent.NAVY_OFFICER.get().getDescriptionId(), "Navy Officer")
                    .add(MobContent.PIRATE_CAPTAIN.get().getDescriptionId(), "Pirate Captain")
                    .item(MobContent.PIRATE_SPAWN_EGG, "Pirate Spawn Egg")
                    .item(MobContent.SAILOR_SPAWN_EGG, "Sailor Spawn Egg")
                    .item(MobContent.NAVY_SOLDIER_SPAWN_EGG, "Navy Soldier Spawn Egg")
                    .item(MobContent.NAVY_OFFICER_SPAWN_EGG, "Navy Officer Spawn Egg")
                    .add(MobContent.SHARK.get().getDescriptionId(), "Shark")
                    .item(MobContent.SHARK_SPAWN_EGG, "Shark Spawn Egg")
                    .add(MobCommands.KEY_SPAWNED, "Spawned %s × %s")
                    .add(MobCommands.KEY_UNKNOWN, "Unknown mob: %s (pirate, sailor, navy_soldier, navy_officer, pirate_captain, shark or kraken)")
                    .add(MobCommands.KEY_DISABLED, "%s is disabled in the server config (mobs)");
        });
    }

    private static void lootTable(DataContributions data, MobKind kind, LootTable table) {
        data.encoded(PackOutput.Target.DATA_PACK, "loot_table", Constants.id("entities/" + kind.id()), LootTable.DIRECT_CODEC, table);
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(MobGameTests.class, SharkGameTests.class, KrakenGameTests.class);
    }
}
