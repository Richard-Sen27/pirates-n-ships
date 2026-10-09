package com.richardsenger.piratesnships.hazards.waves.client.surface;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.richardsenger.piratesnships.Constants;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.inventory.InventoryMenu;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * The sea surface's own copy of the animated water texture (WAV5, physical client only, render thread): one frame of
 * {@code block/water_still} in a texture of the frame's size with its mipmaps and <b>repeat</b> wrapping, so the mesh
 * can tile it once per block over a cell of any size (an atlas sprite cannot repeat inside one quad).
 *
 * <h2>Kept current</h2>
 * <ul>
 *   <li>The sheet and its animation come from the block atlas's sprite: its {@link SpriteContents} (frame size and
 *       {@code .mcmeta} animation, so a resource pack's water is used) and the sheet image read from the same resource
 *       ({@code textures/block/water_still.png}). Every frame and mip level is cut from the sheet's mipmaps made by
 *       vanilla's {@link MipmapGenerator}, exactly as the atlas makes them; the mip count is the block atlas's
 *       ({@code GL_TEXTURE_MAX_LEVEL}).</li>
 *   <li>{@link #tick} counts the ticks vanilla's sprite ticker counts (once per unpaused client tick with a level), and
 *       {@link #bindable} uploads the frame {@link WaterAnimation} names whenever it changes (every 2 ticks for vanilla's
 *       water: 16×16 pixels plus the mip levels), the mix of two frames for an interpolated animation.</li>
 *   <li>A resource reload replaces the atlas's sprite contents: the copy is rebuilt and its clock restarts at 0, like
 *       vanilla's ticker, so both run in step from there (the phase may differ by the ticks between the reload and the
 *       first frame drawn).</li>
 * </ul>
 * If the sheet cannot be read, the texture holds one plain translucent white pixel (the biome colour shows), logged once.
 */
final class SeaFrameTexture {

    private static final ResourceLocation WATER_STILL = ResourceLocation.withDefaultNamespace("block/water_still");

    private static final class Texture extends AbstractTexture {
        @Override
        public void load(net.minecraft.server.packs.resources.ResourceManager manager) {
        }
    }

    private final Texture texture = new Texture();
    private @Nullable SpriteContents source;
    private boolean allocated;
    private int width, height, mips;
    /** Per sheet image: its mip levels (null for images the animation never shows). */
    private NativeImage[][] images = new NativeImage[0][];
    private NativeImage @Nullable [] mix;
    private WaterAnimation animation = WaterAnimation.STILL;
    private long ticks;
    private long shownKey = Long.MIN_VALUE;
    private boolean warned;

    /** One of vanilla's animation ticks (client tick end, unpaused, with a level). */
    void tick() {
        ticks++;
    }

    /** The GL texture holding the current frame, uploaded if the frame changed (render thread). */
    int bindable(Minecraft mc) {
        TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER_STILL);
        if (sprite.contents() != source || !allocated) {
            rebuild(mc, sprite);
        }
        WaterAnimation.Position p = animation.at(ticks);
        long key = animation.key(p);
        if (key != shownKey) {
            shownKey = key;
            upload(p);
        }
        return texture.getId();
    }

    // ------------------------------------------------------------------ building

    private void rebuild(Minecraft mc, TextureAtlasSprite sprite) {
        freeImages();
        source = sprite.contents();
        ticks = 0;
        shownKey = Long.MIN_VALUE;
        width = source.width();
        height = source.height();
        int atlasMips = atlasMipLevels(mc);
        mips = Math.max(0, Math.min(atlasMips, Integer.numberOfTrailingZeros(Math.min(width, height) | (1 << 30))));
        animation = WaterAnimation.STILL;

        NativeImage sheet = readSheet(mc, source.name());
        if (sheet == null || sheet.getWidth() % width != 0 || sheet.getHeight() % height != 0) {
            if (sheet != null) {
                sheet.close();
            }
            if (!warned) {
                warned = true;
                Constants.LOG.warn("Sea surface: could not read the water texture {}; the surface draws in plain water colour", source.name());
            }
            width = height = 1;
            mips = 0;
            NativeImage plain = new NativeImage(1, 1, false);
            plain.setPixelRGBA(0, 0, 0xB0FFFFFF);
            images = new NativeImage[][] {{plain}};
        } else {
            int perRow = sheet.getWidth() / width;
            int count = perRow * (sheet.getHeight() / height);
            AnimationMetadataSection meta = source.metadata().getSection(AnimationMetadataSection.SERIALIZER)
                    .orElse(AnimationMetadataSection.EMPTY);
            List<int[]> explicit = new ArrayList<>();
            meta.forEachFrame((index, time) -> explicit.add(new int[] {index, time}));
            animation = WaterAnimation.of(count, meta.getDefaultFrameTime(), explicit, meta.isInterpolatedFrames());

            NativeImage[] sheetMips = MipmapGenerator.generateMipLevels(new NativeImage[] {sheet}, mips);
            images = new NativeImage[count][];
            for (int img : animation.images()) {
                if (images[img] != null) {
                    continue;
                }
                NativeImage[] levels = new NativeImage[mips + 1];
                int fx = (img % perRow) * width, fy = (img / perRow) * height;
                for (int l = 0; l <= mips; l++) {
                    int w = width >> l, h = height >> l;
                    NativeImage out = new NativeImage(w, h, false);
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            out.setPixelRGBA(x, y, sheetMips[l].getPixelRGBA((fx >> l) + x, (fy >> l) + y));
                        }
                    }
                    levels[l] = out;
                }
                images[img] = levels;
            }
            for (NativeImage m : sheetMips) {
                m.close();
            }
            if (animation.interpolate()) {
                mix = new NativeImage[mips + 1];
                for (int l = 0; l <= mips; l++) {
                    mix[l] = new NativeImage(width >> l, height >> l, false);
                }
            }
        }

        TextureUtil.prepareImage(texture.getId(), mips, width, height);
        // prepareImage left the texture bound; vanilla's upload never sets the wrap mode, the atlas clamps: repeat here
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        texture.setFilter(false, mips > 0);
        allocated = true;
    }

    private static @Nullable NativeImage readSheet(Minecraft mc, ResourceLocation sprite) {
        ResourceLocation file = sprite.withPath(path -> "textures/" + path + ".png");
        Optional<Resource> res = mc.getResourceManager().getResource(file);
        if (res.isEmpty()) {
            return null;
        }
        try (InputStream in = res.get().open()) {
            return NativeImage.read(in);
        } catch (Exception e) {
            Constants.LOG.debug("Sea surface: reading {} failed", file, e);
            return null;
        }
    }

    /** The block atlas's mip level count ({@code GL_TEXTURE_MAX_LEVEL}, which vanilla sets to it), or the option's. */
    private static int atlasMipLevels(Minecraft mc) {
        int fallback = mc.options.mipmapLevels().get();
        try {
            GlStateManager._bindTexture(mc.getTextureManager().getTexture(InventoryMenu.BLOCK_ATLAS).getId());
            int level = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL);
            return level >= 0 && level <= 8 ? level : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ uploading

    private void upload(WaterAnimation.Position p) {
        NativeImage[] levels;
        if (animation.blends(p) && mix != null) {
            NativeImage[] a = images[animation.image(p)], b = images[animation.nextImage(p)];
            double wa = animation.weight(p);
            for (int l = 0; l <= mips; l++) {
                blend(a[l], b[l], wa, mix[l]);
            }
            levels = mix;
        } else {
            levels = images[animation.image(p)];
        }
        if (levels == null) {
            return;
        }
        texture.bind();
        for (int l = 0; l < levels.length; l++) {
            levels[l].upload(l, 0, 0, 0, 0, levels[l].getWidth(), levels[l].getHeight(), mips > 0, false);
        }
    }

    /** Vanilla's frame mix ({@code SpriteContents.InterpolationData}): colour channels weighted, alpha of the first. */
    private static void blend(NativeImage a, NativeImage b, double wa, NativeImage out) {
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                int ca = a.getPixelRGBA(x, y), cb = b.getPixelRGBA(x, y);
                int c2 = (int) (wa * (ca >> 16 & 255) + (1.0 - wa) * (cb >> 16 & 255));
                int c1 = (int) (wa * (ca >> 8 & 255) + (1.0 - wa) * (cb >> 8 & 255));
                int c0 = (int) (wa * (ca & 255) + (1.0 - wa) * (cb & 255));
                out.setPixelRGBA(x, y, ca & 0xFF000000 | c2 << 16 | c1 << 8 | c0);
            }
        }
    }

    private void freeImages() {
        for (NativeImage[] levels : images) {
            if (levels != null) {
                for (NativeImage l : levels) {
                    l.close();
                }
            }
        }
        images = new NativeImage[0][];
        if (mix != null) {
            for (NativeImage m : mix) {
                m.close();
            }
            mix = null;
        }
    }
}
