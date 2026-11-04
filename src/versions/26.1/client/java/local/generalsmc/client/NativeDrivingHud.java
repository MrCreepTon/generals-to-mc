package local.generalsmc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import local.generalsmc.nativebridge.*;

public final class NativeDrivingHud {
    public static boolean mounted(){var mc=Minecraft.getInstance();return !NativeWorldView.controlling()&&mc.player!=null&&mc.player.getVehicle() instanceof NativeActorEntity;}
    public static boolean draw(GuiGraphicsExtractor g){
        if(!mounted())return false;var mc=Minecraft.getInstance();var view=NativeWorldView.current;if(view==null)return false;
        var vehicle=(NativeActorEntity)mc.player.getVehicle();var actor=view.actor(vehicle.nativeId);if(actor==null||actor.weaponRange()<=0)return false;
        var aim=NativeDrivingClient.aim(mc).point();var position=view.overheadAnchor(vehicle.nativeId,0,0,0,true);
        int color=Math.hypot(aim.x-position.x,aim.z-position.z)*WorldScale.UNITS_PER_BLOCK<=actor.weaponRange()?0xfff4e0a0:0xffff8f55;
        int x=mc.getWindow().getGuiScaledWidth()/2,y=mc.getWindow().getGuiScaledHeight()/2;
        g.fill(x-7,y-1,x-3,y+1,color);g.fill(x+3,y-1,x+7,y+1,color);g.fill(x-1,y-7,x+1,y-3,color);g.fill(x-1,y+3,x+1,y+7,color);
        if(actor.health()<actor.maxHealth()){g.fill(x-19,y+12,x+19,y+16,0xc0101822);g.fill(x-18,y+13,x-18+(int)Math.round(36*actor.health()/actor.maxHealth()),y+15,color);}
        return true;
    }
}
