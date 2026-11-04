package local.generalsmc.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.MusicInfo;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundSource;
import java.util.List;
/** Real Minecraft MusicManager lifecycle, excluded from the player artifact. */
final class NativeMusicBench {
    static double master,music;
    static void unchanged(Minecraft mc){if(mc.options.getSoundSourceVolume(SoundSource.MASTER)!=master||mc.options.getSoundSourceVolume(SoundSource.MUSIC)!=music)throw new IllegalStateException("Soundtrack ownership changed saved volume options");}
    static void before(Minecraft mc,List<String> checks){master=mc.options.getSoundSourceVolume(SoundSource.MASTER);music=mc.options.getSoundSourceVolume(SoundSource.MUSIC);mc.getMusicManager().startPlaying(new MusicInfo(Musics.MENU,1));if(!mc.getMusicManager().isPlayingMusic(Musics.MENU))throw new IllegalStateException("Vanilla soundtrack did not start before RTS");checks.add("The real Minecraft MusicManager can start its normal soundtrack before the native match");}
    static void during(Minecraft mc,List<String> checks){
        if(!NativeAudio.claimsMusic()||mc.getMusicManager().isPlayingMusic(Musics.MENU))throw new IllegalStateException("Vanilla background music overlaps the active native soundtrack");
        mc.getMusicManager().startPlaying(new MusicInfo(Musics.MENU,1));mc.getMusicManager().tick();
        if(mc.getMusicManager().isPlayingMusic(Musics.MENU))throw new IllegalStateException("Vanilla soundtrack restarted during native music ownership");
        unchanged(mc);checks.add("An active audio-enabled native match stops an existing Minecraft background track and prevents another from starting without changing saved music/master volumes ("+(MenuScreens.active(mc.screen) instanceof NativeWorldScreen?"RTS screen":"Minecraft/menu mode")+")");
    }
    static void after(Minecraft mc,List<String> checks){
        if(NativeAudio.claimsMusic())throw new IllegalStateException("Stopped RTS retained music ownership");
        var background=mc.getSituationalMusic();for(int i=0;i<102;i++)mc.getMusicManager().tick();
        if(background.music()!=null&&!mc.getMusicManager().isPlayingMusic(background.music()))throw new IllegalStateException("Normal music scheduler did not resume within its restored delay");
        mc.getMusicManager().startPlaying(new MusicInfo(Musics.MENU,1));if(!mc.getMusicManager().isPlayingMusic(Musics.MENU))throw new IllegalStateException("Vanilla music stayed blocked after RTS");mc.getMusicManager().stopPlaying();
        unchanged(mc);checks.add("After the real native process exits, the real Minecraft MusicManager automatically resumes its normal scheduler and accepts music again");
    }
}
