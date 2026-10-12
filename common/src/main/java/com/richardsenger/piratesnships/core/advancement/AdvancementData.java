package com.richardsenger.piratesnships.core.advancement;

import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.ItemUsedOnLocationTrigger;
import net.minecraft.advancements.critereon.KilledTrigger;
import net.minecraft.advancements.critereon.LootTableTrigger;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

/**
 * Datagen of the advancement tab (ADV1): one {@code data/pirates_n_ships/advancement/<path>.json} per
 * {@link AdvancementTree} node, encoded through vanilla's {@link Advancement#CODEC}, and the titles and descriptions.
 * The files carry no experience reward: {@link AdvancementXp} pays it, scaled by {@code advancements.experience_scale}.
 */
public final class AdvancementData {

    private AdvancementData() {
    }

    public static void gather(DataContributions data) {
        data.lang(AdvancementData::lang);
        for (AdvancementTree.Node node : AdvancementTree.nodes()) {
            data.json(PackOutput.Target.DATA_PACK, "advancement", ResourceLocation.parse(node.id()), () -> encode(build(node)));
        }
    }

    /**
     * Encodes through the built-in registries: the criteria hold built-in item and block holders, which the data run's
     * lookup (a copy) does not accept as its own.
     */
    static com.google.gson.JsonElement encode(Advancement advancement) {
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        return Advancement.CODEC.encodeStart(ops, advancement)
                .getOrThrow(msg -> new IllegalStateException("Failed to encode advancement: " + msg));
    }

    /** The lang entries: every node's title and description. */
    public static void lang(LangBuilder lang) {
        for (AdvancementTree.Node node : AdvancementTree.nodes()) {
            lang.add(node.titleKey(), node.title());
            lang.add(node.descriptionKey(), node.description());
        }
    }

    /** Builds the vanilla advancement of {@code node}. */
    public static Advancement build(AdvancementTree.Node node) {
        Advancement.Builder b = Advancement.Builder.advancement();
        if (node.parent() != null) b.parent(ResourceLocation.parse(AdvancementTree.byPath(node.parent()).orElseThrow().id()));
        b.display(new ItemStack(item(node.icon())), Component.translatable(node.titleKey()),
                Component.translatable(node.descriptionKey()),
                node.parent() == null ? ResourceLocation.parse(AdvancementTree.BACKGROUND) : null,
                type(node.frame()), true, true, node.hidden());
        for (AdvancementTree.Crit c : node.criteria()) b.addCriterion(c.name(), criterion(c));
        b.requirements(node.join() == AdvancementTree.Join.ALL
                ? AdvancementRequirements.Strategy.AND : AdvancementRequirements.Strategy.OR);
        return b.build(ResourceLocation.parse(node.id())).value();
    }

    private static Criterion<?> criterion(AdvancementTree.Crit c) {
        return switch (c.type()) {
            case MOD -> ModTriggers.get(c.trigger()).createCriterion(new ModTrigger.Instance(Optional.empty(), c.kind(),
                    c.min().isPresent() ? MinMaxBounds.Ints.atLeast(c.min().getAsInt()) : MinMaxBounds.Ints.ANY));
            case TICK -> PlayerTrigger.TriggerInstance.tick();
            case HAS_ITEMS -> InventoryChangeTrigger.TriggerInstance.hasItems(
                    ItemPredicate.Builder.item().of(c.ids().stream().map(AdvancementData::item).toArray(ItemLike[]::new)));
            case PLACED_BLOCK -> ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(block(c.ids().get(0)));
            case KILLED -> KilledTrigger.TriggerInstance.playerKilledEntity(EntityPredicate.Builder.entity()
                    .of(BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(c.ids().get(0)))
                            .orElseThrow(() -> new IllegalStateException("No entity type " + c.ids().get(0)))));
            case LOOT -> LootTableTrigger.TriggerInstance.lootTableUsed(
                    ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(c.ids().get(0))));
        };
    }

    private static AdvancementType type(AdvancementTree.Frame frame) {
        return switch (frame) {
            case TASK -> AdvancementType.TASK;
            case GOAL -> AdvancementType.GOAL;
            case CHALLENGE -> AdvancementType.CHALLENGE;
        };
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))
                .orElseThrow(() -> new IllegalStateException("Advancement icon or item " + id + " is not registered"));
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id))
                .orElseThrow(() -> new IllegalStateException("Advancement block " + id + " is not registered"));
    }
}
