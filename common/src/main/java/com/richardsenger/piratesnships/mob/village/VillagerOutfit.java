package com.richardsenger.piratesnships.mob.village;

import java.util.EnumMap;
import java.util.Map;

/**
 * The outfit of a village role (LIFE1): a hat, a coat and the clothing under it, the three layers of the apparel
 * system's wearer (head, chest, legs and feet; {@code apparel/ApparelContent}), painted onto one skin sheet on the
 * sailor's geometry by {@code art/models/entity/seafarer_skins.js} ({@code SF.villager(role)}, whose part painters
 * carry these names). Pure: the client draws {@link #texture()}.
 *
 * <p>The outfit follows the role, and the role follows the building the villager belongs to
 * ({@link ResidentPosts#rolesOf}); a hired villager keeps it aboard ({@code CrewMember#outfit}).
 *
 * @param hat      the head layer ({@code none}: bare-headed, the cap's cubes are left transparent)
 * @param coat     the chest layer over the shirt ({@code none}: shirt sleeves)
 * @param clothing shirt, breeches or skirt, shoes
 */
public record VillagerOutfit(VillageRole role, String hat, String coat, String clothing) {

    private static final Map<VillageRole, VillagerOutfit> OUTFITS = new EnumMap<>(VillageRole.class);

    static {
        put(VillageRole.VILLAGER, "felt_cap", "brown_waistcoat", "linen_shirt_and_breeches");
        put(VillageRole.FISHWIFE, "head_scarf", "knitted_shawl", "blue_skirt_and_apron");
        put(VillageRole.NET_MENDER, "knitted_cap", "none", "smock_and_rope_belt");
        put(VillageRole.DOCKHAND, "grey_bandana", "none", "rolled_shirt_and_canvas_slops");
        put(VillageRole.SHIPWRIGHT, "leather_cap", "leather_apron", "work_shirt_and_breeches");
        put(VillageRole.INNKEEPER, "none", "red_waistcoat", "bar_apron_and_breeches");
        put(VillageRole.LAMPLIGHTER, "peaked_cap", "long_grey_coat", "dark_breeches");
        put(VillageRole.VENDOR, "straw_hat", "striped_apron", "green_dress");
    }

    private static void put(VillageRole role, String hat, String coat, String clothing) {
        OUTFITS.put(role, new VillagerOutfit(role, hat, coat, clothing));
    }

    /** The outfit of {@code role}. */
    public static VillagerOutfit of(VillageRole role) {
        return OUTFITS.get(role);
    }

    /** The skin sheet the outfit is painted on: {@code textures/entity/<texture>.png}. */
    public String texture() {
        return role.textureName();
    }

    /** The geometry the outfit is drawn on: the sailor's, as for the harbor master ({@code MobKind.geoId}). */
    public static String geometry() {
        return "sailor";
    }
}
