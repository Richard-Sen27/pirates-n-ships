package com.richardsenger.piratesnships.sailing.anchor;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.SoundEntries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * Datagen of the visible anchor: the two Sable entity-type tags that keep the stowed anchor inside the plot and remove
 * it with the ship, the anchor's {@code sounds.json} entries (through {@code data.sounds}) and the lang entries.
 *
 * <p>The sounds are placeholders made of the vanilla sound files that {@code SoundEvents.CHAIN_STEP},
 * {@code PLAYER_SPLASH_HIGH_SPEED} and {@code STONE_BREAK} use. To replace one, add the recording through
 * {@code tools/sounds/manifest.json} (target {@code anchor/<name>.ogg}) and change its entry in {@link #SOUNDS} to
 * {@code "pirates_n_ships:anchor/<name>"}, then run the data generator.
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

    private AnchorData() {
    }

    public static void gather(DataContributions data) {
        data.entityTypeTags(tags -> {
            tags.tag(SABLE_RETAIN).add(AnchorContent.ANCHOR.get());
            tags.tag(SABLE_DESTROY_WITH_SUB_LEVEL).add(AnchorContent.ANCHOR.get());
        });
        data.sounds(AnchorData::sounds);
        data.lang(lang -> lang
                .add(AnchorContent.ANCHOR.get().getDescriptionId(), "Anchor")
                .add(subtitle("anchor.chain"), "Anchor chain rattles")
                .add(subtitle("anchor.splash"), "Anchor splashes")
                .add(subtitle("anchor.thud"), "Anchor lands"));
    }

    static String subtitle(String event) {
        return Constants.MOD_ID + ".subtitle." + event;
    }

    static void sounds(SoundEntries entries) {
        for (String[] s : SOUNDS) {
            SoundEntries.Event e = entries.event(s[0]).subtitle(subtitle(s[0]));
            for (int i = 1; i < s.length; i++) {
                e.sounds(s[i]);
            }
        }
    }
}
