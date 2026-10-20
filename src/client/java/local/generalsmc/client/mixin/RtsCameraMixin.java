package local.generalsmc.client.mixin;
import local.generalsmc.client.GeneralsClient;
import local.generalsmc.client.NativeWorldView;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class RtsCameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Inject(method = "setup", at = @At("TAIL"))
    private void rts(BlockGetter level, Entity entity, boolean thirdPerson, boolean inverse, float partialTick, CallbackInfo ci) {
        if(NativeWorldView.controlling()) {
            var view=NativeWorldView.current;view.advanceCamera();setRotation((float)view.renderYaw,60);
            double angle=Math.toRadians(view.renderYaw),back=view.renderZoom/Math.tan(Math.toRadians(60));
            double x=view.renderCameraX+Math.sin(angle)*back,z=view.renderCameraZ-Math.cos(angle)*back,y=view.renderCameraY+view.renderZoom;
            var mc=net.minecraft.client.Minecraft.getInstance();
            double atEye=local.generalsmc.nativebridge.WorldSurface.ground(mc.level,(int)Math.floor(x),(int)Math.floor(z));
            y=view.cameraEyeHeight(y,Double.isFinite(atEye)?atEye+2:y);
            setPosition(x,y,z);
            return;
        }
    }
}
