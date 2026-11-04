package local.generalsmc.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import local.generalsmc.nativebridge.*;

/** A camera-centred impact marker; orange means outside the current weapon range. */
final class NativeDrivingVisual {
    static List<NativeWorldView.Batch> batches(NativeWorldView view,Minecraft mc){
        if(NativeWorldView.controlling()||mc.player==null||!(mc.player.getVehicle() instanceof NativeActorEntity vehicle))return List.of();
        var actor=view.actor(vehicle.nativeId);if(actor==null||actor.weaponRange()<=0)return List.of();
        var aim=NativeDrivingClient.aim(mc).point();var ray=NativeDrivingClient.direction(mc);
        var right=ray.cross(new Vec3(0,1,0)).normalize();if(right.lengthSqr()<.01)right=new Vec3(1,0,0);
        var up=right.cross(ray).normalize();var center=aim.subtract(ray.scale(.08));
        double size=Math.clamp(aim.distanceTo(mc.player.position())*.003,.08,.5),thickness=size*.12;
        var position=view.overheadAnchor(vehicle.nativeId,0,0,0,true);
        boolean inRange=Math.hypot(aim.x-position.x,aim.z-position.z)*WorldScale.UNITS_PER_BLOCK<=actor.weaponRange();
        int color=inRange?0xfff4e0a0:0xffff8f55;
        float[] vertices=new float[4*6*8];int[] colors=new int[24];Arrays.fill(colors,color);int at=0;
        for(int segment=0;segment<4;segment++){
            var axis=segment<2?right:up;var side=(segment<2?up:right).scale(thickness);double sign=segment%2==0?1:-1;
            var a=center.add(axis.scale(sign*size*.35));var b=center.add(axis.scale(sign*size));
            var corners=new Vec3[]{a.subtract(side),b.subtract(side),b.add(side),a.add(side)};
            for(int i:new int[]{0,1,2,0,2,3}){var point=view.origin.nativePoint(corners[i]);int k=at++*8;vertices[k]=(float)point.x;vertices[k+1]=(float)point.y;vertices[k+2]=(float)point.z;vertices[k+5]=1;}
        }
        return List.of(new NativeWorldView.Batch(0,0,0,0x33f52,vertices,colors,true));
    }
}
