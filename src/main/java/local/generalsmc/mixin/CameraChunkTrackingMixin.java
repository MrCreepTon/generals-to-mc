package local.generalsmc.mixin;

import local.generalsmc.nativebridge.WorldViewSubscription;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class CameraChunkTrackingMixin {
    @Shadow abstract int getPlayerViewDistance(ServerPlayer player);
    @Shadow private void applyChunkTrackingView(ServerPlayer player,ChunkTrackingView view){throw new AssertionError();}
    @Inject(method="updateChunkTracking",at=@At("HEAD"),cancellable=true)
    private void generalsCameraChunks(ServerPlayer player,CallbackInfo ci){
        var center=WorldViewSubscription.center(player);if(center==null)return;int distance=getPlayerViewDistance(player);
        if(!(player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned positioned&&positioned.center().equals(center)&&positioned.viewDistance()==distance))applyChunkTrackingView(player,ChunkTrackingView.of(center,distance));ci.cancel();
    }
}
