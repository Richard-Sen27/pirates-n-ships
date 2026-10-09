package com.richardsenger.piratesnships.almanac.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.almanac.data.PageElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the Almanac's 3D spotlights (ALM10; client only). One per {@link AlmanacScreen}: the models are built on first
 * sight and dropped by {@link #close()} when the screen closes. Each model is measured once (its vertices captured with
 * no transform, {@link Spotlight3dView.Bounds}) and then drawn turning about its vertical axis through its centre at
 * {@link Spotlight3dView#angle}, leaning toward the reader by {@link Spotlight3dView#TILT_DEGREES}, scaled by
 * {@link Spotlight3dView#fitScale} to its card, lit as the inventory lights entities, full bright, and clipped to the
 * card with a scissor:
 * <ul>
 *   <li>a block: its default state's block model (when it has one) and its block entity's renderer on a block entity
 *       made for it here (the capstan's bars, the helm's wheel, the cannon's barrel); a two-high block (a door) shows
 *       both halves;</li>
 *   <li>an item: its model as an item frame shows it ({@link ItemDisplayContext#FIXED});</li>
 *   <li>an entity: a client-side instance of the type, never added to the level, facing the reader with its head
 *       straight (it follows nothing), drawn through the entity render dispatcher as
 *       {@code InventoryScreen.renderEntityInInventory} does (its own renderer: a GeckoLib mob through its
 *       {@code GeoEntityRenderer}); its tick count runs with the screen so animated mobs play their idle animation.</li>
 * </ul>
 * A model that fails to build or measure is logged once and drawn as nothing; the card and the name still show.
 */
final class Spotlight3dRenderer {

    /** Above the page and its items (z 0), below tooltips (z 400); the model's depth stays within the card's scale. */
    private static final float Z = 150f;
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    private record Key(PageElement.Spotlight3d.Subject subject, net.minecraft.resources.ResourceLocation id) {
    }

    /** A built model: draws itself at the origin of the pose in its own units. */
    private interface Model {
        void render(PoseStack pose, MultiBufferSource buffers, float partialTick);

        /** The pose axes: entities are drawn as the inventory draws them, y down, blocks and items y up. */
        boolean entity();

        default void tick() {
        }

        default void discard() {
        }
    }

    private record Built(@Nullable Model model, Spotlight3dView.Bounds bounds) {
    }

    private final Map<Key, Built> built = new HashMap<>();

    /** The card's name: the block's, item's or entity type's name. */
    static Component name(PageElement.Spotlight3d s) {
        return switch (s.subject()) {
            case BLOCK -> BuiltInRegistries.BLOCK.get(s.id()).getName();
            case ITEM -> new ItemStack(BuiltInRegistries.ITEM.get(s.id())).getHoverName();
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.get(s.id()).getDescription();
        };
    }

    /** One screen tick: animated mobs advance. */
    void tick() {
        for (Built b : built.values()) if (b.model() != null) b.model().tick();
    }

    /** Drops every model (the screen closed). */
    void close() {
        for (Built b : built.values()) if (b.model() != null) b.model().discard();
        built.clear();
    }

    /**
     * Draws {@code s} turning in the box {@code x, y, w, h} of the current pose (book pixels on the page), clipped to
     * the GUI rectangle {@code scissor} ({@code x0, y0, x1, y1}).
     */
    void draw(GuiGraphics g, PageElement.Spotlight3d s, int x, int y, int w, int h, int[] scissor, long ticks, float partialTick) {
        Built b = built(s);
        if (b.model() == null) return;
        float scale = Spotlight3dView.fitScale(b.bounds(), w - 2 * Spotlight3dView.MARGIN, h - 2 * Spotlight3dView.MARGIN);
        if (scale <= 0f) return;
        Model model = b.model();
        Spotlight3dView.Bounds bounds = b.bounds();
        float tilt = (float) Math.toRadians(Spotlight3dView.TILT_DEGREES);
        float spin = (float) Math.toRadians(Spotlight3dView.angle(ticks, partialTick));

        // a fresh stack on the page's transform: a renderer that throws cannot leave the screen's pose unbalanced
        PoseStack pose = new PoseStack();
        pose.last().pose().set(g.pose().last().pose());
        pose.last().normal().set(g.pose().last().normal());
        pose.translate(x + w / 2f, y + h / 2f, Z);
        Quaternionf turn;
        if (model.entity()) {
            // InventoryScreen.renderEntityInInventory: z flipped, then the model turned upside up about z
            pose.scale(scale, scale, -scale);
            turn = new Quaternionf().rotateX(tilt).rotateZ((float) Math.PI).rotateY(spin);
        } else {
            // GuiGraphics.renderItem: y flipped so the model's up is the screen's up
            pose.scale(scale, -scale, scale);
            turn = new Quaternionf().rotateX(tilt).rotateY(spin);
        }
        pose.mulPose(turn);
        pose.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());

        g.flush();
        g.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (model.entity()) {
            dispatcher.overrideCameraOrientation(new Quaternionf().rotateX(tilt).conjugate().rotateY((float) Math.PI));
            dispatcher.setRenderShadow(false);
        }
        try {
            RenderSystem.runAsFancy(() -> model.render(pose, g.bufferSource(), partialTick));
            g.flush();
        } catch (RuntimeException e) {
            Constants.LOG.warn("Almanac 3D spotlight {} {} failed to draw; it is left empty: {}", s.subject().getSerializedName(), s.id(), e.toString());
            built.put(new Key(s.subject(), s.id()), new Built(null, Spotlight3dView.Bounds.EMPTY));
        } finally {
            if (model.entity()) dispatcher.setRenderShadow(true);
            g.disableScissor();
            Lighting.setupFor3DItems();
        }
    }

    private Built built(PageElement.Spotlight3d s) {
        return built.computeIfAbsent(new Key(s.subject(), s.id()), k -> {
            Model model = null;
            try {
                model = build(k);
                if (model == null) return new Built(null, Spotlight3dView.Bounds.EMPTY);
                Spotlight3dView.Bounds bounds = measure(model);
                if (bounds.isEmpty()) {
                    Constants.LOG.warn("Almanac 3D spotlight {} {} draws nothing", k.subject().getSerializedName(), k.id());
                }
                return new Built(model, bounds);
            } catch (RuntimeException e) {
                Constants.LOG.warn("Almanac 3D spotlight {} {} could not be built; it is left empty: {}", k.subject().getSerializedName(), k.id(), e.toString());
                if (model != null) model.discard();
                return new Built(null, Spotlight3dView.Bounds.EMPTY);
            }
        });
    }

    @Nullable
    private static Model build(Key k) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        return switch (k.subject()) {
            case BLOCK -> {
                Block block = BuiltInRegistries.BLOCK.getOptional(k.id()).orElse(null);
                yield block == null ? null : new BlockModel(block, level);
            }
            case ITEM -> {
                ItemStack stack = BuiltInRegistries.ITEM.getOptional(k.id()).map(ItemStack::new).orElse(ItemStack.EMPTY);
                yield stack.isEmpty() ? null : new ItemModel(stack, level);
            }
            case ENTITY -> {
                if (level == null) yield null;
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(k.id()).orElse(null);
                Entity entity = type == null ? null : type.create(level);
                yield entity == null ? null : new MobModel(entity);
            }
        };
    }

    /** The model's extent: its vertices captured with no transform at all. */
    private static Spotlight3dView.Bounds measure(Model model) {
        BoundsCapture capture = new BoundsCapture();
        PoseStack pose = new PoseStack();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (model.entity()) dispatcher.setRenderShadow(false);
        try {
            model.render(pose, capture, 0f);
        } finally {
            if (model.entity()) dispatcher.setRenderShadow(true);
        }
        return capture.bounds();
    }

    // ------------------------------------------------------------------ models

    /** A block's default state: its block model and its block entity's renderer; a door with both halves. */
    private static final class BlockModel implements Model {
        private final List<BlockState> states = new ArrayList<>();
        private final List<BlockEntity> entities = new ArrayList<>();

        BlockModel(Block block, @Nullable ClientLevel level) {
            BlockState state = block.defaultBlockState();
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                states.add(state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
                states.add(state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            } else {
                states.add(state);
            }
            for (int i = 0; i < states.size(); i++) {
                BlockEntity be = block instanceof EntityBlock eb ? eb.newBlockEntity(BlockPos.ZERO.above(i), states.get(i)) : null;
                if (be != null && level != null) be.setLevel(level);
                entities.add(be);
            }
        }

        @Override
        public boolean entity() {
            return false;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void render(PoseStack pose, MultiBufferSource buffers, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            for (int i = 0; i < states.size(); i++) {
                BlockState state = states.get(i);
                pose.pushPose();
                pose.translate(0f, i, 0f);
                if (state.getRenderShape() == RenderShape.MODEL) {
                    mc.getBlockRenderer().renderSingleBlock(state, pose, buffers, LIGHT, OverlayTexture.NO_OVERLAY);
                }
                BlockEntity be = entities.get(i);
                if (be != null) {
                    BlockEntityRenderer renderer = mc.getBlockEntityRenderDispatcher().getRenderer(be);
                    if (renderer != null) renderer.render(be, partialTick, pose, buffers, LIGHT, OverlayTexture.NO_OVERLAY);
                }
                pose.popPose();
            }
        }
    }

    /** An item's model as an item frame shows it. */
    private record ItemModel(ItemStack stack, @Nullable ClientLevel level) implements Model {

        @Override
        public boolean entity() {
            return false;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, float partialTick) {
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LIGHT, OverlayTexture.NO_OVERLAY,
                    pose, buffers, level, 0);
        }
    }

    /** A client-side mob facing the reader, its head straight; never added to the level. */
    private static final class MobModel implements Model {
        private final Entity entity;

        MobModel(Entity entity) {
            this.entity = entity;
            entity.setYRot(180f);
            entity.yRotO = 180f;
            entity.setXRot(0f);
            entity.xRotO = 0f;
            if (entity instanceof LivingEntity living) {
                living.yBodyRot = 180f;
                living.yBodyRotO = 180f;
                living.yHeadRot = 180f;
                living.yHeadRotO = 180f;
            }
        }

        @Override
        public boolean entity() {
            return true;
        }

        @Override
        public void tick() {
            entity.tickCount++;
        }

        @Override
        public void discard() {
            entity.discard();
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, float partialTick) {
            Minecraft.getInstance().getEntityRenderDispatcher().render(entity, 0.0, 0.0, 0.0, 0f, partialTick, pose, buffers, LIGHT);
        }
    }

    // ------------------------------------------------------------------ measuring

    /** Takes every vertex of every render type and keeps the box around their positions. */
    private static final class BoundsCapture implements MultiBufferSource, VertexConsumer {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;

        Spotlight3dView.Bounds bounds() {
            if (minX > maxX) return Spotlight3dView.Bounds.EMPTY;
            return new Spotlight3dView.Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return this;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)) {
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }
    }
}
