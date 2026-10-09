package com.richardsenger.piratesnships.ship.decor.wood;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.core.datagen.ModTagsProvider;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A family of one wooden block in every {@link ShipWood#SHIPPED shipped wood} (RAIL1, docs/design.md §4.8 "Railings
 * (RAIL1/RAIL2)"): registers {@code <wood>_<suffix>} (block and item) per wood, and gives the datagen hooks every
 * family needs, so a new wooden block (WOOD1) is one {@link #register} call plus its datagen:
 * <ul>
 *   <li>models: the block has hand-made parent models with the texture variables {@code #planks} and {@code #log}
 *       ({@code art/models/}, built by a part list in {@code tools/}); {@link #childModels} writes one child per wood
 *       that sets them to the wood's planks and stripped log ({@link ShipWood#planksTexture},
 *       {@link ShipWood#logTexture}); {@link #itemModels} the same for the items;</li>
 *   <li>{@link #lang}: "Oak Railing" from the wood's name and the family's noun;</li>
 *   <li>{@link #forEach}: block states, loot and recipes per wood (the recipe uses that wood's planks);</li>
 *   <li>{@link #tag}: every member into a tag.</li>
 * </ul>
 * RAIL2 widens {@link ShipWood#SHIPPED}; nothing here changes.
 */
public final class WoodFamily<B extends Block> {

    /** The texture variable for a wood's planks in a family's parent models. */
    public static final String PLANKS = "planks";
    /** The texture variable for a wood's stripped log (posts) in a family's parent models. */
    public static final String LOG = "log";

    private final String suffix;
    private final Map<ShipWood, RegistryEntry<Block, B>> entries;

    private WoodFamily(String suffix, Map<ShipWood, RegistryEntry<Block, B>> entries) {
        this.suffix = suffix;
        this.entries = entries;
    }

    /**
     * Registers {@code <wood>_<suffix>} with a plain block item for every shipped wood, in {@link ShipWood#SHIPPED}
     * order (which is also the creative tab's order). {@code factory} makes the block of one wood, usually from
     * {@link #properties}.
     */
    public static <B extends Block> WoodFamily<B> register(String suffix, Function<ShipWood, B> factory) {
        Map<ShipWood, RegistryEntry<Block, B>> entries = new EnumMap<>(ShipWood.class);
        for (ShipWood wood : ShipWood.SHIPPED) {
            entries.put(wood, ModRegistry.blockWithItem(name(wood, suffix), () -> factory.apply(wood)));
        }
        return new WoodFamily<>(suffix, Collections.unmodifiableMap(entries));
    }

    /** The registry name of the member of {@code wood}: {@code oak_railing}. */
    public static String name(ShipWood wood, String suffix) {
        return wood.id() + "_" + suffix;
    }

    /**
     * Properties of a wooden block of {@code wood}: the planks' map colour and sound, a bass note block sound, the
     * strength of a fence (2.0, 3.0), burning in lava unless it is a nether wood.
     */
    public static BlockBehaviour.Properties properties(ShipWood wood) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(wood.planks().defaultMapColor())
                .instrument(NoteBlockInstrument.BASS).strength(2.0f, 3.0f).sound(wood.sound());
        return wood.flammable() ? p.ignitedByLava() : p;
    }

    public String suffix() {
        return suffix;
    }

    /** The woods of this family. */
    public Set<ShipWood> woods() {
        return entries.keySet();
    }

    /** The member of {@code wood}; throws for a wood that is not shipped. */
    public RegistryEntry<Block, B> entry(ShipWood wood) {
        RegistryEntry<Block, B> e = entries.get(wood);
        if (e == null) throw new IllegalArgumentException(wood + " " + suffix + " is not registered");
        return e;
    }

    public B get(ShipWood wood) {
        return entry(wood).get();
    }

    /** The first member: the family's face in the Almanac and the creative tab (oak). */
    public RegistryEntry<Block, B> lead() {
        return entries.get(ShipWood.SHIPPED.get(0));
    }

    /** Every member, in {@link ShipWood#SHIPPED} order. */
    public List<B> blocks() {
        List<B> out = new ArrayList<>(entries.size());
        entries.values().forEach(e -> out.add(e.get()));
        return out;
    }

    /** Whether {@code block} is a member. */
    public boolean contains(Block block) {
        for (RegistryEntry<Block, B> e : entries.values()) {
            if (e.get() == block) return true;
        }
        return false;
    }

    /** Runs {@code action} for every member with its wood. */
    public void forEach(BiConsumer<ShipWood, B> action) {
        entries.forEach((wood, e) -> action.accept(wood, e.get()));
    }

    // ------------------------------------------------------------------ datagen

    /** Lang: "{wood name} {noun}" per member, e.g. "Oak Railing". */
    public void lang(LangBuilder lang, String noun) {
        entries.forEach((wood, e) -> lang.block(e, wood.displayName() + " " + noun));
    }

    /**
     * The child of the hand-made {@code parent} (a block model of ours, {@code block/<name>}) for {@code wood}:
     * {@code block/<wood>_<name>}.
     */
    public static ResourceLocation child(ShipWood wood, ResourceLocation parent) {
        String path = parent.getPath();
        int slash = path.lastIndexOf('/');
        return Constants.id(path.substring(0, slash + 1) + wood.id() + "_" + path.substring(slash + 1));
    }

    /**
     * A child model of {@code parent} for {@code wood}: {@code {"parent": ..., "textures": {"planks", "log",
     * "particle"}}} with the wood's planks and stripped log (vanilla's {@code TextureSlot.create} is private, so the JSON
     * is written directly).
     */
    public static JsonObject childJson(ShipWood wood, ResourceLocation parent) {
        JsonObject textures = new JsonObject();
        textures.addProperty(PLANKS, wood.planksTexture().toString());
        textures.addProperty(LOG, wood.logTexture().toString());
        textures.addProperty("particle", wood.planksTexture().toString());
        JsonObject json = new JsonObject();
        json.addProperty("parent", parent.toString());
        json.add("textures", textures);
        return json;
    }

    /** Writes {@link #child} of {@code parent} for every member's wood ({@link #childJson}). */
    public void childModels(ModelContext m, ResourceLocation parent) {
        for (ShipWood wood : entries.keySet()) {
            m.models().accept(child(wood, parent), () -> childJson(wood, parent));
        }
    }

    /** Every member's item model: a child of the hand-made {@code parent} with the wood's textures ({@link #childJson}). */
    public void itemModels(ModelContext m, ResourceLocation parent) {
        entries.forEach((wood, e) -> m.models().accept(ModelLocationUtils.getModelLocation(e.get().asItem()), () -> childJson(wood, parent)));
    }

    /** Adds every member to each of {@code tags}. */
    @SafeVarargs
    public final void tag(ModTagsProvider<Block> provider, TagKey<Block>... tags) {
        for (TagKey<Block> tag : tags) {
            for (B block : blocks()) provider.tag(tag).add(block);
        }
    }
}
