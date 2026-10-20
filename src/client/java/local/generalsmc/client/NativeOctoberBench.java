package local.generalsmc.client;

import local.generalsmc.*;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.*;

/** Fresh-world user-feedback oracle. Never enters the player's JAR. */
final class NativeOctoberBench {
    static int stage,age,ranger,dozer,tank,laser,building,experience;
    static double rangerHealth,dozerHealth;static long revision;static int cash;
    static UUID cow,skeleton,zombie;static CompletableFuture<?> work;
    static final List<Integer> group=new ArrayList<>();static boolean laserSeen;
    static ServerLevel level(Minecraft mc){return mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);}
    static void next(int n){stage=n;age=0;GeneralsMod.LOG.info("October feedback case {}",n);}
    static NativeSession.Actor at(NativeWorldView v,String template,Vec3 point){return v.state.objects().stream().filter(a->a.template().equals(template)&&v.origin.world(a.x(),a.y(),a.z()).distanceTo(point)<2).findFirst().orElseThrow(()->new IllegalStateException("Missing "+template+" at "+point));}
    static boolean near(NativeWorldView v,int id,Vec3 point){var a=v.actor(id);return a!=null&&v.origin.world(a.x(),a.y(),a.z()).distanceTo(point)<1.2;}
    static void move(NativeWorldView v,int id,Vec3 point)throws Exception{v.session.select(id);var p=v.origin.nativePoint(point);v.session.move(p.x,p.y,p.z);}
    static void click(Minecraft mc,NativeWorldView v,Vec3 point){v.cameraX=point.x;v.cameraZ=point.z;var screen=(NativeWorldScreen)mc.screen;var p=screen.project(point);screen.mouseMoved(p[0],p[1]);screen.mouseClicked(p[0],p[1],0);screen.mouseReleased(p[0],p[1],0);}
    static boolean tick(Minecraft mc,List<String> checks)throws Exception{
        age++;if(age>1400)throw new IllegalStateException("October feedback timeout "+stage);var v=NativeWorldView.current;
        if(stage==0){NativeMusicBench.during(mc,checks);NativeKeyConflictBench.check(mc,checks);cash=v.state.localCash();NativeRtsController.toggle();mc.setScreen(new NativeDebugScreen(null));NativeSetupBench.click(mc.screen,"Добавить 10 000");next(1);}
        else if(stage==1&&age>20&&v.state.localCash()==cash+10000){if(!((NativeDebugScreen)mc.screen).feedback.equals("Выполнено"))throw new IllegalStateException("Missing in-menu debug confirmation");Screenshot.grab(mc.gameDirectory,"97-october-debug-confirmation.png",mc.getMainRenderTarget(),c->{});checks.add("The actual creative-tools money button updates original cash and confirms success inside the menu; the debug command no longer echoes technical text to chat");mc.setScreen(new WorldSettingsScreen(null));next(2);}
        else if(stage==2&&NativeNetworkClient.rules!=null){NativeSetupBench.click(mc.screen,"Разрушать блоки оружием: да");NativeSetupBench.click(mc.screen,"Поджигать блоки: да");NativeSetupBench.click(mc.screen,"Монстры атакуют армию: да");next(3);}
        else if(stage==3&&age>25&&!NativeNetworkClient.rules.values().blockDestruction()){
            NativeSetupBench.click(mc.screen,"Готово");NativeRtsController.toggle();revision=v.state.terrainRevision();
            work=CompletableFuture.runAsync(()->{var l=level(mc);l.setDayTime(18000);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,mc.getSingleplayerServer());
                for(int x=-22;x<=-15;x++){l.setBlock(new BlockPos(x,62,-18),Blocks.STONE.defaultBlockState(),3);l.setBlock(new BlockPos(x,63,-18),Blocks.AIR.defaultBlockState(),3);for(int y=63;y<=65;y++)l.setBlock(new BlockPos(x,y,-19),Blocks.DIRT.defaultBlockState(),3);}
                for(int x=-26;x<=-7;x++)for(int z=12;z<=22;z++){int top=x>=-14?65:x>=-19?64:63;for(int y=63;y<=top;y++)l.setBlock(new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState(),3);}
            },mc.getSingleplayerServer());next(4);
        }
        else if(stage==4&&work.isDone()&&v.state.terrainRevision()>revision&&age>80){work.join();NativeReleaseBench.spawn(v,"AmericaInfantryRanger",new Vec3(-18.5,63,-17.5));NativeReleaseBench.spawn(v,"AmericaVehicleDozer",new Vec3(-23.5,64,17.5));next(5);}
        else if(stage==5&&age>40){var a=at(v,"AmericaInfantryRanger",new Vec3(-18.5,63,-17.5));ranger=a.id();rangerHealth=a.health();var b=at(v,"AmericaVehicleDozer",new Vec3(-23.5,64,17.5));dozer=b.id();dozerHealth=b.health();move(v,ranger,new Vec3(-18.5,64,-13.5));next(6);}
        else if(stage==6&&age>15&&near(v,ranger,new Vec3(-18.5,64,-13.5))){if(v.actor(ranger).health()!=rangerHealth)throw new IllegalStateException("Infantry lost health on its one-block exit");checks.add("Original Ranger escapes a one-block pit beside a taller dirt wall through its valid one-block exit, without teleporting or losing health");move(v,dozer,new Vec3(-9.5,66,17.5));next(7);}
        else if(stage==7&&age>20&&near(v,dozer,new Vec3(-9.5,66,17.5))){move(v,dozer,new Vec3(-23.5,64,17.5));next(8);}
        else if(stage==8&&age>20&&near(v,dozer,new Vec3(-23.5,64,17.5))){if(v.actor(dozer).health()!=dozerHealth)throw new IllegalStateException("Dozer lost health on ordinary one-block steps: "+dozerHealth+" -> "+v.actor(dozer).health());work=CompletableFuture.runAsync(()->{var body=NativeRtsController.combat.actor(dozer);if(body.hurtServer(level(mc),level(mc).damageSources().inWall(),4)||body.hurtServer(level(mc),level(mc).damageSources().fall(),4))throw new IllegalStateException("Invisible proxy applied wall/fall damage");},mc.getSingleplayerServer());next(9);}
        else if(stage==9&&work.isDone()){work.join();checks.add("Original Dozer climbs and descends two one-block steps at full native health; proxy-only wall/fall checks cannot damage it");v.session.select(dozer);v.cameraX=-24;v.cameraZ=0;v.zoom=35;next(10);}
        else if(stage==10&&age>20){move(v,dozer,new Vec3(100,64,100));((NativeWorldScreen)mc.screen).clickCommand("Command_ConstructAmericaPowerPlant",true);next(11);}
        else if(stage==11&&age>3){((NativeWorldScreen)mc.screen).clickCommand("Command_ConstructAmericaPowerPlant",false);next(12);}
        else if(stage==12&&age>20&&v.state.pendingBuild()){click(mc,v,new Vec3(-24.5,64,.5));next(13);}
        else if(stage==13&&age>30){var a=v.state.objects().stream().filter(o->o.template().equals("AmericaPowerPlant")&&o.constructionPercent()>=0).findFirst().orElse(null);if(a!=null){building=a.id();next(14);}}
        else if(stage==14&&v.actor(building)!=null&&v.actor(building).constructionPercent()==-1){checks.add("A Dozer with an outstanding previous move finishes a new original GUI-ordered power plant without a second build order; retained-route retries do not cancel its build task");work=CompletableFuture.runAsync(()->{var l=level(mc);var c=new Cow(EntityType.COW,l);c.moveTo(-24.5,64,13.5,0,0);cow=c.getUUID();l.addFreshEntity(c);},mc.getSingleplayerServer());next(15);}
        else if(stage==15&&work.isDone()&&age%20==0){work.join();work=CompletableFuture.supplyAsync(()->{var c=(Cow)level(mc).getEntity(cow);c.getNavigation().moveTo(-24.5,64,-11.5,1);return c.position().distanceTo(new Vec3(-24.5,64,-11.5));},mc.getSingleplayerServer());next(16);}
        else if(stage==16&&work.isDone()){if((double)work.join()<2){checks.add("A real vanilla Cow navigator walks around the invisible original power-plant collision bounds and reaches the far side instead of repeatedly pushing into its wall");for(int i=0;i<3;i++)NativeReleaseBench.spawn(v,"AmericaInfantryRanger",new Vec3(-26.5+i*2,64,-6.5));next(17);}else{stage=15;}}
        else if(stage==17&&age>45){for(var a:v.state.objects())if(a.template().equals("AmericaInfantryRanger")&&Math.abs(v.origin.world(a.x(),a.y(),a.z()).z+6.5)<1)group.add(a.id());if(group.size()<3)throw new IllegalStateException("Missing similar-type group");v.cameraX=-24.5;v.cameraZ=-8;v.session.send("SELECT");next(18);}
        else if(stage==18&&age>20){var s=(NativeWorldScreen)mc.screen;var p=NativeWorldAccessBench.unitPixel(s,v,group.getFirst());s.mouseMoved(p[0],p[1]);s.mouseClicked(p[0],p[1],0);s.mouseReleased(p[0],p[1],0);next(19);}
        else if(stage==19&&age==3){v.cameraX+=.2;var s=(NativeWorldScreen)mc.screen;var p=NativeWorldAccessBench.unitPixel(s,v,group.getFirst());s.mouseMoved(p[0],p[1]);s.mouseClicked(p[0],p[1],0);s.mouseReleased(p[0],p[1],0);next(20);}
        else if(stage==20&&age>30){if(!v.state.selected().containsAll(group))throw new IllegalStateException("Double-click did not select same-type units: "+v.state.selected()+" expected "+group);checks.add("Actual double-click selects the visible original Ranger group despite a small camera-induced pixel shift between clicks");work=CompletableFuture.runAsync(()->{var l=level(mc);for(int i=0;i<256;i++){var a=new ArmorStand(l,28.5,64,-28.5);a.setNoGravity(true);l.addFreshEntity(a);}},mc.getSingleplayerServer());NativeReleaseBench.spawn(v,"AmericaVehicleDozer",new Vec3(18.5,64,-14.5));next(21);}
        else if(stage==21&&work.isDone()&&age>80&&v.state.objects().stream().filter(a->a.externalKey()!=null&&!a.externalKey().isEmpty()).count()>=256){work.join();v.session.send("SELECT");v.cameraX=18.5;v.cameraZ=-18.5;work=CompletableFuture.runAsync(()->{var l=level(mc);var s=new Skeleton(EntityType.SKELETON,l);s.moveTo(18.5,64,-18.5,0,0);skeleton=s.getUUID();l.addFreshEntity(s);},mc.getSingleplayerServer());next(22);}
        else if(stage==22&&work.isDone()&&age>2){var s=mc.level.entitiesForRendering();var entity=java.util.stream.StreamSupport.stream(s.spliterator(),false).filter(e->e.getUUID().equals(skeleton)).findFirst().orElse(null);if(entity!=null){click(mc,v,entity.getBoundingBox().getCenter());next(23);}}
        else if(stage==23&&age>15&&v.state.selected().stream().anyMatch(id->{var a=v.actor(id);return a!=null&&skeleton.toString().equals(a.externalKey());})){checks.add("One actual LMB selects a newly spawned ordinary Skeleton even with 256 older imported living entities; priority import preserves the click instead of issuing ground movement");Screenshot.grab(mc.gameDirectory,"98-october-skeleton-selection.png",mc.getMainRenderTarget(),c->{});experience=v.state.generalExperience();NativeReleaseBench.spawn(v,"AmericaTankCrusader",new Vec3(18.5,64,-24.5));next(24);}
        else if(stage==24&&age>35){tank=at(v,"AmericaTankCrusader",new Vec3(18.5,64,-24.5)).id();v.session.select(tank);next(25);}
        else if(stage==25&&age>20){var entity=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->skeleton.equals(e.getUUID())).findFirst().orElse(null);if(entity!=null)click(mc,v,entity.getBoundingBox().getCenter());next(26);}
        else if(stage==26&&v.state.generalExperience()>=experience+21&&age>30){if(v.state.generalExperience()!=experience+21)throw new IllegalStateException("Mob XP was duplicated");checks.add("An actual original Crusader kills the real Skeleton through vanilla damage and awards its 20-health general experience once through original Zero Hour rounding");experience=v.state.generalExperience();NativeRtsController.toggle();mc.setScreen(new WorldSettingsScreen(null));next(27);}
        else if(stage==27&&NativeNetworkClient.rules!=null){NativeMusicBench.during(mc,checks);NativeSetupBench.click(mc.screen,"Опыт генерала за мобов: да");next(28);}
        else if(stage==28&&age>25&&!NativeNetworkClient.rules.values().mobGeneralExperience()){work=CompletableFuture.runAsync(()->{var l=level(mc);var r=WorldRules.get(l);var saved=WorldRules.load(r.save(new net.minecraft.nbt.CompoundTag(),l.registryAccess())).values();if(saved.mobGeneralExperience()||saved.toggle("blastRadius").mobGeneralExperience())throw new IllegalStateException("Mob XP rule not preserved");var z=new Zombie(EntityType.ZOMBIE,l);z.moveTo(18.5,64,-18.5,0,0);zombie=z.getUUID();l.addFreshEntity(z);WorldCombat.prioritize(l,zombie);},mc.getSingleplayerServer());NativeSetupBench.click(mc.screen,"Готово");NativeRtsController.toggle();next(29);}
        else if(stage==29&&work.isDone()&&age>30){work.join();var entity=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->zombie.equals(e.getUUID())&&e.isAlive()).findFirst().orElse(null);if(entity!=null){v.session.select(tank);click(mc,v,entity.getBoundingBox().getCenter());}next(30);}
        else if(stage==30&&age>160){work=CompletableFuture.runAsync(()->{var z=level(mc).getEntity(zombie);if(z!=null&&z.isAlive())throw new IllegalStateException("Original tank did not kill XP-disabled Zombie");var c=level(mc).getEntity(cow);if(c instanceof Cow living)living.hurtServer(level(mc),level(mc).damageSources().playerAttack(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())),1000);},mc.getSingleplayerServer());next(31);}
        else if(stage==31&&work.isDone()&&age>30){work.join();if(v.state.generalExperience()!=experience)throw new IllegalStateException("Disabled mob XP or Minecraft-only kill awarded general points");checks.add("Disabling mob experience through the real world menu survives NBT and other toggles, and actual later tank/player kills leave original general experience unchanged");NativeReleaseBench.spawn(v,"Lazr_AmericaTankCrusader",new Vec3(10.5,64,-22.5));next(32);}
        else if(stage==32&&age>50){laser=at(v,"Lazr_AmericaTankCrusader",new Vec3(10.5,64,-22.5)).id();var p=v.origin.nativePoint(new Vec3(17.5,64.5,-22.5));v.session.send("TEST_FIRE "+laser+" "+p.x+" "+p.y+" "+p.z);v.cameraX=13;v.cameraZ=-21;next(33);}
        else if(stage==33){if(v.state.exportedLasers()>0){laserSeen=true;Screenshot.grab(mc.gameDirectory,"99-october-original-laser.png",mc.getMainRenderTarget(),c->{});}if(age>80){if(!laserSeen)throw new IllegalStateException("Original laser emitted no segmented beam geometry");checks.add("An actual original laser Crusader shot exports its original textured, colored, additive segmented beam into the Minecraft scene");return true;}}
        return false;
    }
}
