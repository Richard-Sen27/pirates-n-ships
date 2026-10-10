package com.richardsenger.piratesnships.world.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.core.Vec3i;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Placement type {@code pirates_n_ships:configured_spread} (WG5): vanilla's random spread whose spacing and separation
 * come from the server config section {@code world.structures.<config>} ({@link WorldConfig#spread}) while that
 * config is loaded, and from the structure set's own {@code spacing} and {@code separation} otherwise (before the
 * server config loads, in JUnit). Everything else is vanilla's: salt, spread type, frequency, frequency reduction
 * method, locate offset and exclusion zone are read from the JSON as for {@code minecraft:random_spread}.
 *
 * <pre>{@code
 * "placement": {
 *   "type": "pirates_n_ships:configured_spread",
 *   "config": "seafarer_village",
 *   "salt": 584103926, "spacing": 30, "separation": 10, "spread_type": "linear", "frequency": 1.0
 * }
 * }</pre>
 *
 * <p>It extends {@link RandomSpreadStructurePlacement} and answers {@link #spacing()}, {@link #separation()} and
 * {@link #getPotentialStructureChunk} with the configured numbers, so {@code /locate}, treasure and explorer maps and
 * every other vanilla search over random spreads (they step by {@code spacing()} and ask
 * {@code getPotentialStructureChunk}) see the same grid as chunk generation.
 *
 * <p><b>A value changed after world creation only affects chunks not generated yet</b>: structures already placed
 * stay where they are, and the new grid applies at the edge of the explored world (a changed spacing can put a new
 * structure next to an old one at that edge). A datapack that wants its own spacing regardless of the config uses
 * {@code minecraft:random_spread} instead.
 */
public final class ConfiguredSpreadPlacement extends RandomSpreadStructurePlacement {

    /** The config section names a structure set may name in {@code config} ({@link WorldConfig#spread}). */
    private static final Codec<String> CONFIG_NAME = Codec.STRING.validate(name -> WorldConfig.spread(name).isPresent()
            ? DataResult.success(name)
            : DataResult.error(() -> "Unknown structure spread config '" + name + "', expected one of " + WorldConfig.spreadNames()));

    /** Vanilla's random-spread fields (the same names and defaults) plus {@code config}. */
    @SuppressWarnings("deprecation")
    public static final MapCodec<ConfiguredSpreadPlacement> CODEC = RecordCodecBuilder.<ConfiguredSpreadPlacement>mapCodec(
                    i -> i.group(
                                    Vec3i.offsetCodec(16).optionalFieldOf("locate_offset", Vec3i.ZERO)
                                            .forGetter(ConfiguredSpreadPlacement::locateOffset),
                                    StructurePlacement.FrequencyReductionMethod.CODEC
                                            .optionalFieldOf("frequency_reduction_method", StructurePlacement.FrequencyReductionMethod.DEFAULT)
                                            .forGetter(ConfiguredSpreadPlacement::frequencyReductionMethod),
                                    Codec.floatRange(0.0F, 1.0F).optionalFieldOf("frequency", 1.0F)
                                            .forGetter(ConfiguredSpreadPlacement::frequency),
                                    ExtraCodecs.NON_NEGATIVE_INT.fieldOf("salt").forGetter(ConfiguredSpreadPlacement::salt),
                                    StructurePlacement.ExclusionZone.CODEC.optionalFieldOf("exclusion_zone")
                                            .forGetter(ConfiguredSpreadPlacement::exclusionZone),
                                    Codec.intRange(0, 4096).fieldOf("spacing").forGetter(ConfiguredSpreadPlacement::jsonSpacing),
                                    Codec.intRange(0, 4096).fieldOf("separation").forGetter(ConfiguredSpreadPlacement::jsonSeparation),
                                    RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR)
                                            .forGetter(ConfiguredSpreadPlacement::spreadType),
                                    CONFIG_NAME.fieldOf("config").forGetter(ConfiguredSpreadPlacement::config))
                            .apply(i, ConfiguredSpreadPlacement::new))
            .validate(p -> p.jsonSpacing() <= p.jsonSeparation()
                    ? DataResult.error(() -> "Spacing has to be larger than separation")
                    : DataResult.success(p));

    private final int jsonSpacing;
    private final int jsonSeparation;
    private final String config;
    private final @Nullable WorldConfig.Spread spread;

    public ConfiguredSpreadPlacement(Vec3i locateOffset, StructurePlacement.FrequencyReductionMethod frequencyReductionMethod,
                                     float frequency, int salt, Optional<StructurePlacement.ExclusionZone> exclusionZone,
                                     int spacing, int separation, RandomSpreadType spreadType, String config) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, spacing, separation, spreadType);
        this.jsonSpacing = spacing;
        this.jsonSeparation = separation;
        this.config = config;
        this.spread = WorldConfig.spread(config).orElse(null);
    }

    /** A plain placement (no offset, default frequency reduction, frequency 1, no exclusion zone), for tests. */
    public ConfiguredSpreadPlacement(int spacing, int separation, RandomSpreadType spreadType, int salt, String config) {
        this(Vec3i.ZERO, StructurePlacement.FrequencyReductionMethod.DEFAULT, 1.0F, salt, Optional.empty(), spacing, separation,
                spreadType, config);
    }

    /** The structure set's own spacing, the fallback while the config is not loaded. */
    public int jsonSpacing() {
        return jsonSpacing;
    }

    /** The structure set's own separation, the fallback while the config is not loaded. */
    public int jsonSeparation() {
        return jsonSeparation;
    }

    /** The config section name ({@code seafarer_village}, {@code pirate_island}, {@code navy_outpost}, {@code wreck}). */
    public String config() {
        return config;
    }

    /** The grid in force now: the config's numbers while it is loaded (or overridden in a test), else the JSON's. */
    public SpreadGrid grid() {
        if (spread == null) return new SpreadGrid(jsonSpacing, jsonSeparation);
        return SpreadGrid.resolve(jsonSpacing, jsonSeparation, configured(spread.spacing()), configured(spread.separation()));
    }

    private static @Nullable Integer configured(ConfigValue<Integer> value) {
        return value.isLive() || value.hasOverride() ? value.get() : null;
    }

    @Override
    public int spacing() {
        return grid().spacing();
    }

    @Override
    public int separation() {
        return grid().separation();
    }

    @Override
    public ChunkPos getPotentialStructureChunk(long seed, int regionX, int regionZ) {
        return grid().potentialChunk(seed, regionX, regionZ, salt(), spreadType());
    }

    /** The structure set's salt (public for the tests; vanilla's accessor is protected). */
    @Override
    public int salt() {
        return super.salt();
    }

    @Override
    public StructurePlacementType<?> type() {
        return PortStructures.CONFIGURED_SPREAD.get();
    }
}
