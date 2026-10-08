package com.richardsenger.piratesnships.rpg.career.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.rpg.career.CareerSyncPayload;
import com.richardsenger.piratesnships.rpg.career.ClientCareer;
import com.richardsenger.piratesnships.rpg.reputation.ClientReputation;
import com.richardsenger.piratesnships.rpg.reputation.Faction;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The rank box (docs/design.md §15, HON1), a HUD layer: the local player's rank in its side's colour, navy and pirate
 * reputation ({@link ClientReputation}), the letter of marque and prize money waiting ({@link ClientCareer}). Drawn
 * once the server sent the career; layout {@link RankHudLayout}, client config {@code career_hud}.
 */
public final class RankHud {

    private static final int BACKING = 0x70101418;
    private static final int TEXT = 0xFFEEE0BC;
    private static final int TEXT_DIM = 0xFFB8A27C;

    private RankHud() {
    }

    /** Client init (from {@code CareerClient.init()}). */
    public static void init() {
        ClientEvents.registerHudLayer(Constants.id("career_rank"), RankHud::render);
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null || !RankHudConfig.ENABLED.get()) return;
        if (!ClientCareer.known() || mc.getDebugOverlay().showDebugScreen()) return;
        CareerSyncPayload c = ClientCareer.get();
        RankHudLayout.View v = new RankHudLayout.View(c.navy(), c.enlisted(), c.infamy(), c.letter(), c.prizeMoney(),
                ClientReputation.known());

        Font font = mc.font;
        List<Component> lines = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        for (RankHudLayout.Row row : RankHudLayout.rows(v)) {
            switch (row) {
                case TITLE -> {
                    lines.add(Component.translatable(RankHudLayout.titleKey(v)));
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
        RankHudLayout.Rect r = RankHudLayout.place(RankHudConfig.X.get(), RankHudConfig.Y.get(),
                RankHudLayout.width(widest), RankHudLayout.height(lines.size()), g.guiWidth(), g.guiHeight());
        g.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), BACKING);
        int y = r.y() + RankHudLayout.PAD;
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), r.x() + RankHudLayout.PAD, y, colours.get(i), true);
            y += RankHudLayout.LINE_H;
        }
    }
}
