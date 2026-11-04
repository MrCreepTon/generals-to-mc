package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.concurrent.*;

/** Marker -> actual original capture power -> original capture bonus and periodic income. */
final class NativeOilBench {
    static int stage,age,oil,lotus,cash,capturedCash;static CompletableFuture<?> work;
    static final BlockPos POS=new BlockPos(24,64,24),START=new BlockPos(36,64,24);
    static void begin(Minecraft mc,NativeWorldView v,List<String> checks)throws Exception{
        v.cameraX=24;v.cameraZ=24;v.zoom=22;work=CompletableFuture.runAsync(()->mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA).setBlock(POS,SiteBlocks.OIL.defaultBlockState(),3),mc.getSingleplayerServer());NativeWorldPolishBench.spawn(v,"ChinaInfantryBlackLotus",24.5,17.5);next(0);
    }
    static boolean tick(Minecraft mc,NativeWorldView v,List<String> checks)throws Exception{
        age++;if(age>1200)throw new IllegalStateException("Oil oracle timeout case "+stage+" command="+v.state.pendingCommand());
        if(stage==0&&age>100&&work.isDone()&&v.state.sites()!=null&&!v.state.sites().isEmpty()){work.join();oil=v.state.sites().getFirst().id();lotus=v.state.objects().stream().filter(a->a.template().equals("ChinaInfantryBlackLotus")).findFirst().orElseThrow().id();v.session.select(lotus);cash=v.state.localCash();next(1);}
        else if(stage==1&&age>25){((NativeWorldScreen)MenuScreens.active(mc.screen)).clickCommand("Command_ChinaInfantryBlackLotusCaptureHack",true);next(2);}
        else if(stage==2&&age>6){((NativeWorldScreen)MenuScreens.active(mc.screen)).clickCommand("Command_ChinaInfantryBlackLotusCaptureHack",false);next(3);}
        else if(stage==3&&age>25){if(!v.state.pendingCommand().contains("Capture"))throw new IllegalStateException("Original Black Lotus capture command not armed");var screen=(NativeWorldScreen)MenuScreens.active(mc.screen);var p=meshPixel(v,screen,oil);screen.mouseMoved(p[0],p[1]);screen.mouseClicked(p[0],p[1],0);screen.mouseReleased(p[0],p[1],0);next(4);}
        else if(stage==4&&age>30&&actor(v,oil).owner()==v.state.localPlayerIndex()){if(v.state.localCash()<cash+1000)throw new IllegalStateException("Original oil capture bonus missing");capturedCash=v.state.localCash();checks.add("An actual Minecraft oil-marker creates capturable TechOilDerrick; original Black Lotus GUI power transfers it to the local army and pays original1000 capture bonus");Screenshot.grab(mc.gameDirectory,"75-original-oil-captured.png",mc.getMainRenderTarget(),c->{});next(5);}
        else if(stage==5&&age>300&&v.state.localCash()>=capturedCash+200){checks.add("Original TechOilDerrick AutoDepositUpdate pays periodic200 income through its original12-second timer");NativeRtsController.toggle();work=CompletableFuture.runAsync(()->{var l=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());player.teleportTo(l,36.5,64,24.5,Set.of(),0,60,false);l.setBlock(START,SiteBlocks.START.defaultBlockState(),3);l.getBlockState(START).useWithoutItem(l,player,new BlockHitResult(START.getCenter(),Direction.UP,START,false));},mc.getSingleplayerServer());next(6);}
        else if(stage==6&&work.isDone()&&MenuScreens.active(mc.screen) instanceof NativeStartScreen&&age>20){work.join();NativeSetupBench.click(MenuScreens.active(mc.screen),"Участник: игрок");Screenshot.grab(mc.gameDirectory,"76-army-start-editor.png",mc.getMainRenderTarget(),c->{});NativeSetupBench.click(MenuScreens.active(mc.screen),"Сохранить");next(7);}
        else if(stage==7&&age>35&&!(MenuScreens.active(mc.screen) instanceof NativeStartScreen)){work=CompletableFuture.runAsync(()->{var l=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var s=WorldSiteStore.get(l).starts().stream().filter(a->a.pos().equals(START)).findFirst().orElseThrow();if(s.difficulty()!=0)throw new IllegalStateException("Start editor did not persist easy NPC");var tag=WorldSiteStore.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());if(tag.getList("starts",10).size()!=1)throw new IllegalStateException("Army marker absent from SavedData");l.setBlock(START,Blocks.AIR.defaultBlockState(),3);},mc.getSingleplayerServer());next(8);}
        else if(stage==8&&work.isDone()){work.join();checks.add("Ordinary Minecraft start-marker block interaction opens its real editor; Save travels through Fabric packets and persists chosen NPC difficulty in dimension SavedData");return true;}
        return false;
    }
    static double[] meshPixel(NativeWorldView v,NativeWorldScreen screen,int id){for(var b:v.scene.batches())if(b.actor()==id)for(int i=0;i<b.count();i+=3){double x=0,y=0,z=0;for(int k=0;k<3;k++){x+=b.vertices()[(i+k)*8];y+=b.vertices()[(i+k)*8+1];z+=b.vertices()[(i+k)*8+2];}var p=screen.project(v.origin.world(x/3,y/3,z/3));if(p[0]>0&&p[0]<screen.width&&p[1]>0&&p[1]<screen.height*.65&&screen.actorAt(p[0],p[1])==id)return p;}throw new IllegalStateException("No visible original oil triangle");}
    static NativeSession.Actor actor(NativeWorldView v,int id){return v.state.objects().stream().filter(a->a.id()==id).findFirst().orElseThrow();}
    static void next(int n){stage=n;age=0;GeneralsMod.LOG.info("Oil capture case {}",n);}
}
