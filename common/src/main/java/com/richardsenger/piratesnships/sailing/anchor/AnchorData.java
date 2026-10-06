package com.richardsenger.piratesnships.sailing.anchor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * Datagen of the visible anchor: the two Sable entity-type tags that keep the stowed anchor inside the plot and remove
 * it with the ship, {@code assets/pirates_n_ships/sounds.json} and the lang entries.
 *
 * <p>The sounds are placeholders made of vanilla sound files. To replace one, put an {@code .ogg} at
 * {@code common/src/main/resources/assets/pirates_n_ships/sounds/anchor/<name>.ogg} and change its entry in
 * {@link #SOUNDS} to {@code "pirates_n_ships:anchor/<name>"}, then run the data generator.
 */
public final class AnchorData {

    static final TagKey<EntityType<?>> SABLE_RETAIN = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("sable", "retain_in_sub_level"));
    static final TagKey<EntityType<?>> SABLE_DESTROY_WITH_SUB_LEVEL = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("sable", "destroy_with_sub_level"));

    /** Sound event name → sound files (vanilla placeholders: chain steps, heavy splash, stone digging). */
    static final String[][] SOUNDS = {
            {"anchor.chain", "minecraft:block/chain/step1", "minecraft:block/chain/step2", "minecraft:block/chain/step3",
                    "minecraft:block/chain/step4", "minecraft:block/chain/step5", "minecraft:block/chain/step6"},
            {"anchor.splash", "minecraft:liquid/heavy_splash"},
            {"anchor.thud", "minecraft:dig/stone1", "minecraft:dig/stone2", "minecraft:dig/stone3", "minecraft:dig/stone4"},
    };

    /**
     * Whether our {@code sounds.json} is generated. Core datagen ({@code JsonOutputs#add}) rejects an empty directory,
     * so a namespace-root file can't be written yet. Until core allows it, this stays false: the anchor plays the
     * vanilla placeholder events directly ({@code AnchorContent#chainSound()} and friends), and our three events are
     * registered but unused. When core allows it, set this to true and run the data generator.
     */
    static final boolean SOUNDS_JSON = false;

    private AnchorData() {
    }

    public static void gather(DataContributions data) {
        data.entityTypeTags(tags -> {
            tags.tag(SABLE_RETAIN).add(AnchorContent.ANCHOR.get());
            tags.tag(SABLE_DESTROY_WITH_SUB_LEVEL).add(AnchorContent.ANCHOR.get());
        });
        if (SOUNDS_JSON) {
            data.json(PackOutput.Target.RESOURCE_PACK, "", Constants.id("sounds"), AnchorData::soundsJson);
        }
        data.lang(lang -> lang
                .add(AnchorContent.ANCHOR.get().getDescriptionId(), "Anchor")
                .add(subtitle("anchor.chain"), "Anchor chain rattles")
                .add(subtitle("anchor.splash"), "Anchor splashes")
                .add(subtitle("anchor.thud"), "Anchor lands"));
    }

    static String subtitle(String event) {
        return Constants.MOD_ID + ".subtitle." + event;
    }

    static JsonObject soundsJson() {
        JsonObject root = new JsonObject();
        for (String[] s : SOUNDS) {
            JsonObject e = new JsonObject();
            e.addProperty("subtitle", subtitle(s[0]));
            JsonArray files = new JsonArray();
            for (int i = 1; i < s.length; i++) {
                files.add(s[i]);
            }
            e.add("sounds", files);
            root.add(s[0], e);
        }
        return root;
    }
}
