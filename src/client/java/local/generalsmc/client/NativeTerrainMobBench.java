package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Reproduces ordinary mobs, a real stair porch/roof, deep soil and excavated foundations. */
final class NativeTerrainMobBench {
    private static int stage,age,ranger,launcher,nuke,foundation,mobId,hoverCursor=-1;
    private static boolean nukeHit;
    private static UUID cow,zombie,ender,blastZombie;
    private static double placedZ;
    private static long revision,explosions,nukeHitSequence;
    private static CompletableFuture<Void> work;
    private static Vec3 goal;
    static void begin(Minecraft mc,NativeWorldView view,List<String> checks) throws Exception {
        nukeHit=false;nukeHitSequence=0;view.cameraX=-23;view.cameraZ=-26;view.zoom=25;
        work=CompletableFuture.runAsync(()->{
            mc.getSingleplayerServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
            var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);
            for(int x=-33;x<=-18;x++)for(int z=-28;z<=-20;z++)level.setBlock(new BlockPos(x,63,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
            for(int x=-25;x<=-22;x++)for(int z=-26;z<=-20;z++)level.setBlock(new BlockPos(x,64,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
            for(int x=-24;x<=-24;x++)level.setBlock(new BlockPos(x,64,-27),Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH),3);
            for(int x=-32;x<=-16;x++)for(int z=-26;z<=-15;z++)level.setBlock(new BlockPos(x,67,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
            for(int z=-25;z<=-20;z++)for(int y=65;y<=66;y++)for(int x:new int[]{-25,-23})level.setBlock(new BlockPos(x,y,z),Blocks.COBBLESTONE.defaultBlockState(),3);
            var sample=TerrainExporter.sample(level,new TerrainExporter.Region(-24-view.origin.x(),-27-view.origin.z(),3,5,view.origin.x(),view.origin.y(),view.origin.z()));
            if(Math.abs(WorldSurface.ground(level,-24,-23)-65)>.01||sample.stream().anyMatch(c->c.x()+view.origin.x()==-24&&c.type()!=0))throw new IllegalStateException("Porch floor/clearance incorrectly became roof or cliff: "+sample);
        },mc.getSingleplayerServer());
        goal=view.origin.nativePoint(new Vec3(-23.5,64,-31.5));view.session.send("TEST_SPAWN AmericaInfantryRanger "+goal.x+" "+goal.y+" 0");
        stage=0;age=0;
    }
    static boolean tick(Minecraft mc,NativeWorldView view,List<String> checks) throws Exception {
        age++;var state=view.state;var screen=(NativeWorldScreen)MenuScreens.active(mc.screen);
        if(age>400)throw new IllegalStateException("Mob/terrain regression timeout at case "+stage+", ranger="+(ranger==0?null:actor(view,ranger))+", goal="+goal);
        if(stage==0&&work.isDone()&&age>100){work.join();ranger=state.objects().stream().filter(o->o.template().equals("AmericaInfantryRanger")).findFirst().orElseThrow().id();view.session.select(ranger);next(1);}
        else if(stage==1&&age>15){goal=view.origin.nativePoint(new Vec3(-23.5,64,-13.5));view.session.move(goal.x,goal.y,goal.z);next(2);}
        else if(stage==2){if(actor(view,ranger).z()>8.5)throw new IllegalStateException("Infantry teleported onto overhead deck: "+actor(view,ranger));if(age==80){view.session.send("TEST_PATH "+ranger+" "+goal.x+" "+goal.y+" "+goal.z);Screenshot.grab(mc.gameDirectory,"30-stairs-debug.png",mc.getMainRenderTarget(),c->{});}var unit=actor(view,ranger);if(Math.hypot(unit.x()-goal.x,unit.y()-goal.y)<6&&Math.abs(unit.z()-goal.z)<.5){checks.add("Original MessageStream infantry movement climbed a single-block-wide cobblestone staircase and crossed beneath a 17-block-wide deck without snapping onto it, between solid walls");Screenshot.grab(mc.gameDirectory,"30-stairs-porch.png",mc.getMainRenderTarget(),c->{});view.cameraX=-25;view.cameraZ=-8;
            work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var c=new Cow(EntityType.COW,level);c.setNoAi(true);c.setPos(-28.5,64,-8.5);cow=c.getUUID();level.addFreshEntity(c);var z=new Zombie(EntityType.ZOMBIE,level);z.setNoAi(true);z.setPos(-21.5,64,-8.5);z.setItemSlot(EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));zombie=z.getUUID();level.addFreshEntity(z);for(int x=-30;x<=-26;x++)for(int zz=-10;zz<=-6;zz++)if(x==-30||x==-26||zz==-10||zz==-6)level.setBlock(new BlockPos(x,64,zz),Blocks.OAK_FENCE.defaultBlockState(),3);},mc.getSingleplayerServer());next(3);
        }}
        else if(stage==3&&work.isDone()&&age>60){work.join();deselect(screen);next(4);}
        else if(stage==4&&age>15){clickMob(mc,screen,cow);next(5);}
        else if(stage==5&&age>25){assertSelected(view,cow);if(state.portrait()==null)throw new IllegalStateException("Selected ordinary cow has no original portrait window");checks.add("Actual LMB selects a neutral cow inside a fenced pen and opens the original enemy information/portrait panel");work=CompletableFuture.runAsync(()->((LivingEntity)mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA).getEntity(cow)).setHealth(5),mc.getSingleplayerServer());next(6);}
        else if(stage==6&&work.isDone()&&age>30){work.join();int id=NativeRtsController.combat.nativeMobId(cow);var bar=state.healthBars().stream().filter(b->b.id()==id).findFirst().orElseThrow();if(Math.abs(bar.ratio()-.5)>.01)throw new IllegalStateException("Cow health bar ratio did not follow actual HP: "+bar);verifyHealthPixels(mc,screen,view,bar);checks.add("Actual rendered half-HP bar has a bright filled half and an empty remaining half at GUI scale 2");Screenshot.grab(mc.gameDirectory,"31-cow-selected-half-health.png",mc.getMainRenderTarget(),c->{});deselect(screen);next(7);}
        else if(stage==7&&age>15){clickMob(mc,screen,zombie);next(8);}
        else if(stage==8&&age>25){assertSelected(view,zombie);checks.add("Actual LMB selects an ordinary hostile zombie and exposes its portrait/HP in the original control bar");deselect(screen);next(9);}
        else if(stage==9&&age>15){view.session.select(ranger);next(10);}
        else if(stage==10){if(age%2==0){double step=Math.sin(age*.12)*.35;mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA).getEntity(zombie).setPos(-21.5+step,64,-8.5));}var entity=clientMob(mc,zombie);var position=entity.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)).add(0,entity.getBbHeight()*.6,0);var pixel=screen.project(position);int id=NativeRtsController.combat.nativeMobId(zombie);if(screen.actorAt(pixel[0],pixel[1])!=id)throw new IllegalStateException("Hostile zombie hover missed its visible body");screen.mouseMoved(pixel[0],pixel[1]);if(age>15){if(screen.cursorType()!=7&&screen.cursorType()!=35)throw new IllegalStateException("Expected original attack/out-of-range cursor, got "+screen.cursorType());if(hoverCursor<0)hoverCursor=screen.cursorType();else if(screen.cursorType()!=hoverCursor)throw new IllegalStateException("Attack cursor alternated over a visible ordinary zombie: "+hoverCursor+" -> "+screen.cursorType());}if(age>50){checks.add("Original attack cursor stays consistent over a moving ordinary hostile zombie for 35 successive client ticks");
            view.cameraX=-25;view.cameraZ=15;work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=-27;x<=-21;x++)for(int z=16;z<=22;z++)for(int y=50;y<=63;y++)level.setBlock(new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState(),3);},mc.getSingleplayerServer());goal=view.origin.nativePoint(new Vec3(-30.5,64,15.5));view.session.send("TEST_SPAWN AmericaInfantryMissileDefender "+goal.x+" "+goal.y+" 0");next(11);}}
        else if(stage==11&&work.isDone()&&age>100){work.join();launcher=state.objects().stream().filter(o->o.template().equals("AmericaInfantryMissileDefender")).findFirst().orElseThrow().id();view.session.select(launcher);next(22);}
        else if(stage==22&&age>20){screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,29,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);next(27);}
        else if(stage==27&&age>10&&state.forceAttack()){explosions=NativeRtsController.combat.worldExplosions;click(screen,new Vec3(-24.5,64,19.5));next(12);}
        else if(stage==12){if(age==15)screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,29,0);if(NativeRtsController.combat.worldExplosions>explosions&&age>25){screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_S,31,0);screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_S,31,0);work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);int depth=0;for(int y=63;y>=50;y--)if(level.isEmptyBlock(new BlockPos(-25,y,19)))depth=64-y;if(depth<2)throw new IllegalStateException("Original infantry missile only removed one soil layer: depth="+depth);},mc.getSingleplayerServer());next(13);}}
        else if(stage==13&&work.isDone()){work.join();checks.add("Actual Ctrl+LMB original Missile Defender projectile penetrates at least two real dirt layers, using host pressure from original weapon damage");screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,29,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);next(25);}
        else if(stage==25&&age>20&&state.forceAttack()){explosions=NativeRtsController.combat.worldExplosions;click(screen,new Vec3(-24.5,61.5,19.5));next(26);}
        else if(stage==26){if(age==15)screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,29,0);if(age>30&&NativeRtsController.combat.worldExplosions>explosions){screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_S,31,0);screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_S,31,0);work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);int depth=0;for(int y=63;y>=50;y--)if(level.isEmptyBlock(new BlockPos(-25,y,19)))depth=64-y;if(depth<3)throw new IllegalStateException("Second missile did not reach negative native altitude: depth="+depth);var mob=new EnderMan(EntityType.ENDERMAN,level);mob.setNoAi(true);mob.setPos(-23.5,64,10.5);ender=mob.getUUID();level.addFreshEntity(mob);},mc.getSingleplayerServer());next(28);}}
        else if(stage==28&&work.isDone()&&age>60){work.join();checks.add("A second actual Ctrl+LMB original missile reaches a crater below native zero height and penetrates at least three soil layers");next(23);}
        else if(stage==23){if(age==31)screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,29,0);if(age>36){clickMob(mc,screen,ender);next(14);}}
        else if(stage==14&&age==8){screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,29,0);}
        else if(stage==14&&age>20&&((LivingEntity)clientMob(mc,ender)).getHealth()<40){work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var mob=(EnderMan)level.getEntity(ender);if(mob.getHealth()>=mob.getMaxHealth()||mob.position().distanceTo(new Vec3(-23.5,64,10.5))>2)throw new IllegalStateException("Native impact was evaded by Enderman: hp="+mob.getHealth()+" position="+mob.position());},mc.getSingleplayerServer());next(15);}
        else if(stage==15&&work.isDone()){work.join();checks.add("A confirmed original infantry missile impact actually damages Enderman without unconditional vanilla arrow evasion");view.session.select(launcher);var away=view.origin.nativePoint(new Vec3(-42.5,64,3.5));view.session.send("MOVE "+away.x+" "+away.y+" "+away.z);view.cameraX=-35;view.cameraZ=27;var cannon=view.origin.nativePoint(new Vec3(-47.5,64,27.5));view.session.send("TEST_SPAWN ChinaVehicleNukeLauncher "+cannon.x+" "+cannon.y+" 0");work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var z=new Zombie(EntityType.ZOMBIE,level);z.setNoAi(true);z.setPos(-23.5,65,27.5);z.setItemSlot(EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));blastZombie=z.getUUID();level.setBlock(new BlockPos(-24,64,27),Blocks.COBBLESTONE.defaultBlockState(),3);level.addFreshEntity(z);},mc.getSingleplayerServer());next(16);}
        else if(stage==16&&work.isDone()&&age>100){work.join();nuke=state.objects().stream().filter(o->o.template().equals("ChinaVehicleNukeLauncher")).findFirst().orElseThrow().id();view.session.select(nuke);next(24);}
        else if(stage==24&&age>20){explosions=NativeRtsController.combat.worldExplosions;clickMob(mc,screen,blastZombie);next(17);}
        else if(stage==17){for(var event:state.combatEvents())if(event.source()==nuke&&blastZombie.toString().equals(event.target())){nukeHit=true;nukeHitSequence=event.sequence();}if(age==100)Screenshot.grab(mc.gameDirectory,"33-nuke-debug.png",mc.getMainRenderTarget(),c->{});if(age>40&&nukeHit&&NativeRtsController.combat.appliedCombatSequence()>=nukeHitSequence){work=CompletableFuture.runAsync(()->{var mob=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA).getEntity(blastZombie);if(mob instanceof LivingEntity living&&living.isAlive())throw new IllegalStateException("Elevated zombie survived original 400-damage nuclear blast: "+living.getHealth());},mc.getSingleplayerServer());next(18);}}
        else if(stage==18&&work.isDone()){work.join();checks.add("Actual LMB nuclear cannon projectile/AoE kills an ordinary zombie standing one block above the ground");view.cameraX=18;view.cameraZ=24;work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=10;x<=28;x++)for(int z=15;z<=32;z++)level.setBlock(new BlockPos(x,63,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);},mc.getSingleplayerServer());next(19);}
        else if(stage==19&&work.isDone()&&age>100){work.join();var p=view.origin.nativePoint(new Vec3(18.5,64,24.5));view.session.send("TEST_SPAWN AmericaPowerPlant "+p.x+" "+p.y+" 0");next(20);}
        else if(stage==20&&age>40){var building=state.objects().stream().filter(o->o.template().equals("AmericaPowerPlant")&&view.origin.world(o.x(),o.y(),o.z()).x>10).findFirst().orElseThrow();foundation=building.id();placedZ=building.z();revision=state.terrainRevision();work=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=17;x<=19;x++)for(int z=23;z<=25;z++)for(int y=61;y<=63;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);},mc.getSingleplayerServer());next(21);}
        else if(stage==21&&work.isDone()&&state.terrainRevision()>revision&&age>120){work.join();var building=actor(view,foundation);if(Math.abs(building.z()-placedZ)>.01)throw new IllegalStateException("Placed building sank after excavation: "+placedZ+" -> "+building.z());checks.add("Placed native building retains its foundation height after actual Minecraft soil removal and terrain reclassification");Screenshot.grab(mc.gameDirectory,"32-fixed-building-foundation.png",mc.getMainRenderTarget(),c->{});return true;}
        return false;
    }
    private static void next(int value){stage=value;age=0;GeneralsMod.LOG.info("Mob/terrain case {}",stage);}
    private static NativeSession.Actor actor(NativeWorldView view,int id){return view.state.objects().stream().filter(o->o.id()==id).findFirst().orElseThrow();}
    private static Entity clientMob(Minecraft mc,UUID key){for(var entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(key))return entity;throw new IllegalStateException("Client mob missing: "+key);}
    private static void click(NativeWorldScreen screen,Vec3 p){var pixel=screen.project(p);screen.mouseMoved(pixel[0],pixel[1]);screen.mouseClicked(pixel[0],pixel[1],0);screen.mouseReleased(pixel[0],pixel[1],0);}
    private static void clickMob(Minecraft mc,NativeWorldScreen screen,UUID key){var entity=clientMob(mc,key);click(screen,entity.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)).add(0,entity.getBbHeight()*.65,0));}
    private static void deselect(NativeWorldScreen screen){var p=screen.project(new Vec3(-32,64,-12));screen.mouseClicked(p[0],p[1],1);screen.mouseReleased(p[0],p[1],1);}
    private static void assertSelected(NativeWorldView view,UUID key){int id=NativeRtsController.combat.nativeMobId(key);if(id==0||!view.state.selected().equals(List.of(id)))throw new IllegalStateException("Ordinary mob LMB selection failed: key="+key+" id="+id+" selected="+view.state.selected());}
    private static void verifyHealthPixels(Minecraft mc,NativeWorldScreen screen,NativeWorldView view,NativeSession.HealthBar bar){
        var p=screen.project(view.origin.world(bar.x(),bar.y(),bar.z()));double sx=(double)mc.getWindow().getWidth()/mc.getWindow().getGuiScaledWidth(),sy=(double)mc.getWindow().getHeight()/mc.getWindow().getGuiScaledHeight();
        double nativeScaleX=(double)mc.getWindow().getWidth()/view.session.width,nativeScaleY=(double)mc.getWindow().getHeight()/view.session.height;
        int x=(int)Math.round(p[0]*sx+bar.dx()*nativeScaleX),y=(int)Math.round(p[1]*sy+(bar.dy()+1)*nativeScaleY),w=(int)Math.round(bar.width()*nativeScaleX);
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){int left=image.getPixel(x+w/4,y),right=image.getPixel(x+w*3/4,y);int color=(int)bar.color();if((left&0xffffff)!=(color&0xffffff)||(right&0xffffff)==(color&0xffffff))throw new IllegalStateException("Actual health pixels do not shrink: left="+Integer.toHexString(left)+" right="+Integer.toHexString(right)+" color="+Integer.toHexString(color)+" xy="+x+","+y);}
    }
}
