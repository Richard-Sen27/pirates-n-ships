package com.richardsenger.piratesnships.mob.village.client;

import com.richardsenger.piratesnships.mob.client.SeafarerModel;
import com.richardsenger.piratesnships.mob.village.VillageResident;
import com.richardsenger.piratesnships.mob.village.VillageRole;
import com.richardsenger.piratesnships.mob.village.VillagerOutfit;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

/**
 * The villager's model (LIFE1): the sailor's geometry with the texture of its role's outfit ({@link VillagerOutfit}),
 * chosen per entity from its synced role; animations and the procedural poses as every seafarer mob's.
 */
public class VillagerModel extends SeafarerModel<VillageResident> {

    private static final Map<VillageRole, ResourceLocation> TEXTURES = new EnumMap<>(VillageRole.class);

    static {
        for (VillageRole role : VillageRole.values()) TEXTURES.put(role, entityTexture(VillagerOutfit.of(role).texture()));
    }

    public VillagerModel() {
        super(VillagerOutfit.geometry(), VillageRole.VILLAGER.textureName());
    }

    /** The texture of {@code role}'s outfit. */
    public static ResourceLocation texture(VillageRole role) {
        return TEXTURES.get(role);
    }

    @Override
    public ResourceLocation getTextureResource(VillageResident animatable) {
        return texture(animatable.role());
    }
}
