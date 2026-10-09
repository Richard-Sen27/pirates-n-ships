package com.richardsenger.piratesnships.rpg.career.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.rpg.career.CareerSyncPayload;
import com.richardsenger.piratesnships.rpg.career.ClientCareer;
import com.richardsenger.piratesnships.rpg.reputation.ClientReputation;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import com.richardsenger.piratesnships.ship.hull.client.ShipHud;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudConfig;
import com.richardsenger.piratesnships.ship.hull.client.ShipHudLayout;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The rank box (docs/design.md §15, HON1; HUD5 §4.6), a HUD layer below the chat: the local player's rank in its
 * side's colour, navy and pirate reputation ({@link ClientReputation}), the letter of marque and prize money waiting
 * ({@link ClientCareer}). Drawn once the server sent the career; when and how strongly {@link RankHudVisibility}
 * (HUD5: a few seconds on join, on a change of title and after the career screen closes, unless {@code career_hud.mode}
 * says otherwise), where {@link RankHudLayout#place} in {@code career_hud.corner}, clear of the static HUD parts and
 * the ship HUD's panels ({@link ShipHud#occupied}).
 */
public final class RankHud {

    private static final int BACKING = 0x70101418;
    private static final int TEXT = 0xFFEEE0BC;
    private static final int TEXT_DIM = 0xFFB8A27C;
    /** Below this alpha byte the font would draw text opaque ({@code Font#adjustColor}), so text is left out. */
    private static final int MIN_TEXT_ALPHA = 8;

    private static final RankHudVisibility.State STATE = new RankHudVisibility.State();
    private static volatile long ticks;

    private RankHud() {
    }

    /** Client init (from {@code CareerClient.init()}). */
    public static void init() {
        ClientEvents.registerHudLayerBelowChat(Constants.id("career_rank"), RankHud::render);
        ClientEvents.CLIENT_TICK_END.register(mc -> ticks++);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> STATE.reset());
        ClientCareer.setSyncListener(RankHud::onSync);
    }

    /** A career sync: the first one after joining shows the box, a changed title shows it naming the change. */
    static void onSync(CareerSyncPayload before, CareerSyncPayload after) {
        if (before == null) {
            STATE.show(ticks, null);
            return;
        }
        RankHudVisibility.change(view(before, false), view(after, false)).ifPresent(c -> STATE.show(ticks, c));
    }

    /** The career screen closed ({@code CareerScreen#removed}): show the box again. */
    static void onCareerScreenClosed() {
        STATE.show(ticks, null);
    }

    private static RankHudLayout.View view(CareerSyncPayload c, boolean reputationKnown) {
        return new RankHudLayout.View(c.navy(), c.enlisted(), c.infamy(), c.letter(), c.prizeMoney(), reputationKnown);
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;
        if (!ClientCareer.known() || mc.getDebugOverlay().showDebugScreen()) return;
        int showTicks = RankHudVisibility.showTicks(RankHudConfig.SHOW_SECONDS.get());
        long now = ticks;
        float alpha = RankHudVisibility.alpha(RankHudConfig.MODE.get(),
                STATE.age(now, delta.getGameTimeDeltaPartialTick(false)), showTicks);
        if (alpha <= 0f) return;

        RankHudLayout.View v = view(ClientCareer.get(), ClientReputation.known());
        Optional<RankHudVisibility.Change> headline = STATE.headlineChange(now, showTicks);
        Font font = mc.font;
        List<Component> lines = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        for (RankHudLayout.Row row : RankHudLayout.rows(v)) {
            switch (row) {
                case TITLE -> {
                    lines.add(headline.map(RankHud::headlineText)
                            .orElseGet(() -> Component.translatable(RankHudLayout.titleKey(v))));
                    colours.add(RankHudLayout.titleColour(v));
                }
                case REPUTATION -> {
                    lines.add(Component.translatable(RankHudLayout.KEY_REPUTATION,
                            RankHudLayout.signed(ClientReputation.get(Faction.NAVY)),
                            RankHudLayout.signed(ClientReputation.get(Faction.PIRATES))));
                    colours.add(TEXT);
                }
                case LETTER -> {
                    lines.add(Component.translatable(RankHudLayout.KEY_LETTER, Component.translatable(v.letter().nameKey())));
                    colours.add(TEXT_DIM);
                }
                case PRIZE -> {
                    lines.add(Component.translatable(RankHudLayout.KEY_PRIZE, v.prizeMoney()));
                    colours.add(TEXT_DIM);
                }
            }
        }
        int widest = 0;
        for (Component l : lines) widest = Math.max(widest, font.width(l));
        // one width through the whole window, so the box does not shrink when the headline settles on the rank
        Optional<RankHudVisibility.Change> shown = STATE.windowChange(now, showTicks);
        if (shown.isPresent()) widest = Math.max(widest, font.width(headlineText(shown.get())));

        ShipHudLayout.Rect r = RankHudLayout.place(RankHudConfig.CORNER.get(), RankHudLayout.width(widest),
                RankHudLayout.height(lines.size()), g.guiWidth(), g.guiHeight(), ShipHud.occupied(mc, g),
                ShipHudConfig.MARGIN.get());
        g.fill(r.x(), r.y(), r.right(), r.bottom(), fade(BACKING, alpha));
        if (Math.round(alpha * 255) < MIN_TEXT_ALPHA) return;
        int y = r.y() + RankHudLayout.PAD;
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), r.x() + RankHudLayout.PAD, y, fade(colours.get(i), alpha), true);
            y += RankHudLayout.LINE_H;
        }
    }

    private static Component headlineText(RankHudVisibility.Change c) {
        return Component.translatable(c.promotion() ? RankHudLayout.KEY_PROMOTED : RankHudLayout.KEY_NOW,
                Component.translatable(c.titleKey()));
    }

    private static int fade(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
