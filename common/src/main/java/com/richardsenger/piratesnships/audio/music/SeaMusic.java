package com.richardsenger.piratesnships.audio.music;

import com.mojang.blaze3d.audio.Channel;
import com.richardsenger.piratesnships.audio.AudioConfig;
import com.richardsenger.piratesnships.audio.AudioSounds;
import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.sable.ClientShipPoses;
import com.richardsenger.piratesnships.station.seat.StationSeat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The sea music manager (docs/design.md §16, "Pools"; physical client only, set up from {@code AudioModule.initClient}).
 * Every client tick it works out the player's {@link MusicSituation}: aboard when the player stands on or rides a Sable
 * ship ({@link ClientShipPoses#onShip}) or sits in a station seat, at sea in an ocean, deep ocean or beach biome. When
 * vanilla's music manager asks for music, {@link MusicSelector} turns that into our pool or leaves vanilla alone.
 * A pool change waits for the playing track to end (our {@link Music} doesn't replace the current music).
 *
 * <p>{@code music_volume} is applied when one of our tracks starts streaming, by scaling its audio channel's volume
 * ({@link ClientEvents#SOUND_STREAM_STARTED}, sound thread). Moving the game's music slider during a track resets that
 * track to the plain slider volume until the next one starts.
 */
public final class SeaMusic {

    private static final AboardMemory ABOARD = new AboardMemory();
    private static final Map<MusicSelector.Choice, Music> MUSIC = new HashMap<>();
    private static long ticks;
    private static MusicSituation situation = MusicSituation.NONE;
    /** {@code music_volume}, copied on the client thread for the sound thread. */
    private static volatile double musicVolume = 1.0;

    private SeaMusic() {
    }

    public static void init() {
        ClientEvents.CLIENT_TICK_END.register(SeaMusic::tick);
        ClientEvents.SELECT_MUSIC.register(SeaMusic::select);
        ClientEvents.SOUND_STREAM_STARTED.register(SeaMusic::onStreamStarted);
        ClientEvents.CLIENT_DISCONNECT.register(mc -> reset());
    }

    /** The situation of the last client tick (for debugging and tests). */
    public static MusicSituation situation() {
        return situation;
    }

    private static void reset() {
        ABOARD.reset();
        situation = MusicSituation.NONE;
    }

    private static void tick(Minecraft mc) {
        ticks++;
        musicVolume = AudioConfig.MUSIC_VOLUME.get();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            reset();
            return;
        }
        boolean aboard = ABOARD.update(ClientShipPoses.onShip(player) || player.getVehicle() instanceof StationSeat, ticks);
        Holder<Biome> biome = mc.level.getBiome(player.blockPosition());
        boolean sea = biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_BEACH);
        situation = MusicSituation.of(aboard, sea);
    }

    private static @Nullable Music select(@Nullable Music vanillaChoice) {
        if (vanillaChoice == null) return null; // someone silenced the music on purpose
        if (Minecraft.getInstance().player == null) return null; // menus
        MusicSelector.Choice choice = MusicSelector.select(situation, AudioConfig.musicSettings());
        if (choice == null) return null;
        return MUSIC.computeIfAbsent(choice, c -> new Music(
                c.pool() == MusicSelector.Pool.SHANTY ? AudioSounds.MUSIC_SHANTY.holder() : AudioSounds.MUSIC_SEA.holder(),
                c.minGapTicks(), c.maxGapTicks(), false));
    }

    private static void onStreamStarted(SoundInstance sound, Channel channel) {
        double factor = musicVolume;
        if (factor >= 1.0 || sound.getSource() != SoundSource.MUSIC || !isOurMusic(sound.getLocation())) return;
        float slider = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC);
        channel.setVolume(MusicSelector.channelVolume(sound.getVolume(), slider, factor));
    }

    private static boolean isOurMusic(ResourceLocation event) {
        return event.equals(AudioSounds.MUSIC_SEA.id()) || event.equals(AudioSounds.MUSIC_SHANTY.id());
    }
}
