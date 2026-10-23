package local.generalsmc.client;

import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Focused effects, transport-fire and ownership oracle, shared by all version adapters. */
final class NativeVersionCombatBench {
    interface Capture {void grab(String name)throws Exception;}
    static int stage,age,humvee,victim,passenger,boarded;
    static final List<String> issues=new ArrayList<>();
    static final Set<Integer> riders=new HashSet<>();
    static double initialHealth;static boolean sawRocket,sawDeath,sawNeutralRocket,sawSlimeRocket;
    static java.util.UUID slime;static java.util.concurrent.CompletableFuture<?> work;
    static final String MOB="00000000-0000-0000-0000-000000000123";
    static void next(int value){stage=value;age=0;}
    static NativeSession.Actor find(NativeWorldView v,int id){return v.state.objects().stream().filter(a->a.id()==id).findFirst().orElse(null);}
    static void spawn(NativeWorldView v,String name,double x,double z,int owner)throws Exception{
        var p=v.origin.nativePoint(new Vec3(x,64,z));v.session.send("TEST_SPAWN "+name+" "+p.x+" "+p.y+" "+p.z+" "+owner);
    }
    static boolean tick(Minecraft mc,List<String> checks,Capture capture)throws Exception{
        age++;var v=NativeWorldView.current;
        if(v==null||!(NativeVersionScreenBench.current(mc) instanceof NativeWorldScreen screen))throw new IllegalStateException("Feedback oracle lost its match");
        if(age>650)throw new IllegalStateException("Feedback oracle timeout "+stage);
        if(stage==0){
            if(Boolean.getBoolean("generalsmc.feedbackDeathOnly")){v.fog.enabled(false);spawn(v,"AmericaVehicleHumvee",24,-12,2);v.cameraX=24;v.cameraZ=-12;v.zoom=22;next(9);return false;}
            v.cameraX=26;v.cameraZ=0;v.zoom=24;
            spawn(v,"AmericaVehicleHumvee",24,0,2);
            for(int i=0;i<3;i++)spawn(v,"AmericaInfantryMissileDefender",19,i*2,2);
            next(1);
        }else if(stage==1&&age>60){
            humvee=v.state.objects().stream().filter(a->a.template().equals("AmericaVehicleHumvee")).findFirst().orElseThrow().id();
            var p=v.origin.nativePoint(new Vec3(28,64,0));v.session.send("TEST_FX WeaponFX_RocketBuggyMissileDetonation "+p.x+" "+p.y+" "+p.z);
            var actor=find(v,humvee);var hover=screen.project(v.origin.world(actor.x(),actor.y(),actor.z()).add(0,.7,0));screen.mouseMoved(hover[0],hover[1]);
            next(2);
        }else if(stage==2){
            if((age==9||age==18))capture.grab(String.format("feedback-effects-%02d.png",age));
            if(age>40){next(3);}
        }else if(stage==3){
            var rider=v.state.objects().stream().filter(a->a.template().equals("AmericaInfantryMissileDefender")&&!a.contained()).findFirst().orElse(null);
            if(rider==null){if(boarded!=3)throw new IllegalStateException("Transport boarding lost a passenger");spawn(v,"ChinaPowerPlant",46,0,3);next(5);return false;}
            passenger=rider.id();riders.add(passenger);v.session.select(passenger);var target=find(v,humvee);
            v.session.send("WORLD_MOUSE 513 "+target.x()+" "+target.y()+" "+target.z()+" "+humvee+" 1 640 280");
            v.session.send("WORLD_MOUSE 514 "+target.x()+" "+target.y()+" "+target.z()+" "+humvee+" 0 640 280");next(4);
        }else if(stage==4){
            var rider=find(v,passenger);if(rider==null)throw new IllegalStateException("Boarding removed the actual infantry object");
            if(rider.contained()){boarded++;next(3);}else if(age>250)throw new IllegalStateException("Missile Defender did not board Humvee");
        }else if(stage==5&&age>45){
            var target=v.state.objects().stream().filter(a->a.template().equals("ChinaPowerPlant")&&a.owner()==3).findFirst().orElseThrow();victim=target.id();initialHealth=target.health();
            v.cameraX=35;v.cameraZ=0;v.zoom=29;
            // Move the target out of the Humvee gun's reach but within the original passenger weapon range.
            v.session.select(humvee);v.session.send("WORLD_MOUSE 513 "+target.x()+" "+target.y()+" "+target.z()+" "+victim+" 1 640 280");v.session.send("WORLD_MOUSE 514 "+target.x()+" "+target.y()+" "+target.z()+" "+victim+" 0 640 280");next(6);
        }else if(stage==6){
            sawRocket|=v.state.objects().stream().anyMatch(a->a.template().contains("MissileDefenderMissile"));
            if(age==90)capture.grab(String.format("feedback-transport-%03d.png",age));
            if(age>260){
                var target=find(v,victim);
                if(!sawRocket||target!=null&&target.health()>=initialHealth)issues.add("Contained Missile Defenders did not fire original damaging rockets");
                else checks.add("Three contained Missile Defenders fire original missiles from a Humvee and damage an enemy vehicle");
                var car=find(v,humvee);if(car==null)throw new IllegalStateException("Unarmed native target destroyed Humvee");
                v.session.send("MOB_SYNC "+MOB+" "+(car.x()+70)+" "+car.y()+" "+car.z()+" 1000 1000 8 16 0 0 \"Neutral test mob\"");next(7);
            }
        }else if(stage==7&&age>20){
            var target=v.state.objects().stream().filter(a->MOB.equals(a.externalKey())).findFirst().orElseThrow();v.session.select(humvee);
            v.session.send("WORLD_MOUSE 513 "+target.x()+" "+target.y()+" "+target.z()+" "+target.id()+" 1 640 280");v.session.send("WORLD_MOUSE 514 "+target.x()+" "+target.y()+" "+target.z()+" "+target.id()+" 0 640 280");next(8);
        }else if(stage==8){
            sawNeutralRocket|=v.state.combatEvents()!=null&&v.state.combatEvents().stream().anyMatch(e->MOB.equals(e.target())&&riders.contains(e.source()));
            if(age==100)capture.grab("feedback-neutral-passengers.png");
            if(age>200){
                if(!sawNeutralRocket)issues.add("Humvee passengers ignored an explicitly attacked neutral Minecraft target");else checks.add("Humvee passengers damage the explicit neutral Minecraft target without global neutral hostility");
                v.session.send("MOB_DROP "+MOB);v.session.send("KEY 31 1");v.session.send("KEY 31 0");
                var car=find(v,humvee);var point=v.origin.world(car.x()+70,car.y(),car.z());
                work=java.util.concurrent.CompletableFuture.runAsync(()->{
                    var server=mc.getSingleplayerServer();server.setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
                    var level=server.getLevel(local.generalsmc.GeneralsMod.ARENA);
                    var mob=NativeVersionScreenBench.slime(level);
                    mob.setPos(point);mob.setNoAi(true);mob.setPersistenceRequired();mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);mob.setHealth(1000);slime=mob.getUUID();level.addFreshEntity(mob);
                },mc.getSingleplayerServer());next(11);
            }
        }else if(stage==11&&work.isDone()&&age>30){
            work.join();var mob=v.state.objects().stream().filter(a->slime.toString().equals(a.externalKey())).findFirst().orElse(null);
            if(mob!=null){if(mob.owner()!=1)issues.add("Slime was imported as neutral instead of hostile");next(12);}else if(age>180)throw new IllegalStateException("Real Slime not imported into original targeting");
        }else if(stage==12){
            sawSlimeRocket|=v.state.combatEvents()!=null&&v.state.combatEvents().stream().anyMatch(e->slime.toString().equals(e.target())&&riders.contains(e.source()));
            if(age>150){if(!sawSlimeRocket)issues.add("Humvee passengers did not automatically attack a hostile Slime");else checks.add("A real Minecraft Slime is hostile and Humvee passengers automatically hit it with original rockets");
                mc.getSingleplayerServer().execute(()->{var mob=mc.getSingleplayerServer().getLevel(local.generalsmc.GeneralsMod.ARENA).getEntity(slime);if(mob!=null)mob.discard();});
                spawn(v,"AmericaVehicleHumvee",24,-12,2);v.cameraX=24;v.cameraZ=-12;v.zoom=22;next(9);
            }
        }else if(stage==9&&age>40){
            var car=v.state.objects().stream().filter(a->a.template().equals("AmericaVehicleHumvee")&&v.origin.world(a.x(),a.y(),a.z()).distanceTo(new Vec3(24,64,-12))<3).findFirst().orElseThrow();
            v.session.send("TEST_DAMAGE "+car.id()+" 1000000");next(10);
        }else if(stage==10){
            if(age==12){
                for(String file:List.of("gui.rgba","scene.bin"))java.nio.file.Files.copy(v.session.directory.resolve(file),java.nio.file.Path.of(System.getProperty("generalsmc.evidence","evidence"),"feedback-death-"+file),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                var rows=v.scene.batches().stream().filter(b->b.actor()<0).map(b->Map.of("owner",b.actor(),"texture",b.texture(),"mode",b.mode(),"count",b.count(),"firstColor",Integer.toHexString(b.colors()[0]))).toList();
                java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("generalsmc.evidence","evidence"),"feedback-effect-batches.json"),local.generalsmc.GeneralsMod.JSON.toJson(rows));
            }
            sawDeath|=v.scene!=null&&v.scene.batches().stream().anyMatch(b->b.actor()==0xe0000000);
            if((age==6||age==12||age==24||age==60))capture.grab(String.format("feedback-death-%02d.png",age));
            if(age>75){
                if(!sawDeath)issues.add("Original Humvee death emitted no exported particle batches");else checks.add("Destroyed Humvee exports its original death particles");
                var image=javax.imageio.ImageIO.read(mc.gameDirectory.toPath().resolve("screenshots/feedback-death-12.png").toFile());int warm=0;
                for(int y=image.getHeight()/4;y<image.getHeight()*58/100;y++)for(int x=image.getWidth()*35/100;x<image.getWidth()*65/100;x++){
                    int rgb=image.getRGB(x,y),r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;if(r>180&&r-g>30&&g-b>8)warm++;
                }
                if(warm<100)issues.add("Exported death effects did not produce a visible coloured explosion: "+warm+" pixels");else checks.add("Actual screenshot shows a coloured vehicle explosion ("+warm+" warm pixels)");
                if(!issues.isEmpty())throw new IllegalStateException(String.join("; ",issues));
                if(Boolean.getBoolean("generalsmc.feedbackDeathOnly"))v.fog.enabled(true);
                return true;
            }
        }
        return false;
    }
}
