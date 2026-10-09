package com.richardsenger.piratesnships.almanac.data;

import com.mojang.authlib.GameProfile;
import com.richardsenger.piratesnships.almanac.sync.AlmanacPagePayload;
import com.richardsenger.piratesnships.almanac.sync.AlmanacSync;
import com.richardsenger.piratesnships.almanac.sync.AlmanacSyncPayload;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The Almanac's data side in a running server (ALM2): the built-in chapters after the datapack load, the sync payloads
 * (captured, not sent), and the last page per player through death and relog.
 */
public final class AlmanacDataGameTests {

    private static final String CONFIG_BATCH = "pirates_n_ships_config_almanac_remember_page";

    private AlmanacDataGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(AlmanacDataGameTests.class);
    }

    private static ServerPlayer serverPlayer(GameTestHelper helper, UUID id) {
        GameProfile profile = new GameProfile(id, "almanac_test");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        return p;
    }

    /** The datapack load put both built-in chapters into the server's book, in order and with their pages. */
    @ModGameTest
    public static void serverBookHoldsTheExampleChapters(GameTestHelper helper) {
        AlmanacBook book = AlmanacBook.server();
        Chapter welcome = book.chapter(AlmanacChapterProvider.WELCOME).orElse(null);
        Chapter cannons = book.chapter(AlmanacChapterProvider.CANNONS).orElse(null);
        helper.assertTrue(welcome != null, "welcome chapter loaded, book " + book);
        helper.assertTrue(cannons != null, "cannons chapter loaded, book " + book);
        helper.assertValueEqual(welcome.pageCount(), 2, "welcome pages");
        helper.assertValueEqual(cannons.pageCount(), 3, "cannons pages");
        helper.assertTrue(book.chapters().indexOf(welcome) < book.chapters().indexOf(cannons), "welcome comes before cannons");
        helper.assertValueEqual(welcome.title(), AlmanacLang.title(AlmanacChapterProvider.WELCOME), "welcome title key");
        boolean showMe = cannons.pages().get(2).elements().stream().anyMatch(e -> e instanceof PageElement.ShowMe s
                && s.scene().equals(AlmanacChapterProvider.CANNON_LOADING_SCENE));
        helper.assertTrue(showMe, "the cannons chapter's third page has the cannon_loading Show me button");
        helper.succeed();
    }

    /** A login gets the whole book (surviving the wire codec) and the stored page; a reload sync gets only the book. */
    @ModGameTest
    public static void syncPayloadCarriesTheBook(GameTestHelper helper) {
        ServerPlayer player = serverPlayer(helper, UUID.randomUUID());
        AlmanacBook book = AlmanacBook.server();
        helper.assertFalse(book.isEmpty(), "server book is loaded");

        List<CustomPacketPayload> login = AlmanacSync.payloads(player, true, book);
        helper.assertValueEqual(login.size(), 2, "login payloads " + login);
        helper.assertTrue(login.get(0) instanceof AlmanacSyncPayload, "first login payload is the book: " + login);
        AlmanacSyncPayload sync = (AlmanacSyncPayload) login.get(0);
        helper.assertValueEqual(sync.book(), book, "synced book");
        helper.assertValueEqual(login.get(1), new AlmanacPagePayload.Restore(AlmanacProgress.NONE), "a new player has no page");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        AlmanacSyncPayload.CODEC.encode(buf, sync);
        AlmanacSyncPayload decoded = AlmanacSyncPayload.CODEC.decode(buf);
        helper.assertValueEqual(decoded.book(), book, "book after the wire codec");
        helper.assertValueEqual(decoded.book().chapter(AlmanacChapterProvider.CANNONS).map(Chapter::pageCount).orElse(-1), 3,
                "cannons pages on the client");

        List<CustomPacketPayload> reload = AlmanacSync.payloads(player, false, book);
        helper.assertValueEqual(reload, List.<CustomPacketPayload>of(new AlmanacSyncPayload(book)), "reload payloads");
        helper.succeed();
    }

    /** A reported page is stored, survives death and an NBT round trip (relog), and comes back on login. */
    @ModGameTest
    public static void reportedPageSurvivesDeathAndRelog(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer player = serverPlayer(helper, id);
        AlmanacProgress page = AlmanacProgress.at(AlmanacChapterProvider.CANNONS, 2);
        AlmanacSync.onReport(new AlmanacPagePayload.Report(page), player);
        helper.assertValueEqual(AlmanacSync.progress(player), page, "stored page");

        boolean stale = AlmanacSync.store(player, AlmanacProgress.at(ResourceLocation.fromNamespaceAndPath("test", "gone"), 1), AlmanacBook.server());
        helper.assertFalse(stale, "a chapter the server does not have is not stored");
        helper.assertValueEqual(AlmanacSync.progress(player), page, "page after the stale report");

        ServerPlayer respawned = serverPlayer(helper, id);
        respawned.restoreFrom(player, false);
        helper.assertValueEqual(AlmanacSync.progress(respawned), page, "page after death");

        CompoundTag tag = respawned.saveWithoutId(new CompoundTag());
        ServerPlayer loaded = serverPlayer(helper, id);
        loaded.load(tag);
        helper.assertValueEqual(AlmanacSync.progress(loaded), page, "page after relog");
        helper.assertTrue(AlmanacSync.payloads(loaded, true, AlmanacBook.server()).contains(new AlmanacPagePayload.Restore(page)),
                "the login sync sends the page back");
        helper.succeed();
    }

    /** With {@code almanac.remember_page} off nothing is stored and no page is sent on login. */
    @ModGameTest(batch = CONFIG_BATCH)
    public static void rememberPageOffStoresNothing(GameTestHelper helper) {
        ConfigOverrides.during(helper, AlmanacDataConfig.REMEMBER_PAGE, false);
        ServerPlayer player = serverPlayer(helper, UUID.randomUUID());
        AlmanacSync.onReport(new AlmanacPagePayload.Report(AlmanacProgress.at(AlmanacChapterProvider.WELCOME, 1)), player);
        helper.assertValueEqual(AlmanacSync.progress(player), AlmanacProgress.NONE, "nothing stored");
        List<CustomPacketPayload> login = AlmanacSync.payloads(player, true, AlmanacBook.server());
        helper.assertValueEqual(login.size(), 1, "only the book on login: " + login);
        helper.assertTrue(login.get(0) instanceof AlmanacSyncPayload, "the book");
        helper.succeed();
    }
}
