package com.richardsenger.piratesnships.rpg.career;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.rpg.reputation.Reputation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The rank rewards (docs/design.md §15, CAR2), server side: what a navy rank or pirate infamy is worth in the world.
 * The rules are pure ({@link CareerRewardRules}); this class reads the config ({@code careers.rewards.*}) and the
 * player's {@link CareerRecord} and is called in one line each from the systems it changes:
 * <ul>
 *   <li><b>Flag right</b>: {@link Careers#effectiveNavyStanding} feeds the false-flag rule ({@code LawService.navyStanding},
 *       so {@code isFalseFlag} and {@code FlagCrimes}' observation) and the docking fee.</li>
 *   <li><b>Docking fee</b>: {@link #feeWaived} ({@code TradeService.dockingFee}).</li>
 *   <li><b>Navy shipyard</b>: {@link #navyShipyard} and {@link #shipPriceFactor} ({@code ShipOrders}: the Orders tab and
 *       ordering at a navy outpost's desk, which {@code MarketBackend.openDesk} sends through {@code ShipOrders.view}).</li>
 *   <li><b>Fences</b>: {@link #infamyPriceBonus} ({@code MarketReputation.priceScore} at pirate islands).</li>
 *   <li><b>Pirate friendship</b>: {@link #piratesFriendly} ({@code SeafarerMob}'s target description).</li>
 *   <li><b>Promotion gifts</b>: {@link #afterStore}, called by {@link Careers#store} whenever a career changes; every
 *       rank gained hands out its {@code <rank>_items} once per player.</li>
 *   <li><b>Ship grants</b> (SHP1): {@link #afterStore} also hands out a ship commission on reaching
 *       {@code careers.ship_grants.navy_rank} or {@code infamy_rank} ({@link ShipGrants}), with its own toggle.</li>
 * </ul>
 * Everything answers "no reward" while {@code careers.enabled} is off. The navy's hunt for the infamous (more patrols)
 * is left to the world simulation's hunt rules (WS4b), which will read {@link Careers#infamy}.
 */
public final class CareerRewards {

    public static final String GIFT = CareerText.MSG + "gift";
    public static final String GIFT_DROPPED = CareerText.MSG + "gift.dropped";

    private CareerRewards() {
    }

    private static boolean on() {
        return Careers.enabled();
    }

    public static CareerRewardRules.Params params() {
        return CareerConfig.rewards();
    }

    /** The navy standing for the false-flag rule and the docking fee (see {@link Careers#effectiveNavyStanding}). */
    static int effectiveNavyStanding(Player player) {
        int standing = Reputation.navyStanding(player);
        if (!on()) return standing;
        return CareerRewardRules.effectiveNavyStanding(Careers.record(player), standing, LawConfig.NAVY_FLAG_MIN_STANDING.get(), params());
    }

    /** Whether navy outposts let {@code player} dock for free for the rank. */
    public static boolean feeWaived(Player player) {
        return on() && CareerRewardRules.feeWaived(Careers.record(player), params());
    }

    /** Whether the shipwright of a navy outpost builds for {@code player}. */
    public static boolean navyShipyard(Player player) {
        return on() && CareerRewardRules.navyShipyard(Careers.record(player), params());
    }

    /** The price factor of a ship {@code player} orders at a navy outpost (1.0 while careers are off). */
    public static double shipPriceFactor(Player player) {
        return on() ? CareerRewardRules.shipPriceFactor(Careers.record(player), params()) : 1.0;
    }

    /** The price score {@code player}'s infamy adds at pirate islands' fences (0 while careers are off). */
    public static int infamyPriceBonus(Player player) {
        return on() ? CareerRewardRules.infamyPriceBonus(Careers.record(player), params()) : 0;
    }

    /** Whether the pirates leave {@code player} alone for the infamy alone (the reputation rule is separate). */
    public static boolean piratesFriendly(Player player) {
        return on() && CareerRewardRules.piratesFriendly(Careers.record(player), params());
    }

    // ------------------------------------------------------------------ promotion gifts

    /** The gift keys {@code player} already received. */
    public static Set<String> gifted(Player player) {
        return new LinkedHashSet<>(Services.ATTACHMENTS.get(player, CareerAttachments.GIFTS));
    }

    /**
     * After a career changed from {@code before} to {@code after}: hands out the items of every rank gained that was
     * not gifted to this player yet, and notes them as gifted. Nothing while careers or {@code promotion_gifts} are off.
     */
    static void afterStore(Player player, CareerRecord before, CareerRecord after) {
        if (!(player instanceof ServerPlayer sp) || !on()) return;
        if (CareerConfig.PROMOTION_GIFTS.get()) promotionGifts(sp, before, after);
        ShipGrants.afterStore(sp, after); // SHP1: the ship commission comes with the rank's gifts
    }

    private static void promotionGifts(ServerPlayer sp, CareerRecord before, CareerRecord after) {
        Set<String> gifted = gifted(sp);
        List<String> due = CareerRewardRules.giftsDue(before, after, gifted);
        if (due.isEmpty()) return;
        for (String key : due) {
            gifted.add(key);
            give(sp, items(key));
        }
        Services.ATTACHMENTS.set(sp, CareerAttachments.GIFTS, List.copyOf(gifted));
    }

    /** The configured items of a gift key (unknown ids are logged and skipped). */
    static List<ItemStack> items(String key) {
        var value = CareerConfig.RANK_ITEMS.get(key);
        if (value == null) return List.of();
        List<ItemStack> out = new ArrayList<>();
        for (String id : value.get()) {
            Optional<Item> item = Optional.ofNullable(ResourceLocation.tryParse(id.trim())).flatMap(BuiltInRegistries.ITEM::getOptional);
            if (item.isEmpty() || item.get() == Items.AIR) {
                Constants.LOG.warn("careers.rewards: unknown item '{}' in the gift of {}", id, key);
                continue;
            }
            out.add(new ItemStack(item.get()));
        }
        return out;
    }

    /** Puts the stacks into the inventory; what does not fit is dropped at the player's feet. */
    static void give(ServerPlayer player, List<ItemStack> stacks) {
        if (stacks.isEmpty()) return;
        boolean dropped = false;
        for (ItemStack stack : stacks) {
            Component name = stack.getHoverName();
            if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                player.drop(stack, false);
                dropped = true;
            }
            player.sendSystemMessage(Component.translatable(GIFT, name).withStyle(ChatFormatting.GOLD));
        }
        player.containerMenu.broadcastChanges();
        if (dropped) player.sendSystemMessage(Component.translatable(GIFT_DROPPED).withStyle(ChatFormatting.YELLOW));
    }

    // ------------------------------------------------------------------ text

    public static void lang(LangBuilder lang) {
        lang.add(GIFT, "With your new rank you receive: %s")
                .add(GIFT_DROPPED, "Your pack is full: the rest lies at your feet.");
    }
}
