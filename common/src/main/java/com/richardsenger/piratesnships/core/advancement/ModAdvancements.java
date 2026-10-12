package com.richardsenger.piratesnships.core.advancement;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.rpg.career.CareerRecord;
import com.richardsenger.piratesnships.rpg.career.InfamyRank;
import com.richardsenger.piratesnships.rpg.career.LetterState;
import com.richardsenger.piratesnships.rpg.career.NavyRank;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/**
 * The one door from gameplay to the advancement tab (ADV1, {@code docs/advancements.md}): every event site calls one
 * line here. Each method takes the responsible entity (anything but a {@link ServerPlayer} is ignored, so a crew
 * member's shot or a test's mock player does nothing), fires the matching {@link ModTrigger} and pays the experience
 * of any advancement that became done ({@link AdvancementXp}). Nothing fires while {@code advancements.enabled} is off;
 * a failure is logged and never reaches the caller, so the hooks cannot change gameplay.
 */
public final class ModAdvancements {

    private ModAdvancements() {
    }

    /** Whether the mod's criteria fire ({@code advancements.enabled}). */
    public static boolean enabled() {
        return AdvancementConfig.ENABLED.get();
    }

    // ------------------------------------------------------------------ ships and sailing

    /** A ship assembled at her helm by {@code player}, {@code blocks} blocks. */
    public static void shipAssembled(@Nullable Entity player, int blocks) {
        fire(player, TriggerIds.SHIP_ASSEMBLED, "", blocks);
    }

    /** {@code sails} sails set (half or full) by {@code player} at a winch, a yard or a cleat. */
    public static void sailSet(@Nullable Entity player, int sails) {
        fire(player, TriggerIds.SAIL_SET, "", sails);
    }

    /** {@code player} aboard a ship making way under sail at {@code tenthsPerSecond} tenths of a block a second. */
    public static void sailedWithWind(@Nullable Entity player, int tenthsPerSecond) {
        fire(player, TriggerIds.SAILED_WITH_WIND, "", tenthsPerSecond);
    }

    public static void anchorDropped(@Nullable Entity player) {
        fire(player, TriggerIds.ANCHOR_DROPPED, "", 1);
    }

    /** A hull patch set into a breach by {@code player}. */
    public static void leakFixed(@Nullable Entity player) {
        fire(player, TriggerIds.LEAK_FIXED, "", 1);
    }

    /** A ship of {@code template} ordered at a shipwright (kind: the template id's path). */
    public static void shipOrdered(@Nullable Entity player, ResourceLocation template) {
        fire(player, TriggerIds.SHIP_ORDERED, template.getPath(), 1);
    }

    /** An ordered ship of {@code template}, {@code blocks} blocks, delivered and assembled for {@code player}. */
    public static void shipLaunched(@Nullable Entity player, ResourceLocation template, int blocks) {
        fire(player, TriggerIds.SHIP_LAUNCHED, template.getPath(), blocks);
    }

    /** A flag hoisted by {@code player} (kind: the flag kind, e.g. {@code jolly_roger}). */
    public static void flagHoisted(@Nullable Entity player, String flagKind) {
        fire(player, TriggerIds.FLAG_HOISTED, flagKind.toLowerCase(Locale.ROOT), 1);
    }

    // ------------------------------------------------------------------ crew

    /** A hand hired by {@code player}; {@code aboard} hands now serve on that ship. */
    public static void crewHired(@Nullable Entity player, int aboard) {
        fire(player, TriggerIds.CREW_HIRED, "", aboard);
    }

    /** An order given to the crew (kind: the order id). */
    public static void orderGiven(@Nullable Entity player, String order) {
        fire(player, TriggerIds.ORDER_GIVEN, order, 1);
    }

    /** {@code player} woke from a full night in a hammock. */
    public static void hammockNight(@Nullable Entity player) {
        fire(player, TriggerIds.HAMMOCK_NIGHT, "", 1);
    }

    /**
     * One dawn of a ship's crew for its owner: wages paid to {@code paid} hands, {@code returned} deserters who changed
     * their minds, and whether anyone deserted (or the crew mutinied), which restarts the ship's loyal-day count.
     */
    public static void crewDay(@Nullable Entity owner, UUID ship, int paid, int returned, boolean deserted) {
        if (!(owner instanceof ServerPlayer p) || !enabled()) return;
        guard(() -> {
            AdvancementRecord rec = Services.ATTACHMENTS.get(p, AdvancementAttachments.RECORD).crewDay(ship.toString(), deserted);
            Services.ATTACHMENTS.set(p, AdvancementAttachments.RECORD, rec);
            if (paid > 0) ModTriggers.get(TriggerIds.WAGES_PAID).fire(p, "", paid);
            if (returned > 0) ModTriggers.get(TriggerIds.DESERTER_RETURNED).fire(p, "", returned);
            ModTriggers.get(TriggerIds.CREW_LOYAL).fire(p, "", rec.loyalDays(ship.toString()));
            AdvancementXp.settle(p);
        });
    }

    // ------------------------------------------------------------------ trade

    /** {@code amount} doubloons paid out to {@code player}; the value is the total earned so far. */
    public static void doubloonsEarned(@Nullable Entity player, long amount) {
        if (!(player instanceof ServerPlayer p) || amount <= 0 || !enabled()) return;
        guard(() -> {
            AdvancementRecord rec = Services.ATTACHMENTS.get(p, AdvancementAttachments.RECORD).earned(amount);
            Services.ATTACHMENTS.set(p, AdvancementAttachments.RECORD, rec);
            ModTriggers.get(TriggerIds.DOUBLOONS_EARNED).fire(p, "", (int) Math.min(Integer.MAX_VALUE, rec.doubloons()));
            AdvancementXp.settle(p);
        });
    }

    public static void contractDelivered(@Nullable Entity player) {
        fire(player, TriggerIds.CONTRACT_DELIVERED, "", 1);
    }

    /** A quest completed (kind: the quest type id); an escort also counts as a convoy escorted. */
    public static void questCompleted(@Nullable Entity player, String type) {
        String kind = type.toLowerCase(Locale.ROOT);
        fire(player, TriggerIds.QUEST_COMPLETED, kind, 1);
        if (kind.equals("escort")) fire(player, TriggerIds.CONVOY_ESCORTED, "", 1);
    }

    // ------------------------------------------------------------------ combat

    /** A gun fired by {@code player} (kind: {@code cannon} or {@code swivel_gun}). */
    public static void cannonFired(@Nullable Entity player, String kind) {
        fire(player, TriggerIds.CANNON_FIRED, kind, 1);
    }

    /** A cannonball of {@code player}'s (or of the ship they own) struck another ship. */
    public static void shipHit(@Nullable Entity player) {
        fire(player, TriggerIds.SHIP_HIT, "", 1);
    }

    /** {@code player} boarded another ship (kind: {@code plank} or {@code party}). */
    public static void shipBoarded(@Nullable Entity player, String how) {
        fire(player, TriggerIds.SHIP_BOARDED, how, 1);
    }

    /** {@code player} took a ship (kind: the beaten side, e.g. {@code navy}). */
    public static void shipCaptured(@Nullable Entity player, String side) {
        fire(player, TriggerIds.SHIP_CAPTURED, side.toLowerCase(Locale.ROOT), 1);
    }

    /** {@code player}'s grappling hook latched onto another ship. */
    public static void grappleLatched(@Nullable Entity player) {
        fire(player, TriggerIds.GRAPPLE_LATCHED, "", 1);
    }

    // ------------------------------------------------------------------ the world

    /** {@code player} stands in a port (kind: the port kind, e.g. {@code navy_outpost}). */
    public static void portVisited(@Nullable Entity player, String kind) {
        fire(player, TriggerIds.PORT_VISITED, kind.toLowerCase(Locale.ROOT), 1);
    }

    public static void treasureDug(@Nullable Entity player) {
        fire(player, TriggerIds.TREASURE_DUG, "", 1);
    }

    /** {@code player} has been aboard a ship through {@code seconds} seconds of storm. */
    public static void stormWeathered(@Nullable Entity player, int seconds) {
        fire(player, TriggerIds.STORM_WEATHERED, "", seconds);
    }

    /** {@code player}'s chart now holds {@code markers} markers. */
    public static void markersPlaced(@Nullable Entity player, int markers) {
        fire(player, TriggerIds.MARKERS_PLACED, "", markers);
    }

    // ------------------------------------------------------------------ the law and careers

    /** {@code player} paid a fine of {@code doubloons}. */
    public static void bountyPaid(@Nullable Entity player, int doubloons) {
        fire(player, TriggerIds.BOUNTY_PAID, "", doubloons);
    }

    /** {@code player} took a prisoner (kind: {@code shackled}, or {@code brigged} once led into a cell). */
    public static void prisonerTaken(@Nullable Entity player, String how) {
        fire(player, TriggerIds.PRISONER_TAKEN, how, 1);
    }

    /**
     * {@code player}'s career record changed: every rank held on either ladder up to the current one (a jump of two
     * ranks earns both) and an active letter of marque.
     */
    public static void careerChanged(@Nullable Entity player, CareerRecord record) {
        if (!(player instanceof ServerPlayer) || !enabled()) return;
        for (NavyRank r : NavyRank.values()) {
            if (r != NavyRank.NONE && r.ordinal() <= record.navy().ordinal()) rankReached(player, "navy/" + r.id());
        }
        for (InfamyRank r : InfamyRank.values()) {
            if (r != InfamyRank.DECKHAND && r.ordinal() <= record.infamy().ordinal()) rankReached(player, "infamy/" + r.id());
        }
        if (record.letter() == LetterState.ACTIVE) rankReached(player, "letter");
    }

    /** A career step (kind: {@code navy/<rank>}, {@code infamy/<rank>} or {@code letter}). */
    public static void rankReached(@Nullable Entity player, String kind) {
        fire(player, TriggerIds.RANK_REACHED, kind, 1);
    }

    // ------------------------------------------------------------------ the Almanac

    public static void almanacOpened(@Nullable Entity player) {
        fire(player, TriggerIds.ALMANAC_OPENED, "", 1);
    }

    /** A Ponder scene opened from the Almanac (kind: the scene id). */
    public static void sceneOpened(@Nullable Entity player, ResourceLocation scene) {
        fire(player, TriggerIds.SCENE_OPENED, scene.toString(), 1);
    }

    /**
     * {@code player} read a page of {@code chapter}. The value is the count of chapters read; the kind is {@code all}
     * once every chapter of {@code book} has been read.
     */
    public static void chapterRead(@Nullable Entity player, ResourceLocation chapter, Collection<ResourceLocation> book) {
        if (!(player instanceof ServerPlayer p) || !enabled() || !book.contains(chapter)) return;
        guard(() -> {
            AdvancementRecord rec = Services.ATTACHMENTS.get(p, AdvancementAttachments.RECORD).withChapter(chapter.toString());
            Services.ATTACHMENTS.set(p, AdvancementAttachments.RECORD, rec);
            boolean all = book.stream().allMatch(c -> rec.chapters().contains(c.toString()));
            ModTriggers.get(TriggerIds.CHAPTERS_READ).fire(p, all ? "all" : "", rec.chapters().size());
            AdvancementXp.settle(p);
        });
    }

    // ------------------------------------------------------------------ plumbing

    private static void fire(@Nullable Entity player, String trigger, String kind, int value) {
        if (!(player instanceof ServerPlayer p) || !enabled()) return;
        guard(() -> {
            ModTriggers.get(trigger).fire(p, kind, value);
            AdvancementXp.settle(p);
        });
    }

    private static void guard(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            Constants.LOG.error("Advancement hook failed", e);
        }
    }
}
