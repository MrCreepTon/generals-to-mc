package local.generalsmc.client;

import local.generalsmc.nativebridge.NativeSession;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import java.io.IOException;

/** Minecraft master/music/blocks/voice options control original Miles mixer categories. */
public final class NativeAudio {
    public static boolean claimsMusic(){var view=NativeWorldView.current;return NativeWorldView.active()&&view.session.alive()&&view.state!=null&&view.state.inGame()&&view.state.audioEnabled();}
    private String previous="";
    private long next;
    void tick(Minecraft mc,NativeSession session)throws IOException{
        if(System.currentTimeMillis()<next)return;next=System.currentTimeMillis()+150;
        double master=mc.options.getSoundSourceVolume(SoundSource.MASTER);
        String command="HOST_AUDIO "+master*mc.options.getSoundSourceVolume(SoundSource.MUSIC)+" "+master*mc.options.getSoundSourceVolume(SoundSource.BLOCKS)+" "+master*mc.options.getSoundSourceVolume(SoundSource.VOICE);
        if(!command.equals(previous)){session.send(command);previous=command;}
    }
}
