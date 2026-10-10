package com.richardsenger.piratesnships.sailing;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import com.richardsenger.piratesnships.sailing.block.SailWinchBlock;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.helm.HelmSetup;
import com.richardsenger.piratesnships.sailing.sail.SailDecorations;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsControls;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips;
import com.richardsenger.piratesnships.sailing.ship.SailingRuntimes;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import com.richardsenger.piratesnships.sailing.wind.WindSync;
import com.richardsenger.piratesnships.ship.ShipBlockChanges;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipForces;
import java.util.List;
import net.minecraft.tags.BlockTags;

/**
 * The {@code sailing} module (docs/design.md §5): the wind field and its client sync, the pure force model
 * ({@code sailing.force}), the sail blocks and the sail winch ({@code sailing.block}), and the per-ship runtime that
 * applies sail and keel forces to Sable ships every physics substep ({@code sailing.ship}).
 */
public final class SailingModule implements ModModule {

    @Override
    public String id() {
        return "sailing";
    }

    @Override
    public void registerConfig() {
        SailingConfig.init();
        com.richardsenger.piratesnships.sailing.anchor.AnchorConfig.init(); // visible anchor (F4)
        HelmSetup.registerConfig(); // wheel steering (HELM1)
        com.richardsenger.piratesnships.sailing.effects.SeaEffectsConfig.init(); // wind streaks (WD1)
        com.richardsenger.piratesnships.sailing.sail.SailVisualsConfig.init(); // sails in the wind (VIS1b), client section
    }

    @Override
    public void registerContent() {
        SailingBlocks.init();
        ShipForces.register();
        ShipForces.registerAnchor(); // the anchor's chain (AN2a)
        ShipForces.registerBallast(); // the shipwright's ballast (TRM1)
        com.richardsenger.piratesnships.sailing.anchor.AnchorContent.init(); // visible anchor (F4)
        HelmSetup.registerContent(); // wheel steering (HELM1)
    }

    @Override
    public void registerPayloads() {
        WindSync.registerPayloads();
        HelmSetup.registerPayloads(); // wheel steering (HELM1)
        com.richardsenger.piratesnships.sailing.ship.ShipBowSync.registerPayloads(); // the bow for the sails (VIS1c)
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(WindSync::onServerTick);
        CommonEvents.PLAYER_LOGIN.register(WindSync::onLogin);
        CommonEvents.LEVEL_TICK_END.register(SailingRuntimes::onLevelTick);
        // TRM1: the shipwright's ballast, after the runtimes so a freshly found ship gets its ballast this tick
        CommonEvents.LEVEL_TICK_END.register(com.richardsenger.piratesnships.sailing.ballast.ShipBallast::onLevelTick);
        // VIS1c: each ship's bow to the clients that render it, after the runtimes so a fresh one goes out this tick
        CommonEvents.LEVEL_TICK_END.register(com.richardsenger.piratesnships.sailing.ship.ShipBowSync::onLevelTick);
        CommonEvents.SERVER_STOPPED.register(server -> SailingRuntimes.onServerStopped());
        CommonEvents.SERVER_STOPPED.register(server -> com.richardsenger.piratesnships.sailing.ballast.ShipBallast.clear());
        CommonEvents.SERVER_STOPPED.register(server -> com.richardsenger.piratesnships.sailing.ship.ShipBowSync.onServerStopped());
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> SailingCommands.register(dispatcher));
        SableShips.onShipRemoved(SailingRuntimes::onShipRemoved);
        SableShips.onShipRemoved(com.richardsenger.piratesnships.sailing.ship.ShipBowSync::onShipRemoved);
        SableShips.onPhysicsTick(SailingRuntimes::onPhysicsTick);
        ShipBlockChanges.register(SailingRuntimes::onBlockChanged);
        SableShips.onShipRemoved((level, id, destroyed) -> com.richardsenger.piratesnships.sailing.ballast.ShipBallast.onShipRemoved(level, id));
        SableShips.onPhysicsTick(com.richardsenger.piratesnships.sailing.ballast.ShipBallast::onPhysicsTick);
        ShipBlockChanges.register(com.richardsenger.piratesnships.sailing.ballast.ShipBallast::onBlockChanged);
        // HL1b: a ship Sable moves to a new body keeps its bow (position-free); the anchor (a plot position) stays behind
        com.richardsenger.piratesnships.ship.assembly.ShipSplits.carryOnIdentityMove(SailingRuntimes.USER_DATA_KEY, tag -> {
            net.minecraft.nbt.CompoundTag kept = new net.minecraft.nbt.CompoundTag();
            if (tag.contains("bow")) {
                kept.putString("bow", tag.getString("bow"));
            }
            return kept;
        });
        // wheel steering (HELM1): the helm's steering handler starts a wheel session, or runs the click steps of
        // ShipControls.steer when helm.wheel.drag_steering is off
        HelmSetup.registerEvents();
        com.richardsenger.piratesnships.sailing.anchor.AnchorEntities.registerEvents(); // visible anchor (F4)
        com.richardsenger.piratesnships.sailing.anchor.AnchorPhysics.registerEvents(); // the anchor's chain force (AN2a)
    }

    @Override
    public void gatherData(DataContributions data) {
        com.richardsenger.piratesnships.sailing.anchor.AnchorData.gather(data); // visible anchor (F4)
        com.richardsenger.piratesnships.sailing.sail.TriangularSailData.gather(data); // cleat and rope (F5b)
        HelmSetup.gatherData(data); // wheel steering (HELM1)
        com.richardsenger.piratesnships.sailing.block.YardData.gather(data); // the yard in every wood (WOOD3)
        com.richardsenger.piratesnships.sailing.block.WinchAndCapstanData.gather(data); // winch and capstan families (WOOD4)
        data.lang(lang -> {
            lang.add(YardBlock.KEY_NO_SAIL, "No sail: hang a yard %s-%s below")
                    .add(ShipForces.SAILING_KEY, "Sails and Keel")
                    .add(ShipForces.ANCHOR_KEY, "Anchor Chain")
                    .add(ShipForces.BALLAST_KEY, "Ballast")
                    .add(SailWinchBlock.trimKey(SailTrim.FURLED), "furled")
                    .add(SailWinchBlock.trimKey(SailTrim.HALF), "half sail")
                    .add(SailWinchBlock.trimKey(SailTrim.FULL), "full sail")
                    .add(SailWinchBlock.KEY_NOT_ON_SHIP, "The winch must be on an assembled ship")
                    .add(SailWinchBlock.KEY_NO_SAILS, "This ship has no sails")
                    .add(ShipControls.KEY_RUDDER_MIDSHIPS, "Rudder midships")
                    .add(ShipControls.KEY_PORT, "port")
                    .add(ShipControls.KEY_STARBOARD, "starboard")
                    .add(ShipControls.KEY_STEERING_OFF, "Steering is off on this server")
                    .add(ShipControls.KEY_CAPSTAN_NOT_ON_SHIP, "The capstan must be on an assembled ship")
                    .add(ShipControls.KEY_CAPSTAN_OFF, "Anchors are disabled on this server")
                    .add(ShipControls.KEY_NO_GROUND, "Too deep: no ground within %s blocks");
            // SAIL2: dyeing sails and hanging banners on them
            lang.add(SailDecorations.Outcome.SAME_DYE.key(), "This sail is already %s")
                    .add(SailDecorations.Outcome.NO_SAIL.key(), "No sail here to dye or hang a banner on")
                    .add(SailDecorations.Outcome.HAS_BANNER.key(), "Take the banner down first")
                    .add(SailDecorations.Outcome.TOO_SMALL.key(), "Too small: yards %s+ long, %s+ apart")
                    .add(SailDecorations.Outcome.ALREADY_BANNER.key(), "A banner hangs on this sail already")
                    .add(SailDecorations.Outcome.STAY_DYE_ONLY.key(), "Triangular sails take dye only");
            String k = SailingCommands.KEY;
            lang.add(k + "wind.get", "Wind from %s° at %s blocks/s (%s)")
                    .add(k + "wind.fixed", "fixed by command")
                    .add(k + "wind.natural", "natural")
                    .add(k + "wind.set", "Wind fixed: from %s° at %s blocks/s, until /pirates wind clear")
                    .add(k + "wind.clear", "Wind override cleared, the natural wind blows again")
                    .add(k + "ship.none", "You are not on an assembled ship")
                    .add(k + "ship.idle", "No forces this tick: no sail is set and the ship is not moving in water");
        });
        data.blockTags(tags -> {
            // what may stand between the two yards of a square sail (besides air); our own mast blocks join later
            tags.tag(SailingBlocks.MASTS).addTag(BlockTags.LOGS).addTag(BlockTags.WOODEN_FENCES);
        });
    }

    @Override
    public void initClient() {
        com.richardsenger.piratesnships.sailing.client.SailingClient.init();
        com.richardsenger.piratesnships.sailing.anchor.client.AnchorClient.init(); // visible anchor (F4)
        HelmSetup.initClient(); // wheel steering (HELM1)
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(SailingGameTests.class, SailingGameTestsShips.class, SailingGameTestsControls.class,
                com.richardsenger.piratesnships.sailing.ship.SailingGameTestsStays.class,
                com.richardsenger.piratesnships.sailing.ship.SailingGameTestsRigging.class,
                com.richardsenger.piratesnships.sailing.helm.HelmSteeringGameTests.class,
                com.richardsenger.piratesnships.sailing.rope.RopeLineGameTests.class,
                com.richardsenger.piratesnships.sailing.ship.HelmHandoverGameTests.class,
                com.richardsenger.piratesnships.sailing.anchor.AnchorPhysicsGameTests.class,
                com.richardsenger.piratesnships.sailing.ship.SailingGameTestsTurning.class,
                com.richardsenger.piratesnships.sailing.ship.ShipBowSyncGameTests.class,
                com.richardsenger.piratesnships.sailing.ship.SailDyeGameTests.class,
                com.richardsenger.piratesnships.sailing.ballast.BallastGameTests.class);
    }
}
