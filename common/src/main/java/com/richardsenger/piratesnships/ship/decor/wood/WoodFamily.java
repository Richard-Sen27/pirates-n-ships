package com.richardsenger.piratesnships.ship.decor.wood;

import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.core.datagen.ModTagsProvider;
import com.richardsenger.piratesnships.core.datagen.ModelContext;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.data.PackOutput;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A family of one wooden block in every {@link ShipWood#shipped() shipped wood} (RAIL1, WOOD1; docs/woods.md,
 * docs/design.md §4.8 "Railings (RAIL1/RAIL2)"): registers {@code <wood>_<suffix>} (block and item) per wood, and gives
 * the datagen hooks every family needs, so a new wooden block is one {@link #register} call plus its datagen:
 * <ul>
 *   <li>models: the block has hand-made parent models with the texture variables {@code #planks}, {@code #log} and
 *       {@code #log_top} ({@code art/models/}, built by a part list in {@code tools/}); {@link #childModels} writes one
 *       child per wood that sets them to the wood's planks, stripped log and its end ({@link #childJson});
 *       {@link #itemModels} the same for the items;</li>
 *   <li>{@link #lang}: "Spruce Railing" from the wood's name and the family's noun;</li>
 *   <li>{@link #forEach}: block states per wood; {@link #loot}/{@link #dropSelf} and {@link #recipe}: loot tables and
 *       recipes per wood, a mod's wood's files with both loaders' load conditions ({@link WoodConditions});</li>
 *   <li>{@link #tag}: every member into a tag, a mod's wood's member as an optional entry.</li>
 * </ul>
 * A family may keep one wood's member under a legacy id (its id before WOOD1, e.g. {@code helm} for dark oak; see
 * {@link #register(String, ShipWood, Function)}), so saved worlds, structures and schematics keep it.
 *
 * <p>Registration: every vanilla wood, and a mod's wood only when its mod is loaded (or in a data run).
 */
public final class WoodFamily<B extends Block> {

    /** The texture variable for a wood's planks in a family's parent models. */
    public static final String PLANKS = "planks";
    /** The texture variable for a wood's stripped log (side) in a family's parent models. */
    public static final String LOG = "log";
    /** The texture variable for a wood's stripped log end in a family's parent models. */
    public static final String LOG_TOP = "log_top";

    /** Which texture a child model's {@code particle} uses. */
    public enum Particle {
        PLANKS, LOG;

        ResourceLocation of(ShipWood wood) {
            return this == PLANKS ? wood.planksTexture() : wood.logTexture();
        }
    }

    /** Every family registered so far, in registration order (the Almanac's recipe panels find a family by its suffix). */
    private static final List<WoodFamily<?>> ALL = new CopyOnWriteArrayList<>();

    private final String suffix;
    @Nullable
    private final ShipWood legacyWood;
    private final Map<ShipWood, RegistryEntry<Block, B>> entries;

    private WoodFamily(String suffix, @Nullable ShipWood legacyWood, Map<ShipWood, RegistryEntry<Block, B>> entries) {
        this.suffix = suffix;
        this.legacyWood = legacyWood;
        this.entries = entries;
    }

    /**
     * Registers {@code <wood>_<suffix>} with a plain block item for every shipped wood, in {@link ShipWood#shipped()}
     * order (which is also the creative tab's order). {@code factory} makes the block of one wood, usually from
     * {@link #properties}.
     */
    public static <B extends Block> WoodFamily<B> register(String suffix, Function<ShipWood, B> factory) {
        return register(suffix, null, factory);
    }

    /**
     * Like {@link #register(String, Function)}, but the member of {@code legacyWood} keeps the legacy id
     * {@code <suffix>} (e.g. {@code register("helm", ShipWood.DARK_OAK, ...)}: {@code helm} for dark oak,
     * {@code <wood>_helm} for every other wood). The legacy id is internal: the member is named for its wood ("Dark Oak
     * Helm") like the others, and it leads the family ({@link #lead}).
     */
    public static <B extends Block> WoodFamily<B> register(String suffix, @Nullable ShipWood legacyWood, Function<ShipWood, B> factory) {
        Map<ShipWood, RegistryEntry<Block, B>> entries = new LinkedHashMap<>();
        for (ShipWood wood : ShipWood.shipped()) {
            entries.put(wood, ModRegistry.blockWithItem(memberName(wood, suffix, legacyWood), () -> factory.apply(wood)));
        }
        WoodFamily<B> family = new WoodFamily<>(suffix, legacyWood, Collections.unmodifiableMap(entries));
        ALL.add(family);
        return family;
    }

    /** The registry name convention: {@code oak_railing}, {@code biomesoplenty_redwood_railing}. */
    public static String name(ShipWood wood, String suffix) {
        return wood.id() + "_" + suffix;
    }

    /** The registry name of the member of {@code wood} in a family with {@code legacyWood} (or none). */
    public static String memberName(ShipWood wood, String suffix, @Nullable ShipWood legacyWood) {
        return wood == legacyWood ? suffix : name(wood, suffix);
    }

    /**
     * Properties of a wooden block of {@code wood}: the wood's map colour and sound, a bass note block sound, the
     * strength of a fence (2.0, 3.0), burning in lava unless it is a nether wood.
     */
    public static BlockBehaviour.Properties properties(ShipWood wood) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(wood.mapColor())
                .instrument(NoteBlockInstrument.BASS).strength(2.0f, 3.0f).sound(wood.sound());
        return wood.flammable() ? p.ignitedByLava() : p;
    }

    /** Every family registered so far. */
    public static List<WoodFamily<?>> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** The registered family with {@code suffix}, or {@code null}. */
    @Nullable
    public static WoodFamily<?> bySuffix(String suffix) {
        for (WoodFamily<?> f : ALL) if (f.suffix.equals(suffix)) return f;
        return null;
    }

    /**
     * The family a registry path of ours belongs to, or {@code null}: {@code <wood>_<suffix>} of a family, or a
     * family's legacy id.
     */
    @Nullable
    public static WoodFamily<?> ofPath(String path) {
        for (WoodFamily<?> f : ALL) {
            if (f.legacyWood != null && f.suffix.equals(path)) return f;
            for (ShipWood w : f.entries.keySet()) {
                if (path.equals(name(w, f.suffix))) return f;
            }
        }
        return null;
    }

    public String suffix() {
        return suffix;
    }

    /** The wood whose member keeps the legacy id {@code <suffix>}, or {@code null}. */
    @Nullable
    public ShipWood legacyWood() {
        return legacyWood;
    }

    /** The registry name of {@code wood}'s member (legacy aware). */
    public String name(ShipWood wood) {
        return memberName(wood, suffix, legacyWood);
    }

    /** The woods of this family, in {@link ShipWood#shipped()} order. */
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

    /** The family's face in the Almanac and on recipe panels: the legacy member if there is one, else oak's. */
    public RegistryEntry<Block, B> lead() {
        return entries.get(legacyWood != null ? legacyWood : ShipWood.shipped().get(0));
    }

    /** Every member, in {@link ShipWood#shipped()} order. */
    public List<B> blocks() {
        List<B> out = new ArrayList<>(entries.size());
        entries.values().forEach(e -> out.add(e.get()));
        return out;
    }

    /** Every member's id, in {@link ShipWood#shipped()} order. */
    public List<ResourceLocation> ids() {
        List<ResourceLocation> out = new ArrayList<>(entries.size());
        entries.values().forEach(e -> out.add(e.id()));
        return out;
    }

    /** Whether {@code block} is a member. */
    public boolean contains(Block block) {
        return woodOf(block) != null;
    }

    /** The wood of member {@code block}, or {@code null} if it is none. */
    @Nullable
    public ShipWood woodOf(Block block) {
        for (Map.Entry<ShipWood, RegistryEntry<Block, B>> e : entries.entrySet()) {
            if (e.getValue().get() == block) return e.getKey();
        }
        return null;
    }

    /** Runs {@code action} for every member with its wood. */
    public void forEach(BiConsumer<ShipWood, B> action) {
        entries.forEach((wood, e) -> action.accept(wood, e.get()));
    }

    /**
     * One block entity type {@code name} whose valid blocks are every member (for a family with a block entity; the
     * members are resolved when the type is built).
     */
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> RegistryEntry<BlockEntityType<?>, BlockEntityType<T>> blockEntity(
            String name, BlockEntityType.BlockEntitySupplier<T> factory) {
        Supplier<? extends Block>[] members = entries.values().toArray(Supplier[]::new);
        return ModRegistry.blockEntity(name, factory, members);
    }

    // ------------------------------------------------------------------ datagen

    /** Lang: "{wood name} {noun}" per member, e.g. "Oak Railing" (the legacy member too: "Dark Oak Helm"). */
    public void lang(LangBuilder lang, String noun) {
        entries.forEach((wood, e) -> lang.block(e, wood.displayName() + " " + noun));
    }

    /**
     * The child of the hand-made {@code parent} (a block model of ours, {@code block/<name>}) for {@code wood}:
     * {@code block/<wood>_<name>} (a model id, so a legacy member's child is named like every other).
     */
    public static ResourceLocation child(ShipWood wood, ResourceLocation parent) {
        String path = parent.getPath();
        int slash = path.lastIndexOf('/');
        return Constants.id(path.substring(0, slash + 1) + wood.id() + "_" + path.substring(slash + 1));
    }

    /** {@link #childJson(ShipWood, ResourceLocation, Particle)} with the planks as particle. */
    public static JsonObject childJson(ShipWood wood, ResourceLocation parent) {
        return childJson(wood, parent, Particle.PLANKS);
    }

    /**
     * A child model of {@code parent} for {@code wood}: {@code {"parent": ..., "textures": {"planks", "log",
     * "log_top", "particle"}}} with the wood's planks, stripped log and its end, the particle from {@code particle}
     * (vanilla's {@code TextureSlot.create} is private, so the JSON is written directly).
     */
    public static JsonObject childJson(ShipWood wood, ResourceLocation parent, Particle particle) {
        JsonObject textures = new JsonObject();
        textures.addProperty(PLANKS, wood.planksTexture().toString());
        textures.addProperty(LOG, wood.logTexture().toString());
        textures.addProperty(LOG_TOP, wood.logTopTexture().toString());
        textures.addProperty("particle", particle.of(wood).toString());
        JsonObject json = new JsonObject();
        json.addProperty("parent", parent.toString());
        json.add("textures", textures);
        return json;
    }

    /** Writes {@link #child} of {@code parent} for every member's wood ({@link #childJson}, planks particle). */
    public void childModels(ModelContext m, ResourceLocation parent) {
        childModels(m, parent, Particle.PLANKS);
    }

    public void childModels(ModelContext m, ResourceLocation parent, Particle particle) {
        for (ShipWood wood : entries.keySet()) {
            m.models().accept(child(wood, parent), () -> childJson(wood, parent, particle));
        }
    }

    /** Every member's item model: a child of the hand-made {@code parent} with the wood's textures ({@link #childJson}). */
    public void itemModels(ModelContext m, ResourceLocation parent) {
        itemModels(m, parent, Particle.PLANKS);
    }

    public void itemModels(ModelContext m, ResourceLocation parent, Particle particle) {
        entries.forEach((wood, e) -> m.models().accept(ModelLocationUtils.getModelLocation(e.get().asItem()),
                () -> childJson(wood, parent, particle)));
    }

    /** Adds every member to each of {@code tags}; a mod's wood's member as an optional entry ({@code required: false}). */
    @SafeVarargs
    public final void tag(ModTagsProvider<Block> provider, TagKey<Block>... tags) {
        for (TagKey<Block> tag : tags) {
            entries.forEach((wood, e) -> {
                if (wood.isVanilla()) provider.tag(tag).add(e.get());
                else provider.tag(tag).addOptional(e.id());
            });
        }
    }

    /**
     * Loot per member from {@code table}: a vanilla wood's through the loot provider, a mod's wood's as a loot table
     * file with its load conditions (the vanilla provider cannot add them).
     */
    public void loot(DataContributions data, Function<B, LootTable.Builder> table) {
        data.blockLoot(loot -> entries.forEach((wood, e) -> {
            if (wood.isVanilla()) loot.add(e.get(), table.apply(e.get()));
        }));
        entries.forEach((wood, e) -> {
            if (wood.isVanilla()) return;
            ResourceLocation id = e.get().getLootTable().location();
            data.json(PackOutput.Target.DATA_PACK, "loot_table", id, () -> {
                LootTable.Builder b = table.apply(e.get());
                b.setRandomSequence(id);
                LootTable built = b.setParamSet(LootContextParamSets.BLOCK).build();
                return WoodRecipe.encodeForMod(LootTable.DIRECT_CODEC, built, wood, Set.of());
            });
        });
    }

    /** Every member drops itself (vanilla's {@code dropSelf}: one item, survives explosions). */
    public void dropSelf(DataContributions data) {
        loot(data, WoodFamily::selfDrop);
    }

    /** Vanilla's {@code createSingleItemTable} with its explosion condition, without a loot provider instance. */
    public static LootTable.Builder selfDrop(Block block) {
        return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0f))
                .add(LootItem.lootTableItem(block)).when(ExplosionCondition.survivesExplosion()));
    }

    /**
     * {@code recipe} for every member, saved as the member's id: a vanilla wood's through the recipe provider, a mod's
     * wood's recipe and unlock advancement as files with their load conditions.
     */
    public void recipe(DataContributions data, WoodRecipe recipe) {
        data.recipes(out -> entries.forEach((wood, e) -> {
            if (wood.isVanilla()) recipe.saveVanilla(out, wood, e.get(), e.id());
        }));
        entries.forEach((wood, e) -> {
            if (wood.isVanilla()) return;
            ResourceLocation id = e.id();
            data.json(PackOutput.Target.DATA_PACK, "recipe", id,
                    () -> WoodRecipe.encodeForMod(Recipe.CODEC, recipe.standInRecipe(wood, e.get()), wood, recipe.woodItems(wood)));
            data.json(PackOutput.Target.DATA_PACK, "advancement", recipe.advancementId(id),
                    () -> WoodRecipe.encodeForMod(net.minecraft.advancements.Advancement.CODEC,
                            recipe.standInAdvancement(wood, id).value(), wood, recipe.woodItems(wood)));
        });
    }
}
