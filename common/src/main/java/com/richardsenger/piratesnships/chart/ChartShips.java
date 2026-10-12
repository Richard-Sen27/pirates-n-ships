package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.net.ChartShip;
import com.richardsenger.piratesnships.chart.net.ChartStatePayload;
import com.richardsenger.piratesnships.sailing.ship.BowFrame;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntime;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import com.richardsenger.piratesnships.ship.sable.ShipEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ships on the chart, server side (work package CHART2). A viewer's chart shows the ship they stand on or ride, and
 * their own ships (owner in the {@link ShipRegistry}) that are loaded in the overworld, each with its heading. Other
 * players' ships are never shown, nor are the ships of NPC convoys and patrols (the chart does not know them): the
 * chart reveals no one but its owner (the human, 2026-10-12).
 */
public final class ChartShips {

    private ChartShips() {
    }

    /** The ships on {@code viewer}'s chart; empty with {@code chart.show_ships} off or outside the overworld. */
    public static List<ChartShip> of(ServerPlayer viewer) {
        if (!ChartConfig.SHOW_SHIPS.get() || !ChartService.charted(viewer.level())) return List.of();
        ServerLevel level = viewer.serverLevel();
        ShipRegistry registry = ShipRegistry.get(level.getServer());
        ShipBody under = ShipEntities.standingOrRiding(viewer);
        UUID aboard = under == null ? null : under.id();
        UUID me = viewer.getUUID();
        List<ChartShip> out = new ArrayList<>();
        for (ShipBody ship : SableShips.all(level)) {
            if (ship.isRemoved()) continue;
            Optional<ShipData> data = registry.find(ship.id());
            boolean isAboard = ship.id().equals(aboard);
            boolean owned = data.flatMap(ShipData::owner).map(me::equals).orElse(false);
            if (!isAboard && !owned) continue;
            // one place is always left for the ship under the viewer
            if (!isAboard && out.size() >= ChartStatePayload.MAX_SHIPS - 1) continue;
            Vec3 c = centre(ship);
            out.add(new ChartShip(data.map(ShipData::name).orElse(""), (int) Math.floor(c.x), (int) Math.floor(c.z),
                    (float) heading(level, ship), isAboard));
        }
        // the ship under the viewer last, so it is drawn on top
        out.sort((a, b) -> Boolean.compare(a.aboard(), b.aboard()));
        return out;
    }

    /**
     * The middle of the ship's blocks in the world: the centre of its plot bounds through the ship's pose (the world
     * bounds lag a tick behind a fresh assembly).
     */
    public static Vec3 centre(ShipBody ship) {
        BlockPos[] b = ship.plotBounds();
        Vec3 plot = new Vec3((b[0].getX() + b[1].getX() + 1) / 2.0, (b[0].getY() + b[1].getY() + 1) / 2.0, (b[0].getZ() + b[1].getZ() + 1) / 2.0);
        return ship.toWorld(plot);
    }

    /** Compass bearing of the bow in degrees (0 north, 90 east): the sailing runtime's bow, else the default frame. */
    public static double heading(ServerLevel level, ShipBody ship) {
        SailingRuntime rt = SailingRuntimes.get(level, ship.id());
        if (rt != null) return rt.headingDegrees(ship);
        Quaterniond q = BowFrame.SOUTH.shipToWorld(ship.orientation(new Quaterniond()), new Quaterniond());
        Vector3d f = q.transform(new Vector3d(0, 0, 1));
        double d = Math.toDegrees(Math.atan2(f.x, -f.z));
        return d < 0 ? d + 360 : d;
    }
}
