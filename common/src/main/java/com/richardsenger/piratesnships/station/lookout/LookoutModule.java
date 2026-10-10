package com.richardsenger.piratesnships.station.lookout;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import com.richardsenger.piratesnships.ship.decor.wood.WoodRecipe;
import net.minecraft.resources.ResourceLocation;
import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.SoundEntries;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.station.lookout.client.LookoutClient;
import com.richardsenger.piratesnships.station.lookout.client.SightingMarks;
import com.richardsenger.piratesnships.crew.npc.StationPoses;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
import com.richardsenger.piratesnships.ship.decor.SableWeightTags;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import java.util.List;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleFooting;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

/**
 * The crow's nest and its lookout (CN1, docs/design.md §4.8 "Visual backlog 2" item 2, §6, §7): the block
 * ({@link CrowsNestBlock}, hand-made model {@code block/crows_nest}), the lookout station ({@link LookoutStation}) and
 * the watch ({@link Lookouts}), config {@code lookout.*}; LKO2: the call as a {@link SightingPayload} (action-bar line,
 * hail, mark on the ship HUD's rose).
 */
public final class LookoutModule implements ModModule {

    @Override
    public String id() {
        return "station.lookout";
    }

    @Override
    public void registerConfig() {
        LookoutConfig.init();
    }

    @Override
    public void registerContent() {
        LookoutContent.init();
    }

    @Override
    public void registerPayloads() {
        // LKO2: the lookout's call; the handler only stores it (SightingMarks has no client classes)
        Services.NETWORK.registerToClient(SightingPayload.TYPE, SightingPayload.CODEC, (p, player) -> SightingMarks.accept(p));
    }

    @Override
    public void initClient() {
        LookoutClient.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.LEVEL_TICK_END.register(Lookouts::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> Lookouts.onServerStopped());
        SableShips.onShipRemoved(Lookouts::onShipRemoved);
        StationPoses.registerFacing(LookoutStation.INSTANCE.id(), Lookouts::facing); // ST5: the lookout faces the bow
    }

    @Override
    public void gatherData(DataContributions data) {
        WoodFamily<CrowsNestBlock> nests = LookoutContent.CROWS_NESTS;
        data.lang(lang -> nests.lang(lang, "Crow's Nest")); // WOOD3: "Spruce Crow's Nest", ...
        data.lang(LookoutLang::lang);
        // LKO2: the hail, the bell lookouts rang from the masthead (vanilla's bell strikes, high and soft)
        data.sounds(sounds -> sounds.event(LookoutContent.HAIL).subtitle(LookoutLang.KEY_HAIL_SUBTITLE)
                .sound(SoundEntries.file("minecraft:block/bell/bell_use01").pitch(1.5f).volume(0.6f))
                .sound(SoundEntries.file("minecraft:block/bell/bell_use02").pitch(1.5f).volume(0.6f)));
        // the model is hand-made (art/models/crows_nest.bbmodel) and carries the item's display entries; WOOD3: a child
        // per wood (staves #planks, rim and struts #log; the dark oak underside, the iron and the birch rope stay), the
        // item model its child too. FLG3: with a flagpole on it the nest shows crows_nest_pole (the same project's pole
        // group: stub, collar, cleat and halyard; the stub in the nest's own wood)
        data.models(m -> {
            nests.childModels(m, NEST);
            nests.childModels(m, NEST_POLE);
            nests.itemModels(m, NEST);
            nests.forEach((wood, nest) -> m.blockStates().accept(MultiVariantGenerator.multiVariant(nest)
                    .with(PropertyDispatch.property(FlagpoleFooting.POLE)
                            .select(false, Variant.variant().with(VariantProperties.MODEL, WoodFamily.child(wood, NEST)))
                            .select(true, Variant.variant().with(VariantProperties.MODEL, WoodFamily.child(wood, NEST_POLE))))));
        });
        nests.dropSelf(data);
        // wooden, like planks: light on the masthead
        data.blockTags(tags -> nests.tag(tags, BlockTags.MINEABLE_WITH_AXE, SableWeightTags.LIGHT));
        nests.recipe(data, WoodRecipe.shaped(RecipeCategory.DECORATIONS, 1, "PRP", "N N", "PPP")
                .define('P', WoodRecipe.planks()).define('R', WoodRecipe.item(TriangularSailContent.ROPE.get()))
                .define('N', WoodRecipe.item(Items.IRON_NUGGET))
                .group(Constants.MOD_ID + ":crows_nest").unlockedBy("has_rope", WoodRecipe.item(TriangularSailContent.ROPE.get())));
    }

    /** CN1, FLG3: the crow's nest's hand-made parents. */
    static final ResourceLocation NEST = Constants.id("block/crows_nest");
    static final ResourceLocation NEST_POLE = Constants.id("block/crows_nest_pole");

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(LookoutGameTests.class);
    }
}
