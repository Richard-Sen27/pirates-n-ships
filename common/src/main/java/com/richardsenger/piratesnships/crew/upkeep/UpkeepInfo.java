package com.richardsenger.piratesnships.crew.upkeep;

import com.richardsenger.piratesnships.crew.galley.GalleyText;
import com.richardsenger.piratesnships.crew.galley.ProvisionContainer;
import com.richardsenger.piratesnships.crew.galley.ShipProvisions;
import com.richardsenger.piratesnships.crew.hammock.ShipBunks;
import com.richardsenger.piratesnships.crew.provisions.CrewHeadcount;
import com.richardsenger.piratesnships.crew.provisions.SuppliesLeft;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * The upkeep lines of {@code /pirates crew info} (CR2): supplies left for the crew aboard now ("Supplies: food 3.5
 * days, water 1.0 days, rum 0.0 days"), the last pay and the work speed.
 */
public final class UpkeepInfo {

    private UpkeepInfo() {
    }

    public static List<Component> lines(ServerLevel level, ShipBody ship) {
        ShipUpkeep upkeep = ShipUpkeepData.get(level.getServer()).get(ship.id());
        SuppliesLeft left = suppliesLeft(level, ship);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(UpkeepText.INFO_SUPPLIES, days(left.foodDays()), days(left.waterDays()), days(left.rumDays())));
        ShipUpkeep.PayRecord pay = upkeep.lastPay();
        if (!Upkeep.settings().wagesEnabled()) {
            lines.add(Component.translatable(UpkeepText.INFO_PAY_OFF));
        } else if (!pay.wagesOn()) {
            lines.add(Component.translatable(UpkeepText.INFO_PAY_NONE));
        } else if (pay.walletCoins() > 0) {
            lines.add(Component.translatable(UpkeepText.INFO_PAY_WALLET, pay.paid(), pay.unpaid(), pay.coins(), pay.walletCoins()));
        } else {
            lines.add(Component.translatable(UpkeepText.INFO_PAY, pay.paid(), pay.unpaid(), pay.coins()));
        }
        lines.add(Component.translatable(UpkeepText.INFO_WORK, String.format(Locale.ROOT, "%.0f", upkeep.workSpeed() * 100)));
        return lines;
    }

    /** The supplies left aboard {@code ship} for the crew aboard now. */
    public static SuppliesLeft suppliesLeft(ServerLevel level, ShipBody ship) {
        ShipUpkeep upkeep = ShipUpkeepData.get(level.getServer()).get(ship.id());
        List<BlockPos> containers = new ArrayList<>();
        for (BlockPos p : ship.plotBlocks()) {
            if (level.getBlockEntity(p) instanceof ProvisionContainer) containers.add(p.immutable());
        }
        int crew = ShipBunks.crewOf(level, ship).size();
        return ShipProvisions.suppliesLeft(level, containers, upkeep.provisioning(), CrewHeadcount.crew(crew));
    }

    /** Supplies as a number of days, or "plenty". */
    public static Object days(double d) {
        return Double.isInfinite(d) ? Component.translatable(UpkeepText.INFO_PLENTY) : GalleyText.number(d);
    }
}
