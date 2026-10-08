package com.richardsenger.piratesnships.world.wreck;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * One wreck piece, placed rigidly at the height fixed when the structure was planned (no terrain adaptation, no
 * per-chunk height lookup). It turns about the template's horizontal centre on the seabed row, so the piece stays
 * centred on the planned point whatever the rotation.
 *
 * <p><b>No neighbour-shape pass</b> ({@code knownShape = true}, as vanilla's jigsaw pieces): world generation places
 * a piece one chunk at a time, and vanilla's pass after each chunk would see the next chunk's half of the piece still
 * missing and drop blocks attached across the border (a cleat on a mast in the next chunk, a lantern under a beam).
 * Every block of the template is placed as it is; fences, panes and iron bars get their connections from the whole
 * template through {@link WreckConnectionsProcessor} instead.
 *
 * <p>Processors: {@link BlockIgnoreProcessor#STRUCTURE_AND_AIR} (the templates hold no air or structure blocks anyway)
 * and {@link WreckConnectionsProcessor}. Liquid settings {@link LiquidSettings#APPLY_WATERLOGGING} (vanilla's default): the templates' own
 * {@code waterlogged=true} states are kept, and blocks placed into the sea take the water as well. Chest block entity
 * data (the {@code chests/wreck} loot table) is loaded as is; vanilla only adds a loot seed.
 */
public final class WreckPiece extends TemplateStructurePiece {

    public WreckPiece(StructureTemplateManager templates, ResourceLocation template, BlockPos templatePosition, Rotation rotation) {
        super(WreckStructures.WRECK_PIECE.get(), 0, templates, template, template.toString(),
                settings(templates, template, rotation), templatePosition);
    }

    public WreckPiece(StructureTemplateManager templates, CompoundTag tag) {
        super(WreckStructures.WRECK_PIECE.get(), tag, templates,
                template -> settings(templates, template, Rotation.valueOf(tag.getString("Rot"))));
    }

    /** The template's id. */
    public ResourceLocation templateId() {
        return makeTemplateLocation();
    }

    /** The rotation pivot: the template's horizontal centre on its seabed row. */
    public static BlockPos pivot(Vec3i size) {
        return new BlockPos(size.getX() / 2, 0, size.getZ() / 2);
    }

    /** Where the template's origin goes so that its {@link #pivot} lands on {@code centre} (y: the seabed row). */
    public static BlockPos templatePosition(Vec3i size, BlockPos centre) {
        BlockPos pivot = pivot(size);
        return new BlockPos(centre.getX() - pivot.getX(), centre.getY(), centre.getZ() - pivot.getZ());
    }

    private static StructurePlaceSettings settings(StructureTemplateManager templates, ResourceLocation template, Rotation rotation) {
        return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setRotationPivot(pivot(templates.getOrCreate(template).getSize()))
                .setLiquidSettings(LiquidSettings.APPLY_WATERLOGGING)
                .setKnownShape(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR)
                .addProcessor(WreckConnectionsProcessor.INSTANCE);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", placeSettings.getRotation().name());
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
        // the wreck templates have no data markers; their chests carry the loot table in their own data
    }
}
