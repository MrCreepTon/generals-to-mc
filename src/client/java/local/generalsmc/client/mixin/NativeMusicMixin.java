package local.generalsmc.client.mixin;

import local.generalsmc.client.NativeAudio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicInfo;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One background soundtrack; effects, jukeboxes and saved volume options stay independent. */
@Mixin(MusicManager.class)
public abstract class NativeMusicMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private SoundInstance currentMusic;
    @Shadow private int nextSongDelay;
    @Unique private boolean generalsOwnsMusic;
    @Unique private int generalsSavedDelay;

    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void generalsMusicTick(CallbackInfo ci){
        if(NativeAudio.claimsMusic()){
            if(!generalsOwnsMusic){generalsSavedDelay=nextSongDelay;generalsOwnsMusic=true;}
            if(currentMusic!=null){minecraft.getSoundManager().stop(currentMusic);currentMusic=null;}
            ci.cancel();
        }else if(generalsOwnsMusic){
            generalsOwnsMusic=false;
            // A previous playing track uses MAX_VALUE as its delay. Restore the
            // normal scheduler promptly instead of leaving it silent indefinitely.
            nextSongDelay=Math.clamp(generalsSavedDelay,0,100);
        }
    }
    @Inject(method="startPlaying",at=@At("HEAD"),cancellable=true)
    private void generalsPreventSecondSoundtrack(MusicInfo info,CallbackInfo ci){if(NativeAudio.claimsMusic())ci.cancel();}
}
