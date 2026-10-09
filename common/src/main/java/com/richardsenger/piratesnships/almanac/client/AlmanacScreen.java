package com.richardsenger.piratesnships.almanac.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.almanac.AlmanacPayloads;
import com.richardsenger.piratesnships.almanac.data.AlmanacBook;
import com.richardsenger.piratesnships.almanac.data.AlmanacProgress;
import com.richardsenger.piratesnships.almanac.data.Chapter;
import com.richardsenger.piratesnships.almanac.data.Page;
import com.richardsenger.piratesnships.almanac.data.PageElement;
import com.richardsenger.piratesnships.almanac.sync.ClientAlmanac;
import com.richardsenger.piratesnships.core.client.gui.GuiKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Captain's Almanac's book screen (client only, docs/design.md §3.4, ALM3): a two-page parchment spread with the
 * chapter bookmarks down its left edge and, when that is full, its right edge ({@link Bookmarks}). Everything is drawn in book pixels ({@link #BOOK_W} x {@link #BOOK_H}) under one
 * pose scale, so the layout ({@link PageLayout}) never depends on the window; the scale is the client config
 * {@code almanac_screen.scale}, shrunk to fit small windows. Pages turn with the page corners, the arrow and page
 * keys, the mouse wheel, a bookmark or a link, animated by {@link PageTurn} with vanilla's page-turn sound; input waits
 * while a page turns. Every page change is reported through {@link ClientAlmanac#reportPage}.
 */
public final class AlmanacScreen extends Screen {

    static final ResourceLocation BOOK = Constants.id("textures/gui/almanac/book.png");
    static final ResourceLocation BOOKMARK = Constants.id("textures/gui/almanac/bookmark.png");
    private static final ResourceLocation PAGE_FORWARD = ResourceLocation.withDefaultNamespace("widget/page_forward");
    private static final ResourceLocation PAGE_FORWARD_HOVER = ResourceLocation.withDefaultNamespace("widget/page_forward_highlighted");
    private static final ResourceLocation PAGE_BACKWARD = ResourceLocation.withDefaultNamespace("widget/page_backward");
    private static final ResourceLocation PAGE_BACKWARD_HOVER = ResourceLocation.withDefaultNamespace("widget/page_backward_highlighted");

    /** The book texture's size and geometry (tools/gen_almanac_textures.py draws the same). */
    static final int BOOK_W = 292;
    static final int BOOK_H = 180;
    static final int SPINE_X = 146;
    static final int PAGE_TOP = 5;
    static final int PAGE_BOTTOM = 172;
    static final int LEFT_PAGE_X = 9;
    static final int RIGHT_PAGE_END = 283;
    static final int LEFT_PAGE_W = SPINE_X - LEFT_PAGE_X;
    static final int RIGHT_PAGE_W = RIGHT_PAGE_END - SPINE_X;
    /** The text box of each page. */
    static final int CONTENT_W = 116;
    static final int CONTENT_H = 140;
    static final int CONTENT_TOP = 15;
    static final int LEFT_CONTENT_X = 19;
    static final int RIGHT_CONTENT_X = 157;
    static final int PAGE_NUMBER_Y = 159;
    /** Vanilla's page arrows. */
    static final int ARROW_W = 23;
    static final int ARROW_H = 13;
    static final int ARROW_Y = PAGE_BOTTOM - ARROW_H - 3;
    static final int RIGHT_ARROW_X = RIGHT_PAGE_END - ARROW_W - 4;
    static final int LEFT_ARROW_X = LEFT_PAGE_X + 4;
    /** Bookmarks run down the left edge, then the right edge, between these. */
    static final int TABS_TOP = 8;
    static final int TABS_BOTTOM = 172;
    /** Room kept on both sides for the bookmarks, so the book stays centred. */
    static final int SIDE_ROOM = Bookmarks.TAB_W - Bookmarks.UNDER_COVER + Bookmarks.SELECTED_OUT;
    /** Turning pages draw above the items of the pages under them. */
    private static final float TURN_Z = 300f;
    private static final int LINK_COLOR = GuiKit.INK_NAVY;
    private static final int BUTTON_EDGE = 0xFFA08054;
    private static final int BUTTON_FACE = 0xFFEEE0BC;
    private static final int BUTTON_FACE_HOVER = 0xFFF8EED4;
    private static final int PLACEHOLDER = 0x40A08054;
    /** Alpha strips of the fold's shade; 32 keep the steps below a visible band on the mottled parchment (ART14). */
    private static final int SHADE_STRIPS = 32;

    private final AlmanacBook book;
    private final List<Chapter> chapters;
    private final boolean empty;
    /** The server's open request; handed back to {@link AlmanacClient#closed} so a placed tome closes. */
    private final AlmanacPayloads.Open request;
    private final AlmanacProgress startAt;
    private final Map<Integer, List<PageLayout.ScreenPage>> pages = new HashMap<>();
    private final Map<ResourceLocation, int[]> imageSizes = new HashMap<>();
    private final Map<ResourceLocation, ItemStack> stacks = new HashMap<>();
    private final PageTurn turn = new PageTurn();
    private final BookNavigator nav;
    private BookNavigator.Spread spread;
    private BookNavigator.Spread target;
    private List<Bookmarks.Tab> tabs = List.of();
    private float scale = 1f;
    private float left;
    private float top;
    private boolean started;

    AlmanacScreen(AlmanacBook book, AlmanacProgress progress, AlmanacPayloads.Open request) {
        super(Component.translatable(AlmanacScreenText.TITLE));
        this.book = book;
        this.empty = book.isEmpty();
        this.chapters = empty ? List.of(emptyChapter()) : book.chapters();
        this.request = request;
        this.startAt = progress;
        this.nav = new BookNavigator(chapters.size(), c -> pages(c).size());
        this.spread = nav.first();
        this.target = spread;
    }

    /** Opens the book for {@code request} at the page the player last read ({@link ClientAlmanac#progress()}). */
    static void show(AlmanacPayloads.Open request) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            AlmanacClient.closed(request);
            return;
        }
        mc.setScreen(new AlmanacScreen(ClientAlmanac.book(), ClientAlmanac.progress(), request));
    }

    /** The single page shown when no chapter reached the client. */
    private static Chapter emptyChapter() {
        return new Chapter(Constants.id("empty"), AlmanacScreenText.TITLE, 0, Chapter.DEFAULT_BOOKMARK_COLOR,
                BuiltInRegistries.ITEM.getKey(Items.BOOK), List.of(Page.of(PageElement.Text.of(AlmanacScreenText.EMPTY))));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        // the layout depends on the font and the language, which may have changed since the last init
        pages.clear();
        double fit = Math.min((width - 16.0) / (BOOK_W + 2.0 * SIDE_ROOM), (height - 16.0) / BOOK_H);
        scale = (float) Math.max(0.25, Math.min(AlmanacScreenConfig.SCALE.get(), fit));
        left = (width - BOOK_W * scale) / 2f;
        top = (height - BOOK_H * scale) / 2f;
        if (!started) {
            started = true;
            // the last page read; a chapter the book no longer has opens the book at its start
            if (!empty && startAt.chapter().isPresent()) {
                for (int i = 0; i < chapters.size(); i++) {
                    if (chapters.get(i).id().equals(startAt.chapter().get())) spread = nav.clamp(i, startAt.page());
                }
            }
        }
        spread = nav.clamp(spread.chapter(), spread.page());
        target = nav.clamp(target.chapter(), target.page());
        layoutTabs();
    }

    private void layoutTabs() {
        tabs = empty ? List.of() : Bookmarks.layout(chapters.size(), shownChapter(), 0, BOOK_W, TABS_TOP,
                TABS_BOTTOM - TABS_TOP);
    }

    /** The chapter whose bookmark is drawn as open: the one being turned to while a page turns. */
    private int shownChapter() {
        return turn.turning() ? target.chapter() : spread.chapter();
    }

    // ------------------------------------------------------------------ pages

    private List<PageLayout.ScreenPage> pages(int chapter) {
        return pages.computeIfAbsent(chapter, c -> PageLayout.layoutChapter(chapters.get(c), CONTENT_W, CONTENT_H,
                this::measure, new Lang()));
    }

    private int measure(String text, boolean bold, boolean italic) {
        return font.width(FormattedText.of(text, Style.EMPTY.withBold(bold).withItalic(italic)));
    }

    private final class Lang implements PageLayout.TextSource {
        @Override
        public String text(String key) {
            return I18n.get(key);
        }

        @Override
        public String chapterTitle(ResourceLocation chapter) {
            return book.chapter(chapter).map(c -> I18n.get(c.title())).orElse(chapter.toString());
        }
    }

    @Nullable
    private PageLayout.ScreenPage page(BookNavigator.Spread s, int offset) {
        List<PageLayout.ScreenPage> list = pages(s.chapter());
        int i = s.page() + offset;
        return i >= 0 && i < list.size() ? list.get(i) : null;
    }

    // ------------------------------------------------------------------ turning

    private void turnTo(BookNavigator.Spread to) {
        if (turn.turning() || to.equals(spread)) return;
        PageTurn.Direction dir = to.compareTo(spread) > 0 ? PageTurn.Direction.FORWARD : PageTurn.Direction.BACK;
        if (AlmanacScreenConfig.PAGE_TURN_SOUND.get()) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        }
        target = to;
        if (!turn.start(dir, AlmanacScreenConfig.PAGE_TURN_TICKS.get())) settle(to);
        layoutTabs();
    }

    private void settle(BookNavigator.Spread s) {
        spread = s;
        target = s;
        layoutTabs();
        if (!empty) ClientAlmanac.reportPage(chapters.get(s.chapter()).id(), s.page());
    }

    private void next() {
        nav.next(spread).ifPresent(this::turnTo);
    }

    private void previous() {
        nav.previous(spread).ifPresent(this::turnTo);
    }

    private void openChapter(ResourceLocation id) {
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).id().equals(id)) {
                turnTo(new BookNavigator.Spread(i, 0));
                return;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (turn.tick()) settle(target);
    }

    @Override
    public void removed() {
        if (turn.finishNow()) settle(target);
        AlmanacClient.closed(request);
        super.removed();
    }

    // ------------------------------------------------------------------ input

    private double bookX(double mouseX) {
        return (mouseX - left) / scale;
    }

    private double bookY(double mouseY) {
        return (mouseY - top) / scale;
    }

    private boolean onRightCorner(double bx, double by) {
        return bx >= RIGHT_ARROW_X && bx < RIGHT_PAGE_END + 3 && by >= ARROW_Y - 4 && by < PAGE_BOTTOM + 3;
    }

    private boolean onLeftCorner(double bx, double by) {
        return bx >= LEFT_PAGE_X - 3 && bx < LEFT_ARROW_X + ARROW_W && by >= ARROW_Y - 4 && by < PAGE_BOTTOM + 3;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (turn.turning()) return true;
        double bx = bookX(mouseX), by = bookY(mouseY);
        int tab = Bookmarks.hit(tabs, bx, by);
        if (tab >= 0) {
            turnTo(new BookNavigator.Spread(tab, 0));
            return true;
        }
        if (onRightCorner(bx, by) && nav.next(spread).isPresent()) {
            next();
            return true;
        }
        if (onLeftCorner(bx, by) && nav.previous(spread).isPresent()) {
            previous();
            return true;
        }
        if (clickPage(page(spread, 0), bx - LEFT_CONTENT_X, by - CONTENT_TOP)) return true;
        if (clickPage(page(spread, 1), bx - RIGHT_CONTENT_X, by - CONTENT_TOP)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickPage(@Nullable PageLayout.ScreenPage page, double px, double py) {
        if (page == null) return false;
        for (PageLayout.Placed p : page.elements()) {
            if (p instanceof PageLayout.TextRun run && run.style().link() != null && run.area().contains(px, py)) {
                openChapter(run.style().link());
                return true;
            }
            if (p instanceof PageLayout.Button b && b.area().contains(px, py)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                AlmanacSceneOpener.open(b.scene());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case InputConstants.KEY_RIGHT, InputConstants.KEY_PAGEDOWN -> {
                if (!turn.turning()) next();
                return true;
            }
            case InputConstants.KEY_LEFT, InputConstants.KEY_PAGEUP -> {
                if (!turn.turning()) previous();
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (turn.turning() || scrollY == 0) return true;
        if (scrollY < 0) next();
        else previous();
        return true;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        double bx = bookX(mouseX), by = bookY(mouseY);
        boolean still = !turn.turning();
        g.pose().pushPose();
        g.pose().translate(left, top, 0f);
        g.pose().scale(scale, scale, 1f);
        int hoverTab = still ? Bookmarks.hit(tabs, bx, by) : -1;
        drawTabs(g, hoverTab);
        RenderSystem.enableBlend();
        g.blit(BOOK, 0, 0, 0, 0, BOOK_W, BOOK_H, BOOK_W, BOOK_H);
        Hover hover = new Hover();
        if (still) {
            drawPage(g, spread, 0, LEFT_CONTENT_X, bx, by, hover);
            drawPage(g, spread, 1, RIGHT_CONTENT_X, bx, by, hover);
            if (nav.next(spread).isPresent()) {
                g.blitSprite(onRightCorner(bx, by) ? PAGE_FORWARD_HOVER : PAGE_FORWARD, RIGHT_ARROW_X, ARROW_Y, ARROW_W, ARROW_H);
            }
            if (nav.previous(spread).isPresent()) {
                g.blitSprite(onLeftCorner(bx, by) ? PAGE_BACKWARD_HOVER : PAGE_BACKWARD, LEFT_ARROW_X, ARROW_Y, ARROW_W, ARROW_H);
            }
        } else {
            drawTurn(g, turn.progress(partialTick));
        }
        g.pose().popPose();
        if (hoverTab >= 0) {
            g.renderTooltip(font, Component.translatable(chapters.get(hoverTab).title()), mouseX, mouseY);
        } else if (hover.item != null) {
            g.renderTooltip(font, stack(hover.item), mouseX, mouseY);
        } else if (still && onRightCorner(bx, by) && nav.next(spread).isPresent()) {
            g.renderTooltip(font, Component.translatable(AlmanacScreenText.NEXT), mouseX, mouseY);
        } else if (still && onLeftCorner(bx, by) && nav.previous(spread).isPresent()) {
            g.renderTooltip(font, Component.translatable(AlmanacScreenText.PREVIOUS), mouseX, mouseY);
        }
    }

    /** What the mouse is over on a page, for the tooltip drawn after the book. */
    private static final class Hover {
        @Nullable
        ResourceLocation item;
    }

    private void drawTabs(GuiGraphics g, int hover) {
        for (Bookmarks.Tab t : tabs) {
            Chapter c = chapters.get(t.chapter());
            int rgb = c.bookmarkColor();
            float lift = t.chapter() == hover ? 1.15f : 1f;
            RenderSystem.setShaderColor(Math.min(1f, ((rgb >> 16) & 0xFF) / 255f * lift),
                    Math.min(1f, ((rgb >> 8) & 0xFF) / 255f * lift), Math.min(1f, (rgb & 0xFF) / 255f * lift), 1f);
            // a hovered tab reaches one pixel further out on its own side
            int extra = t.chapter() == hover ? 1 : 0;
            boolean right = t.side() == Bookmarks.Side.RIGHT;
            int x = right ? t.x() : t.x() - extra;
            // the texture's swallowtail points right; a left tab draws it mirrored through the UV (u runs backwards)
            g.blit(BOOKMARK, x, t.y(), t.w() + extra, t.h(), right ? 0 : Bookmarks.TAB_W, 0,
                    right ? Bookmarks.TAB_W : -Bookmarks.TAB_W, Bookmarks.MAX_H, Bookmarks.TAB_W, Bookmarks.MAX_H);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            int size = Math.min(12, t.h() - 4);
            if (size < 6) continue;
            float s = size / 16f;
            // the icon sits 4 pixels in from the outer end, mirrored on the left
            float iconX = right ? t.tipX() + extra - size - 4 : t.tipX() - extra + 4;
            g.pose().pushPose();
            g.pose().translate(iconX, t.y() + (t.h() - size) / 2f, 0f);
            g.pose().scale(s, s, 1f);
            g.renderItem(stack(c.icon()), 0, 0);
            g.pose().popPose();
        }
    }

    /** One page of {@code s} ({@code offset} 0 left, 1 right) with its text box at {@code contentX}. */
    private void drawPage(GuiGraphics g, BookNavigator.Spread s, int offset, int contentX, double bx, double by, @Nullable Hover hover) {
        PageLayout.ScreenPage page = page(s, offset);
        if (page == null) return;
        double px = bx - contentX, py = by - CONTENT_TOP;
        g.pose().pushPose();
        g.pose().translate(contentX, CONTENT_TOP, 0f);
        for (PageLayout.Placed p : page.elements()) {
            switch (p) {
                case PageLayout.Picture pic -> drawPicture(g, pic);
                case PageLayout.ItemIcon icon -> {
                    g.renderItem(stack(icon.item()), icon.x(), icon.y());
                    if (hover != null && icon.area().contains(px, py)) hover.item = icon.item();
                }
                case PageLayout.InlineIcon icon -> {
                    float sc = PageLayout.INLINE_ICON / 16f;
                    g.pose().pushPose();
                    g.pose().translate(icon.x(), icon.y(), 0f);
                    g.pose().scale(sc, sc, 1f);
                    g.renderItem(stack(icon.item()), 0, 0);
                    g.pose().popPose();
                    if (hover != null && px >= icon.x() && px < icon.x() + PageLayout.INLINE_ICON
                            && py >= icon.y() && py < icon.y() + PageLayout.INLINE_ICON) hover.item = icon.item();
                }
                case PageLayout.Bullet b -> g.fill(b.x() + 3, b.y() + 2, b.x() + 6, b.y() + 5, GuiKit.INK);
                case PageLayout.Button b -> drawButton(g, b, hover != null && b.area().contains(px, py));
                case PageLayout.TextRun run -> drawRun(g, run, hover != null && run.style().link() != null && run.area().contains(px, py));
            }
        }
        String number = String.valueOf(s.page() + offset + 1);
        g.drawString(font, number, (CONTENT_W - font.width(number)) / 2, PAGE_NUMBER_Y - CONTENT_TOP, GuiKit.INK_DIM, false);
        g.pose().popPose();
    }

    private void drawRun(GuiGraphics g, PageLayout.TextRun run, boolean hovered) {
        Markup.Style st = run.style();
        boolean link = st.link() != null;
        Style style = Style.EMPTY.withBold(st.bold()).withItalic(st.italic()).withUnderlined(link && hovered);
        int color = st.hasColor() ? 0xFF000000 | st.color() : link ? LINK_COLOR : GuiKit.INK;
        g.drawString(font, Component.literal(run.text()).withStyle(style), run.x(), run.y(), color, false);
    }

    private void drawButton(GuiGraphics g, PageLayout.Button b, boolean hovered) {
        PageLayout.Area a = b.area();
        g.fill(a.x(), a.y(), a.right(), a.bottom(), BUTTON_EDGE);
        g.fill(a.x() + 1, a.y() + 1, a.right() - 1, a.bottom() - 1, hovered ? BUTTON_FACE_HOVER : BUTTON_FACE);
        g.fill(a.x() + 1, a.bottom() - 2, a.right() - 1, a.bottom() - 1, 0x30000000);
        String label = font.plainSubstrByWidth(b.label(), a.w() - 4);
        g.drawString(font, label, a.x() + (a.w() - font.width(label)) / 2, a.y() + (a.h() - 8) / 2, GuiKit.INK, false);
    }

    private void drawPicture(GuiGraphics g, PageLayout.Picture pic) {
        int[] size = imageSize(pic.texture());
        if (size[0] <= 0 || size[1] <= 0) {
            PageLayout.Area a = pic.box();
            g.fill(a.x(), a.y(), a.right(), a.bottom(), PLACEHOLDER);
            return;
        }
        PageLayout.Area a = PageLayout.fit(size[0], size[1], pic.box());
        RenderSystem.enableBlend();
        g.blit(pic.texture(), a.x(), a.y(), a.w(), a.h(), 0, 0, size[0], size[1], size[0], size[1]);
    }

    /** The turning page over the pages that stay: the old page closes toward the spine, then the new one opens. */
    private void drawTurn(GuiGraphics g, float progress) {
        boolean forward = turn.direction() == PageTurn.Direction.FORWARD;
        // the pages under the turning one: the side it leaves shows the old page, the side it reaches the new one
        BookNavigator.Spread leftBase = forward ? spread : target;
        BookNavigator.Spread rightBase = forward ? target : spread;
        drawPage(g, leftBase, 0, LEFT_CONTENT_X, -1, -1, null);
        drawPage(g, rightBase, 1, RIGHT_CONTENT_X, -1, -1, null);
        float w = PageTurn.pageWidth(progress);
        boolean second = PageTurn.secondHalf(progress);
        // forward: the old right page closes, then the new left page opens; back: the mirror image
        boolean onRight = forward != second;
        BookNavigator.Spread shown = second ? target : spread;
        int offset = onRight ? 1 : 0;
        float shade = PageTurn.shade(progress);
        g.pose().pushPose();
        g.pose().translate(0f, 0f, TURN_Z);
        g.pose().pushPose();
        g.pose().translate(SPINE_X, 0f, 0f);
        g.pose().scale(Math.max(w, 0.001f), 1f, 1f);
        g.pose().translate(-SPINE_X, 0f, 0f);
        if (onRight) g.blit(BOOK, SPINE_X, PAGE_TOP, SPINE_X, PAGE_TOP, RIGHT_PAGE_W, PAGE_BOTTOM - PAGE_TOP, BOOK_W, BOOK_H);
        else g.blit(BOOK, LEFT_PAGE_X, PAGE_TOP, LEFT_PAGE_X, PAGE_TOP, LEFT_PAGE_W, PAGE_BOTTOM - PAGE_TOP, BOOK_W, BOOK_H);
        drawPage(g, shown, offset, onRight ? RIGHT_CONTENT_X : LEFT_CONTENT_X, -1, -1, null);
        // the fold darkens toward the turning page's free edge
        int pageW = onRight ? RIGHT_PAGE_W : LEFT_PAGE_W;
        for (int i = 0; i < SHADE_STRIPS; i++) {
            int a0 = pageW * i / SHADE_STRIPS, a1 = pageW * (i + 1) / SHADE_STRIPS;
            int alpha = Math.round(255 * shade * (i + 1) / SHADE_STRIPS);
            int x0 = onRight ? SPINE_X + a0 : SPINE_X - a1;
            int x1 = onRight ? SPINE_X + a1 : SPINE_X - a0;
            g.fill(x0, PAGE_TOP, x1, PAGE_BOTTOM, alpha << 24);
        }
        g.pose().popPose();
        // its shadow on the page beyond its edge
        int edge = Math.round(SPINE_X + (onRight ? 1 : -1) * pageW * w);
        for (int i = 0; i < 6; i++) {
            int alpha = Math.round(255 * shade * 0.6f * (6 - i) / 6f);
            int x = onRight ? edge + i : edge - i - 1;
            if (x < LEFT_PAGE_X || x >= RIGHT_PAGE_END) continue;
            g.fill(x, PAGE_TOP, x + 1, PAGE_BOTTOM, alpha << 24);
        }
        g.pose().popPose();
    }

    private ItemStack stack(ResourceLocation item) {
        return stacks.computeIfAbsent(item, id -> BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY));
    }

    /** An illustration's pixel size, read once from its PNG; 0x0 when it is missing or unreadable. */
    private int[] imageSize(ResourceLocation texture) {
        return imageSizes.computeIfAbsent(texture, t -> {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(t);
            if (res.isEmpty()) {
                Constants.LOG.warn("Almanac illustration {} not found", t);
                return new int[]{0, 0};
            }
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                return new int[]{img.getWidth(), img.getHeight()};
            } catch (IOException e) {
                Constants.LOG.warn("Almanac illustration {} unreadable: {}", t, e.toString());
                return new int[]{0, 0};
            }
        });
    }
}
