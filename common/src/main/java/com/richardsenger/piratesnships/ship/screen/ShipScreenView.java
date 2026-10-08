package com.richardsenger.piratesnships.ship.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

/**
 * What the ship screen at the helm shows (HGUI1, docs/design.md §7.2): one snapshot of a ship for one viewer, built on
 * the server ({@link ShipScreenViews}) and sent in {@link ShipScreenPayloads.State}. Pure data with a codec; the client
 * only draws it and decides which buttons are active through {@link ShipScreenRules}. Text travels as translation keys
 * and plain names, so the client translates.
 *
 * @param ship     the ship's id (actions name it, so a stale screen of another ship is refused)
 * @param helm     plot position of the helm the screen was opened at
 * @param header   name, flag, owner
 * @param status   hull, load, speed, anchor, sails
 * @param upkeep   crew count, bunks, provisions, the last payday
 * @param crew     every crew member aboard, by name
 * @param stations every station aboard, by kind and position
 * @param toggles  which features are on (dismissal, stations)
 */
public record ShipScreenView(UUID ship, BlockPos helm, Header header, Status status, Upkeep upkeep, List<CrewLine> crew,
                             List<StationLine> stations, Toggles toggles) {

    /** Longest ship name the screen accepts (a name tag's anvil limit). */
    public static final int MAX_NAME = 50;

    public ShipScreenView {
        crew = List.copyOf(crew);
        stations = List.copyOf(stations);
    }

    /**
     * @param name       the stored name (with the owner's title in front, HON1), empty when unnamed
     * @param bareName   the name without a title: what the rename field starts with
     * @param flag       the flag the ship flies ({@code FlagReading#id()}: "" none, "jolly_roger", "struck:navy")
     * @param allegiance what the flag tells others
     * @param coverBlown the navy has seen through the ship's colours (FL2)
     * @param owner      the owner's name, empty when the ship has none or the name is unknown
     * @param ownerless  the ship has no owner (anyone may manage it)
     * @param title      translation key of the owner's career title (§15), empty for none or an owner offline
     */
    public record Header(String name, String bareName, String flag, ShipScreenRules.Allegiance allegiance, boolean coverBlown,
                         Optional<String> owner, boolean ownerless, Optional<String> title) {
        public static final Codec<Header> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Header::name),
                Codec.STRING.fieldOf("bare_name").forGetter(Header::bareName),
                Codec.STRING.fieldOf("flag").forGetter(Header::flag),
                ShipScreenRules.enumCodec(ShipScreenRules.Allegiance.class).fieldOf("allegiance").forGetter(Header::allegiance),
                Codec.BOOL.fieldOf("cover_blown").forGetter(Header::coverBlown),
                Codec.STRING.optionalFieldOf("owner").forGetter(Header::owner),
                Codec.BOOL.fieldOf("ownerless").forGetter(Header::ownerless),
                Codec.STRING.optionalFieldOf("title").forGetter(Header::title)
        ).apply(i, Header::new));
    }

    /**
     * @param hull   compartments, flooding, breaches, pumps (as the HUD's strip knows them)
     * @param load   cargo load level ({@code CargoWeight.LoadLevel} ordinal), -1 when unknown
     * @param speed  horizontal speed [blocks/s]
     * @param anchor the anchor's state
     * @param sails  sails aboard
     * @param full   sails set full
     * @param half   sails reefed (half)
     */
    public record Status(ShipScreenRules.Hull hull, int load, float speed, ShipScreenRules.Anchor anchor, int sails, int full,
                         int half) {
        public static final Codec<Status> CODEC = RecordCodecBuilder.create(i -> i.group(
                ShipScreenRules.Hull.CODEC.fieldOf("hull").forGetter(Status::hull),
                Codec.INT.fieldOf("load").forGetter(Status::load),
                Codec.FLOAT.fieldOf("speed").forGetter(Status::speed),
                ShipScreenRules.enumCodec(ShipScreenRules.Anchor.class).fieldOf("anchor").forGetter(Status::anchor),
                Codec.INT.fieldOf("sails").forGetter(Status::sails),
                Codec.INT.fieldOf("full").forGetter(Status::full),
                Codec.INT.fieldOf("half").forGetter(Status::half)
        ).apply(i, Status::new));

        /** Sails furled. */
        public int furled() {
            return Math.max(0, sails - full - half);
        }
    }

    /**
     * @param crew         crew members aboard
     * @param bunks        the crew limit from the hammocks
     * @param foodDays     days of food left for this crew ({@code Double.POSITIVE_INFINITY}: plenty, no crew)
     * @param waterDays    days of water left
     * @param rumDays      days of rum left
     * @param wagesEnabled {@code crew.wages.enabled}
     * @param paidOnce     a payday with wages on has happened
     * @param paid         members paid at the last payday
     * @param unpaid       members not paid at the last payday
     * @param coins        doubloons paid out at the last payday
     */
    public record Upkeep(int crew, int bunks, double foodDays, double waterDays, double rumDays, boolean wagesEnabled,
                         boolean paidOnce, int paid, int unpaid, long coins) {
        public static final Codec<Upkeep> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("crew").forGetter(Upkeep::crew),
                Codec.INT.fieldOf("bunks").forGetter(Upkeep::bunks),
                Codec.DOUBLE.fieldOf("food").forGetter(Upkeep::foodDays),
                Codec.DOUBLE.fieldOf("water").forGetter(Upkeep::waterDays),
                Codec.DOUBLE.fieldOf("rum").forGetter(Upkeep::rumDays),
                Codec.BOOL.fieldOf("wages").forGetter(Upkeep::wagesEnabled),
                Codec.BOOL.fieldOf("paid_once").forGetter(Upkeep::paidOnce),
                Codec.INT.fieldOf("paid").forGetter(Upkeep::paid),
                Codec.INT.fieldOf("unpaid").forGetter(Upkeep::unpaid),
                Codec.LONG.fieldOf("coins").forGetter(Upkeep::coins)
        ).apply(i, Upkeep::new));
    }

    /**
     * One crew member aboard.
     *
     * @param id          its entity UUID (what the actions name)
     * @param name        its name
     * @param morale      0..100
     * @param state       at a station, in a hammock or free
     * @param station     plot position of its station, empty when it mans none
     * @param stationKey  translation key of its station's block, "" without one
     * @param orderKey    translation key of the order it carries out right now, "" for none
     * @param hiredBy     the hirer's name, empty when unknown or never hired
     * @param hiredByYou  the viewer hired it
     * @param unpaid      it went unpaid at the last payday
     * @param desertion   how close it is to deserting
     * @param lowDays     low-morale dawns in a row
     * @param mayCommand  the viewer may release and dismiss it ({@link ShipScreenRules#mayCommand})
     */
    public record CrewLine(UUID id, String name, int morale, ShipScreenRules.CrewState state, Optional<BlockPos> station,
                           String stationKey, String orderKey, Optional<String> hiredBy, boolean hiredByYou, boolean unpaid,
                           ShipScreenRules.Desertion desertion, int lowDays, boolean mayCommand) {
        public static final Codec<CrewLine> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(CrewLine::id),
                Codec.STRING.fieldOf("name").forGetter(CrewLine::name),
                Codec.INT.fieldOf("morale").forGetter(CrewLine::morale),
                ShipScreenRules.enumCodec(ShipScreenRules.CrewState.class).fieldOf("state").forGetter(CrewLine::state),
                BlockPos.CODEC.optionalFieldOf("station").forGetter(CrewLine::station),
                Codec.STRING.fieldOf("station_key").forGetter(CrewLine::stationKey),
                Codec.STRING.fieldOf("order").forGetter(CrewLine::orderKey),
                Codec.STRING.optionalFieldOf("hired_by").forGetter(CrewLine::hiredBy),
                Codec.BOOL.fieldOf("hired_by_you").forGetter(CrewLine::hiredByYou),
                Codec.BOOL.fieldOf("unpaid").forGetter(CrewLine::unpaid),
                ShipScreenRules.enumCodec(ShipScreenRules.Desertion.class).fieldOf("desertion").forGetter(CrewLine::desertion),
                Codec.INT.fieldOf("low_days").forGetter(CrewLine::lowDays),
                Codec.BOOL.fieldOf("may_command").forGetter(CrewLine::mayCommand)
        ).apply(i, CrewLine::new));
    }

    /**
     * One station aboard.
     *
     * @param pos            plot position of the station (its master block)
     * @param blockKey       translation key of its block ("Sail Winch")
     * @param occupant       who mans it, empty when free
     * @param occupantName   the occupant's name, "" when free or unknown
     * @param playerOccupant a player mans it (the screen cannot release a player)
     * @param orderKey       translation key of the order carried out there now, "" for none
     * @param jobKey         translation key of the order of an open job there (CR1), "" for none
     */
    public record StationLine(BlockPos pos, String blockKey, Optional<UUID> occupant, String occupantName, boolean playerOccupant,
                              String orderKey, String jobKey) {
        public static final Codec<StationLine> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(StationLine::pos),
                Codec.STRING.fieldOf("block").forGetter(StationLine::blockKey),
                UUIDUtil.STRING_CODEC.optionalFieldOf("occupant").forGetter(StationLine::occupant),
                Codec.STRING.fieldOf("occupant_name").forGetter(StationLine::occupantName),
                Codec.BOOL.fieldOf("player").forGetter(StationLine::playerOccupant),
                Codec.STRING.fieldOf("order").forGetter(StationLine::orderKey),
                Codec.STRING.fieldOf("job").forGetter(StationLine::jobKey)
        ).apply(i, StationLine::new));
    }

    /**
     * @param stations {@code crew_stations.enabled}: orders, assignments and releases work
     * @param hiring   {@code crew.hiring.enabled}: dismissal works
     * @param mayManage the viewer may rename, order and disassemble ({@link ShipScreenRules#mayManage})
     */
    public record Toggles(boolean stations, boolean hiring, boolean mayManage) {
        public static final Codec<Toggles> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("stations").forGetter(Toggles::stations),
                Codec.BOOL.fieldOf("hiring").forGetter(Toggles::hiring),
                Codec.BOOL.fieldOf("may_manage").forGetter(Toggles::mayManage)
        ).apply(i, Toggles::new));
    }

    public static final Codec<ShipScreenView> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("ship").forGetter(ShipScreenView::ship),
            BlockPos.CODEC.fieldOf("helm").forGetter(ShipScreenView::helm),
            Header.CODEC.fieldOf("header").forGetter(ShipScreenView::header),
            Status.CODEC.fieldOf("status").forGetter(ShipScreenView::status),
            Upkeep.CODEC.fieldOf("upkeep").forGetter(ShipScreenView::upkeep),
            CrewLine.CODEC.listOf().fieldOf("crew").forGetter(ShipScreenView::crew),
            StationLine.CODEC.listOf().fieldOf("stations").forGetter(ShipScreenView::stations),
            Toggles.CODEC.fieldOf("toggles").forGetter(ShipScreenView::toggles)
    ).apply(i, ShipScreenView::new));
}
