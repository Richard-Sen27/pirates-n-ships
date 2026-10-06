package com.richardsenger.piratesnships.station;

import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import com.richardsenger.piratesnships.station.winch.CaptainsWhistleItem;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;

/** Registered content of the station module: the seat, the test crew member and the captain's whistle. */
public final class StationContent {

    public static final RegistryEntry<EntityType<?>, EntityType<StationSeat>> STATION_SEAT = ModRegistry.entity("station_seat",
            () -> EntityType.Builder.<StationSeat>of(StationSeat::new, MobCategory.MISC)
                    .sized(0.25f, 0.01f).noSummon().fireImmune().clientTrackingRange(10).updateInterval(1));

    public static final RegistryEntry<EntityType<?>, EntityType<CrewMember>> CREW_MEMBER = ModRegistry.entity("crew_member",
            () -> EntityType.Builder.of(CrewMember::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10));

    /** The sail order selected on a whistle. */
    public static final RegistryEntry<DataComponentType<?>, DataComponentType<SailOrder>> WHISTLE_ORDER = ModRegistry.dataComponent(
            "whistle_order", b -> b.persistent(SailOrder.CODEC).networkSynchronized(ByteBufCodecs.idMapper(i -> SailOrder.values()[i], SailOrder::ordinal)));

    public static final RegistryEntry<net.minecraft.world.item.Item, CaptainsWhistleItem> CAPTAINS_WHISTLE = ModRegistry.item("captains_whistle",
            () -> new CaptainsWhistleItem(new Item.Properties().stacksTo(1)));

    private StationContent() {
    }

    public static void init() {
        Services.REGISTRY.registerEntityAttributes(CREW_MEMBER, CrewMember::createAttributes);
    }
}
