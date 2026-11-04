package local.generalsmc.client;

import local.generalsmc.*;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.Files;

/** Exercises the packaged riding mixins at each Minecraft API boundary. */
final class NativeVersionDrivingBench {
    static int stage,age,unit;static double before,error;static long shot;static CompletableFuture<?> work;
    static NativeActorEntity proxy(Minecraft mc){for(var e:mc.level.entitiesForRendering())if(e instanceof NativeActorEntity a&&a.nativeId==unit)return a;return null;}
    static void next(int value){stage=value;age=0;GeneralsMod.LOG.info("Version driving case {}",value);}
    static long shot(NativeWorldView view)throws Exception{var data=GeneralsMod.JSON.fromJson(Files.readString(view.session.directory.resolve("state.json")),com.google.gson.JsonObject.class);for(var a:data.getAsJsonArray("objects"))if(a.getAsJsonObject().get("id").getAsInt()==unit)return a.getAsJsonObject().get("weaponShot").getAsLong();throw new IllegalStateException("Missing driven unit");}
    static boolean tick(Minecraft mc,List<String> checks)throws Exception{
        if(++age>300)throw new IllegalStateException("Version driving timeout "+stage);var view=NativeWorldView.current;
        switch(stage){
            case 0->{var a=view.state.objects().stream().filter(o->o.owner()==view.state.localPlayerIndex()&&o.vehicle()).findFirst().orElseThrow();var base=view.state.objects().stream().filter(o->o.owner()==a.owner()&&o.structure()).findFirst().orElseThrow();double dx=a.x()-base.x(),dy=a.y()-base.y(),length=Math.max(1,Math.hypot(dx,dy));view.session.send("TEST_SPAWN ChinaTankBattleMaster "+(a.x()+dx/length*128)+" "+(a.y()+dy/length*128)+" "+a.z()+" "+a.owner()+" 1.5707963");next(1);}
            case 1->{var a=view.state.objects().stream().filter(o->o.template().equals("ChinaTankBattleMaster")).max(Comparator.comparingInt(NativeSession.Actor::id)).orElse(null);if(a!=null&&age>20){unit=a.id();var world=mc.getSingleplayerServer().getLevel(mc.level.dimension());work=CompletableFuture.runAsync(()->{var body=java.util.stream.StreamSupport.stream(world.getAllEntities().spliterator(),false).filter(e->e instanceof NativeActorEntity p&&p.nativeId==unit).map(e->(NativeActorEntity)e).findFirst().orElseThrow();var p=world.getServer().getPlayerList().getPlayer(mc.player.getUUID());var box=body.getBoundingBox();p.teleportTo(world,box.maxX+1,body.getY()+.05,body.getZ(),Set.of(),0,0,false);double old=p.getX();p.move(MoverType.SELF,new Vec3(-box.getXsize()-2,0,0));if(old-p.getX()>.8)throw new IllegalStateException("Player crosses the mobile unit body");p.teleportTo(world,box.maxX+.5,body.getY()+1,body.getZ(),Set.of(),0,0,false);},mc.getSingleplayerServer());next(2);}}
            case 2->{if(work.isDone()&&proxy(mc)!=null&&mc.player.distanceToSqr(proxy(mc))<50&&age>20){work.join();NativeVersionInteractionBench.board(mc,proxy(mc));next(3);}}
            case 3->{if(mc.player.getVehicle() instanceof NativeActorEntity vehicle&&vehicle.nativeId==unit){checks.add("Minecraft movement collides with a native mobile unit and actual interaction boards its owner");before=view.actor(unit).y();mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);mc.player.setYRot(0);mc.options.keyUp.setDown(true);next(4);}}
            case 4->{var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,.5f);var expected=view.riderPosition(mc.player);if(expected!=null)error=Math.max(error,new Vec3(state.x,state.y,state.z).distanceTo(expected));if(age>35){mc.options.keyUp.setDown(false);if(error>.001||view.actor(unit).y()<before+WorldScale.UNITS_PER_BLOCK)throw new IllegalStateException("Riding pose or native movement failed "+error+" position="+view.actor(unit)+" start="+before);checks.add("Third-person rider and vehicle share the rendered native pose while Minecraft movement drives the locomotor");mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setYRot(90);mc.player.setXRot(20);shot=shot(view);next(5);}}
            case 5->{if(age>20){mc.options.keyAttack.setDown(true);next(6);}}
            case 6->{if(shot(view)>shot){mc.options.keyAttack.setDown(false);next(7);}}
            case 7->{if(age==15)shot=shot(view);if(age>90){if(shot(view)!=shot)throw new IllegalStateException("Turret keeps firing after release");checks.add("Mounted attack fires the original turret and releasing attack stops subsequent shots");NativeRtsController.toggle();next(10);}}
            // Local resume opens its screen on the next client tick. A command issued
            // before that tick is intentionally still in Minecraft's disabled RTS context.
            case 10->{if(NativeWorldView.controlling()&&MenuScreens.active(NativeVersionScreenBench.current(mc)) instanceof NativeWorldScreen){view.session.select(unit);next(11);}}
            case 11->{if(view.state.selected().contains(unit)&&age>2){var a=view.actor(unit);before=a.y();view.session.send("MOVE "+a.x()+" "+(a.y()+48)+" "+a.z());next(9);}}
            case 9->{if(age>75){if(view.actor(unit).y()<before+WorldScale.UNITS_PER_BLOCK)throw new IllegalStateException("Mounted driving interrupts RTS orders after F8: "+view.actor(unit));checks.add("Entering RTS while mounted releases direct driving so commander movement orders still execute");NativeRtsController.toggle();mc.options.keyShift.setDown(true);next(8);}}
            case 8->{if(mc.player.getVehicle()==null&&age>10){mc.options.keyShift.setDown(false);checks.add("Actual sneak binding dismounts and releases native vehicle control");return true;}}
        }
        return false;
    }
}
