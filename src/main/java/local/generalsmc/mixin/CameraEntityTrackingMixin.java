package local.generalsmc.mixin;

import local.generalsmc.nativebridge.WorldViewSubscription;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Vanilla mob packets follow the camera subscription, with the player body untouched. */
@Mixin(targets="net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class CameraEntityTrackingMixin {
    @Redirect(method="updatePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 generalsCameraEntityDistance(ServerPlayer player){return WorldViewSubscription.trackingPosition(player);}
}
