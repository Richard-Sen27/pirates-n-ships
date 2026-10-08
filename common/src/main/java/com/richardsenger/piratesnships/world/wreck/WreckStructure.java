package com.richardsenger.piratesnships.world.wreck;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;
import java.util.Optional;

/**
 * A wreck on the ocean floor (design.md §10.1, WK1): one of the ST5 pieces, no jigsaw. At the chunk's centre it reads
 * the floor ({@code OCEAN_FLOOR_WG}, fluids ignored) and the sea surface ({@code getSeaLevel() - 1}), picks a piece by
 * weight among those whose {@code water_above} fits the water there ({@link WreckPlan}), turns it at random about its
 * centre and puts its seabed row (y 0) {@code depth_below_floor} (1) below the floor height, so it replaces the
 * floor's top block. Nothing fits (shallow water, land): no wreck.
 *
 * <p>Only the centre column is read: on a sloping floor the piece may stand partly in the slope or over a step. Its
 * blocks are waterlogged, the gaps keep the sea's water, so it never makes air pockets below the surface.
 */
public final class WreckStructure extends Structure {

    public static final MapCodec<WreckStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            ExtraCodecs.nonEmptyList(WreckPieceEntry.CODEC.listOf()).fieldOf("pieces").forGetter(WreckStructure::pieces),
            Codec.intRange(0, 16).optionalFieldOf("depth_below_floor", 1).forGetter(WreckStructure::depthBelowFloor)
    ).apply(i, WreckStructure::new));

    /** Salt of the frequency roll (independent of the piece and rotation random). */
    private static final long FREQUENCY_SALT = 0x3A1C_E5B7L;

    private final List<WreckPieceEntry> pieces;
    private final int depthBelowFloor;

    public WreckStructure(StructureSettings settings, List<WreckPieceEntry> pieces, int depthBelowFloor) {
        super(settings);
        this.pieces = List.copyOf(pieces);
        this.depthBelowFloor = depthBelowFloor;
    }

    public List<WreckPieceEntry> pieces() {
        return pieces;
    }

    public int depthBelowFloor() {
        return depthBelowFloor;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!WorldConfig.WRECK_ENABLED.get()) return Optional.empty();
        double frequency = WorldConfig.WRECK.frequency().get();
        if (frequency < 1.0) {
            WorldgenRandom roll = new WorldgenRandom(new LegacyRandomSource(0L));
            roll.setLargeFeatureSeed(context.seed() ^ FREQUENCY_SALT, context.chunkPos().x, context.chunkPos().z);
            if (roll.nextDouble() >= frequency) return Optional.empty();
        }
        ChunkGenerator generator = context.chunkGenerator();
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        int floor = generator.getFirstFreeHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
        return plan(context.structureTemplateManager(), x, z, floor, generator.getSeaLevel() - 1, context.random());
    }

    /**
     * The wreck centred on column ({@code x}, {@code z}) whose first free height (fluids ignored) is
     * {@code floorHeight}, with the sea surface (topmost water block) at {@code seaSurface}: a piece by weight, then a
     * rotation, both from {@code random}. Checks the {@code enabled} toggle, but not the frequency roll. GameTests call
     * this with a hand-built floor.
     */
    public Optional<GenerationStub> plan(StructureTemplateManager templates, int x, int z, int floorHeight, int seaSurface,
                                         RandomSource random) {
        if (!WorldConfig.WRECK_ENABLED.get()) return Optional.empty();
        int seabed = WreckPlan.seabedY(floorHeight, depthBelowFloor);
        Optional<WreckPieceEntry> piece = WreckPlan.choose(pieces, WreckPlan.waterAbove(seaSurface, seabed), random);
        if (piece.isEmpty()) return Optional.empty();
        Rotation rotation = Rotation.getRandom(random);
        return Optional.of(stub(templates, piece.get().template(), rotation, new BlockPos(x, seabed, z)));
    }

    /** A wreck of {@code template} turned by {@code rotation} with its centre on its seabed row at {@code centre}. */
    public static GenerationStub stub(StructureTemplateManager templates, ResourceLocation template,
                                      Rotation rotation, BlockPos centre) {
        BlockPos origin = WreckPiece.templatePosition(templates.getOrCreate(template).getSize(), centre);
        return new GenerationStub(centre, builder -> builder.addPiece(new WreckPiece(templates, template, origin, rotation)));
    }

    @Override
    public StructureType<?> type() {
        return WreckStructures.WRECK_TYPE.get();
    }
}
