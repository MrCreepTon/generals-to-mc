package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded real-world checks for the remaining reports; uses the fixed QA world. */
final class NativeRemainingBench {
    static int stage,age,buggy,truck,cc,barracks,hidden,runner,shotFrame,sample;
    static boolean enabled,sawFx,frameRegistered;
    static CompletableFuture<?> work;
    static final List<UUID> cows=new ArrayList<>();
    static final List<String> issues=new ArrayList<>();
    static final Map<String,Object> measurements=new LinkedHashMap<>();
    static final Map<Long,NativeSession.CombatEvent> impacts=new LinkedHashMap<>();
    static final List<Map<String,Object>> latencies=new ArrayList<>();
    static final List<double[]> ccPixels=new ArrayList<>(),barracksPixels=new ArrayList<>();
    static long clickAt,ackAt,goalAt,motionAt,meshAt,ticket,stopTicket,stoppedAt;
    static Vec3 initial,goal;static float[] initialMesh;static long armedAt;
    static final Vec3 SPLASH=new Vec3(40.5,64,-20.5),TRUCK_TARGET=new Vec3(39.5,64,16.5),FX=new Vec3(65.5,64,19.5);
    static NativeWorldView view(){return NativeWorldView.current;}
    static ServerLevel level(Minecraft mc){return mc.getSingleplayerServer().getLevel(mc.level.dimension());}
    static NativeSession.Actor actor(int id){return view().actor(id);}
    static void next(int value){stage=value;age=0;GeneralsMod.LOG.info("Remaining oracle case {}",value);}
    static void begin(Minecraft mc)throws Exception{
        enabled=true;view().fog.enabled(false);view().cameraX=32;view().cameraZ=-20;view().zoom=28;
        if(!frameRegistered){net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(context->frame(mc));frameRegistered=true;}
        work=CompletableFuture.runAsync(()->{
            var world=level(mc);world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());world.setDayTime(6000);
            for(var e:world.getAllEntities())if(e instanceof Mob&&!(e instanceof NativeActorEntity))e.discard();
            addCows(world,SPLASH,3);
        },mc.getSingleplayerServer());
        spawn("GLAVehicleRocketBuggy",24.5,-20.5,2);next(0);
    }
    static void addCows(ServerLevel world,Vec3 p,int count){cows.clear();for(int i=0;i<count;i++){var cow=new Cow(EntityType.COW,world);cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20);cow.setHealth(20);cow.setPos(p.add(0,0,(i-(count-1)/2.)*.7));cow.setNoAi(true);cow.setPersistenceRequired();world.addFreshEntity(cow);cows.add(cow.getUUID());}}
    static void clearCows(ServerLevel world){for(var id:cows){var e=world.getEntity(id);if(e!=null)e.discard();}cows.clear();}
    static void spawn(String type,double x,double z,int owner)throws Exception{var p=view().origin.nativePoint(new Vec3(x,64,z));view().session.send("TEST_SPAWN "+type+" "+p.x+" "+p.y+" "+p.z+" "+owner);}
    static int find(String type){return view().state.objects().stream().filter(a->a.template().equals(type)).mapToInt(NativeSession.Actor::id).max().orElse(0);}
    static boolean mobsImported(){return cows.stream().allMatch(id->view().state.objects().stream().anyMatch(a->id.toString().equals(a.externalKey())));}
    static void shot(int id,Vec3 target)throws Exception{var p=view().origin.nativePoint(target);view().session.send("TEST_FIRE "+id+" "+p.x+" "+p.y+" "+p.z);shotFrame=view().state.frame();}
    static List<Float> health(ServerLevel world){return cows.stream().map(id->world.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity e?e.getHealth():0f).toList();}
    static void check(boolean pass,String message,List<String> checks){if(pass)checks.add(message);else{issues.add(message);GeneralsMod.LOG.error("Remaining check failed: {}",message);}}
    static void picture(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"remaining-"+name+".png",mc.getMainRenderTarget(),c->{});}
    static boolean tick(Minecraft mc,List<String> checks)throws Exception{
        age++;if(age>500)throw new IllegalStateException("Remaining fixture timeout "+stage+" issues="+issues);
        var v=view();if(v==null||v.state==null)throw new IllegalStateException("Remaining fixture lost its match");
        switch(stage){
            case 0 -> {if(work.isDone()&&mobsImported()&&(buggy=find("GLAVehicleRocketBuggy"))>0&&age>25){work.join();shot(buggy,SPLASH);next(1);}}
            case 1 -> {if(v.state.frame()>shotFrame+95){work=CompletableFuture.runAsync(()->{var hp=health(level(mc));measurements.put("rocketBuggyMobHealth",hp);if(hp.stream().anyMatch(h->h>=20))issues.add("Rocket Buggy splash did not reduce every real Minecraft cow's health: "+hp);},mc.getSingleplayerServer());picture(mc,"splash");next(2);}}
            case 2 -> {if(work.isDone()){work.join();if(issues.isEmpty())checks.add("One original Rocket Buggy rocket reduces the actual health of three distinct Minecraft mobs");v.session.send("TEST_DAMAGE "+buggy+" 99999");work=CompletableFuture.runAsync(()->{clearCows(level(mc));addCows(level(mc),TRUCK_TARGET,2);},mc.getSingleplayerServer());spawn("GLAVehicleBombTruck",25.5,16.5,2);v.cameraX=35;v.cameraZ=16;next(3);}}
            case 3 -> {if(work.isDone()&&mobsImported()&&(truck=find("GLAVehicleBombTruck"))>0&&age>25){work.join();var target=v.state.objects().stream().filter(a->cows.getFirst().toString().equals(a.externalKey())).findFirst().orElseThrow();impacts.clear();v.session.send("TEST_ATTACK "+truck+" "+target.id()+" 1");shotFrame=v.state.frame();next(4);}}
            case 4 -> {if(actor(truck)==null||actor(truck).health()<=0||v.state.frame()>shotFrame+330){var alive=actor(truck);check(alive==null||alive.health()<=0,"An original Bomb Truck reaches and detonates against a Minecraft mob",checks);measurements.put("bombTruckImpacts",List.copyOf(impacts.values()));if(alive!=null)measurements.put("bombTruckLastActor",alive);picture(mc,"bomb-truck");next(5);}}
            case 5 -> {if(age>20){work=CompletableFuture.runAsync(()->measurements.put("bombTruckMobHealth",health(level(mc))),mc.getSingleplayerServer());next(51);}}
            case 51 -> {if(work.isDone()){work.join();for(var event:v.session.recentCombatEvents())if(event.source()==truck)impacts.putIfAbsent(event.sequence(),event);measurements.put("bombTruckImpacts",List.copyOf(impacts.values()));@SuppressWarnings("unchecked") var hp=(List<Float>)measurements.get("bombTruckMobHealth");check(hp.stream().allMatch(h->h<20),"Bomb Truck explosion damages both real Minecraft mobs",checks);var blast=impacts.values().stream().filter(e->e.radius()>0).min(Comparator.comparingDouble(e->v.origin.world(e.x(),e.y(),e.z()).distanceTo(TRUCK_TARGET)));var last=(NativeSession.Actor)measurements.get("bombTruckLastActor");Vec3 blastPoint=blast.isPresent()?v.origin.world(blast.get().x(),blast.get().y(),blast.get().z()):last!=null&&last.health()<=0?v.origin.world(last.x(),last.y(),last.z()):null;measurements.put("bombTruckDetonationPosition",blastPoint);check(blastPoint!=null&&Math.abs(blastPoint.y-64)<1.5,"Bomb Truck detonation remains at Minecraft ground height",checks);work=CompletableFuture.runAsync(()->{clearCows(level(mc));level(mc).setDayTime(6000);},mc.getSingleplayerServer());spawn("GLACommandCenter",35.5,-17.5,2);spawn("GLABarracks",62.5,-17.5,2);spawn("GLAArmsDealer",68.5,26.5,3);v.cameraX=48;v.cameraZ=-17;v.cameraYaw=0;v.zoom=37;next(6);}}
            case 6 -> {if(work.isDone()&&(cc=find("GLACommandCenter"))>0&&(barracks=find("GLABarracks"))>0&&(hidden=find("GLAArmsDealer"))>0&&age>65){work.join();var screen=(NativeWorldScreen)MenuScreens.active(mc.screen);roof(screen,cc,ccPixels);roof(screen,barracks,barracksPixels);picture(mc,"day");next(7);}}
            case 7 -> {if(age>8){double a=brightness(mc,"day",ccPixels),b=brightness(mc,"day",barracksPixels);measurements.put("dayTime",mc.level.getDayTime());measurements.put("dayCommandCenter",a);measurements.put("dayBarracks",b);work=CompletableFuture.runAsync(()->level(mc).setDayTime(18000),mc.getSingleplayerServer());next(8);}}
            case 8 -> {if(work.isDone()&&age>65){work.join();picture(mc,"night");next(9);}}
            case 9 -> {if(age>8){double a=brightness(mc,"night",ccPixels),b=brightness(mc,"night",barracksPixels);measurements.put("nightTime",mc.level.getDayTime());measurements.put("nightCommandCenter",a);measurements.put("nightBarracks",b);double ar=a/((Number)measurements.get("dayCommandCenter")).doubleValue(),br=b/((Number)measurements.get("dayBarracks")).doubleValue();check(ccPixels.size()>10&&barracksPixels.size()>10&&ar<.65&&br<.65&&ar<br*1.8,"Command Centre and Barracks roofs both darken under Minecraft night lighting",checks);v.fog.enabled(true);v.cameraX=68.5;v.cameraZ=26.5;work=CompletableFuture.runAsync(()->level(mc).setDayTime(6000),mc.getSingleplayerServer());next(10);}}
            case 10 -> {if(work.isDone()&&age>55){work.join();measurements.put("enemyFogBeforeF8",v.fog.at(68.5,26.5));measurements.put("enemyMeshBeforeF8",v.sceneActors().contains(hidden));NativeRtsController.toggle();work=CompletableFuture.runAsync(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(level(mc),65.5,78,1.5,Set.of(),0,38,false);},mc.getSingleplayerServer());next(11);}}
            case 11 -> {if(work.isDone()&&age>55){work.join();check(((Number)measurements.get("enemyFogBeforeF8")).intValue()>=80&&v.sceneActors().contains(hidden),"An unexplored enemy building is exported and visible after F8 in ordinary Minecraft",checks);picture(mc,"minecraft-fog");var p=v.origin.nativePoint(FX);v.session.send("TEST_FX WeaponFX_RocketBuggyMissileDetonation "+p.x+" "+p.y+" "+p.z);shotFrame=v.state.frame();sawFx=false;next(12);}}
            case 12 -> {if(age==8)picture(mc,"effects-live");if(age==12){double facing=0;for(var b:v.scene.batches())if(b.actor()==0xe0000000&&b.count()>=3){var a=b.vertices();var p=v.origin.world(a[0],a[1],a[2]);if(p.distanceTo(FX)>6)continue;var q=v.origin.world(a[8],a[9],a[10]);var r=v.origin.world(a[16],a[17],a[18]);var normal=q.subtract(p).cross(r.subtract(p)).normalize();var look=mc.gameRenderer.getMainCamera().getLookVector();facing=Math.max(facing,Math.abs(normal.dot(new Vec3(look.x,look.y,look.z))));}measurements.put("fxCameraFacingDot",facing);check(facing>.98,"Original explosion billboards face the actual first-person Minecraft camera",checks);}if(!v.state.error().isBlank())measurements.put("fxError",v.state.error());if(!sawFx&&v.state.testFXParticles()>0){sawFx=true;}measurements.put("fxMaxParticles",Math.max(((Number)measurements.getOrDefault("fxMaxParticles",0)).intValue(),v.state.testFXParticles()));if(v.state.frame()>shotFrame+220){measurements.put("particlesAfterExpiry",v.state.testFXParticles());check(sawFx&&v.state.testFXParticles()==0,"Original explosion particles appear in Minecraft mode and expire without a frozen remnant",checks);picture(mc,"effects-expired");work=CompletableFuture.runAsync(()->{var pos=new BlockPos(65,64,8);level(mc).setBlock(pos,SiteBlocks.START.defaultBlockState(),3);WorldSiteStore.get(level(mc)).putStart(pos,7,"",-1,-1);var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());player.setItemSlot(EquipmentSlot.MAINHAND,net.minecraft.world.item.ItemStack.EMPTY);player.teleportTo(level(mc),65.5,108,22.5,Set.of(),180,72,false);},mc.getSingleplayerServer());mc.player.setItemSlot(EquipmentSlot.MAINHAND,net.minecraft.world.item.ItemStack.EMPTY);next(13);}}
            case 13 -> {if(work.isDone()&&age>45){work.join();var marker=mc.level.getBlockEntity(new BlockPos(65,64,8));check(marker instanceof SiteMarkerEntity s&&StartAreaPreview.visible(s)&&StartAreaPreview.radius()>0,"A loaded start marker exposes its clearing outline with empty hands and no hover",checks);picture(mc,"start-outline");NativeRtsController.toggle();v.fog.enabled(false);spawn("AmericaInfantryRanger",24.5,-30.5,2);v.cameraX=28;v.cameraZ=-30;v.zoom=22;next(14);}}
            case 14 -> {if((runner=find("AmericaInfantryRanger"))>0&&age>50){v.session.select(runner);next(15);}}
            case 15 -> {if(v.state.selected().contains(runner)&&age>8){sample=0;Files.writeString(v.session.directory.resolve("latency-request.json"),"{\"id\":\"remaining-input\",\"seconds\":5}");arm();next(16);}}
            case 16 -> {if(sample>=8){var capture=v.session.directory.resolve("latency-client.json");if(!Files.isRegularFile(capture))break;var report=GeneralsMod.JSON.fromJson(Files.readString(capture),com.google.gson.JsonObject.class);if(!report.get("id").getAsString().equals("remaining-input"))break;measurements.put("clientTimings",report);check(report.getAsJsonObject("statistics").has("clickHandlingMs")&&report.getAsJsonObject("statistics").has("localIpcAckMs"),"Opt-in client report records real click handling and local command acknowledgements",checks);measurements.put("inputSamples",latencies);var accepts=latencies.stream().mapToLong(m->((Number)m.get("goalMillis")).longValue()).sorted().toArray();measurements.put("inputGoalP50",accepts[accepts.length/2]);measurements.put("inputGoalMax",accepts[accepts.length-1]);check(accepts[accepts.length-1]<=250,"Eight physical move clicks reach the original unit's movement goal within 250 ms with an active NPC",checks);enabled=false;return true;}}
        }return false;
    }
    static void roof(NativeWorldScreen screen,int id,List<double[]> points){
        for(var batch:view().scene.batches())if(batch.actor()==id&&(batch.mode()&0x800)!=0){var a=batch.vertices();for(int i=0;i<batch.count();i+=3){int k=i*8;if(a[k+5]<.75||a[k+13]<.75||a[k+21]<.75)continue;double x=0,y=0,z=0;for(int j=0;j<3;j++){int p=k+j*8;x+=a[p]/3.;y+=a[p+1]/3.;z+=a[p+2]/3.;}var p=screen.project(view().origin.world(x,y,z));if(p[0]>8&&p[0]<screen.width-8&&p[1]>8&&p[1]<screen.height*.7)points.add(new double[]{p[0]/screen.width,p[1]/screen.height});}}
    }
    static double brightness(Minecraft mc,String name,List<double[]> points)throws Exception{try(var stream=Files.newInputStream(mc.gameDirectory.toPath().resolve("screenshots/remaining-"+name+".png"));var image=com.mojang.blaze3d.platform.NativeImage.read(stream)){double sum=0;for(var p:points){int color=image.getPixel((int)(p[0]*image.getWidth()),(int)(p[1]*image.getHeight()));sum+=((color&255)+((color>>8)&255)+((color>>16)&255))/3.;}return sum/Math.max(1,points.size());}}
    static void arm()throws Exception{view().session.key(0x1f,true);stopTicket=view().session.send("KEY 31 0");stoppedAt=0;clickAt=ackAt=goalAt=motionAt=meshAt=0;armedAt=System.nanoTime()+200_000_000L;initialMesh=null;}
    static void frame(Minecraft mc){if(!enabled||view()==null||view().state==null)return;try{
        for(var event:view().session.recentCombatEvents())if(stage==4&&(event.source()==truck||event.radius()>0))impacts.putIfAbsent(event.sequence(),event);
        if(stage!=16||sample>=8||!(MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen)||System.nanoTime()<armedAt)return;
        var a=actor(runner);if(a==null)throw new IllegalStateException("Latency fixture lost Ranger");long now=System.nanoTime();
        if(clickAt==0){if(view().state.consumed()<stopTicket||a.groundMovement()){stoppedAt=0;return;}if(stoppedAt==0){stoppedAt=now;return;}if(now-stoppedAt<250_000_000L)return;initial=new Vec3(a.x(),a.y(),a.z());goal=view().origin.world(a.x(),a.y(),a.z()).add(5,0,0);var xy=screen.project(goal);clickAt=now;screen.mouseMoved(xy[0],xy[1]);screen.mouseClicked(xy[0],xy[1],0);screen.mouseReleased(xy[0],xy[1],0);var field=NativeWorldScreen.class.getDeclaredField("lastWorldCommand");field.setAccessible(true);ticket=field.getLong(screen);return;}
        if(ackAt==0&&view().state.consumed()>=ticket)ackAt=now;
        var p=view().origin.nativePoint(goal);if(goalAt==0&&a.groundMovement()&&Math.hypot(a.goalX()-p.x,a.goalY()-p.y)<4)goalAt=now;
        if(motionAt==0&&initial.distanceTo(new Vec3(a.x(),a.y(),a.z()))>.24)motionAt=now;
        if(goalAt>0&&motionAt>0){latencies.add(Map.of("sample",sample,"acceptedMillis",ackAt==0?-1:(ackAt-clickAt)/1_000_000,"goalMillis",(goalAt-clickAt)/1_000_000,"translationMillis",(motionAt-clickAt)/1_000_000,"clickFrame",view().state.frame()));sample++;arm();}
        else if(now-clickAt>3_000_000_000L){issues.add("Physical move sample timed out: "+sample);latencies.add(Map.of("sample",sample,"acceptedMillis",ackAt==0?-1:(ackAt-clickAt)/1_000_000,"goalMillis",goalAt==0?3000:(goalAt-clickAt)/1_000_000,"translationMillis",motionAt==0?3000:(motionAt-clickAt)/1_000_000));sample++;arm();}
    }catch(Throwable error){issues.add("Render probe: "+error);enabled=false;}}
    static void report(String result,String error,List<String> checks){enabled=false;try{Files.writeString(Path.of(System.getProperty("generalsmc.evidence"),"native-remaining-tests.json"),GeneralsMod.JSON.toJson(Map.of("result",result,"stage",stage,"error",error,"issues",issues,"checks",checks,"measurements",measurements)));}catch(Exception e){GeneralsMod.LOG.error("Remaining evidence unavailable",e);}}
}

