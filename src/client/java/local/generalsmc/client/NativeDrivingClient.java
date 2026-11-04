package local.generalsmc.client;

import local.generalsmc.nativebridge.NativeActorEntity;
import net.minecraft.client.Minecraft;

final class NativeDrivingClient {
    private static int lastVehicle;private static boolean lastEnabled;
    static void tick(Minecraft mc){
        if(mc.player==null||!(mc.player.getVehicle() instanceof NativeActorEntity vehicle)){lastVehicle=0;lastEnabled=false;return;}
        if(lastVehicle!=vehicle.getId()){lastVehicle=vehicle.getId();lastEnabled=true;mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("generalsmc.vehicle.controls",mc.options.keyUp.getTranslatedKeyMessage(),mc.options.keyLeft.getTranslatedKeyMessage(),mc.options.keyDown.getTranslatedKeyMessage(),mc.options.keyRight.getTranslatedKeyMessage(),mc.options.keyAttack.getTranslatedKeyMessage(),mc.options.keyShift.getTranslatedKeyMessage()),true);}
        boolean enabled=!NativeWorldView.controlling();if(!enabled&&!lastEnabled)return;lastEnabled=enabled;
        boolean focused=mc.screen==null&&mc.isWindowActive()&&!NativeWorldView.controlling();
        int forward=focused?(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0):0,side=focused?(mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0):0;
        boolean fire=focused&&mc.options.keyAttack.isDown();
        var aim=enabled?raycast(mc):new Aim(net.minecraft.world.phys.Vec3.ZERO,"");var target=aim.point();String targetEntity=aim.target();
        NativeNetworkClient.request("drive",j->{j.addProperty("enabled",enabled);j.addProperty("target",targetEntity);j.addProperty("forward",forward);j.addProperty("side",side);j.addProperty("yaw",mc.player.getYRot());j.addProperty("fire",fire);j.addProperty("x",target.x);j.addProperty("y",target.y);j.addProperty("z",target.z);});
    }
    static net.minecraft.world.phys.Vec3 direction(Minecraft mc){return new net.minecraft.world.phys.Vec3(mc.gameRenderer.getMainCamera().getLookVector());}
    record Aim(net.minecraft.world.phys.Vec3 point,String target){}
    private static Aim frameAim;
    static void beginFrame(){frameAim=null;}
    static Aim aim(Minecraft mc){if(frameAim==null)frameAim=raycast(mc);return frameAim;}
    private static Aim raycast(Minecraft mc){
        var camera=mc.gameRenderer.getMainCamera();
        var eye=camera.getPosition();
        var ray=direction(mc);var end=eye.add(ray.scale(128));
        var block=mc.level.clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.ANY,mc.player));
        var point=block.getLocation();
        var entity=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(mc.player,eye,end,new net.minecraft.world.phys.AABB(eye,end).inflate(1),e->e.isAlive()&&e.isPickable()&&!e.isPassengerOfSameVehicle(mc.player),eye.distanceToSqr(point));
        return entity==null?new Aim(point,""):new Aim(entity.getLocation(),entity.getEntity().getUUID().toString());
    }

}
