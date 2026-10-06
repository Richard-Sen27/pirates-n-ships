package com.richardsenger.piratesnships.core.datagen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.CoreDefinitions;
import com.richardsenger.piratesnships.core.CoreDefinitions.TestMarker;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs our datagen providers into a temp folder with vanilla registries (no loader). */
class DatagenPathTest {

    private static CompletableFuture<HolderLookup.Provider> lookup;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        lookup = CompletableFuture.completedFuture(VanillaRegistries.createLookup());
    }

    /** Writes files directly, no hash cache. */
    private static final CachedOutput DIRECT = (path, data, hash) -> {
        Files.createDirectories(path.getParent());
        Files.write(path, data);
    };

    private static JsonObject read(Path file) throws IOException {
        assertTrue(Files.exists(file), "missing " + file);
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    @Test
    void rawJsonAndDefinitionsLandInTheRightFolders(@TempDir Path out) throws IOException {
        DataContributions data = new DataContributions();
        data.json(PackOutput.Target.DATA_PACK, "physics_block_properties", Constants.id("plank"), () -> {
            JsonObject o = new JsonObject();
            o.addProperty("selector", "#minecraft:planks");
            return o;
        });
        data.json(PackOutput.Target.RESOURCE_PACK, "misc", ResourceLocation.fromNamespaceAndPath("sable", "x"), JsonObject::new);
        data.definition(CoreDefinitions.TEST_MARKER, Constants.id("example"), new TestMarker("Example", 3));

        PackOutput output = new PackOutput(out);
        new JsonOutputs.Provider(output, PackOutput.Target.DATA_PACK, data.json, lookup).run(DIRECT).join();
        new JsonOutputs.Provider(output, PackOutput.Target.RESOURCE_PACK, data.json, lookup).run(DIRECT).join();

        assertEquals("#minecraft:planks", read(out.resolve("data/pirates_n_ships/physics_block_properties/plank.json")).get("selector").getAsString());
        assertTrue(Files.exists(out.resolve("assets/sable/misc/x.json")));
        JsonObject def = read(out.resolve("data/pirates_n_ships/pirates_n_ships/test_marker/example.json"));
        assertEquals("Example", def.get("label").getAsString());
        assertEquals(3, def.get("weight").getAsInt());
        assertTrue(Files.notExists(out.resolve("assets/pirates_n_ships/physics_block_properties")), "targets are separate");
    }

    @Test
    void duplicateOutputPathsFail() {
        DataContributions data = new DataContributions();
        data.definition(CoreDefinitions.TEST_MARKER, Constants.id("dup"), new TestMarker("a", 1));
        var e = assertThrows(IllegalStateException.class,
                () -> data.definition(CoreDefinitions.TEST_MARKER, Constants.id("dup"), new TestMarker("b", 1)));
        assertTrue(e.getMessage().contains("data/pirates_n_ships/pirates_n_ships/test_marker/dup.json"), e.getMessage());
        // Same id in a different target or directory is fine
        data.json(PackOutput.Target.RESOURCE_PACK, CoreDefinitions.TEST_MARKER.directory(), Constants.id("dup"), JsonObject::new);
        data.json(PackOutput.Target.DATA_PACK, "other", Constants.id("dup"), JsonObject::new);
        assertThrows(IllegalStateException.class, () -> data.json(PackOutput.Target.DATA_PACK, "other", Constants.id("dup"), JsonObject::new));
    }

    @Test
    void entityTagsInForeignNamespacesWithTagRefsAndOptionals(@TempDir Path out) throws IOException {
        TagKey<EntityType<?>> retain = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("sable", "retain_in_sub_level"));
        TagKey<EntityType<?>> ours = TagKey.create(Registries.ENTITY_TYPE, Constants.id("crew"));
        DataContributions data = new DataContributions();
        data.entityTypeTags(tags -> {
            tags.tag(ours).add(EntityType.VILLAGER);
            tags.tag(retain).add(EntityType.PIG).addTag(ours);
            tags.addOptional(retain, ResourceLocation.fromNamespaceAndPath("other_mod", "parrot"))
                    .addOptionalTag(retain, ResourceLocation.fromNamespaceAndPath("c", "bosses"));
        });
        runTags(out, data);

        JsonObject json = read(out.resolve("data/sable/tags/entity_type/retain_in_sub_level.json"));
        assertTrue(json.get("replace") == null || !json.get("replace").getAsBoolean(), "must merge, not replace");
        String values = json.get("values").toString();
        assertTrue(values.contains("\"minecraft:pig\""), values);
        assertTrue(values.contains("\"#pirates_n_ships:crew\""), values);
        assertTrue(values.contains("other_mod:parrot") && values.contains("#c:bosses") && values.contains("\"required\":false"), values);
        assertTrue(Files.exists(out.resolve("data/pirates_n_ships/tags/entity_type/crew.json")));
    }

    @Test
    void genericTagsAndBlockTagsInForeignNamespace(@TempDir Path out) throws IOException {
        DataContributions data = new DataContributions();
        data.blockTags(tags -> tags.tag(TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("sable", "heavy"))).add(Blocks.ANVIL));
        data.tags(Registries.BLOCK, tags -> tags.tag(TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "storage_blocks/test"))).add(Blocks.IRON_BLOCK));
        runTags(out, data);
        assertTrue(read(out.resolve("data/sable/tags/block/heavy.json")).get("values").toString().contains("minecraft:anvil"));
        assertTrue(Files.exists(out.resolve("data/c/tags/block/storage_blocks/test.json")));
    }

    @Test
    void requiredReferenceToUnknownTagFails(@TempDir Path out) {
        DataContributions data = new DataContributions();
        data.blockTags(tags -> tags.tag(TagKey.create(Registries.BLOCK, Constants.id("x")))
                .addTag(TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("sable", "not_defined_here"))));
        var e = assertThrows(CompletionException.class, () -> runTags(out, data));
        assertTrue(String.valueOf(e.getCause().getMessage()).contains("missing following references"), String.valueOf(e.getCause()));
    }

    @Test
    void vanillaTagsAreKnown() {
        assertTrue(VanillaTags.exists(BlockTags.DIRT));
        assertTrue(VanillaTags.exists(ItemTags.PLANKS));
        assertTrue(VanillaTags.exists(EntityTypeTags.SKELETONS));
        assertTrue(VanillaTags.exists(FluidTags.WATER));
        assertTrue(VanillaTags.exists(BlockTags.MINEABLE_WITH_AXE), "nested paths");
        assertFalse(VanillaTags.exists(TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("dirtt"))));
        assertFalse(VanillaTags.exists(TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("mineable/axe"))), "registry matters");
        assertFalse(VanillaTags.exists(TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"))), "other namespaces are unknown");
    }

    @Test
    void requiredVanillaTagReferencesAreWrittenPlainForEveryRegistry(@TempDir Path out) throws IOException {
        TagKey<Block> blocks = TagKey.create(Registries.BLOCK, Constants.id("junit_ground"));
        TagKey<Item> items = TagKey.create(Registries.ITEM, Constants.id("junit_wood"));
        TagKey<EntityType<?>> entities = TagKey.create(Registries.ENTITY_TYPE, Constants.id("junit_undead"));
        TagKey<Fluid> fluids = TagKey.create(Registries.FLUID, Constants.id("junit_sea"));
        DataContributions data = new DataContributions();
        data.blockTags(tags -> tags.tag(blocks).add(Blocks.CLAY).addTag(BlockTags.DIRT)
                .addOptionalTag(ResourceLocation.fromNamespaceAndPath("c", "sands")));
        data.itemTags(tags -> tags.tag(items).addTag(ItemTags.PLANKS).addOptional(ResourceLocation.fromNamespaceAndPath("other_mod", "plank")));
        data.entityTypeTags(tags -> tags.tag(entities).addTag(EntityTypeTags.SKELETONS));
        data.tags(Registries.FLUID, tags -> tags.tag(fluids).addTag(FluidTags.WATER));
        runTags(out, data);

        String block = read(out.resolve("data/pirates_n_ships/tags/block/junit_ground.json")).get("values").toString();
        assertTrue(block.contains("\"minecraft:clay\""), block);
        assertTrue(block.contains("\"#minecraft:dirt\""), "required reference is a plain string: " + block);
        assertTrue(block.contains("{\"id\":\"#c:sands\",\"required\":false}"), "optional reference: " + block);
        String item = read(out.resolve("data/pirates_n_ships/tags/item/junit_wood.json")).get("values").toString();
        assertTrue(item.contains("\"#minecraft:planks\""), item);
        assertTrue(item.contains("{\"id\":\"other_mod:plank\",\"required\":false}"), item);
        assertTrue(read(out.resolve("data/pirates_n_ships/tags/entity_type/junit_undead.json")).get("values").toString()
                .contains("\"#minecraft:skeletons\""));
        assertTrue(read(out.resolve("data/pirates_n_ships/tags/fluid/junit_sea.json")).get("values").toString()
                .contains("\"#minecraft:water\""));
    }

    @Test
    void requiredReferenceToMisspelledVanillaTagFails(@TempDir Path out) {
        DataContributions data = new DataContributions();
        data.blockTags(tags -> tags.tag(TagKey.create(Registries.BLOCK, Constants.id("typo")))
                .addTag(TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("dirtt"))));
        var e = assertThrows(CompletionException.class, () -> runTags(out, data));
        String message = String.valueOf(e.getCause().getMessage());
        assertTrue(message.contains("missing following references") && message.contains("#minecraft:dirtt"), message);
    }

    @Test
    void requiredReferenceToOtherModsTagFailsButOptionalPasses(@TempDir Path out) {
        ResourceLocation cOres = ResourceLocation.fromNamespaceAndPath("c", "ores");
        DataContributions required = new DataContributions();
        required.blockTags(tags -> tags.tag(TagKey.create(Registries.BLOCK, Constants.id("ores"))).addTag(TagKey.create(Registries.BLOCK, cOres)));
        assertThrows(CompletionException.class, () -> runTags(out, required));

        DataContributions optional = new DataContributions();
        optional.blockTags(tags -> tags.addOptionalTag(TagKey.create(Registries.BLOCK, Constants.id("ores")), cOres));
        runTags(out, optional);
        assertTrue(Files.exists(out.resolve("data/pirates_n_ships/tags/block/ores.json")));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void runTags(Path out, DataContributions data) {
        PackOutput output = new PackOutput(out);
        data.tags.forEach((registry, contributors) ->
                ModTagsProvider.forBuiltIn(output, (net.minecraft.resources.ResourceKey) registry, lookup, (List<Consumer<ModTagsProvider<Block>>>) (List) contributors)
                        .run(DIRECT).join());
    }
}
