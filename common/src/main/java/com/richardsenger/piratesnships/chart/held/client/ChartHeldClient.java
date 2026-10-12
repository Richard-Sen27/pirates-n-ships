package com.richardsenger.piratesnships.chart.held.client;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.chart.ChartContent;
import com.richardsenger.piratesnships.chart.held.ChartHeld;
import com.richardsenger.piratesnships.chart.held.ChartHeldMotion;
import com.richardsenger.piratesnships.combat.melee.MeleeClientConfig;
import com.richardsenger.piratesnships.combat.melee.client.anim.MeleeAnimationsSetup;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of the held chart (CHART3, physical client only): every tick each player in view moves their chart's
 * unroll progress towards their synced {@link ChartHeld#READING} flag ({@link ChartHeldMotion#step}), the chart's
 * item model follows it through the property {@link #OPEN_PROPERTY} (the overrides in {@code chart.json} pick the
 * frame), and the arms play the reading or stowing animation through PAL ({@link PalChartPoses}, only when PAL is
 * loaded; without it the chart unrolls in vanilla's arm pose). First person draws the held item with the frames' own
 * first-person display: the open chart low in the view like vanilla's map.
 */
public final class ChartHeldClient {

    /** Item model property: the unroll progress 0..1 of the chart in a hand of the entity drawn, 0 anywhere else. */
    public static final ResourceLocation OPEN_PROPERTY = Constants.id("chart_open");
    /** How far above the melee layer the chart layer sits (nobody reads a chart while fighting). */
    public static final int PRIORITY_ABOVE_MELEE = 40;

    /** Shows, changes or stops the chart arm animation of one player. */
    public interface Driver {
        /** @param animation {@link ChartHeldMotion#READ}, {@link ChartHeldMotion#STOW} or null; started at {@code startTick} when it changes */
        void update(Player player, @Nullable String animation, float startTick);
    }

    private static final class Progress {
        float previous;
        float current;
    }

    private static final Int2ObjectOpenHashMap<Progress> PROGRESS = new Int2ObjectOpenHashMap<>();
    private static @Nullable ClientLevel progressLevel;
    private static Driver driver = (player, animation, startTick) -> { };

    private ChartHeldClient() {
    }

    public static void init() {
        ClientEvents.CLIENT_SETUP.register(ChartHeldClient::onClientSetup);
        ClientEvents.CLIENT_TICK_END.register(ChartHeldClient::onClientTickEnd);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> reset());
    }

    private static void onClientSetup() {
        ClampedItemPropertyFunction open = (stack, level, entity, seed) -> progress(stack, entity);
        ItemProperties.register(ChartContent.CHART.get(), OPEN_PROPERTY, open);
        if (!Services.PLATFORM.isModLoaded(MeleeAnimationsSetup.PAL_MOD_ID)) {
            Constants.LOG.debug("Player Animation Library is not loaded: the chart unrolls without the reading pose");
            return;
        }
        driver = PalChartPoses.register(MeleeClientConfig.ANIMATIONS_LAYER_PRIORITY.get() + PRIORITY_ABOVE_MELEE);
    }

    private static synchronized void reset() {
        PROGRESS.clear();
        progressLevel = null;
    }

    private static void onClientTickEnd(Minecraft mc) {
        if (mc.level == null) {
            reset();
            return;
        }
        synchronized (ChartHeldClient.class) {
            if (progressLevel != mc.level) {
                PROGRESS.clear();
                progressLevel = mc.level;
            }
            PROGRESS.int2ObjectEntrySet().removeIf(e -> !(mc.level.getEntity(e.getIntKey()) instanceof Player));
        }
        for (AbstractClientPlayer player : mc.level.players()) {
            boolean holding = ChartHeld.chartHand(player) != null;
            boolean reading = holding && ChartHeld.isReading(player);
            float before;
            float after;
            synchronized (ChartHeldClient.class) {
                Progress p = PROGRESS.get(player.getId());
                if (p == null) {
                    if (!reading) {
                        driver.update(player, null, 0f);
                        continue;
                    }
                    p = new Progress();
                    PROGRESS.put(player.getId(), p);
                }
                before = p.current;
                after = holding ? ChartHeldMotion.step(before, reading) : 0f;
                p.previous = holding ? before : 0f;
                p.current = after;
                if (!reading && after == 0f && before == 0f) PROGRESS.remove(player.getId());
            }
            String animation = ChartHeldMotion.animation(holding, reading, before);
            float start = ChartHeldMotion.READ.equals(animation) ? ChartHeldMotion.readStartTick(before) : ChartHeldMotion.stowStartTick(before);
            driver.update(player, animation, start);
        }
    }

    /** The property value: the unroll progress of the chart {@code stack} in a hand of {@code entity}, else 0. */
    private static float progress(ItemStack stack, @Nullable LivingEntity entity) {
        if (!(entity instanceof Player player)) return 0f;
        if (player.getMainHandItem() != stack && player.getOffhandItem() != stack) return 0f;
        Progress p;
        synchronized (ChartHeldClient.class) {
            p = PROGRESS.get(player.getId());
            if (p == null) return 0f;
            float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            return ChartHeldMotion.lerp(p.previous, p.current, partial);
        }
    }
}
