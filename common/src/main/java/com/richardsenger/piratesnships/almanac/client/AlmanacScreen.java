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
import net.minecraft.util.FormattedCharSequence;
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
 *
 * <p>ALM8: the book's first spread is the index (before chapter one, without a bookmark or page numbers): the chapter
 * grid ({@link IndexLayout}) on the left page, the foreword and "Continue where you left off" on the right. The
 * navigator counts it as chapter 0, so a navigator chapter {@code c > 0} is the book's chapter {@code c - 1}. Recipe
 * panels ({@link RecipeGrid}, {@link RecipeView}) look their recipe up in the client's recipe manager each frame; item
 * spotlights draw the item at twice its size.
 *
 * <p>ALM10: 3D spotlights draw a block, an item or a mob turning in a larger card ({@link Spotlight3dRenderer}); the
 * models live as long as the screen. While a page turns the cards show without their models (the turning page is
 * squeezed flat, a model on it would not be).
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
    /** Whether the book opens on the index spread (the first opening since joining, or nothing read yet). */
    private final boolean startAtIndex;
    /** 1 when the index spread comes first (a book with chapters), else 0: navigator chapter = book chapter + this. */
    private final int lead;
    @Nullable
    private List<IndexLayout.Entry> indexEntries;
    @Nullable
    private PageLayout.ScreenPage indexRight;
    @Nullable
    private PageLayout.Area continueArea;
    private final Map<Integer, List<PageLayout.ScreenPage>> pages = new HashMap<>();
    private final Map<ResourceLocation, int[]> imageSizes = new HashMap<>();
    private final Map<ResourceLocation, ItemStack> stacks = new HashMap<>();
    private final PageTurn turn = new PageTurn();
    private final Spotlight3dRenderer models = new Spotlight3dRenderer();
    /** Screen ticks since opening: the 3D spotlights' clock. */
    private long ticks;
    /** This frame's partial tick, for the 3D spotlights. */
    private float partialTick;
    /** Whether this frame draws the 3D spotlights' models (not while a page turns). */
    private boolean drawModels;
    private final BookNavigator nav;
    private BookNavigator.Spread spread;
    private BookNavigator.Spread target;
    private List<Bookmarks.Tab> tabs = List.of();
    private float scale = 1f;
    private float left;
    private float top;
    private boolean started;

    AlmanacScreen(AlmanacBook book, AlmanacProgress progress, boolean atIndex, AlmanacPayloads.Open request) {
        super(Component.translatable(AlmanacScreenText.TITLE));
        this.book = book;
        this.empty = book.isEmpty();
        this.chapters = empty ? List.of(emptyChapter()) : book.chapters();
        this.request = request;
        this.startAt = progress;
        this.startAtIndex = atIndex;
        this.lead = empty ? 0 : 1;
        this.nav = new BookNavigator(chapters.size() + lead, c -> c < lead ? 1 : pages(c - lead).size());
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
        boolean atIndex = ClientAlmanac.takeFirstOpening() || ClientAlmanac.progress().chapter().isEmpty();
        mc.setScreen(new AlmanacScreen(ClientAlmanac.book(), ClientAlmanac.progress(), atIndex, request));
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
        indexEntries = null;
        indexRight = null;
        double fit = Math.min((width - 16.0) / (BOOK_W + 2.0 * SIDE_ROOM), (height - 16.0) / BOOK_H);
        scale = (float) Math.max(0.25, Math.min(AlmanacScreenConfig.SCALE.get(), fit));
        left = (width - BOOK_W * scale) / 2f;
        top = (height - BOOK_H * scale) / 2f;
        if (!started) {
            started = true;
            // the index on the first opening since joining; else the last page read (a chapter the book no longer has
            // opens the book at its start, the index)
            if (!empty && !startAtIndex) lastRead().ifPresent(s -> spread = s);
            target = spread;
        }
        spread = nav.clamp(spread.chapter(), spread.page());
        target = nav.clamp(target.chapter(), target.page());
        layoutTabs();
    }

    private void layoutTabs() {
        tabs = empty ? List.of() : Bookmarks.layout(chapters.size(), shownChapter(), 0, BOOK_W, TABS_TOP,
                TABS_BOTTOM - TABS_TOP);
    }

    /** The book chapter whose bookmark is drawn as open (the one being turned to while a page turns); -1 on the index. */
    private int shownChapter() {
        return (turn.turning() ? target.chapter() : spread.chapter()) - lead;
    }

    private boolean isIndex(BookNavigator.Spread s) {
        return s.chapter() < lead;
    }

    /** The spread of the page the player last read, if the book has its chapter. */
    private Optional<BookNavigator.Spread> lastRead() {
        if (empty) return Optional.empty();
        AlmanacProgress p = ClientAlmanac.progress();
        if (p.chapter().isEmpty()) p = startAt;
        if (p.chapter().isEmpty()) return Optional.empty();
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).id().equals(p.chapter().get())) return Optional.of(nav.clamp(i + lead, p.page()));
        }
        return Optional.empty();
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
        if (isIndex(s)) return offset == 1 ? indexRight() : null;
        List<PageLayout.ScreenPage> list = pages(s.chapter() - lead);
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
        if (!empty && !isIndex(s)) ClientAlmanac.reportPage(chapters.get(s.chapter() - lead).id(), s.page());
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
                turnTo(new BookNavigator.Spread(i + lead, 0));
                return;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        models.tick();
        if (turn.tick()) settle(target);
    }

    @Override
    public void removed() {
        if (turn.finishNow()) settle(target);
        models.close();
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
            turnTo(new BookNavigator.Spread(tab + lead, 0));
            return true;
        }
        if (isIndex(spread)) {
            int entry = indexHit(bx - LEFT_CONTENT_X, by - CONTENT_TOP);
            if (entry >= 0) {
                turnTo(new BookNavigator.Spread(entry + lead, 0));
                return true;
            }
            indexRight();
            if (continueArea != null && continueArea.contains(bx - RIGHT_CONTENT_X, by - CONTENT_TOP)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                turnTo(lastRead().orElse(new BookNavigator.Spread(lead, 0)));
                return true;
            }
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
            case InputConstants.KEY_HOME -> {
                if (!turn.turning() && !empty) turnTo(nav.first());
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
        this.partialTick = partialTick;
        this.drawModels = still;
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
        } else if (hover.stack != null && !hover.stack.isEmpty()) {
            g.renderTooltip(font, hover.stack, mouseX, mouseY);
        } else if (hover.item != null) {
            g.renderTooltip(font, stack(hover.item), mouseX, mouseY);
        } else if (hover.text != null) {
            g.renderTooltip(font, hover.text, mouseX, mouseY);
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
        /** A particular stack (a recipe slot: its count and the alternative shown). */
        @Nullable
        ItemStack stack;
        @Nullable
        Component text;
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
        double px = bx - contentX, py = by - CONTENT_TOP;
        if (isIndex(s) && offset == 0) {
            g.pose().pushPose();
            g.pose().translate(contentX, CONTENT_TOP, 0f);
            drawIndex(g, px, py, hover);
            g.pose().popPose();
            return;
        }
        PageLayout.ScreenPage page = page(s, offset);
        if (page == null) return;
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
                case PageLayout.RecipeBox box -> drawRecipe(g, box, px, py, hover);
                case PageLayout.SpotlightBox spot -> drawSpotlight(g, spot, px, py, hover);
                case PageLayout.Spotlight3dBox spot -> drawSpotlight3d(g, spot, contentX, px, py, hover);
            }
        }
        if (isIndex(s)) {
            drawContinue(g, px, py, hover);
        } else {
            String number = String.valueOf(s.page() + offset + 1);
            g.drawString(font, number, (CONTENT_W - font.width(number)) / 2, PAGE_NUMBER_Y - CONTENT_TOP, GuiKit.INK_DIM, false);
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ the index spread (ALM8)

    private List<IndexLayout.Entry> indexEntries() {
        if (indexEntries == null) {
            List<String> titles = chapters.stream().map(c -> I18n.get(c.title())).toList();
            indexEntries = IndexLayout.layout(titles, CONTENT_W, CONTENT_H, this::measure);
        }
        return indexEntries;
    }

    /** The right page of the index: the book's title, the foreword and the line over the "continue" link. */
    private PageLayout.ScreenPage indexRight() {
        if (indexRight == null) {
            boolean read = lastRead().isPresent();
            Page page = new Page(List.of(new PageElement.Heading(AlmanacScreenText.TITLE),
                    PageElement.Text.of(AlmanacScreenText.FOREWORD),
                    new PageElement.Text(read ? AlmanacScreenText.CONTINUE : AlmanacScreenText.BEGIN, false, true, Optional.empty())));
            indexRight = PageLayout.layout(page, 0, CONTENT_W, CONTENT_H, this::measure, new Lang()).getFirst();
            int bottom = 0;
            for (PageLayout.Placed p : indexRight.elements()) {
                if (p instanceof PageLayout.TextRun run) bottom = Math.max(bottom, run.y() + PageLayout.LINE_H);
            }
            int y = Math.min(CONTENT_H - PageLayout.BUTTON_H, bottom + PageLayout.HEADING_GAP);
            continueArea = new PageLayout.Area(0, y, CONTENT_W, PageLayout.BUTTON_H);
        }
        return indexRight;
    }

    /** The book chapter of the index entry at a point of the left page, or -1. */
    private int indexHit(double px, double py) {
        for (IndexLayout.Entry e : indexEntries()) if (e.cell().contains(px, py)) return e.chapter();
        return -1;
    }

    private void drawIndex(GuiGraphics g, double px, double py, @Nullable Hover hover) {
        for (IndexLayout.Entry e : indexEntries()) {
            Chapter c = chapters.get(e.chapter());
            boolean hovered = hover != null && e.cell().contains(px, py);
            PageLayout.Area cell = e.cell();
            if (hovered) g.fill(cell.x(), cell.y(), cell.right(), cell.bottom(), 0x24704020);
            PageLayout.Area tile = e.tile();
            if (e.framed()) {
                RenderSystem.enableBlend();
                g.blit(AlmanacPanels.TEXTURE, tile.x(), tile.y(), AlmanacPanels.tileU(tile.w()), AlmanacPanels.TILE_V,
                        tile.w(), tile.h(), AlmanacPanels.TEX_W, AlmanacPanels.TEX_H);
            }
            float s = e.iconSize() / 16f;
            g.pose().pushPose();
            g.pose().translate(tile.x() + (tile.w() - e.iconSize()) / 2f, tile.y() + (tile.h() - e.iconSize()) / 2f, 0f);
            g.pose().scale(s, s, 1f);
            g.renderItem(stack(c.icon()), 0, 0);
            g.pose().popPose();
            int color = 0xFF000000 | darkInk(c.bookmarkColor());
            g.pose().pushPose();
            g.pose().translate(e.textX(), e.textY(), 0f);
            g.pose().scale(e.scale(), e.scale(), 1f);
            for (int i = 0; i < e.lines().size(); i++) {
                Component line = Component.literal(e.lines().get(i)).withStyle(Style.EMPTY.withUnderlined(hovered));
                g.drawString(font, line, 0, i * IndexLayout.FONT_LINE, color, false);
            }
            g.pose().popPose();
            if (hovered) hover.text = Component.translatable(c.title());
        }
    }

    /** A bookmark colour as ink on parchment: light colours are darkened so the title stays readable. */
    static int darkInk(int rgb) {
        int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int luma = (r * 299 + gr * 587 + b * 114) / 1000;
        if (luma <= 110) return rgb;
        float f = 110f / luma;
        return ((int) (r * f) << 16) | ((int) (gr * f) << 8) | (int) (b * f);
    }

    /** The "continue" link of the index's right page: the chapter last read and its page, or the first chapter. */
    private void drawContinue(GuiGraphics g, double px, double py, @Nullable Hover hover) {
        if (continueArea == null) return;
        Optional<BookNavigator.Spread> last = lastRead();
        BookNavigator.Spread to = last.orElse(new BookNavigator.Spread(lead, 0));
        Chapter c = chapters.get(to.chapter() - lead);
        String label = last.isPresent()
                ? I18n.get(AlmanacScreenText.CONTINUE_LINK, I18n.get(c.title()), to.page() + 1)
                : I18n.get(c.title());
        boolean hovered = hover != null && continueArea.contains(px, py);
        PageLayout.Area a = continueArea;
        g.fill(a.x(), a.y(), a.right(), a.bottom(), BUTTON_EDGE);
        g.fill(a.x() + 1, a.y() + 1, a.right() - 1, a.bottom() - 1, hovered ? BUTTON_FACE_HOVER : BUTTON_FACE);
        g.fill(a.x() + 1, a.bottom() - 2, a.right() - 1, a.bottom() - 1, 0x30000000);
        int icon = PageLayout.INLINE_ICON;
        g.pose().pushPose();
        g.pose().translate(a.x() + 3, a.y() + (a.h() - icon) / 2f, 0f);
        g.pose().scale(icon / 16f, icon / 16f, 1f);
        g.renderItem(stack(c.icon()), 0, 0);
        g.pose().popPose();
        int textW = a.w() - icon - 8;
        String shown = font.width(label) > textW ? font.plainSubstrByWidth(label, textW - font.width("...")) + "..." : label;
        g.drawString(font, shown, a.x() + icon + 5, a.y() + (a.h() - 8) / 2, 0xFF000000 | darkInk(c.bookmarkColor()), false);
        if (hovered && !shown.equals(label)) hover.text = Component.literal(label);
    }

    // ------------------------------------------------------------------ recipe panels and spotlights (ALM8)

    private void drawRecipe(GuiGraphics g, PageLayout.RecipeBox box, double px, double py, @Nullable Hover hover) {
        PageLayout.Area a = box.area();
        RenderSystem.enableBlend();
        g.blit(AlmanacPanels.TEXTURE, a.x(), a.y(), AlmanacPanels.PANEL_U, AlmanacPanels.PANEL_V, RecipeGrid.W, RecipeGrid.H,
                AlmanacPanels.TEX_W, AlmanacPanels.TEX_H);
        Minecraft mc = Minecraft.getInstance();
        RecipeView view = mc.level == null ? RecipeView.NONE : RecipeView.of(mc.level, box.recipe());
        long time = mc.level == null ? 0 : mc.level.getGameTime();
        g.blit(AlmanacPanels.TEXTURE, a.x() + RecipeGrid.ARROW_X, a.y() + RecipeGrid.ARROW_Y, AlmanacPanels.ARROW_U,
                AlmanacPanels.ARROW_V, RecipeGrid.ARROW_W, RecipeGrid.ARROW_H, AlmanacPanels.TEX_W, AlmanacPanels.TEX_H);
        switch (view.kind()) {
            case SHAPED, SHAPELESS -> {
                for (int slot = 0; slot < 9; slot++) {
                    slot(g, a.x() + RecipeGrid.slotX(slot), a.y() + RecipeGrid.slotY(slot), view.inputs().get(slot), time, px, py, hover);
                }
                if (view.kind() == RecipeGrid.Kind.SHAPELESS) {
                    g.blit(AlmanacPanels.TEXTURE, a.x() + RecipeGrid.MARK_X, a.y() + RecipeGrid.MARK_Y, AlmanacPanels.MARK_U,
                            AlmanacPanels.MARK_V, RecipeGrid.MARK, RecipeGrid.MARK, AlmanacPanels.TEX_W, AlmanacPanels.TEX_H);
                    if (hover != null && within(px, py, a.x() + RecipeGrid.MARK_X, a.y() + RecipeGrid.MARK_Y, RecipeGrid.MARK)) {
                        hover.text = Component.translatable(AlmanacScreenText.SHAPELESS);
                    }
                }
            }
            case COOKING -> {
                slot(g, a.x() + RecipeGrid.COOK_IN_X, a.y() + RecipeGrid.COOK_IN_Y, view.inputs().getFirst(), time, px, py, hover);
                g.blit(AlmanacPanels.TEXTURE, a.x() + RecipeGrid.FLAME_X, a.y() + RecipeGrid.FLAME_Y, AlmanacPanels.FLAME_U,
                        AlmanacPanels.FLAME_V, RecipeGrid.FLAME, RecipeGrid.FLAME, AlmanacPanels.TEX_W, AlmanacPanels.TEX_H);
            }
            case SINGLE -> slot(g, a.x() + RecipeGrid.SINGLE_X, a.y() + RecipeGrid.SINGLE_Y, view.inputs().getFirst(), time, px, py, hover);
            case OTHER -> {
                String missing = I18n.get(view.result().isEmpty() ? AlmanacScreenText.RECIPE_MISSING : AlmanacScreenText.RECIPE_UNKNOWN);
                List<FormattedCharSequence> lines = font.split(Component.literal(missing), RecipeGrid.ARROW_X - RecipeGrid.GRID_X - 4);
                int ty = a.y() + (RecipeGrid.H - lines.size() * 9) / 2;
                for (FormattedCharSequence line : lines) {
                    g.drawString(font, line, a.x() + RecipeGrid.GRID_X + 2, ty, GuiKit.INK_DIM, false);
                    ty += 9;
                }
            }
        }
        int ox = a.x() + RecipeGrid.OUT_X, oy = a.y() + RecipeGrid.OUT_Y;
        g.blit(AlmanacPanels.TEXTURE, ox, oy, AlmanacPanels.OUT_U, AlmanacPanels.OUT_V, RecipeGrid.OUT, RecipeGrid.OUT,
                AlmanacPanels.TEX_W, AlmanacPanels.TEX_H);
        ItemStack out = view.result();
        if (!out.isEmpty()) {
            g.renderItem(out, ox + 5, oy + 5);
            g.renderItemDecorations(font, out, ox + 5, oy + 5);
            if (hover != null && within(px, py, ox, oy, RecipeGrid.OUT)) hover.stack = out;
        }
    }

    /** One input slot with the alternative of its ingredient shown now. */
    private void slot(GuiGraphics g, int x, int y, List<ItemStack> options, long time, double px, double py, @Nullable Hover hover) {
        g.blit(AlmanacPanels.TEXTURE, x, y, AlmanacPanels.SLOT_U, AlmanacPanels.SLOT_V, RecipeGrid.SLOT, RecipeGrid.SLOT,
                AlmanacPanels.TEX_W, AlmanacPanels.TEX_H);
        if (options.isEmpty()) return;
        ItemStack shown = options.get(RecipeGrid.cycle(options.size(), time));
        g.renderItem(shown, x + 1, y + 1);
        if (hover != null && within(px, py, x, y, RecipeGrid.SLOT)) hover.stack = shown;
    }

    private static boolean within(double px, double py, int x, int y, int size) {
        return px >= x && px < x + size && py >= y && py < y + size;
    }

    private void drawSpotlight(GuiGraphics g, PageLayout.SpotlightBox spot, double px, double py, @Nullable Hover hover) {
        PageLayout.Area f = spot.frame();
        RenderSystem.enableBlend();
        g.blit(AlmanacPanels.TEXTURE, f.x(), f.y(), AlmanacPanels.SPOTLIGHT_U, AlmanacPanels.SPOTLIGHT_V, f.w(), f.h(),
                AlmanacPanels.TEX_W, AlmanacPanels.TEX_H);
        ItemStack stack = stack(spot.item());
        g.pose().pushPose();
        g.pose().translate(f.x() + (f.w() - 32) / 2f, f.y() + (f.h() - 32) / 2f, 0f);
        g.pose().scale(2f, 2f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
        Style bold = Style.EMPTY.withBold(true);
        String name = stack.getHoverName().getString();
        int room = spot.area().w();
        if (font.width(Component.literal(name).withStyle(bold)) > room) {
            name = font.plainSubstrByWidth(name, room - font.width("...") - 8) + "...";
        }
        Component label = Component.literal(name).withStyle(bold);
        g.drawString(font, label, spot.area().x() + (room - font.width(label)) / 2, spot.nameY(),
                0xFF000000 | PageLayout.HEADING_COLOR, false);
        if (hover != null && f.contains(px, py)) hover.item = spot.item();
    }

    /**
     * A 3D spotlight (ALM10): the card, the model turning inside its rim (clipped there), the name under it; the
     * tooltip over the card names it.
     */
    private void drawSpotlight3d(GuiGraphics g, PageLayout.Spotlight3dBox box, int contentX, double px, double py, @Nullable Hover hover) {
        PageLayout.Area f = box.frame();
        RenderSystem.enableBlend();
        g.blit(AlmanacPanels.TEXTURE, f.x(), f.y(), AlmanacPanels.SPOTLIGHT_3D_U, AlmanacPanels.SPOTLIGHT_3D_V, f.w(), f.h(),
                AlmanacPanels.TEX_W, AlmanacPanels.TEX_H);
        PageLayout.Area in = new PageLayout.Area(f.x() + AlmanacPanels.SPOTLIGHT_3D_INSET, f.y() + AlmanacPanels.SPOTLIGHT_3D_INSET,
                f.w() - 2 * AlmanacPanels.SPOTLIGHT_3D_INSET, f.h() - 2 * AlmanacPanels.SPOTLIGHT_3D_INSET);
        if (drawModels) {
            // the scissor is in GUI pixels: the inner card through the book's offset and scale, rounded inward
            int[] clip = {
                    (int) Math.ceil(left + (contentX + in.x()) * scale),
                    (int) Math.ceil(top + (CONTENT_TOP + in.y()) * scale),
                    (int) Math.floor(left + (contentX + in.right()) * scale),
                    (int) Math.floor(top + (CONTENT_TOP + in.bottom()) * scale)};
            models.draw(g, box.spotlight(), in.x(), in.y(), in.w(), in.h(), clip, ticks, partialTick);
        }
        Component name = box.spotlight().caption().<Component>map(Component::translatable)
                .orElseGet(() -> Spotlight3dRenderer.name(box.spotlight()));
        Style bold = Style.EMPTY.withBold(true);
        String shown = name.getString();
        int room = box.area().w();
        if (font.width(Component.literal(shown).withStyle(bold)) > room) {
            shown = font.plainSubstrByWidth(shown, room - font.width("...") - 8) + "...";
        }
        Component label = Component.literal(shown).withStyle(bold);
        g.drawString(font, label, box.area().x() + (room - font.width(label)) / 2, box.nameY(), 0xFF000000 | PageLayout.HEADING_COLOR, false);
        if (hover != null && f.contains(px, py)) hover.text = Spotlight3dRenderer.name(box.spotlight());
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
