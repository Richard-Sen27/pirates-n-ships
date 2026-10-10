package com.richardsenger.piratesnships.ship.decor.wood;

import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * A wood a wooden block family of ours comes in (RAIL1, WOOD1; docs/woods.md, docs/design.md §4.8 "Railings
 * (RAIL1/RAIL2)"): an open list of the eleven vanilla woods ({@link #VANILLA}) and the woods of known wood mods
 * ({@link WoodCompat#WOODS}). Each wood has its id ({@code spruce}; a mod's wood {@code <modid>_<name>}, e.g.
 * {@code biomesoplenty_redwood}), its English name, the planks and stripped-log textures a family's child models use,
 * its planks block/item id, its sound, whether it burns, its map colour and the mod it needs ({@code null} for vanilla).
 *
 * <p>Nothing here touches a registry at class load: the planks are looked up by id ({@link #planksItem()}), the sound
 * and the vanilla wood type are resolved when asked, so the list loads in plain JUnit.
 *
 * <p>{@link #shipped()} is the list the families register, decided once (at registration): every vanilla wood, then
 * every compat wood whose mod is loaded, compat woods ordered by mod id then name. A data run ships every wood, so
 * datagen writes the compat woods' files unconditionally (they load only with their mod; docs/woods.md).
 */
public final class ShipWood {

    /** Which vanilla sound set a wood uses (resolved lazily: {@link SoundType} needs a bootstrapped game). */
    public enum Sound {
        WOOD, CHERRY, BAMBOO, NETHER;

        SoundType type() {
            return switch (this) {
                case WOOD -> SoundType.WOOD;
                case CHERRY -> SoundType.CHERRY_WOOD;
                case BAMBOO -> SoundType.BAMBOO_WOOD;
                case NETHER -> SoundType.NETHER_WOOD;
            };
        }
    }

    public static final ShipWood OAK = vanilla("oak", "Oak", "oak_log", MapColor.WOOD, Sound.WOOD, true);
    public static final ShipWood SPRUCE = vanilla("spruce", "Spruce", "spruce_log", MapColor.PODZOL, Sound.WOOD, true);
    public static final ShipWood BIRCH = vanilla("birch", "Birch", "birch_log", MapColor.SAND, Sound.WOOD, true);
    public static final ShipWood JUNGLE = vanilla("jungle", "Jungle", "jungle_log", MapColor.DIRT, Sound.WOOD, true);
    public static final ShipWood ACACIA = vanilla("acacia", "Acacia", "acacia_log", MapColor.COLOR_ORANGE, Sound.WOOD, true);
    public static final ShipWood DARK_OAK = vanilla("dark_oak", "Dark Oak", "dark_oak_log", MapColor.COLOR_BROWN, Sound.WOOD, true);
    public static final ShipWood MANGROVE = vanilla("mangrove", "Mangrove", "mangrove_log", MapColor.COLOR_RED, Sound.WOOD, true);
    public static final ShipWood CHERRY = vanilla("cherry", "Cherry", "cherry_log", MapColor.TERRACOTTA_WHITE, Sound.CHERRY, true);
    /** Bamboo's "log" is the stripped bamboo block. */
    public static final ShipWood BAMBOO = vanilla("bamboo", "Bamboo", "bamboo_block", MapColor.COLOR_YELLOW, Sound.BAMBOO, true);
    /** The nether woods use their stripped stems and do not burn. */
    public static final ShipWood CRIMSON = vanilla("crimson", "Crimson", "crimson_stem", MapColor.CRIMSON_STEM, Sound.NETHER, false);
    public static final ShipWood WARPED = vanilla("warped", "Warped", "warped_stem", MapColor.WARPED_STEM, Sound.NETHER, false);

    /** The eleven vanilla woods, in the creative tab's order (vanilla's fence order, with cherry and bamboo after mangrove). */
    public static final List<ShipWood> VANILLA = List.of(OAK, SPRUCE, BIRCH, JUNGLE, ACACIA, DARK_OAK, MANGROVE, CHERRY, BAMBOO, CRIMSON, WARPED);

    private static volatile List<ShipWood> shipped;

    private final String id;
    private final String displayName;
    @Nullable
    private final String modId;
    private final ResourceLocation planksTexture;
    private final ResourceLocation logTexture;
    private final ResourceLocation logTopTexture;
    private final ResourceLocation planksId;
    private final ResourceLocation logId;
    private final ResourceLocation strippedLogId;
    private final MapColor mapColor;
    private final Sound sound;
    private final boolean flammable;

    ShipWood(String id, String displayName, @Nullable String modId, ResourceLocation planksTexture, ResourceLocation logTexture,
             ResourceLocation logTopTexture, ResourceLocation planksId, ResourceLocation logId, ResourceLocation strippedLogId,
             MapColor mapColor, Sound sound, boolean flammable) {
        this.id = id;
        this.displayName = displayName;
        this.modId = modId;
        this.planksTexture = planksTexture;
        this.logTexture = logTexture;
        this.logTopTexture = logTopTexture;
        this.planksId = planksId;
        this.logId = logId;
        this.strippedLogId = strippedLogId;
        this.mapColor = mapColor;
        this.sound = sound;
        this.flammable = flammable;
    }

    /**
     * A vanilla wood; {@code log} is the unstripped log block ({@code oak_log}, {@code bamboo_block},
     * {@code crimson_stem}), its stripped block {@code stripped_<log>} is also the side texture, {@code _top} the end.
     */
    private static ShipWood vanilla(String id, String displayName, String log, MapColor color, Sound sound, boolean flammable) {
        return new ShipWood(id, displayName, null, ResourceLocation.withDefaultNamespace("block/" + id + "_planks"),
                ResourceLocation.withDefaultNamespace("block/stripped_" + log), ResourceLocation.withDefaultNamespace("block/stripped_" + log + "_top"),
                ResourceLocation.withDefaultNamespace(id + "_planks"), ResourceLocation.withDefaultNamespace(log),
                ResourceLocation.withDefaultNamespace("stripped_" + log), color, sound, flammable);
    }

    /** Every wood we know: {@link #VANILLA}, then {@link WoodCompat#WOODS} in {@link #shipped()} order. */
    public static List<ShipWood> all() {
        return shippedFor(mod -> true, true);
    }

    /**
     * The woods every family registers, in creative-tab order: decided on the first call (at registration) from the
     * loaded mods; a data run ({@code Services.PLATFORM.isDataGeneration()}) ships every wood.
     */
    public static List<ShipWood> shipped() {
        List<ShipWood> s = shipped;
        if (s == null) {
            synchronized (ShipWood.class) {
                s = shipped;
                if (s == null) {
                    s = shippedFor(Services.PLATFORM::isModLoaded, Services.PLATFORM.isDataGeneration());
                    shipped = s;
                }
            }
        }
        return s;
    }

    /**
     * Pure: the shipped list for {@code modLoaded}: every vanilla wood, then each compat wood whose mod is loaded (or
     * every one when {@code everything}), ordered by mod id, then wood id.
     */
    public static List<ShipWood> shippedFor(Predicate<String> modLoaded, boolean everything) {
        List<ShipWood> out = new ArrayList<>(VANILLA);
        List<ShipWood> compat = new ArrayList<>();
        for (ShipWood w : WoodCompat.WOODS) {
            if (everything || modLoaded.test(w.modId())) compat.add(w);
        }
        compat.sort(Comparator.comparing(ShipWood::modId).thenComparing(ShipWood::id));
        out.addAll(compat);
        return List.copyOf(out);
    }

    /** The wood with {@code id} among {@link #all()}, or {@code null}. */
    @Nullable
    public static ShipWood byId(String id) {
        for (ShipWood w : all()) if (w.id.equals(id)) return w;
        return null;
    }

    /** The wood's id: vanilla's spelling ({@code oak}, {@code dark_oak}), or {@code <modid>_<name>} for a mod's wood. */
    public String id() {
        return id;
    }

    /** The English name that leads a family member's name: "Oak", "Dark Oak", "Redwood" (no mod name). */
    public String displayName() {
        return displayName;
    }

    /** The mod the wood needs, or {@code null} for a vanilla wood. */
    @Nullable
    public String modId() {
        return modId;
    }

    public boolean isVanilla() {
        return modId == null;
    }

    /** Whether the wood burns (the nether woods do not). */
    public boolean flammable() {
        return flammable;
    }

    /** Whether {@link #shipped()} holds this wood. */
    public boolean isShipped() {
        return shipped().contains(this);
    }

    /** The texture of the wood's planks: {@code minecraft:block/oak_planks}. */
    public ResourceLocation planksTexture() {
        return planksTexture;
    }

    /** The side texture of the wood's stripped log (or stem, or bamboo block): {@code minecraft:block/stripped_oak_log}. */
    public ResourceLocation logTexture() {
        return logTexture;
    }

    /** The end texture of the wood's stripped log: {@code minecraft:block/stripped_oak_log_top} (texture variable {@code #log_top}). */
    public ResourceLocation logTopTexture() {
        return logTopTexture;
    }

    /** The id of the wood's log block and item: {@code minecraft:oak_log}, {@code minecraft:bamboo_block}, {@code minecraft:crimson_stem}. */
    public ResourceLocation logId() {
        return logId;
    }

    /** The id of the wood's stripped log: {@code minecraft:stripped_oak_log}. */
    public ResourceLocation strippedLogId() {
        return strippedLogId;
    }

    /** The log item, looked up by id (for a family recipe that wants this wood's log; air when the mod is absent). */
    public Item log() {
        return BuiltInRegistries.ITEM.get(logId);
    }

    /** The stripped log item, looked up by id (air when the mod is absent). */
    public Item strippedLog() {
        return BuiltInRegistries.ITEM.get(strippedLogId);
    }

    /** The id of the wood's planks block and item: {@code minecraft:oak_planks}, {@code biomesoplenty:redwood_planks}. */
    public ResourceLocation planksId() {
        return planksId;
    }

    /** The planks item, looked up by id (air when the wood's mod is absent). */
    public Item planksItem() {
        return BuiltInRegistries.ITEM.get(planksId);
    }

    /** The planks block, looked up by id (air when the wood's mod is absent). */
    public Block planks() {
        return BuiltInRegistries.BLOCK.get(planksId);
    }

    /** The map colour of the family's blocks (vanilla: the planks'; a mod's wood: the table's, see WoodCompat). */
    public MapColor mapColor() {
        return mapColor;
    }

    public Sound soundSet() {
        return sound;
    }

    /** The sound of the wood's planks. */
    public SoundType sound() {
        return sound.type();
    }

    /**
     * The vanilla wood type whose fence-gate sounds the railing gate plays (RAIL3): the wood's own for a vanilla wood;
     * for a mod's wood the vanilla type of its sound set (oak, cherry, bamboo, crimson).
     */
    public WoodType woodType() {
        if (this == OAK) return WoodType.OAK;
        if (this == SPRUCE) return WoodType.SPRUCE;
        if (this == BIRCH) return WoodType.BIRCH;
        if (this == JUNGLE) return WoodType.JUNGLE;
        if (this == ACACIA) return WoodType.ACACIA;
        if (this == DARK_OAK) return WoodType.DARK_OAK;
        if (this == MANGROVE) return WoodType.MANGROVE;
        if (this == CHERRY) return WoodType.CHERRY;
        if (this == BAMBOO) return WoodType.BAMBOO;
        if (this == CRIMSON) return WoodType.CRIMSON;
        if (this == WARPED) return WoodType.WARPED;
        return switch (sound) {
            case WOOD -> WoodType.OAK;
            case CHERRY -> WoodType.CHERRY;
            case BAMBOO -> WoodType.BAMBOO;
            case NETHER -> WoodType.CRIMSON;
        };
    }

    @Override
    public String toString() {
        return id;
    }
}
