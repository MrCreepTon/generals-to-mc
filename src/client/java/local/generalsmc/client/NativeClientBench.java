package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in real Minecraft + original-engine oracle. No OS input, no user worlds. */
public final class NativeClientBench {
    private static int ticks,stage,stageTicks;
    private static boolean created;
    private static NativeSession session;
    private static NativeSession.Actor dozer;
    private static TerrainExporter.Region region;
    private static CompletableFuture<List<TerrainExporter.Column>> terrain;
    private static TerrainSynchronizer terrainSync;
    private static double initialX,initialY;
    private static int wallFrame;
    private static NativeWorldView worldView;
    private static double moveX,moveY;
    private static long oldDraws;
    private static int beforeWorldGuiCash,beforeWorldGuiDozers;
    private static final List<String> checks=new ArrayList<>();
    private static final String world="GeneralsOriginalEngineBench-"+System.currentTimeMillis();
    public static void tick(Minecraft mc) {
        if(!Boolean.getBoolean("generalsmc.nativeBench"))return;
        ticks++;stageTicks++;
        try {
            if(ticks>20*240)throw new IllegalStateException("Original-engine client bench timed out at stage "+stage);
            if(!created&&ticks>60&&(mc.screen instanceof TitleScreen||mc.screen instanceof AccessibilityOnboardingScreen)) {
                created=true;mc.options.guiScale().set(2);mc.options.pauseOnLostFocus=false;
                mc.createWorldOpenFlows().createFreshLevel(world,
                    new LevelSettings(world,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(FeatureFlags.DEFAULT_FLAGS),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1263217L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);return;
            }
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
            if(stage==0) {
                session=new NativeSession(Path.of(System.getProperty("generalsmc.nativeRoot",System.getenv("LOCALAPPDATA")+"/GeneralsMinecraft")),true);
                stage=1;stageTicks=0;return;
            }
            var state=session.read();if(state==null)return;
            if(!state.error().isEmpty())throw new IllegalStateException(state.error());
            if(terrainSync!=null)terrainSync.tick(state);
            if(worldView!=null)worldView.tick(state);
            if(stage==1) {
                String map=state.maps().stream().filter(s->s.toLowerCase(Locale.ROOT).contains("tournament desert")).findFirst().orElseThrow();
                session.start(map,"FactionAmerica","FactionChina",12345,10000);stage=2;stageTicks=0;return;
            }
            if(stage==2&&state.inGame()&&state.objects().size()>10&&stageTicks>50) {
                dozer=state.objects().stream().filter(o->o.template().equals("AmericaVehicleDozer")).findFirst().orElseThrow();
                session.select(state.objects().stream().filter(o->o.template().equals("AmericaCommandCenter")).findFirst().orElseThrow().id());
                mc.setScreen(new NativeFrameScreen(session,false));stage=21;stageTicks=0;return;
            }
            if(stage==21&&stageTicks>30&&state.frame()>=120&&mc.screen instanceof NativeFrameScreen screen&&screen.updates>0) {
                screen.clickOriginal(245,515,true);stage=22;stageTicks=0;return;
            }
            if(stage==22&&stageTicks>4&&mc.screen instanceof NativeFrameScreen screen) {
                screen.clickOriginal(245,515,false);stage=23;stageTicks=0;return;
            }
            if(stage==23&&state.localCash()<10000&&state.objects().stream().filter(o->o.template().equals("AmericaVehicleDozer")).count()>1) {
                checks.add("Minecraft screen mouse input ordered a second dozer through the original control bar, payment and production");
                mc.setScreen(null);
                // Test away from stock buildings: their ignored footprints can overlap a terrain wall.
                initialX=dozer.x()-160;initialY=dozer.y()-160;
                session.select(dozer.id());session.move(initialX,initialY,dozer.z());
                stage=20;stageTicks=0;return;
            }
            if(stage==20) {
                var actor=state.objects().stream().filter(o->o.id()==dozer.id()).findFirst().orElseThrow();
                if(Math.hypot(actor.x()-initialX,actor.y()-initialY)>15)return;
                dozer=actor;
                initialX=dozer.x();initialY=dozer.y();
                mc.setScreen(new NativeFrameScreen(session.directory.resolve("frame.bmp")));stage=3;stageTicks=0;return;
            }
            if(stage==3&&stageTicks>40&&mc.screen instanceof NativeFrameScreen screen&&screen.updates>0) {
                checks.add("Original WW3D framebuffer uploaded and drawn in the real Minecraft client");
                Screenshot.grab(mc.gameDirectory,"06-original-frame-transport.png",mc.getMainRenderTarget(),c->{});
                mc.setScreen(null);
                int cx=(int)Math.floor(initialX/10),cz=(int)Math.floor(initialY/10);
                region=new TerrainExporter.Region(cx-12,cz-12,32,32,0,64,0);
                terrain=CompletableFuture.supplyAsync(()->{
                    var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);
                    for(int x=region.x()-1;x<=region.x()+region.width();x++)for(int z=region.y()-1;z<=region.y()+region.depth();z++) {
                        level.setBlock(new BlockPos(x,63,z),Blocks.SAND.defaultBlockState(),3);
                        for(int y=64;y<=68;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                    }
                    // Minecraft blocks enclose the original unit; leave a roomy interior.
                    for(int x=cx-7;x<=cx+7;x++)for(int z=cz-7;z<=cz+7;z++)if(Math.abs(x-cx)>=6||Math.abs(z-cz)>=6)
                        for(int y=64;y<=66;y++)level.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),3);
                    return TerrainExporter.sample(level,region);
                },mc.getSingleplayerServer());
                stage=4;stageTicks=0;return;
            }
            if(stage==4&&terrain.isDone()) {
                var columns=terrain.join();
                if(columns.stream().noneMatch(c->c.type()==2))throw new IllegalStateException("Minecraft wall did not create cliff columns");
                terrainSync=new TerrainSynchronizer(session,mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA),List.of(region),state.terrainRevision());
                stage=41;stageTicks=0;return;
            }
            if(stage==41&&state.terrainRevision()==1&&state.terrainColumns()==1024) {
                session.select(dozer.id());session.move(initialX+160,initialY,0);
                wallFrame=state.frame();stage=5;stageTicks=0;return;
            }
            if(stage==5&&state.terrainRevision()==1&&state.frame()-wallFrame>=150) {
                var actor=state.objects().stream().filter(o->o.id()==dozer.id()).findFirst().orElseThrow();
                if(Math.abs(actor.x()-initialX)>60||Math.abs(actor.y()-initialY)>60)throw new IllegalStateException("Native dozer escaped through Minecraft wall: "+actor+" from "+initialX+","+initialY);
                checks.add("Original pathfinder kept the dozer inside a wall sampled from real Minecraft blocks for 150 native frames");
                if(terrainSync.revision()!=1)throw new IllegalStateException("Unchanged Minecraft surface was retransmitted");
                checks.add("Continuous terrain synchronizer sent the initial surface once and skipped unchanged columns");
                terrain=CompletableFuture.supplyAsync(()->{
                    var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);
                    for(int x=region.x();x<region.x()+region.width();x++)for(int z=region.y();z<region.y()+region.depth();z++)
                        for(int y=64;y<=68;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                    return TerrainExporter.sample(level,region);
                },mc.getSingleplayerServer());stage=6;stageTicks=0;return;
            }
            if(stage==6&&terrain.isDone()) {
                terrain.join();
                if(state.terrainRevision()<2)return;
                session.select(dozer.id());session.move(initialX+160,initialY,0);
                stage=7;stageTicks=0;return;
            }
            if(stage==7&&state.terrainRevision()==2) {
                var actor=state.objects().stream().filter(o->o.id()==dozer.id()).findFirst().orElseThrow();
                if(actor.x()>initialX+90) {
                    checks.add("Continuous synchronizer detected removed Minecraft wall, reclassified native terrain and original locomotor passed its former location");
                    session.move(actor.x(),actor.y(),actor.z());
                    worldView=new NativeWorldView(session,new NativeWorldView.Origin(0,64,0),actor.x(),actor.y());worldView.zoom=18;
                    terrain=CompletableFuture.supplyAsync(()->{
                        var server=mc.getSingleplayerServer();var level=server.getLevel(GeneralsMod.ARENA);
                        for(int x=region.x();x<region.x()+region.width();x++)for(int z=region.y();z<region.y()+region.depth();z++)
                            level.setBlock(new BlockPos(x,63,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
                        int x=(int)Math.floor(actor.x()/10),z=(int)Math.floor(actor.y()/10);
                        for(int y=64;y<67;y++)level.setBlock(new BlockPos(x+6,y,z+5),Blocks.STONE_BRICKS.defaultBlockState(),3);
                        var player=server.getPlayerList().getPlayer(mc.player.getUUID());
                        player.teleportTo(level,actor.x()/10,65,actor.y()/10,Set.of(),0,60,false);
                        return List.<TerrainExporter.Column>of();
                    },mc.getSingleplayerServer());stage=8;stageTicks=0;return;
                }
                if(stageTicks>700)throw new IllegalStateException("Native dozer did not pass the removed wall");
            }
            if(stage==8&&terrain.isDone()&&mc.level.dimension().equals(GeneralsMod.ARENA)&&stageTicks>50) {
                terrain.join();mc.setScreen(new NativeWorldScreen(session,false));stage=9;stageTicks=0;return;
            }
            if(stage==9&&stageTicks>50&&worldView.scene!=null&&worldView.sceneActors().contains(dozer.id())&&worldView.drawnVertices>0&&worldView.textureCount()>0) {
                var actor=state.objects().stream().filter(o->o.id()==dozer.id()).findFirst().orElseThrow();
                var bounds=worldView.bounds(dozer.id());var center=bounds.getCenter();
                var expected=worldView.origin.world(actor.x(),actor.y(),actor.z());
                if(Math.abs(center.x-expected.x)>3||Math.abs(center.z-expected.z)>3||bounds.minY<63.5||bounds.maxY>68)throw new IllegalStateException("Original dozer mesh is not standing on Minecraft surface: "+bounds);
                checks.add("Original posed W3D triangles and original textures drawn in Minecraft world pass at native object coordinates on block surface");
                Screenshot.grab(mc.gameDirectory,"07-original-units-in-world.png",mc.getMainRenderTarget(),c->{});
                if(!(mc.screen instanceof NativeWorldScreen screen))throw new IllegalStateException("World input screen missing");
                var pixel=screen.project(center);screen.mouseClicked(pixel[0],pixel[1],0);screen.mouseReleased(pixel[0],pixel[1],0);
                stage=91;stageTicks=0;return;
            }
            if(stage==91&&stageTicks>10&&mc.screen instanceof NativeWorldScreen screen){
                if(!state.selected().contains(dozer.id()))throw new IllegalStateException("Minecraft ray did not select original dozer");
                checks.add("Minecraft camera ray selected the original dozer's world mesh and sent native selection");
                var actor=state.objects().stream().filter(o->o.id()==dozer.id()).findFirst().orElseThrow();
                moveX=actor.x()-60;moveY=actor.y();oldDraws=worldView.drawnVertices;
                var target=screen.project(worldView.origin.world(moveX,moveY,0));screen.mouseClicked(target[0],target[1],0);screen.mouseReleased(target[0],target[1],0);
                stage=10;stageTicks=0;return;
            }
            if(stage==10) {
                var actor=state.objects().stream().filter(o->o.id()==dozer.id()).findFirst().orElseThrow();
                if(Math.hypot(actor.x()-moveX,actor.y()-moveY)>10)return;
                if(worldView.drawnVertices<=oldDraws)throw new IllegalStateException("Original world mesh stopped updating during movement");
                checks.add("Minecraft ground picking issued original locomotor movement and original world mesh followed the unit");
                worldView.cameraX=actor.x()/10;worldView.cameraZ=actor.y()/10;
                Screenshot.grab(mc.gameDirectory,"08-original-unit-after-move.png",mc.getMainRenderTarget(),c->{});
                terrain=CompletableFuture.supplyAsync(()->{
                    var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);int x=(int)Math.floor(actor.x()/10),z=(int)Math.floor(actor.y()/10);
                    for(int wx=x-4;wx<=x+4;wx++)for(int y=64;y<=69;y++)level.setBlock(new BlockPos(wx,y,z-1),Blocks.RED_WOOL.defaultBlockState(),3);
                    return List.<TerrainExporter.Column>of();
                },mc.getSingleplayerServer());stage=11;stageTicks=0;return;
            }
            if(stage==11&&terrain.isDone()&&stageTicks>50) {
                terrain.join();Screenshot.grab(mc.gameDirectory,"09-original-unit-block-depth.png",mc.getMainRenderTarget(),c->{});
                checks.add("Captured original world mesh behind actual Minecraft wool wall for visual depth-buffer verification");
                beforeWorldGuiCash=state.localCash();beforeWorldGuiDozers=(int)state.objects().stream().filter(o->o.template().equals("AmericaVehicleDozer")).count();
                session.select(state.objects().stream().filter(o->o.template().equals("AmericaCommandCenter")).findFirst().orElseThrow().id());
                stage=12;stageTicks=0;return;
            }
            if(stage==12&&stageTicks>40&&mc.screen instanceof NativeWorldScreen screen) {
                screen.clickOriginal(245,515,true);stage=13;stageTicks=0;return;
            }
            if(stage==13&&stageTicks>4&&mc.screen instanceof NativeWorldScreen screen) {
                screen.clickOriginal(245,515,false);stage=14;stageTicks=0;return;
            }
            if(stage==14&&state.localCash()<beforeWorldGuiCash&&state.objects().stream().filter(o->o.template().equals("AmericaVehicleDozer")).count()>beforeWorldGuiDozers) {
                checks.add("Separate original alpha GUI layer in Minecraft world accepted original production input and completed another native dozer");
                finish(mc,"PASS",null);return;
            }
        }catch(Throwable e){GeneralsMod.LOG.error("NATIVE_CLIENT_BENCH failed at stage {}",stage,e);finish(mc,"FAIL",e.toString());}
    }
    private static void finish(Minecraft mc,String result,String error) {
        try {
            var report=new LinkedHashMap<String,Object>();report.put("result",result);report.put("checks",checks);report.put("stage",stage);report.put("world",world);
            report.put("scope","Original engine IPC, original posed textured meshes in Minecraft world, world picking and surface terrain; first material pass only, NOT full game parity");
            if(worldView!=null&&worldView.scene!=null){report.put("sceneFrame",worldView.scene.frame());report.put("drawnVertices",worldView.drawnVertices);report.put("loadedTextures",worldView.textureCount());}
            if(session!=null)report.put("session",session.directory.toString());if(error!=null)report.put("error",error);
            Path path=Path.of(System.getProperty("generalsmc.evidence","../evidence"));Files.createDirectories(path);
            Files.writeString(path.resolve("native-client-tests.json"),GeneralsMod.JSON.toJson(report));
        }catch(Exception e){GeneralsMod.LOG.error("Unable to save native client report",e);}
        if(worldView!=null)worldView.close();if(terrainSync!=null)terrainSync.close();if(session!=null)session.close();System.setProperty("generalsmc.nativeBench","false");mc.stop();
    }
}
