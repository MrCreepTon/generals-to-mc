package local.generalsmc.client;

import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Original superweapon controls and elevated projectiles in the existing private test world. */
final class NativeStrategicBench {
    static int stage,age,source,shot,runner;static Vec3 detonation;static double runnerHealth,minHazardDistance=999;static long sequence;static CompletableFuture<?> work;
    static final Vec3 BEAM=new Vec3(112.5,64,24.5),NUKE=new Vec3(115.5,64,-26.5);
    static final Map<Long,NativeSession.CombatEvent> events=new LinkedHashMap<>();
    static NativeWorldView v(){return NativeWorldView.current;}
    static void next(int s){stage=s;age=0;local.generalsmc.GeneralsMod.LOG.info("Strategic oracle case {}",s);}
    static void begin(Minecraft mc)throws Exception{
        NativePolishBench.values.put("worldOrigin",v().origin.toString());
        if(Boolean.getBoolean("generalsmc.hazardOnly")){
            v().fog.enabled(false);v().cameraX=61.5;v().cameraZ=.5;v().zoom=25;mc.setScreen(new NativePolishBench.Hover(v().session));
            work=CompletableFuture.runAsync(()->{var w=NativePolishBench.level(mc);for(int x=50;x<=72;x++)for(int z=-8;z<=8;z++){w.setBlock(new BlockPos(x,62,z),Blocks.SANDSTONE.defaultBlockState(),2);w.setBlock(new BlockPos(x,63,z),Blocks.SANDSTONE.defaultBlockState(),2);for(int y=64;y<=68;y++)w.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}for(int z=-3;z<=3;z+=2){w.setBlock(new BlockPos(60,63,z),Blocks.SAND.defaultBlockState(),2);w.setBlock(new BlockPos(60,64,z),Blocks.CACTUS.defaultBlockState(),3);}},mc.getSingleplayerServer());NativePolishBench.spawn("AmericaTankCrusader",51.5,.5,0);next(13);return;
        }
        v().fog.enabled(true);v().cameraX=BEAM.x;v().cameraZ=BEAM.z;v().zoom=25;
        mc.setScreen(new NativePolishBench.Hover(v().session));
        work=CompletableFuture.runAsync(()->{
            var w=NativePolishBench.level(mc);var store=WorldRules.get(w);var a=store.values();store.update(new WorldRules.Values(true,a.fireSpread(),a.hostileMobs(),a.neutralRetaliation(),a.environmentDamage(),a.naturalResources(),2,1,a.mobGeneralExperience(),a.eliminationVictory(),true,a.prepareStarts(),a.emp(),a.options()));for(var e:w.getAllEntities())if(e instanceof net.minecraft.world.entity.Mob&&!(e instanceof NativeActorEntity))e.discard();
            for(int x=108;x<=117;x++)for(int z=20;z<=29;z++)w.setBlock(new BlockPos(x,64,z),Blocks.SANDSTONE.defaultBlockState(),2);
            var c=new Cow(EntityType.COW,w);c.setPos(BEAM.add(0,1,0));c.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);c.setHealth(20);c.setNoAi(true);c.setPersistenceRequired();w.addFreshEntity(c);NativePolishBench.cow=c.getUUID();
        },mc.getSingleplayerServer());
        for(int i=0;i<4;i++)NativePolishBench.spawn("AmericaPowerPlant",-30.5+i*12,-28.5,0);
        NativePolishBench.spawn("AmericaParticleCannonUplink",22.5,-25.5,0);next(0);
    }
    static void command(String contains)throws Exception{
        var screen=(NativeWorldScreen)Minecraft.getInstance().screen;var c=v().state.controls().stream().filter(a->a.command().toLowerCase(Locale.ROOT).contains(contains.toLowerCase(Locale.ROOT))).findFirst().orElseThrow(()->new IllegalStateException("Power control "+contains+" unavailable: "+v().state.controls()));
        screen.clickCommand(c.command(),true);screen.clickCommand(c.command(),false);
    }
    static void pointer(Vec3 p){var s=(NativePolishBench.Hover)Minecraft.getInstance().screen;var xy=s.project(p);s.px=(int)xy[0];s.py=(int)xy[1];s.scripted=true;s.mouseMoved(s.px,s.py);}
    static void fire(Vec3 p)throws Exception{pointer(p);var s=(NativePolishBench.Hover)Minecraft.getInstance().screen;s.mouseClicked(s.px,s.py,0);s.mouseReleased(s.px,s.py,0);shot=v().state.frame();events.clear();sequence=v().session.recentCombatEvents().stream().mapToLong(NativeSession.CombatEvent::sequence).max().orElse(0);}
    static boolean tick(Minecraft mc)throws Exception{
        age++;if(age>700)throw new IllegalStateException("Strategic timeout "+stage+" state="+v().state.error()+" pending="+v().state.pendingCommand()+" powers="+v().state.specialPowers());
        for(var e:v().session.recentCombatEvents())if(e.sequence()>sequence)events.putIfAbsent(e.sequence(),e);
        switch(stage){
            case 0->{if(work.isDone()&&(source=NativePolishBench.find("AmericaParticleCannonUplink"))>0&&age>40){work.join();v().session.select(source);v().session.send("DEBUG_COOLDOWN 0");next(1);}}
            case 1->{if(age>30){NativePolishBench.values.put("particleTargetFog",v().fog.at(BEAM.x,BEAM.z));NativePolishBench.check(v().fog.at(BEAM.x,BEAM.z)>=80,"Particle target is genuinely in unexplored RTS fog");var p=v().origin.nativePoint(BEAM);v().session.send("TEST_POWER_TARGET "+source+" "+p.x+" "+p.y+" "+p.z);command("Particle");next(2);}}
            case 2->{if(age>15){var allowed=local.generalsmc.GeneralsMod.JSON.fromJson(Files.readString(v().session.directory.resolve("power-target-test.json")),com.google.gson.JsonObject.class);NativePolishBench.check(allowed.get("allowed").getAsBoolean(),"Original ActionManager permits a strategic strike in loaded unexplored terrain");NativePolishBench.check(!v().state.pendingCommand().isBlank(),"Original particle-cannon control enters targeting mode");fire(BEAM.add(0,1,0));next(3);}}
            case 3->{pointer(BEAM.add(0,1,0));if(v().state.frame()>shot+590){NativePolishBench.check(events.values().stream().anyMatch(e->e.damageType()==22&&e.radius()>0&&e.forcedAttack()),"Actual particle-cannon click exports original damage pulses with explicit strategic terrain intent");NativePolishBench.values.put("particlePulses",events.values().stream().filter(e->e.damageType()==22&&e.radius()>0).count());NativePolishBench.values.put("particleSamples",events.values().stream().filter(e->e.damageType()==22&&e.radius()>0).limit(5).toList());work=CompletableFuture.runAsync(()->{var w=NativePolishBench.level(mc);int remaining=0;for(int x=109;x<=116;x++)for(int z=21;z<=28;z++)if(!w.getBlockState(new BlockPos(x,64,z)).isAir())remaining++;NativePolishBench.values.put("particlePlatformRemaining",remaining);NativePolishBench.check(remaining<60,"Original particle cannon destroys actual Minecraft blocks under the default Ctrl-only terrain rule");var c=w.getEntity(NativePolishBench.cow);NativePolishBench.check(!(c instanceof Cow cow)||cow.getHealth()<20,"Original particle pulses damage an actual Minecraft mob");},mc.getSingleplayerServer());NativePolishBench.damage(source);NativePolishBench.spawn("ChinaNuclearMissileLauncher",22.5,-25.5,0);((NativePolishBench.Hover)MenuScreens.active(mc.screen)).scripted=false;v().cameraX=NUKE.x;v().cameraZ=NUKE.z;next(4);}}
            case 4->{if(work.isDone()&&(source=NativePolishBench.find("ChinaNuclearMissileLauncher"))>0&&age>40){work.join();v().session.select(source);v().session.send("DEBUG_COOLDOWN 0");next(5);}}
            case 5->{if(age>30){NativePolishBench.check(v().fog.at(NUKE.x,NUKE.z)>=80,"Nuclear target is genuinely unexplored");command("Neutron");next(6);}}
            case 6->{if(age>15){NativePolishBench.check(!v().state.pendingCommand().isBlank(),"Original nuclear control enters targeting mode");fire(NUKE);next(7);}}
            case 7->{pointer(NUKE);if(events.values().stream().anyMatch(e->e.radius()>0&&e.damageType()==0&&e.amount()>=3500)){var blast=events.values().stream().filter(e->e.radius()>0&&e.damageType()==0&&e.amount()>=3500).findFirst().orElseThrow();NativePolishBench.check(blast.forcedAttack(),"Nuclear warhead retains strategic terrain intent in unexplored fog");NativePolishBench.values.put("nuclearOriginalMaxDamage",blast.amount());NativePolishBench.values.put("nuclearOriginalRadiusBlocks",blast.radius()/WorldScale.UNITS_PER_BLOCK);next(8);}}
            case 8->{if(age>30){work=CompletableFuture.runAsync(()->{var w=NativePolishBench.level(mc);int air=0;for(int x=111;x<=119;x++)for(int z=-30;z<=-22;z++)if(w.getBlockState(new BlockPos(x,63,z)).isAir())air++;NativePolishBench.values.put("nuclearGroundDestroyed",air);NativePolishBench.check(air>10,"Original nuclear warhead excavates actual Minecraft ground in unexplored fog");},mc.getSingleplayerServer());next(9);}}
            case 9->{if(work.isDone()){work.join();((NativePolishBench.Hover)MenuScreens.active(mc.screen)).scripted=false;v().fog.enabled(false);v().cameraX=43.5;v().cameraZ=18.5;v().zoom=30;work=CompletableFuture.runAsync(()->{
                var w=NativePolishBench.level(mc);var rules=WorldRules.get(w);var a=rules.values();rules.update(new WorldRules.Values(a.blockDestruction(),a.fireSpread(),a.hostileMobs(),a.neutralRetaliation(),a.environmentDamage(),a.naturalResources(),a.blastRadius(),a.blastPower(),a.mobGeneralExperience(),a.eliminationVictory(),false,a.prepareStarts(),a.emp(),a.options()));
                for(var e:w.getAllEntities())if(e instanceof Cow)e.discard();for(int x=40;x<=47;x++)for(int z=15;z<=22;z++)for(int y=63;y<=70;y++)w.setBlock(new BlockPos(x,y,z),y==63||y==64||y==70||x==40||x==47||z==15||z==22?Blocks.SANDSTONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
                var c=new Cow(EntityType.COW,w);c.setPos(43.5,71,18.5);c.setNoAi(true);c.setPersistenceRequired();w.addFreshEntity(c);NativePolishBench.cow=c.getUUID();
            },mc.getSingleplayerServer());var p=v().origin.nativePoint(new Vec3(23.5,85,18.5));v().session.send("TEST_SPAWN AmericaJetAurora "+p.x+" "+p.y+" "+p.z+" 2");next(10);}}
            case 10->{if(work.isDone()&&(source=NativePolishBench.find("AmericaJetAurora"))>0&&age>35&&NativePolishBench.mobId()>0){work.join();v().session.send("TEST_ATTACK "+source+" "+NativePolishBench.mobId()+" 1");shot=v().state.frame();events.clear();sequence=v().session.recentCombatEvents().stream().mapToLong(NativeSession.CombatEvent::sequence).max().orElse(0);next(11);}}
            case 11->{var impact=events.values().stream().filter(e->e.radius()>0&&e.damageType()==25).findFirst();if(impact.isPresent()){detonation=v().origin.world(impact.get().x(),impact.get().y(),impact.get().z());NativePolishBench.values.put("auroraElevatedImpact",detonation);NativePolishBench.check(detonation.y>=69,"Original Aurora bomb detonates at the elevated Minecraft roof, rather than the ground below");next(12);}}
            case 12->{if(age>25){work=CompletableFuture.runAsync(()->{var w=NativePolishBench.level(mc);int roof=0,wall=0;for(int x=40;x<=47;x++)for(int z=15;z<=22;z++){if(w.getBlockState(new BlockPos(x,70,z)).isAir())roof++;for(int y=65;y<=69;y++)if((x==40||x==47||z==15||z==22)&&w.getBlockState(new BlockPos(x,y,z)).isAir())wall++;}NativePolishBench.values.put("auroraRoofDestroyed",roof);NativePolishBench.values.put("auroraWallDestroyed",wall);NativePolishBench.check(roof>5&&wall>0,"An actual Aurora strike destroys both raised sandstone roof and village-house walls");
                for(int x=50;x<=72;x++)for(int z=-8;z<=8;z++){w.setBlock(new BlockPos(x,62,z),Blocks.SANDSTONE.defaultBlockState(),2);w.setBlock(new BlockPos(x,63,z),Blocks.SANDSTONE.defaultBlockState(),2);for(int y=64;y<=68;y++)w.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}for(int z=-3;z<=3;z+=2){w.setBlock(new BlockPos(60,63,z),Blocks.SAND.defaultBlockState(),2);w.setBlock(new BlockPos(60,64,z),Blocks.CACTUS.defaultBlockState(),3);}
            },mc.getSingleplayerServer());NativePolishBench.damage(source);NativePolishBench.spawn("AmericaTankCrusader",51.5,.5,0);v().cameraX=61.5;v().cameraZ=.5;v().zoom=25;next(13);}}
            case 13->{if(work.isDone()&&(runner=NativePolishBench.find("AmericaTankCrusader"))>0&&age>55){work.join();work=CompletableFuture.runAsync(()->{var w=NativePolishBench.level(mc);for(int z=-3;z<=3;z+=2)if(!w.getBlockState(new BlockPos(60,64,z)).is(Blocks.CACTUS))throw new IllegalStateException("Cactus fixture lost its supported obstacle");},mc.getSingleplayerServer());runnerHealth=v().actor(runner).health();v().session.select(runner);next(14);}}
            case 14->{if(age>20&&work.isDone()){work.join();var s=(NativePolishBench.Hover)MenuScreens.active(mc.screen);pointer(new Vec3(70.5,64,.5));s.mouseClicked(s.px,s.py,0);s.mouseReleased(s.px,s.py,0);next(15);}}
            case 15->{var a=v().actor(runner);if(a==null)throw new IllegalStateException("Hazard route lost its tank");var p=v().origin.world(a.x(),a.y(),a.z());for(int z=-3;z<=3;z+=2)minHazardDistance=Math.min(minHazardDistance,Math.hypot(p.x-60.5,p.z-z-.5));NativePolishBench.values.put("cactusLastPosition",p.toString());NativePolishBench.values.put("cactusRouteMinimumDistance",minHazardDistance);if(p.x>=68&&Math.abs(p.z)<9){NativePolishBench.values.put("cactusRouteInitialHealth",runnerHealth);NativePolishBench.values.put("cactusRouteFinalHealth",a.health());NativePolishBench.values.put("cactusRouteMinimumDistance",minHazardDistance);NativePolishBench.check(a.health()==runnerHealth&&minHazardDistance>1,"A physical movement order routes the original tank around cacti without contact damage");NativePolishBench.values.put("cactusRouteMinimumDistance",minHazardDistance);return true;}}

        }return false;
    }
}
