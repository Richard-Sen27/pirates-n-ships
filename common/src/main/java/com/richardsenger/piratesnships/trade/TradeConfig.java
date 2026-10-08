package com.richardsenger.piratesnships.trade;

import com.richardsenger.piratesnships.core.config.ConfigSection;
import com.richardsenger.piratesnships.core.config.ConfigValue;
import com.richardsenger.piratesnships.core.config.ModConfigs;
import com.richardsenger.piratesnships.trade.cargo.CargoWeight;
import com.richardsenger.piratesnships.trade.contract.ContractParams;
import com.richardsenger.piratesnships.trade.market.MarketParams;
import com.richardsenger.piratesnships.trade.plunder.PlunderRules;
import com.richardsenger.piratesnships.trade.plunder.PortFees;

/**
 * Server config of the trade module (design.md §17, group "Cargo &amp; trade"), section {@code cargo_trade}. The pure
 * rules never read these handles; the adapters below copy them into plain parameter records whose {@code DEFAULTS}
 * equal the config defaults.
 */
public final class TradeConfig {

    private static final MarketParams M = MarketParams.DEFAULTS;
    private static final ContractParams C = ContractParams.DEFAULTS;
    private static final PlunderRules.Params P = PlunderRules.Params.DEFAULTS;
    private static final PortFees.Params F = PortFees.Params.DEFAULTS;
    private static final CargoWeight.Params W = CargoWeight.Params.DEFAULTS;

    private static final ConfigSection S = ModConfigs.server("cargo_trade", "Cargo weight, port markets, contracts, plunder and port fees");

    // --- Cargo ------------------------------------------------------------------------------------------------
    public static final ConfigValue<Boolean> CARGO_WEIGHT_AFFECTS_SHIPS = S.bool("cargo_weight_affects_ships", W.affectsShips(),
            "Cargo weight makes ships sit lower, slower and less agile");
    public static final ConfigValue<Double> WEIGHT_FACTOR = S.doubleRange("weight_factor", W.weightFactor(), 0.0, 100.0,
            "Multiplier from cargo weight to the weight applied to the ship");
    public static final ConfigValue<Double> DEFAULT_ITEM_WEIGHT = S.doubleRange("default_item_weight", W.defaultItemWeight(), 0.0, 100.0,
            "Cargo weight of one item that is not a trade good");
    public static final ConfigValue<Integer> WEIGH_INTERVAL_TICKS = S.intRange("weigh_interval_ticks", 40, 1, 1200,
            "Ticks between two weighings of a ship's cargo (vanilla containers' downward force, load level)");
    /** Read by {@code world.treasure} (TM1): the fence's market line for blank treasure maps on pirate islands. */
    public static final ConfigValue<Integer> TREASURE_MAP_PRICE = S.intRange("treasure_map_price", 60, 0, 100_000,
            "Doubloons a fence on a pirate island asks for one blank treasure map");
    private static final ConfigSection LOAD = S.section("load_levels", "Load level thresholds as weight / ship capacity");
    public static final ConfigValue<Double> LADEN_AT = LOAD.doubleRange("laden_at", W.ladenAt(), 0.0, 10.0, "Ratio from which a ship is laden");
    public static final ConfigValue<Double> HEAVY_AT = LOAD.doubleRange("heavily_laden_at", W.heavyAt(), 0.0, 10.0, "Ratio from which a ship is heavily laden");
    public static final ConfigValue<Double> OVERLOADED_AT = LOAD.doubleRange("overloaded_above", W.overloadedAt(), 0.0, 10.0, "Ratio above which a ship is overloaded");
    public static final ConfigValue<Double> CAPACITY_PER_BLOCK = LOAD.doubleRange("capacity_per_block", CargoWeight.CAPACITY_PER_BLOCK, 0.01, 1000.0,
            "A ship's cargo capacity in weight units per block of the ship (the load ratio is cargo weight / (blocks × this))");

    // --- Market -----------------------------------------------------------------------------------------------
    private static final ConfigSection MARKET = S.section("market", "Port markets and dynamic prices");
    public static final ConfigValue<Double> PRICE_VOLATILITY = MARKET.doubleRange("price_volatility", M.volatility(), 0.0, 10.0,
            "How strongly trading moves prices: log-price change per 64 units of a normal good in a village (0.05 = about 5 % per stack)");
    public static final ConfigValue<Double> PRICE_RECOVERY_RATE = MARKET.doubleRange("price_recovery_rate", M.recoveryPerDay(), 0.0, 1.0,
            "Share of a price and stock deviation that recovers per in-game day (1 = instantly)");
    public static final ConfigValue<Double> SPREAD = MARKET.doubleRange("spread", M.spread(), 0.01, 1.0,
            "Gap between buy and sell price as a share of the mid price (must be above 0 so trading in one port always loses)");
    public static final ConfigValue<Double> PRODUCED_FACTOR = MARKET.doubleRange("produced_price_factor", M.producedFactor(), 0.01, 100.0,
            "Price multiplier for goods a port produces");
    public static final ConfigValue<Double> NEUTRAL_FACTOR = MARKET.doubleRange("neutral_price_factor", M.neutralFactor(), 0.01, 100.0,
            "Price multiplier for goods a port neither produces nor demands");
    public static final ConfigValue<Double> DEMANDED_FACTOR = MARKET.doubleRange("demanded_price_factor", M.demandedFactor(), 0.01, 100.0,
            "Price multiplier for goods a port demands");
    public static final ConfigValue<Boolean> STOCK_LIMITS = MARKET.bool("stock_limits", M.stockLimits(),
            "Ports have limited stock to sell and stop buying a good once saturated");
    public static final ConfigValue<Double> STOCK_PRODUCED = MARKET.doubleRange("stock_produced", M.stockProduced(), 0.0, 1_000_000.0,
            "Units in stock of a produced good (times the good's stock factor and the port depth)");
    public static final ConfigValue<Double> STOCK_NEUTRAL = MARKET.doubleRange("stock_neutral", M.stockNeutral(), 0.0, 1_000_000.0,
            "Units in stock of a neutral good");
    public static final ConfigValue<Double> STOCK_DEMANDED = MARKET.doubleRange("stock_demanded", M.stockDemanded(), 0.0, 1_000_000.0,
            "Units in stock of a demanded good");
    public static final ConfigValue<Double> ABSORB_PRODUCED = MARKET.doubleRange("absorb_produced", M.absorbProduced(), 0.0, 1_000_000.0,
            "Units of a produced good a port buys before it is saturated");
    public static final ConfigValue<Double> ABSORB_NEUTRAL = MARKET.doubleRange("absorb_neutral", M.absorbNeutral(), 0.0, 1_000_000.0,
            "Units of a neutral good a port buys before it is saturated");
    public static final ConfigValue<Double> ABSORB_DEMANDED = MARKET.doubleRange("absorb_demanded", M.absorbDemanded(), 0.0, 1_000_000.0,
            "Units of a demanded good a port buys before it is saturated");
    public static final ConfigValue<Double> DEPTH_VILLAGE = MARKET.doubleRange("depth_seafarer_village", M.depthVillage(), 0.01, 100.0,
            "Market depth of seafarer villages: bigger stock and smaller price impact");
    public static final ConfigValue<Double> DEPTH_NAVY = MARKET.doubleRange("depth_navy_outpost", M.depthNavy(), 0.01, 100.0,
            "Market depth of navy outposts");
    public static final ConfigValue<Double> DEPTH_PIRATE = MARKET.doubleRange("depth_pirate_island", M.depthPirate(), 0.01, 100.0,
            "Market depth of pirate islands");

    // --- Contracts --------------------------------------------------------------------------------------------
    private static final ConfigSection CONTRACTS = S.section("contracts", "Delivery contracts offered by harbor masters");
    public static final ConfigValue<Boolean> CONTRACTS_ENABLED = CONTRACTS.bool("enabled", C.enabled(), "Harbor masters offer delivery contracts");
    public static final ConfigValue<Integer> OFFERS_PER_DAY = CONTRACTS.intRange("offers_per_day", C.offersPerDay(), 0, 32, "Contract offers per port and day");
    public static final ConfigValue<Integer> MIN_QUANTITY = CONTRACTS.intRange("min_quantity", C.minQuantity(), 1, 100000, "Smallest contract quantity (before the good's stock factor)");
    public static final ConfigValue<Integer> MAX_QUANTITY = CONTRACTS.intRange("max_quantity", C.maxQuantity(), 1, 100000, "Largest contract quantity (before the good's stock factor)");
    public static final ConfigValue<Double> REWARD_BASE = CONTRACTS.doubleRange("reward_base", C.rewardBase(), 0.0, 100.0, "Reward per unit of base value before bonuses");
    public static final ConfigValue<Double> PRICE_DIFFERENCE_WEIGHT = CONTRACTS.doubleRange("price_difference_weight", C.priceDifferenceWeight(), 0.0, 100.0,
            "Extra reward per unit of base value per point of price factor difference between the ports");
    public static final ConfigValue<Double> DISTANCE_BONUS = CONTRACTS.doubleRange("distance_bonus_per_1000_blocks", C.distanceBonusPer1000(), 0.0, 100.0,
            "Reward multiplier gained per 1000 blocks of distance");
    public static final ConfigValue<Double> RISK_BONUS = CONTRACTS.doubleRange("risk_bonus", C.riskBonus(), 0.0, 100.0,
            "Reward multiplier gained on the riskiest routes (through pirate waters)");
    public static final ConfigValue<Double> BLOCKS_PER_DAY = CONTRACTS.doubleRange("blocks_per_day", C.blocksPerDay(), 1.0, 1_000_000.0,
            "Expected travel per in-game day when setting deadlines");
    public static final ConfigValue<Integer> SLACK_DAYS = CONTRACTS.intRange("slack_days", C.slackDays(), 0, 1000, "Extra days on top of the travel time");
    public static final ConfigValue<Integer> OFFER_LIFETIME_DAYS = CONTRACTS.intRange("offer_lifetime_days", C.offerLifetimeDays(), 0, 1000,
            "Days an offer stays on the board after the day it was made");
    public static final ConfigValue<Double> DEPOSIT_FRACTION = CONTRACTS.doubleRange("deposit_fraction", C.depositFraction(), 0.0, 10.0,
            "Deposit paid on accepting, as a share of the reward (refunded on delivery, lost on failure)");
    public static final ConfigValue<Integer> MAX_ACTIVE = CONTRACTS.intRange("max_active_per_player", C.maxActivePerPlayer(), 0, 100,
            "Accepted contracts one player may hold at once");

    // --- Plunder ----------------------------------------------------------------------------------------------
    private static final ConfigSection PLUNDER = S.section("plunder", "Selling plundered cargo");
    public static final ConfigValue<Boolean> PLUNDER_ENABLED = PLUNDER.bool("enabled", P.enabled(),
            "Plundered goods are marked and treated differently by fences and navy ports");
    public static final ConfigValue<Double> FENCE_DISCOUNT = PLUNDER.doubleRange("fence_discount", P.fenceDiscount(), 0.0, 1.0,
            "Share a pirate fence takes off the price of plundered goods");
    public static final ConfigValue<Double> NAVY_NOTICE_CHANCE = PLUNDER.doubleRange("navy_notice_chance", P.navyNoticeChance(), 0.0, 1.0,
            "Chance per 64 units that a navy outpost notices plundered goods");
    public static final ConfigValue<Double> VILLAGE_NOTICE_CHANCE = PLUNDER.doubleRange("village_notice_chance", P.villageNoticeChance(), 0.0, 1.0,
            "Chance per 64 units that a seafarer village notices plundered goods");
    public static final ConfigValue<Boolean> CONFISCATE = PLUNDER.bool("confiscate_when_noticed", P.confiscateWhenNoticed(),
            "Noticed plundered goods are confiscated without payment");

    // --- Fees -------------------------------------------------------------------------------------------------
    private static final ConfigSection FEES = S.section("port_fees", "Docking fees in navy ports");
    public static final ConfigValue<Boolean> PORT_FEES = FEES.bool("enabled", F.enabled(), "Navy outposts charge a docking fee");
    public static final ConfigValue<Integer> NAVY_DOCKING_FEE = FEES.intRange("navy_docking_fee", F.navyDockingFee(), 0, 1_000_000, "Docking fee in doubloons");
    public static final ConfigValue<Integer> FEE_WAIVER_STANDING = FEES.intRange("waiver_navy_standing", F.waiverStanding(), -1_000_000, 1_000_000,
            "Navy standing at or above which the fee is waived");

    // --- Cargo containers and market backend ----------------------------------------------------------------
    /** What an empty cargo container accepts. */
    public enum ContainerAccepts { ANY_STACKABLE, TRADE_GOODS_ONLY }

    private static final ConfigSection CONTAINERS = S.section("containers", "Bulk cargo crates and barrels (one kind of item each)");
    public static final ConfigValue<Integer> CRATE_CAPACITY_STACKS = CONTAINERS.intRange("crate_capacity_stacks", 32, 1, 4096,
            "Capacity of a cargo crate in full stacks of the held item (32 stacks = 2048 sugar or 512 rum)");
    public static final ConfigValue<Integer> BARREL_CAPACITY_ITEMS = CONTAINERS.intRange("barrel_capacity_items", 1536, 1, 262144,
            "Capacity of a cargo barrel in items, whatever their stack size (better than a crate for rum and other small stacks)");
    public static final ConfigValue<ContainerAccepts> CONTAINER_ACCEPTS = CONTAINERS.enumValue("accepts", ContainerAccepts.ANY_STACKABLE,
            "What an empty container accepts: any stackable item, or only trade goods");

    private static final ConfigSection BACKEND = S.section("market_backend", "Limits the server enforces on market requests from clients");
    public static final ConfigValue<Integer> MAX_TRADE_QUANTITY = BACKEND.intRange("max_trade_quantity", 4096, 1, 262144,
            "Largest quantity one buy or sell request may move");
    public static final ConfigValue<Double> MARKET_REACH = BACKEND.doubleRange("market_reach", 16.0, 1.0, 256.0,
            "How far (blocks) a player may move from where the market was opened and still trade");
    public static final ConfigValue<Double> CONTAINER_REACH = BACKEND.doubleRange("container_reach", 16.0, 1.0, 256.0,
            "How far (blocks) a cargo container may be from the player to buy into it or sell or deliver from it");
    public static final ConfigValue<Integer> MARKET_REFRESH_TICKS = BACKEND.intRange("market_refresh_ticks", 20, 1, 1200,
            "How often (ticks) open market screens are re-sent when what they show changed (prices, stock, the viewer's doubloons)");

    // --- Harbor master's desks -------------------------------------------------------------------------------
    private static final ConfigSection DESKS = S.section("harbor_desks", "Harbor master's desks: the block that opens a port's market screen");
    public static final ConfigValue<Boolean> DESKS_ENABLED = DESKS.bool("desks_enabled", true,
            "Harbor master's desks open the market screen (off = desks are inert; the debug commands still work)");
    public static final ConfigValue<Double> DESK_REACH = DESKS.doubleRange("desk_reach", 8.0, 1.0, 64.0,
            "How far (blocks) a player may be from the desk and still trade; the screen closes beyond it");
    public static final ConfigValue<Boolean> DESK_DIRECT_USE = DESKS.bool("direct_use", true,
            "Using the desk block opens the market (off = only talking to the port's harbor master opens it)");

    private TradeConfig() {
    }

    /** Loads the class so the values above are declared in time. Called from {@code registerConfig()}. */
    public static void init() {
    }

    public static MarketParams marketParams() {
        return new MarketParams(PRICE_VOLATILITY.get(), PRICE_RECOVERY_RATE.get(), Math.max(0.01, SPREAD.get()),
                PRODUCED_FACTOR.get(), NEUTRAL_FACTOR.get(), DEMANDED_FACTOR.get(),
                STOCK_LIMITS.get(), STOCK_PRODUCED.get(), STOCK_NEUTRAL.get(), STOCK_DEMANDED.get(),
                ABSORB_PRODUCED.get(), ABSORB_NEUTRAL.get(), ABSORB_DEMANDED.get(),
                DEPTH_VILLAGE.get(), DEPTH_NAVY.get(), DEPTH_PIRATE.get());
    }

    public static ContractParams contractParams() {
        int lo = MIN_QUANTITY.get();
        int hi = Math.max(lo, MAX_QUANTITY.get());
        return new ContractParams(CONTRACTS_ENABLED.get(), OFFERS_PER_DAY.get(), lo, hi,
                REWARD_BASE.get(), PRICE_DIFFERENCE_WEIGHT.get(), DISTANCE_BONUS.get(), RISK_BONUS.get(),
                BLOCKS_PER_DAY.get(), SLACK_DAYS.get(), OFFER_LIFETIME_DAYS.get(), DEPOSIT_FRACTION.get(), MAX_ACTIVE.get());
    }

    public static PlunderRules.Params plunderParams() {
        return new PlunderRules.Params(PLUNDER_ENABLED.get(), FENCE_DISCOUNT.get(), NAVY_NOTICE_CHANCE.get(),
                VILLAGE_NOTICE_CHANCE.get(), CONFISCATE.get());
    }

    public static PortFees.Params feeParams() {
        return new PortFees.Params(PORT_FEES.get(), NAVY_DOCKING_FEE.get(), FEE_WAIVER_STANDING.get());
    }

    public static CargoWeight.Params cargoParams() {
        // Keep the thresholds ordered even if the config file isn't
        double laden = LADEN_AT.get();
        double heavy = Math.max(laden, HEAVY_AT.get());
        double over = Math.max(heavy, OVERLOADED_AT.get());
        return new CargoWeight.Params(CARGO_WEIGHT_AFFECTS_SHIPS.get(), WEIGHT_FACTOR.get(), DEFAULT_ITEM_WEIGHT.get(), laden, heavy, over);
    }
}
