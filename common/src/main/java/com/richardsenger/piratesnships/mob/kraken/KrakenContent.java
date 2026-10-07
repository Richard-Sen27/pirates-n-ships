package com.richardsenger.piratesnships.mob.kraken;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.mob.MobLoot;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipForces;
import net.minecraft.data.PackOutput;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.models.model.ModelTemplate;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Optional;

/**
 * Registration of the kraken (work package K1a): the entity, its part entity, the spawn egg and the two loot items, plus
 * its events (the spawner on the level tick, the grips on the physics substep) and its data (loot table, lang, item
 * models). Called from the {@code mob} module.
 */
public final class KrakenContent {

    public static final String ID = "kraken";

    /** 3.5 × 3.5 body; tentacles and eyes are {@link KrakenPart}s. MISC: never spawned by the natural mob spawner. */
    public static final RegistryEntry<EntityType<?>, EntityType<Kraken>> KRAKEN = ModRegistry.entity(ID,
            () -> EntityType.Builder.of(Kraken::new, MobCategory.MISC).sized(3.5f, 3.5f).eyeHeight(2.6f)
                    .clientTrackingRange(16).fireImmune());

    /** The kraken's hit boxes: not saved, not summonable, position sent every tick. */
    public static final RegistryEntry<EntityType<?>, EntityType<KrakenPart>> PART = ModRegistry.entity(ID + "_part",
            () -> EntityType.Builder.<KrakenPart>of(KrakenPart::new, MobCategory.MISC).sized(0.9f, 2.5f)
                    .noSave().noSummon().fireImmune().clientTrackingRange(16).updateInterval(1));

    /** Dark violet with sea-green spots: unlike the shark (grey/white) and the humanoids (browns, reds, navy blue). */
    public static final RegistryEntry<Item, SpawnEggItem> SPAWN_EGG = ModRegistry.item(ID + "_spawn_egg",
            () -> new SpawnEggItem(KRAKEN.get(), 0x2E1F3A, 0x5FB89A, new Item.Properties()));

    public static final RegistryEntry<Item, Item> KRAKEN_BEAK = ModRegistry.item("kraken_beak",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)));
    public static final RegistryEntry<Item, Item> KRAKEN_INK = ModRegistry.item("kraken_ink",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    public static final String KEY_DISABLED = "commands." + Constants.MOD_ID + ".mob.kraken_disabled";

    private static final ModelTemplate SPAWN_EGG_MODEL = new ModelTemplate(
            Optional.of(ResourceLocation.withDefaultNamespace("item/template_spawn_egg")), Optional.empty());

    private KrakenContent() {
    }

    /** Attributes and the force group the grips use (shared with the H1 hazards; registering twice is a no-op). */
    public static void init() {
        Services.REGISTRY.registerEntityAttributes(KRAKEN, Kraken::createAttributes);
        ShipForces.registerHazards();
    }

    public static void registerEvents() {
        CommonEvents.LEVEL_TICK_END.register(KrakenSpawner::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> KrakenShipForces.clear());
        SableShips.onPhysicsTick(KrakenShipForces::onPhysicsTick);
    }

    public static void gatherData(DataContributions data) {
        data.encoded(PackOutput.Target.DATA_PACK, "loot_table", Constants.id("entities/" + ID), LootTable.DIRECT_CODEC, MobLoot.kraken());
        data.models(m -> {
            SPAWN_EGG_MODEL.create(ModelLocationUtils.getModelLocation(SPAWN_EGG.get()), new TextureMapping(), m.models());
            // kraken_beak and kraken_ink are hand-made Blockbench item models (ART1c, art/models/)
        });
        data.lang(lang -> lang
                .add(KRAKEN.get().getDescriptionId(), "Kraken")
                .add(PART.get().getDescriptionId(), "Kraken")
                .item(SPAWN_EGG, "Kraken Spawn Egg")
                .item(KRAKEN_BEAK, "Kraken Beak")
                .item(KRAKEN_INK, "Kraken Ink")
                .add(KEY_DISABLED, "The kraken is disabled in the server config (hazards.kraken.enabled)"));
    }
}
