package local.generalsmc.client;

import local.generalsmc.nativebridge.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/** Real roof placement, high impact, live fluid and Miles mixer regression fixture. */
final class NativeFieldBench {
 static int stage,age,dozer,plant,launcher,shot;static CompletableFuture<?> work;static double master,music;
 static final Vec3 BUILD=new Vec3(-96.5,84,.5),TARGET=new Vec3(145.5,104,4.5);
 static NativeWorldView v(){return NativeWorldView.current;}
 static void next(int s){stage=s;age=0;local.generalsmc.GeneralsMod.LOG.info("Field oracle case {}",s);}
 static void check(boolean b,String s){NativePolishBench.check(b,s);}
 static void spawn(String type,Vec3 p)throws Exception{var q=v().origin.nativePoint(p);v().session.send("TEST_SPAWN "+type+" "+q.x+" "+q.y+" "+q.z+" 2");}
 static void begin(Minecraft mc)throws Exception{
  check(v().state.localCash()==20000,"Host starting cash reaches the live human army before any construction");
  master=mc.options.getSoundSourceVolume(SoundSource.MASTER);music=mc.options.getSoundSourceVolume(SoundSource.MUSIC);
  mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(1.);mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(.15);
  v().fog.enabled(false);v().cameraX=-104;v().cameraZ=0;v().cameraY=84;v().zoom=30;mc.setScreen(new NativePolishBench.Hover(v().session));
  work=CompletableFuture.runAsync(()->{var w=NativePolishBench.level(mc);
   for(int x=-120;x<=-84;x++)for(int z=-18;z<=18;z++){w.setBlock(new BlockPos(x,83,z),Blocks.STONE_BRICKS.defaultBlockState(),3);for(int y=84;y<=92;y++)w.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
   for(int y=64;y<=103;y++)for(int x=144;x<=147;x++)for(int z=3;z<=6;z++)w.setBlock(new BlockPos(x,y,z),Blocks.BRICKS.defaultBlockState(),3);
   for(int x=39;x<=50;x++)for(int z=29;z<=31;z++){w.setBlock(new BlockPos(x,65,z),Blocks.STONE.defaultBlockState(),3);w.setBlock(new BlockPos(x,66,z),(z==30&&x>39&&x<50?Blocks.AIR:Blocks.STONE).defaultBlockState(),3);}
   w.setBlock(new BlockPos(40,66,30),Blocks.WATER.defaultBlockState(),3);
   check(WorldSurface.ground(w,-104,0)==84&&WorldSurface.ground(w,-119,0)==84,"Broad elevated roof and its edge remain usable terrain surfaces");
   check(SiteBlocks.START.defaultBlockState().getLightEmission()==0,"Start marker under the command center emits no artificial block light");
  },mc.getSingleplayerServer());next(0);
 }
 static void pointer(Vec3 p){var s=(NativePolishBench.Hover)Minecraft.getInstance().screen;var xy=s.project(p);s.px=(int)xy[0];s.py=(int)xy[1];s.scripted=true;s.mouseMoved(s.px,s.py);}
 static boolean tick(Minecraft mc)throws Exception{
  age++;if(age>1000)throw new IllegalStateException("Field timeout "+stage+" pending="+v().state.pendingCommand()+" error="+v().state.error());
  switch(stage){
   case 0->{if(work.isDone()&&age>80){work.join();spawn("AmericaVehicleDozer",new Vec3(-109.5,84,.5));next(1);}}
   case 1->{dozer=NativePolishBench.find("AmericaVehicleDozer");if(dozer>0&&age>30){var a=v().state.objects().stream().filter(o->o.id()==dozer).findFirst().orElseThrow();if(v().origin.world(a.x(),a.y(),a.z()).y<83)return false;check(v().origin.world(a.x(),a.y(),a.z()).y>83,"Original builder keeps its elevated roof position after terrain synchronization");v().session.select(dozer);next(2);}}
   case 2->{if(age>25){var screen=(NativeWorldScreen)MenuScreens.active(mc.screen);var command=v().state.controls().stream().filter(c->c.command().contains("PowerPlant")).findFirst().orElseThrow();screen.clickCommand(command.command(),true);screen.clickCommand(command.command(),false);next(3);}}
   case 3->{pointer(BUILD);if(v().state.pendingBuild()&&age>25){var s=(NativeWorldScreen)MenuScreens.active(mc.screen);s.mouseClicked(((NativePolishBench.Hover)s).px,((NativePolishBench.Hover)s).py,0);s.mouseReleased(((NativePolishBench.Hover)s).px,((NativePolishBench.Hover)s).py,0);next(4);}}
   case 4->{pointer(BUILD);plant=v().state.objects().stream().filter(a->a.template().equals("AmericaPowerPlant")&&v().origin.world(a.x(),a.y(),a.z()).distanceTo(BUILD)<8).mapToInt(NativeSession.Actor::id).findFirst().orElse(0);if(plant>0&&age>60){check(!v().state.pendingBuild(),"Normal original build controls accept placement on a Minecraft roof");var a=v().state.objects().stream().filter(o->o.id()==plant).findFirst().orElseThrow();check(v().origin.world(a.x(),a.y(),a.z()).y>83,"Constructed building stays on the roof rather than sinking to street level");Screenshot.grab(mc.gameDirectory,"beta19-roof.png",mc.getMainRenderTarget(),c->{});spawn("ChinaPowerPlant",new Vec3(22.5,64,-35.5));spawn("ChinaNuclearMissileLauncher",new Vec3(25.5,64,-25.5));v().cameraX=TARGET.x;v().cameraZ=TARGET.z;v().cameraY=104;v().zoom=28;next(5);}}
   case 5->{if((launcher=NativePolishBench.find("ChinaNuclearMissileLauncher"))>0&&age>60){v().session.select(launcher);v().session.send("DEBUG_COOLDOWN 0");next(6);}}
   case 6->{if(age>25){var command=v().state.controls().stream().filter(c->c.command().contains("NeutronMissile")).findFirst().orElseThrow();var s=(NativeWorldScreen)MenuScreens.active(mc.screen);s.clickCommand(command.command(),true);s.clickCommand(command.command(),false);next(7);}}
   case 7->{pointer(TARGET);if(age==1){var p=v().origin.nativePoint(TARGET);v().session.send("TEST_POWER_TARGET "+launcher+" "+p.x+" "+p.y+" "+p.z);}if(!v().state.pendingCommand().isBlank()&&age>40){var allowed=local.generalsmc.GeneralsMod.JSON.fromJson(Files.readString(v().session.directory.resolve("power-target-test.json")),com.google.gson.JsonObject.class);check(allowed.get("allowed").getAsBoolean(),"Native special-power eligibility accepts the actual high target");var s=(NativePolishBench.Hover)MenuScreens.active(mc.screen);var method=NativeWorldScreen.class.getDeclaredMethod("hit",double.class,double.class);method.setAccessible(true);var hit=(net.minecraft.world.phys.BlockHitResult)method.invoke(s,(double)s.px,(double)s.py);NativePolishBench.values.put("roofTargetHit",hit.getLocation().toString());s.mouseClicked(s.px,s.py,0);s.mouseReleased(s.px,s.py,0);shot=v().state.frame();next(8);}}
   case 8->{pointer(TARGET);if(age==5){var f=NativeWorldScreen.class.getDeclaredField("lastWorldCommand");f.setAccessible(true);long c=(long)f.get(MenuScreens.active(mc.screen));NativePolishBench.values.put("targetMouseCommand",Files.readString(v().session.directory.resolve("commands/"+c+".cmd")));}var blast=v().session.recentCombatEvents().stream().filter(e->e.damageType()==0&&e.amount()>=3500&&e.radius()>0).findFirst().orElse(null);if(blast!=null){var impact=v().origin.world(blast.x(),blast.y(),blast.z());NativePolishBench.values.put("highNukeImpact",impact.toString());check(impact.y>95,"Original nuclear missile detonates at the targeted rooftop height");work=CompletableFuture.runAsync(()->{var w=NativePolishBench.level(mc);var columns=TerrainExporter.sample(w,new TerrainExporter.Region(39,29,12,3,0,64,0));check(columns.stream().anyMatch(c->c.type()==1),"Water poured during the match reaches exported water terrain cells");check(!w.getFluidState(new BlockPos(44,66,30)).isEmpty(),"Actual vanilla water flows through the private test channel");},mc.getSingleplayerServer());next(90);}}
   case 90->{if(age>60&&work.isDone()){work.join();work=CompletableFuture.runAsync(()->{var w=NativePolishBench.level(mc);check(w.getBlockState(new BlockPos(145,103,4)).isAir(),"Nuclear blast removes real upper-story Minecraft blocks");},mc.getSingleplayerServer());next(9);}}
   case 9->{if(work.isDone()&&age>20){work.join();var state=local.generalsmc.GeneralsMod.JSON.fromJson(Files.readString(v().session.directory.resolve("state.json")),com.google.gson.JsonObject.class);check(state.get("musicPlaying").getAsBoolean(),"A real original soundtrack stream is playing");check(Math.abs(state.get("actualMusicVolume").getAsDouble()-.15)<.015,"Minecraft music slider changes actual Miles stream gain to 0.15");spawn("AmericaInfantryRanger",new Vec3(44.5,67,30.5));mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.);next(10);}}
   case 10->{if(age>30){var state=local.generalsmc.GeneralsMod.JSON.fromJson(Files.readString(v().session.directory.resolve("state.json")),com.google.gson.JsonObject.class);check(state.get("actualMusicVolume").getAsDouble()<.001,"Minecraft zero music volume actually mutes the original stream");var ranger=v().state.objects().stream().filter(a->a.template().equals("AmericaInfantryRanger")).max(java.util.Comparator.comparingInt(NativeSession.Actor::id)).orElseThrow();NativePolishBench.values.put("waterUnitStart",v().origin.world(ranger.x(),ranger.y(),ranger.z()).toString());dozer=ranger.id();next(11);}}
   case 11->{if(age>100){var ranger=v().state.objects().stream().filter(a->a.id()==dozer).findFirst().orElseThrow();var p=v().origin.world(ranger.x(),ranger.y(),ranger.z());NativePolishBench.values.put("waterUnitEnd",p.toString());check(p.x>44.52,"Actual poured-water current pushes original infantry instead of being erased by source pose synchronization");mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(master);mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(music);return true;}}
  }return false;
 }
}
