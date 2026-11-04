package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real server blocks, original effect modules and native movement through newly exported terrain. */
final class NativeEffectsBench {
    private static int stage,age,airfield,ranger,launcher,cashBefore;
    private static long uploads,started,ignitions,explosions,cpuStarted;
    private static int measuredFrame;
    private static UUID remoteZombie;
    private static CompletableFuture<Void> work;
    private static boolean sawFlame,sawNuke;
    private static Vec3 goal,plantPosition;
    private static Vec3 stalledPosition;
    private static long stalledRevision;
    private static int stalledFrame;
    private static WorldResourceStore.Node supply;
    static void begin(Minecraft mc,NativeWorldView view,List<String> checks){
        stage=0;age=0;view.cameraX=-16;view.cameraZ=-15;view.zoom=30;
        work=CompletableFuture.runAsync(()->{
            var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);
            mc.getSingleplayerServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
            level.setDayTime(18000);
            for(int x=-32;x<=32;x++)for(int z=-32;z<=32;z++){
                level.setBlock(new BlockPos(x,63,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
                if(x<0&&z<0)level.setBlock(new BlockPos(x,64,z),Blocks.SHORT_GRASS.defaultBlockState(),3);
            }
        },mc.getSingleplayerServer());
    }
    static boolean tick(Minecraft mc,NativeWorldView view,List<String> checks)throws Exception{
        age++;var screen=(NativeWorldScreen)MenuScreens.active(mc.screen);
        if(age>1200)throw new IllegalStateException("Effects oracle timeout at case "+stage+"; goal="+goal);
        if(stage==0&&work.isDone()&&age>80){work.join();spawn(view,"ChinaAirfield",new Vec3(-18.5,64,-18.5));next(1);}
        else if(stage==1&&age>50){
            var building=view.state.objects().stream().filter(o->o.template().equals("ChinaAirfield")).findFirst().orElseThrow();airfield=building.id();
            if(!building.structure())throw new IllegalStateException("Original structure flag was not exported");
            work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var center=view.origin.world(building.x(),building.y(),building.z());double a=building.geometryMajor()/8,b=building.geometryMinor()/8;
                for(int x=(int)Math.ceil(center.x-a);x<center.x+a;x++)for(int z=(int)Math.ceil(center.z-b);z<center.z+b;z++)if(level.getBlockState(new BlockPos(x,64,z)).is(Blocks.SHORT_GRASS))throw new IllegalStateException("Airfield runway still contains Minecraft grass at "+x+","+z);
                if(!level.getBlockState(new BlockPos(-32,64,-32)).is(Blocks.SHORT_GRASS))throw new IllegalStateException("Construction cleared plants outside its footprint");
            },mc.getSingleplayerServer());next(2);
        }
        else if(stage==2&&work.isDone()){work.join();checks.add("Built original China Airfield clears Minecraft grass within its actual foundation while retaining vegetation outside it");Screenshot.grab(mc.gameDirectory,"40-airfield-cleared.png",mc.getMainRenderTarget(),c->{});view.session.select(airfield);uploads=screen.guiUploads();started=System.currentTimeMillis();measuredFrame=view.state.frame();cpuStarted=((com.sun.management.OperatingSystemMXBean)java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime();next(3);}
        else if(stage==3&&age>80){double rate=(screen.guiUploads()-uploads)*1000.0/(System.currentTimeMillis()-started);if(rate<Math.min(15,Math.max(2,mc.getFps()*.4))||screen.guiAgeMillis()>180)throw new IllegalStateException("Original control-bar transfer too slow: "+rate+" uploads/s, age="+screen.guiAgeMillis()+", native frame="+view.state.frame()+", elapsedFrames="+(view.state.frame()-measuredFrame));checks.add("Original selected-unit GUI refresh measured "+String.format(Locale.ROOT,"%.1f",rate)+" uploads/s with current frame age "+screen.guiAgeMillis()+" ms");double cpu=(((com.sun.management.OperatingSystemMXBean)java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime()-cpuStarted)/1e6/(System.currentTimeMillis()-started)/Runtime.getRuntime().availableProcessors()*100;checks.add("Loaded RTS scene Java process CPU "+String.format(Locale.ROOT,"%.1f%%",cpu)+" of total CPU, native logic "+String.format(Locale.ROOT,"%.1f Hz",(view.state.frame()-measuredFrame)*1000.0/(System.currentTimeMillis()-started))+", "+Runtime.getRuntime().availableProcessors()+" logical CPUs");
            work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=18;x<=30;x++)for(int z=18;z<=30;z++)level.setBlock(new BlockPos(x,63,z),Blocks.OAK_PLANKS.defaultBlockState(),3);},mc.getSingleplayerServer());view.cameraX=24;view.cameraZ=24;next(4);
        }
        else if(stage==4&&work.isDone()&&age>80){work.join();ignitions=NativeRtsController.combat.worldIgnitions;spawn(view,"FirestormSmall",new Vec3(24.5,64,24.5));next(5);}
        else if(stage==5){sawFlame|=view.state.combatEvents().stream().anyMatch(e->e.target().isEmpty()&&e.damageType()==6&&e.radius()>0);if(sawFlame&&NativeRtsController.combat.worldIgnitions>ignitions&&age>45){work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);int count=0;for(int x=18;x<=30;x++)for(int z=18;z<=30;z++)if(level.getBlockState(new BlockPos(x,64,z)).is(Blocks.FIRE))count++;if(count==0)throw new IllegalStateException("Native firestorm did not create any actual Minecraft fire");},mc.getSingleplayerServer());next(6);}}
        else if(stage==6&&work.isDone()){work.join();checks.add("Original FirestormDynamicGeometryInfoUpdate emits its real FLAME area and ignites actual Minecraft fire on wooden terrain");Screenshot.grab(mc.gameDirectory,"41-original-fire-ignition.png",mc.getMainRenderTarget(),c->{});view.cameraX=70;view.cameraZ=0;
            work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=57;x<=83;x++)for(int z=-13;z<=13;z++)for(int y=59;y<=63;y++)level.setBlock(new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState(),3);for(int x=40;x<=56;x++)for(int z=24;z<=40;z++){level.setBlock(new BlockPos(x,63,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int y=64;y<=67;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}},mc.getSingleplayerServer());view.cameraX=48;view.cameraZ=32;next(7);
        }
        else if(stage==7&&work.isDone()&&age>140){work.join();spawn(view,"ChinaNuclearMissileLauncher",new Vec3(-22.5,64,20.5));plantPosition=freePlant(view);spawn(view,"ChinaPowerPlant",plantPosition);cashBefore=view.state.localCash();chat(mc,"/zh-debug money 25000");next(101);}
        else if(stage==101&&age>40){if(view.state.localCash()<cashBefore+25000)throw new IllegalStateException("Chat debug money did not reach original Money");checks.add("Real F8 / ChatScreen / Fabric command gives original local player money while simulation continues");view.session.select(view.state.objects().stream().filter(o->o.template().equals("ChinaPowerPlant")).findFirst().orElseThrow().id());view.cameraX=plantPosition.x;view.cameraZ=plantPosition.z;next(121);}
        else if(stage==121&&age>30){screen.clickCommand("Command_Overcharge",true);next(122);}
        else if(stage==122&&age>5){screen.clickCommand("Command_Overcharge",false);next(123);}
        else if(stage==123&&age>50){var reactor=view.state.objects().stream().filter(o->o.template().equals("ChinaNuclearMissileLauncher")).findFirst().orElseThrow();checks.add("Original Power Plant Overcharge activated through original GUI before superweapon timer test");Screenshot.grab(mc.gameDirectory,"45-powerplant-overcharge-symbol.png",mc.getMainRenderTarget(),c->{});view.cameraX=70;view.cameraZ=0;chat(mc,"/zh-debug cooldown 30");next(102);}
        else if(stage==102&&age>35){var power=power(view);if(power.readyFrame()-view.state.frame()<700)throw new IllegalStateException("Debug cooldown did not update original power timer: "+power);chat(mc,"/zh-debug ready");next(103);}
        else if(stage==103&&age>35){if(!power(view).ready())throw new IllegalStateException("Debug ready did not complete original timer: "+power(view));checks.add("Debug cooldown and ready manipulate the original SpecialPowerModule ready frame");if(!view.state.audioEnabled()||view.state.audio2DSamples()+view.state.audio3DSamples()==0)throw new IllegalStateException("Original Miles audio device is not open");chat(mc,"/zh-debug sound DozerUSAVoiceSelect");next(104);}
        else if(stage==104){if(view.state.audio2DUsed()+view.state.audio3DUsed()>0){checks.add("Original Miles device allocates samples and plays an original BIG audio event (active native sample)");launcher=view.state.objects().stream().filter(o->o.template().equals("ChinaNuclearMissileLauncher")).findFirst().orElseThrow().id();view.session.select(launcher);next(105);}}
        else if(stage==105&&age>30){screen.clickCommand("Command_NeutronMissile",true);next(106);}
        else if(stage==106&&age>5){screen.clickCommand("Command_NeutronMissile",false);next(107);}
        else if(stage==107&&age>30){if(view.state.pendingCommand()==null||!view.state.pendingCommand().contains("Neutron"))throw new IllegalStateException("Original nuclear command did not arm: "+view.state.pendingCommand());var xy=screen.project(new Vec3(70.5,64,.5));screen.mouseMoved(xy[0],xy[1]);next(108);}
        else if(stage==108&&age>35){if(view.state.objects().stream().anyMatch(o->o.template().equals("NeutronMissile")))throw new IllegalStateException("Missile launched before target click");if(view.state.exportedDecals()==0)throw new IllegalStateException("Original nuclear preview decal is absent before activation");boolean radiusMatches=false;for(var batch:view.scene.batches())if(batch.actor()==0xd0000000){double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minY=minX,maxY=maxX;for(int i=0;i<batch.count();i++){minX=Math.min(minX,batch.vertices()[i*8]);maxX=Math.max(maxX,batch.vertices()[i*8]);minY=Math.min(minY,batch.vertices()[i*8+1]);maxY=Math.max(maxY,batch.vertices()[i*8+1]);}if(Math.abs(Math.max(maxX-minX,maxY-minY)-420)<5)radiusMatches=true;}if(!radiusMatches)throw new IllegalStateException("Pre-click preview does not match BIG radius 210");checks.add("Original nuclear radius decal visible before target activation, over Minecraft terrain outside base vision");Screenshot.grab(mc.gameDirectory,"44-nuclear-target-preview.png",mc.getMainRenderTarget(),c->{});explosions=NativeRtsController.combat.worldExplosions;click(screen,new Vec3(70.5,64,.5));next(8);}
        else if(stage==8){
            sawNuke|=view.state.combatEvents().stream().anyMatch(e->e.target().isEmpty()&&e.radius()>=200&&e.amount()>=3000);
            if(age>100&&sawNuke&&NativeRtsController.combat.worldExplosions>explosions){work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);int removed=0;for(int x=65;x<=75;x++)for(int z=-5;z<=5;z++)if(level.isEmptyBlock(new BlockPos(x,63,z)))removed++;if(removed<30)throw new IllegalStateException("Original nuclear missile shockwave did not destroy host terrain: "+removed);},mc.getSingleplayerServer());next(9);}
        }
        else if(stage==9&&work.isDone()){work.join();checks.add("Original GUI nuclear launch at a distant Minecraft target produces its 3500-damage / 210-unit shockwave and destroys real host soil");Screenshot.grab(mc.gameDirectory,"42-nuclear-shockwave-crater.png",mc.getMainRenderTarget(),c->{});view.cameraX=210;view.cameraZ=0;
            work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);level.getChunkAt(new BlockPos(210,64,0));var mob=new Zombie(EntityType.ZOMBIE,level);mob.setNoAi(true);mob.setPos(210.5,64,.5);remoteZombie=mob.getUUID();level.addFreshEntity(mob);},mc.getSingleplayerServer());next(10);
        }
        else if(stage==10&&work.isDone()&&age>100){work.join();work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var entity=level.getEntity(remoteZombie);if(!(entity instanceof Zombie z)||!z.isAlive())throw new IllegalStateException("RTS-visible mob despawned because the commander remained 210 blocks away");z.checkDespawn();if(z.isRemoved())throw new IllegalStateException("Explicit vanilla despawn check discarded RTS-visible mob");},mc.getSingleplayerServer());next(11);}
        else if(stage==11&&work.isDone()){work.join();checks.add("Nonpersistent ordinary Zombie survives 100 real ticks and vanilla checkDespawn at the RTS camera, 210 blocks from the unmoved commander");
            view.cameraX=-24;view.cameraZ=15;
            work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=-32;x<=-12;x++)for(int z=8;z<=18;z++)level.setBlock(new BlockPos(x,63,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int x=-30;x<=-26;x++)for(int z=10;z<=15;z++)if(x==-30||x==-26||z==10||z==15)for(int y=64;y<=66;y++)level.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),3);},mc.getSingleplayerServer());spawn(view,"AmericaInfantryRanger",new Vec3(-28.5,64,12.5));next(12);
        }
        else if(stage==12&&work.isDone()&&age>80){work.join();ranger=view.state.objects().stream().filter(o->o.template().equals("AmericaInfantryRanger")).findFirst().orElseThrow().id();view.session.select(ranger);next(13);}
        else if(stage==13&&age>20){goal=view.origin.nativePoint(new Vec3(-15.5,64,12.5));click(screen,new Vec3(-15.5,64,12.5));next(14);}
        else if(stage==14&&age>20){var actor=actor(view,ranger);if(!actor.groundMovement()||Math.abs(actor.goalX()-goal.x)>8)throw new IllegalStateException("Player move goal missing from streamed state");
            next(15);
        }
        else if(stage==15&&age>100){var trapped=actor(view,ranger);var world=view.origin.world(trapped.x(),trapped.y(),trapped.z());if(world.x< -29.8||world.x> -26.2||world.z<10.2||world.z>14.8||world.y>64.1)throw new IllegalStateException("Infantry escaped a three-block cage before removal: "+world);work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=-30;x<=-26;x++)for(int z=10;z<=15;z++)if(x==-30||x==-26||z==10||z==15)for(int y=64;y<=66;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);},mc.getSingleplayerServer());next(16);}
        else if(stage==16&&work.isDone()&&age>20){var a=actor(view,ranger);if(Math.hypot(a.x()-goal.x,a.y()-goal.y)<8){work.join();checks.add("Original infantry move resumes toward its player-requested coordinates after staying trapped for100 ticks in a real three-block-high Minecraft cage which is then removed, without a second order");Screenshot.grab(mc.gameDirectory,"43-movement-resumed.png",mc.getMainRenderTarget(),c->{});
            goal=view.origin.nativePoint(new Vec3(-28.5,64,12.5));view.session.move(goal.x,goal.y,goal.z);next(201);}}
        else if(stage==201&&age>5){view.session.send("TEST_STALL "+ranger);next(202);}
        else if(stage==202&&age>15){var a=actor(view,ranger);if(!a.groundMovement()||Math.hypot(a.goalX()-goal.x,a.goalY()-goal.y)>1)throw new IllegalStateException("AI stop discarded the player's retained goal");stalledPosition=new Vec3(a.x(),a.y(),a.z());stalledRevision=view.state.terrainRevision();stalledFrame=view.state.frame();next(203);}
        else if(stage==203&&actor(view,ranger).moveRetryFrame()>stalledFrame){if(view.state.terrainRevision()!=stalledRevision)throw new IllegalStateException("No-change movement retry fixture changed terrain");checks.add("An original AI stop retains the player's movement goal and triggers a source aiMoveToPosition retry despite an unchanged terrain revision");next(204);}
        else if(stage==204&&Math.hypot(actor(view,ranger).x()-goal.x,actor(view,ranger).y()-goal.y)<8){checks.add("After the injected AI stall the original Ranger reaches its original destination without another player order");goal=view.origin.nativePoint(new Vec3(-15.5,64,12.5));view.session.move(goal.x,goal.y,goal.z);next(205);}
        else if(stage==205&&age>8){view.session.key(0x1f,true);view.session.key(0x1f,false);next(206);}
        else if(stage==206&&age>30){var a=actor(view,ranger);stalledPosition=new Vec3(a.x(),a.y(),a.z());next(207);}
        else if(stage==207&&age>100){var a=actor(view,ranger);if(a.groundMovement()||stalledPosition.distanceTo(new Vec3(a.x(),a.y(),a.z()))>2)throw new IllegalStateException("Movement retry overrode the player's original S stop command");checks.add("Original S stops the unit and cancels retained host movement; the retry loop does not restart it during100 further ticks");
            view.cameraX=560;view.cameraZ=560;view.zoom=40;
            work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=32;x<40;x++)for(int z=32;z<40;z++)level.getChunkAt(new BlockPos(x*16,64,z*16));var store=WorldResourceStore.get(level);var sites=store.discover(level,512,512,639,639,view.state.resourceTemplate());if(sites.size()!=1)throw new IllegalStateException("Expected one supply per 128x128 clear cell, got "+sites.size());supply=sites.getFirst();store.remaining(supply.key(),32100);level.getDataStorage().saveAndJoin();},mc.getSingleplayerServer());next(17);}
        else if(stage==17&&work.isDone()&&age>140){work.join();if(view.state.resources().stream().noneMatch(r->r.key().equals(supply.key())))throw new IllegalStateException("Known supply was not registered while camera covered its terrain");checks.add("Fresh loaded 128x128 cell produces exactly one seeded supply; it registers immediately without camera-dependent deferral");view.cameraX=supply.x()-80;view.cameraZ=supply.z();view.zoom=25;next(18);}
        else if(stage==18&&age>60){var site=view.state.resources().stream().filter(r->r.key().equals(supply.key())).findFirst().orElse(null);if(site!=null){if(site.cash()!=32100)throw new IllegalStateException("Saved supply stock was replaced: "+site);checks.add("Known supply remains registered outside the viewport with its persisted 32100 stock retained");
            var file=view.session.directory.resolve("state.json");var moved=view.session.directory.resolve("snapshot-read-gap-probe.json");
            try {java.nio.file.Files.move(file,moved,java.nio.file.StandardCopyOption.REPLACE_EXISTING);if(view.session.read()==null||!view.session.alive())throw new IllegalStateException("A missing atomic snapshot closed a running native match");}
            finally {if(java.nio.file.Files.exists(moved)&&!java.nio.file.Files.exists(file))try{java.nio.file.Files.move(moved,file);}catch(java.nio.file.FileAlreadyExistsException newerSnapshot){} }
            checks.add("A deliberately missing state.json poll retains the last complete snapshot and the running original process");return true;}}
        return false;
    }
    private static Vec3 freePlant(NativeWorldView view){for(int x=44;x<=52;x+=4)for(int z=28;z<=36;z+=4){boolean clear=true;for(var object:view.state.objects())if(object.structure()){var p=view.origin.world(object.x(),object.y(),object.z());if(Math.abs(p.x-(x+.5))<object.geometryMajor()/8+4&&Math.abs(p.z-(z+.5))<object.geometryMinor()/8+4){clear=false;break;}}if(clear)return new Vec3(x+.5,64,z+.5);}throw new IllegalStateException("No original free Power Plant footprint in prepared test terrain");}
    private static NativeSession.SpecialPower power(NativeWorldView view){return view.state.specialPowers().stream().filter(p->p.name().equals("SuperweaponNeutronMissile")).findFirst().orElseThrow();}
    private static void chat(Minecraft mc,String command){NativeRtsController.toggle();var chat=new net.minecraft.client.gui.screens.ChatScreen(command);mc.setScreen(chat);chat.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,0);NativeRtsController.toggle();}
    private static void next(int value){stage=value;age=0;GeneralsMod.LOG.info("Effects regression case {}",stage);}
    private static void spawn(NativeWorldView view,String name,Vec3 world)throws Exception{var p=view.origin.nativePoint(world);view.session.send("TEST_SPAWN "+name+" "+p.x+" "+p.y+" "+p.z);}
    private static void click(NativeWorldScreen screen,Vec3 p){var xy=screen.project(p);screen.mouseMoved(xy[0],xy[1]);screen.mouseClicked(xy[0],xy[1],0);screen.mouseReleased(xy[0],xy[1],0);}
    private static NativeSession.Actor actor(NativeWorldView view,int id){return view.state.objects().stream().filter(o->o.id()==id).findFirst().orElseThrow();}
}
