package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Actual server entities, source orders, transport UI, placed marker and fog framebuffer. */
final class NativeWorldPolishBench {
    static int stage,age,ranger,technical,fireUnit,oil,baseline;static UUID ender,golem;
    static long sequence;static double hp,light;static String unload;
    static CompletableFuture<Void> work;
    static final BlockPos OIL=new BlockPos(24,64,24);
    static void begin(Minecraft mc,NativeWorldView v,List<String> checks)throws Exception{
        stage=age=0;v.cameraX=-21;v.cameraZ=-24;v.zoom=22;
        work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);mc.getSingleplayerServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
            var e=new EnderMan(EntityType.ENDERMAN,level);e.setPos(-26.5,64,-25.5);e.setPersistenceRequired();ender=e.getUUID();level.addFreshEntity(e);
            level.setBlock(OIL,SiteBlocks.OIL.defaultBlockState(),3);
            var water=new BlockPos(29,63,-29);level.setBlock(water,Blocks.WATER.defaultBlockState(),3);level.setBlock(water.south(8),Blocks.LAVA.defaultBlockState(),3);
            var columns=TerrainExporter.sample(level,new TerrainExporter.Region(29-v.origin.x(),-29-v.origin.z(),1,9,v.origin.x(),v.origin.y(),v.origin.z()));
            if(columns.size()!=9||columns.getFirst().type()!=1||columns.getLast().type()!=6)throw new IllegalStateException("Water/lava navigation classification: "+columns);
        },mc.getSingleplayerServer());
        spawn(v,"AmericaInfantryRanger",-24.5,-24.5);spawn(v,"GLAVehicleTechnical",-17.5,-24.5);spawn(v,"AmericaInfantryRanger",-8.5,-24.5);
    }
    static boolean tick(Minecraft mc,NativeWorldView v,List<String> checks)throws Exception{
        age++;if(age>650)throw new IllegalStateException("World polish timeout case "+stage+", objects="+v.state.objects());
        var screen=(NativeWorldScreen)mc.screen;
        if(stage==0&&work.isDone()&&age>100){work.join();var units=v.state.objects().stream().filter(a->a.template().equals("AmericaInfantryRanger")).sorted(Comparator.comparingDouble(NativeSession.Actor::x)).toList();ranger=units.getFirst().id();fireUnit=units.getLast().id();technical=v.state.objects().stream().filter(a->a.template().startsWith("GLAVehicleTechnical")).findFirst().orElseThrow().id();
            work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var e=(EnderMan)level.getEntity(ender);if(e==null||e.getTarget()!=null||e.getRemainingPersistentAngerTime()!=0)throw new IllegalStateException("Unprovoked Enderman missing or acquired native unit: "+e);e.discard();},mc.getSingleplayerServer());next(1);
        }else if(stage==1&&work.isDone()){work.join();checks.add("A real AI-enabled neutral Enderman remains calm beside an original Ranger; water imports as water and lava as impassable");v.session.select(ranger);next(2);}
        else if(stage==2&&age>15){click(screen,v.origin.world(actor(v,technical).x(),actor(v,technical).y(),actor(v,technical).z()).add(0,.5,0));next(3);}
        else if(stage==3&&actor(v,ranger).contained()&&age>20){
            if(NativeRtsController.combat.actor(ranger)!=null)throw new IllegalStateException("Contained rider still exposed to Minecraft combat");checks.add("Actual original LMB enter order loads Ranger into Technical and removes its separate Minecraft attack target");v.session.select(technical);next(4);
        }else if(stage==4&&age>20){unload=v.state.controls().stream().map(NativeSession.Control::command).filter(c->c!=null&&(c.toLowerCase(Locale.ROOT).contains("evacuate")||c.toLowerCase(Locale.ROOT).contains("emptyvehicle"))).findFirst().orElseThrow(()->new IllegalStateException("No original unload command: "+v.state.controls()));screen.clickCommand(unload,true);next(5);}
        else if(stage==5&&age>6){screen.clickCommand(unload,false);next(6);}
        else if(stage==6&&!actor(v,ranger).contained()&&age>40){
            var a=actor(v,ranger);if(a.health()<=0||Math.abs(v.origin.world(a.x(),a.y(),a.z()).y-64)>1.1||NativeRtsController.combat.actor(ranger)==null||!v.sceneActors().contains(ranger))throw new IllegalStateException("Unloaded rider missing, buried or dead: "+a);
            checks.add("Original Technical unload button restores the same live Ranger, its posed mesh and Minecraft combat entity at ground height");work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var g=new IronGolem(EntityType.IRON_GOLEM,level);g.setPos(-22.5,64,-18.5);g.setPersistenceRequired();golem=g.getUUID();level.addFreshEntity(g);},mc.getSingleplayerServer());v.session.select(ranger);next(7);
        }else if(stage==7&&age==12){screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,29,0);}
        else if(stage==7&&age>18){clickMob(mc,screen,golem);next(8);}
        else if(stage==8&&age==8){screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,29,0);}
        else if(stage==8&&age>45){work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var g=(IronGolem)level.getEntity(golem);if(g.getHealth()>=g.getMaxHealth()||!(g.getTarget() instanceof NativeActorEntity proxy)||(proxy.nativeId!=ranger&&proxy.nativeId!=technical))throw new IllegalStateException("Golem did not retaliate against original projectile: hp="+g.getHealth()+" target="+g.getTarget());},mc.getSingleplayerServer());next(9);}
        else if(stage==9&&work.isDone()){work.join();checks.add("A real neutral Iron Golem loses HP to original weapons and retaliates against a firing Ranger/Technical Minecraft entity");hp=actor(v,fireUnit).health();work=CompletableFuture.runAsync(()->{var proxy=NativeRtsController.combat.actor(fireUnit);var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);level.setBlock(BlockPos.containing(proxy.position()),Blocks.LAVA.defaultBlockState(),3);},mc.getSingleplayerServer());next(10);}
        else if(stage==10&&age>8){work.join();var burned=v.state.objects().stream().filter(a->a.id()==fireUnit).findFirst();if(burned.isEmpty()||burned.get().health()<hp){checks.add("Actual Minecraft lava contact reduces original infantry HP through original flame resistance");next(11);}else if(age>50)throw new IllegalStateException("Real lava contact did not reach original health");}
        else if(stage==11&&v.state.sites()!=null&&!v.state.sites().isEmpty()&&age>30){oil=v.state.sites().getFirst().id();if(!actor(v,oil).template().equals("TechOilDerrick"))throw new IllegalStateException("Marker did not create original oil template");
            work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var store=WorldSiteStore.get(level);if(store.sites().stream().noneMatch(s->s.pos().equals(OIL)))throw new IllegalStateException("Placed marker not saved");var tag=store.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());if(tag.getList("sites",10).size()!=1)throw new IllegalStateException("Oil marker NBT missing");},mc.getSingleplayerServer());v.cameraX=24;v.cameraZ=24;next(12);
        }else if(stage==12&&work.isDone()&&age>60){work.join();if(!v.sceneActors().contains(oil))throw new IllegalStateException("Original oil mesh missing");checks.add("Placing the actual Minecraft oil-marker block creates the original neutral TechOilDerrick and persists its position in dimension SavedData");v.fog.enabled(false);v.cameraX=55;v.cameraZ=24;next(13);}
        else if(stage==13&&age>100){Screenshot.grab(mc.gameDirectory,"61-unfogged-reference.png",mc.getMainRenderTarget(),c->{});light=pixel(mc,screen,new Vec3(55.5,64,24.5));if(light<15)throw new IllegalStateException("Unfogged reference pixel absent: "+light+" pixel="+Arrays.toString(screen.project(new Vec3(55.5,64,24.5)))+" camera="+mc.gameRenderer.getMainCamera().getPosition());v.fog.enabled(true);next(14);}
        else if(stage==14&&age>40){double dim=pixel(mc,screen,new Vec3(55.5,64,24.5));if(dim<light*.18||dim>light*.65)throw new IllegalStateException("Fog must dim terrain: original="+light+" fog="+dim);Screenshot.grab(mc.gameDirectory,"60-dim-terrain-fog.png",mc.getMainRenderTarget(),c->{});checks.add("Actual unexplored Minecraft terrain remains visible under dim fog instead of becoming black");v.cameraX=24;v.cameraZ=24;next(15);}
        else if(stage==15&&age>60){if(!v.sceneActors().contains(oil))throw new IllegalStateException("Derrick vanished after camera return");work=CompletableFuture.runAsync(()->mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA).setBlock(OIL,Blocks.AIR.defaultBlockState(),3),mc.getSingleplayerServer());next(16);}
        else if(stage==16&&work.isDone()&&age>60){work.join();if(v.state.sites().stream().anyMatch(s->s.id()==oil)||v.state.objects().stream().anyMatch(a->a.id()==oil))throw new IllegalStateException("Removed marker left ghost structure");checks.add("Known original derrick survives camera departure; removing its marker removes the same native structure without a respawn");
            v.fog.enabled(false);v.cameraX=20;v.cameraZ=24;v.zoom=10;
            work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=14;x<=27;x++)for(int z=10;z<=35;z++){level.setBlock(new BlockPos(x,19,z),Blocks.STONE.defaultBlockState(),3);for(int y=20;y<=64;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}},mc.getSingleplayerServer());next(17);
        }else if(stage==17&&work.isDone()&&age>180){work.join();var xy=screen.project(new Vec3(20.5,20,24.5));sequence=v.session.send("TEST_PROJECT "+((int)(xy[0]*v.session.width/screen.width)+7)+" "+(int)(xy[1]*v.session.height/screen.height));next(18);}
        else if(stage==18&&v.state.consumed()>=sequence&&age>10){var p=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(v.session.directory.resolve("project-test.json"))).getAsJsonObject();if(!p.get("valid").getAsBoolean()||p.get("zeroPlane").getAsBoolean()||p.get("eyeZ").getAsDouble()>=0||Math.abs(p.get("z").getAsDouble()+44*WorldScale.UNITS_PER_BLOCK)>.1)throw new IllegalStateException("Native picking below origin used a sky/zero plane: "+p);checks.add("Native source screen-to-terrain picks the real floor44 blocks below match origin with its camera below native zero; the unrelated stock camera is never used");spawnLow(v);next(19);}
        else if(stage==19&&age>35){ranger=v.state.objects().stream().filter(a->a.template().equals("AmericaInfantryRanger")&&a.z()<-300).findFirst().orElseThrow().id();v.session.select(ranger);next(20);}
        else if(stage==20&&age>15){click(screen,new Vec3(23.5,20,24.5));next(21);}
        else if(stage==21&&age>50){var a=actor(v,ranger);var p=v.origin.world(a.x(),a.y(),a.z());if(p.distanceTo(new Vec3(23.5,20,24.5))>.9)throw new IllegalStateException("LMB order below origin failed: "+a);checks.add("Actual original LMB movement reaches a Minecraft floor44 blocks below its starting height without an airborne goal");Screenshot.grab(mc.gameDirectory,"62-ground-order-below-origin.png",mc.getMainRenderTarget(),c->{});spawn(v,"AmericaInfantryRanger",-80.5,-24.5);next(22);}
        else if(stage==22&&age>50){ranger=v.state.objects().stream().filter(a->a.template().equals("AmericaInfantryRanger")&&v.origin.world(a.x(),a.y(),a.z()).x<-79).findFirst().orElseThrow().id();v.session.select(ranger);next(23);}
        else if(stage==23&&age>10){var p=v.origin.nativePoint(new Vec3(-144.5,64,-24.5));v.session.send("MOVE "+p.x+" "+p.y+" "+p.z);next(24);}
        else if(stage==24&&age>80){var p=v.origin.world(actor(v,ranger).x(),actor(v,ranger).y(),actor(v,ranger).z());if(p.distanceTo(new Vec3(-144.5,64,-24.5))<1.1){checks.add("Original infantry walks64 blocks away from a stationary distant camera into asynchronously loaded army chunks and reaches its real Minecraft goal");return true;}if(age>620)throw new IllegalStateException("Offscreen route stalled: world="+p+" native="+actor(v,ranger));}
        return false;
    }
    static void spawn(NativeWorldView v,String type,double x,double z)throws Exception{var p=v.origin.nativePoint(new Vec3(x,64,z));v.session.send("TEST_SPAWN "+type+" "+p.x+" "+p.y+" "+p.z);}
    static void spawnLow(NativeWorldView v)throws Exception{var p=v.origin.nativePoint(new Vec3(18.5,20,24.5));v.session.send("TEST_SPAWN AmericaInfantryRanger "+p.x+" "+p.y+" "+p.z);}
    static NativeSession.Actor actor(NativeWorldView v,int id){return v.state.objects().stream().filter(a->a.id()==id).findFirst().orElseThrow();}
    static void click(NativeWorldScreen screen,Vec3 p){var xy=screen.project(p);screen.mouseMoved(xy[0],xy[1]);screen.mouseClicked(xy[0],xy[1],0);screen.mouseReleased(xy[0],xy[1],0);}
    static void clickMob(Minecraft mc,NativeWorldScreen screen,UUID id){for(var e:mc.level.entitiesForRendering())if(e.getUUID().equals(id)){click(screen,e.position().add(0,e.getBbHeight()*.6,0));return;}throw new IllegalStateException("Missing fixture mob "+id);}
    static double pixel(Minecraft mc,NativeWorldScreen screen,Vec3 p){var xy=screen.project(p);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){int c=image.getPixel((int)(xy[0]*mc.getWindow().getGuiScale()),(int)(xy[1]*mc.getWindow().getGuiScale()));return ((c&255)+((c>>8)&255)+((c>>16)&255))/3.;}}
    static void next(int value){stage=value;age=0;GeneralsMod.LOG.info("World polish case {}",stage);}
}
