package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.WorldScale;
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
import java.util.concurrent.CompletableFuture;

/** Chat Enter -> Fabric command -> controller, four tiles and original base geometry. */
public final class NativeWorldAccessBench {
    private static int ticks,stage,age,before;
    private static boolean invoking;
    public static boolean blocksInput(){return Boolean.getBoolean("generalsmc.nativeManualBench")&&!invoking;}
    private static double savedCameraX,savedCameraZ;
    private static boolean created,sawClock;
    private static final List<Integer> groupUnits=new ArrayList<>();
    private static double dragX,dragY,targetX,targetY,rotateInitial,cameraAngleBefore;
    private static long cameraRotationBefore;
    private static int walker,combatDozer,combatRanger,combatLauncher,rotateBuildingCount;
    private static long worldExplosionsBefore,dayBefore;
    private static int uiStepper,combatAvenger,combatGattling,combatTank,cloakedUnit;
    private static boolean sawCloaked;
    private static int rallyFactory,remainingBlocks,enemySelectionPasses;
    private static long acceptedDamageBefore;
    private static final Set<Long> tracerFrames=new HashSet<>();
    private static long nativeDamageBefore;
    private static java.util.UUID stealthZombie;
    private static java.util.UUID combatWither;
    private static java.util.UUID combatZombie;
    private static long combatOutboundBefore,combatInboundBefore;
    private static double combatMobHealth;
    private static int missileUnit,hoverUnit,hoverFactory;
    private static CompletableFuture<Void> setup;
    private static final List<String> checks=new ArrayList<>();
    private static long performanceStart,performanceCpu;
    private static int performanceFrame, fpsSum, fpsSamples;
    private static final Map<String,Object> performance=new LinkedHashMap<>();
    private static int dozerId,baseId,pauseFrame,barrierFrame,stopBefore;
    private static long terrainBefore;
    private static NativeWorldView savedView;
    private static net.minecraft.world.phys.Vec3 goal,stopPosition;
    private static String buildType;
    private static int buildingBefore,buildingId,rangerId,attackBefore;
    private static double healthBefore;
    private static String resourceKey;private static int resourceId,resourceCash,cashBefore,collectorId;
    private static CompletableFuture<Void> resourceSave;
    private static final Set<Integer> cursorFrames=new HashSet<>(),cursorPixels=new HashSet<>();
    private static int constructionPose;
    private static boolean constructionProgressSeen,constructionPoseChanged,constructionFar;
    private static final String world="GeneralsWorldAccessBench-"+System.currentTimeMillis();
    public static void tick(Minecraft mc) {
        if(!Boolean.getBoolean("generalsmc.nativeManualBench"))return;
        ticks++;age++;invoking=true;
        try {
            if(ticks%100==0)GeneralsMod.LOG.info("Native world oracle stage {}, age {}",stage,age);
            if(ticks>20*450)throw new IllegalStateException("Manual world entry timed out at stage "+stage);
            if(!created&&ticks>60&&(MenuScreens.active(mc.screen) instanceof TitleScreen||MenuScreens.active(mc.screen) instanceof AccessibilityOnboardingScreen)) {
                created=true;mc.options.guiScale().set(2);mc.options.pauseOnLostFocus=false;
                mc.createWorldOpenFlows().createFreshLevel(world,
                    new LevelSettings(world,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(FeatureFlags.DEFAULT_FLAGS),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1263217L,false,false),WorldPresets::createNormalWorldDimensions,MenuScreens.active(mc.screen));return;
            }
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
            if(stage==0) {
                setup=CompletableFuture.runAsync(()->{
                    var server=mc.getSingleplayerServer();var level=server.getLevel(GeneralsMod.ARENA);
                    for(int x=-33;x<=33;x++)for(int z=-33;z<=33;z++)level.setBlock(new BlockPos(x,63,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
                    server.getPlayerList().getPlayer(mc.player.getUUID()).teleportTo(level,.5,64,.5,Set.of(),0,60,false);
                },mc.getSingleplayerServer());stage=1;age=0;return;
            }
            if(stage==1&&setup.isDone()&&mc.level.dimension().equals(GeneralsMod.ARENA)&&age>50) {
                setup.join();
                var chat=new ChatScreen("/zh-native");mc.setScreen(chat);
                chat.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,0);
                if(MenuScreens.active(mc.screen)!=null)throw new IllegalStateException("Vanilla chat did not finish closing after Enter");
                checks.add("Submitted /zh-native through real ChatScreen Enter and Fabric command dispatch");stage=2;age=0;return;
            }
            if(stage==2&&age==2) {
                if(!(MenuScreens.active(mc.screen) instanceof NativeWorldScreen))throw new IllegalStateException("Chat cleanup closed the native RTS screen after /zh-native");
                checks.add("Native world screen survived vanilla chat cleanup on the next client tick");
            }
            var view=NativeWorldView.current;if(view==null){if(stage>2||(stage==2&&age>80&&MenuScreens.active(mc.screen) instanceof GeneralsFailureScreen))throw new IllegalStateException("Original engine session closed during oracle stage "+stage);return;}if(view.state==null||view.scene==null)return;
            var state=view.state;if(!state.error().isEmpty())throw new IllegalStateException(state.error());
            checkFarFog(view);
            if(stage==214){if(NativeTerrainMobBench.tick(mc,view,checks))finish(mc,"PASS",null);return;}
            if(stage==216){if(NativeFogAudioBench.tick(mc,view,checks))finish(mc,"PASS",null);return;}
            if(stage==217){if(NativeWorldPolishBench.tick(mc,view,checks))finish(mc,"PASS",null);return;}
            if(stage==218){if(NativeOilBench.tick(mc,view,checks))finish(mc,"PASS",null);return;}
            if(stage==215){if(NativeEffectsBench.tick(mc,view,checks))finish(mc,"PASS",null);return;}
            if(stage==2&&Boolean.getBoolean("generalsmc.nativePagedBench")&&state.terrainColumns()>=4096&&age>60){stage=100;age=0;return;}
            if(stage==2&&Boolean.getBoolean("generalsmc.nativeCombatBench")&&state.terrainColumns()>=4096&&age>60){stage=76;age=0;return;}
            if(stage==2&&state.terrainColumns()>=4096&&view.drawnVertices>0&&age>60) {
                checks.add("Manual controller synchronized all four tiles: 4096 real Minecraft surface columns");
                var cc=state.objects().stream().filter(o->o.template().equals("AmericaCommandCenter")&&o.owner()==state.localPlayerIndex()).findFirst().orElseThrow();
                if(Math.abs(cc.z())>.1)throw new IllegalStateException("Original base retained stock map height instead of Minecraft height: "+cc.z());
                if(!view.sceneActors().contains(cc.id()))throw new IllegalStateException("Original Command Center geometry missing");
                checks.add("Original Command Center height and posed geometry anchored to Minecraft surface through native Object placement");
                if(Boolean.getBoolean("generalsmc.nativeFogBench")){NativeFogAudioBench.begin(mc,view,checks);stage=216;age=0;return;}
                if(Boolean.getBoolean("generalsmc.nativePolishBench")){NativeWorldPolishBench.begin(mc,view,checks);stage=217;age=0;return;}
                if(Boolean.getBoolean("generalsmc.nativeOilBench")){NativeOilBench.begin(mc,view,checks);stage=218;age=0;return;}
                if(Boolean.getBoolean("generalsmc.nativeRegressionBench")){NativeTerrainMobBench.begin(mc,view,checks);stage=214;age=0;return;}
                if(Boolean.getBoolean("generalsmc.nativeEffectsBench")){NativeEffectsBench.begin(mc,view,checks);stage=215;age=0;return;}
                if(Boolean.getBoolean("generalsmc.nativeIssueBench")){beginIssueCases(mc,view);return;}
                Screenshot.grab(mc.gameDirectory,"10-original-base-in-minecraft.png",mc.getMainRenderTarget(),c->{});
                if(!(MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen))throw new IllegalStateException("Manual entry used an overlay screen");
                var p=screen.project(view.bounds(cc.id()).getCenter());screen.mouseClicked(p[0],p[1],0);screen.mouseReleased(p[0],p[1],0);
                before=(int)state.objects().stream().filter(o->o.template().equals("AmericaVehicleDozer")).count();stage=3;age=0;return;
            }
            if(stage==3&&age>40&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){
                var cc=state.objects().stream().filter(o->o.template().equals("AmericaCommandCenter")&&o.owner()==state.localPlayerIndex()).findFirst().orElseThrow();
                if(!state.selected().contains(cc.id()))throw new IllegalStateException("Original LMB did not select Command Center");
                checks.add("Original LMB selected Command Center through native picking and control bar");
                if(state.radarRevision()<1||state.radarSpan()!=128*WorldScale.UNITS_PER_BLOCK)throw new IllegalStateException("Original radar did not accept Minecraft terrain window");
                checks.add("Original radar uploaded the moving Minecraft terrain window while preserving native icons and fog");
                screen.clickCommand("Command_ConstructAmericaDozer",true);stage=4;age=0;return;
            }
            if(stage==4&&age>4&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){screen.clickCommand("Command_ConstructAmericaDozer",false);stage=5;age=0;return;}
            if(stage==5&&state.guiClockCount()>0&&state.guiClockPercent()>0&&state.guiClockPercent()<100&&!sawClock){sawClock=true;Screenshot.grab(mc.gameDirectory,"19-original-production-clock.png",mc.getMainRenderTarget(),c->{});}
            if(stage==5&&state.localCash()<10000&&state.objects().stream().filter(o->o.template().equals("AmericaVehicleDozer")&&view.sceneActors().contains(o.id())).count()>before) {
                if(!sawClock)throw new IllegalStateException("Original production clock was not drawn during native production");
                checks.add("Original push-button production clock rendered while the dozer was being produced, including both GUI matte passes");
                checks.add("Original GUI produced an additional native dozer whose original mesh appeared in the same Minecraft world");
                Screenshot.grab(mc.gameDirectory,"11-original-base-produced-dozer.png",mc.getMainRenderTarget(),c->{});
                performanceStart=System.nanoTime();performanceCpu=ProcessHandle.current().info().totalCpuDuration().orElse(java.time.Duration.ZERO).toNanos();performanceFrame=state.frame();stage=6;age=0;return;
            }
            if(stage==6) {
                if(age%20==0){fpsSum+=mc.getFps();fpsSamples++;}
                double seconds=(System.nanoTime()-performanceStart)/1e9;
                if(seconds>=10){performance.put("sampleSeconds",seconds);performance.put("javaCpuCores",(ProcessHandle.current().info().totalCpuDuration().orElse(java.time.Duration.ZERO).toNanos()-performanceCpu)/1e9/seconds);performance.put("nativeLogicFps",(state.frame()-performanceFrame)/seconds);performance.put("minecraftFps",fpsSamples==0?0:(double)fpsSum/fpsSamples);stage=7;age=0;}
            }
            if(stage==7&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen) {
                if(age==1){savedCameraX=view.cameraX;savedCameraZ=view.cameraZ;screen.mouseClicked(120,100,1);}
                if(age==3)screen.mouseDragged(180,130,1,60,30);
                if(age<20)return;
                if(view.cameraX==savedCameraX&&view.cameraZ==savedCameraZ||!state.scrolling()||screen.cursorType()!=3)throw new IllegalStateException("Original RMB scrolling/cursor did not reach host: scrolling="+state.scrolling()+" cursor="+screen.cursorType());
                screen.mouseReleased(180,130,1);
                view.cameraX=savedCameraX;view.cameraZ=savedCameraZ;checks.add("Held RMB uses original scrolling, directional arrow and continuous movement without issuing an order");
                savedView=view;pauseFrame=state.frame();screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F8,0,0);stage=8;age=0;return;
            }
            if(stage==8&&age>30) {
                if(MenuScreens.active(mc.screen)!=null||NativeWorldView.controlling()||view!=savedView||!view.session.alive())throw new IllegalStateException("Minecraft switch lost the native match or retained RTS camera");
                if(state.frame()-pauseFrame<10||view.scene==null||view.scene.frame()<=pauseFrame)throw new IllegalStateException("Native match or scene stopped in Minecraft mode");
                Screenshot.grab(mc.gameDirectory,"12-native-match-in-minecraft-mode.png",mc.getMainRenderTarget(),c->{});
                checks.add("F8 restored normal Minecraft controls/HUD while the same native match and visible scene continued updating");
                var chat=new ChatScreen("/zh-native");mc.setScreen(chat);chat.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,0);stage=9;age=0;return;
            }
            if(stage==9&&age>30) {
                if(!(MenuScreens.active(mc.screen) instanceof NativeWorldScreen)||view!=savedView||state.frame()<=pauseFrame+5)throw new IllegalStateException("Resume did not keep and unpause the original match");
                checks.add("Chat /zh-native resumed the same process, units, cash, selection and camera");
                baseId=state.objects().stream().filter(o->o.template().equals("AmericaCommandCenter")&&o.owner()==state.localPlayerIndex()).findFirst().orElseThrow().id();
                dozerId=state.objects().stream().filter(o->o.template().equals("AmericaVehicleDozer")&&o.owner()==state.localPlayerIndex()).mapToInt(o->o.id()).max().orElseThrow();
                goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-25.5,64,-18.5));view.selectionCommand=view.session.send("SELECT "+dozerId);view.session.move(goal.x,goal.y,goal.z);stage=10;age=0;return;
            }
            var dozer=state.objects().stream().filter(o->o.id()==dozerId).findFirst().orElse(null);
            if(stage==10&&dozer!=null&&distance(dozer,goal)<12) {
                terrainBefore=state.terrainRevision();
                setup=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);
                    for(int x=-23;x<=-11;x++)for(int z=-25;z<=-10;z++)for(int y=64;y<(x<=-20?65:66);y++)level.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),3);
                    level.setBlock(new BlockPos(-28,64,-28),Blocks.STONE_SLAB.defaultBlockState(),3);
                    var columns=local.generalsmc.nativebridge.TerrainExporter.sample(level,new local.generalsmc.nativebridge.TerrainExporter.Region(0,0,1,1,-28,64,-28));
                    if(columns.getFirst().height()!=.5f*WorldScale.UNITS_PER_BLOCK)throw new IllegalStateException("Slab collision surface was not exported at half-block height");
                },mc.getSingleplayerServer());stage=11;age=0;return;
            }
            if(stage==11&&setup.isDone()&&state.terrainRevision()>terrainBefore&&age>60) {
                setup.join();checks.add("Minecraft collision shapes export slab height 0.5 and one-block step surfaces");
                goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-21.5,65,-18.5));view.session.move(goal.x,goal.y,goal.z);stage=12;age=0;return;
            }
            if(stage==12&&dozer!=null&&distance(dozer,goal)<15&&Math.abs(dozer.z()-WorldScale.UNITS_PER_BLOCK)<2) {
                checks.add("Original dozer locomotor and physics climbed a full Minecraft block to height +1");
                goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-15.5,66,-18.5));view.session.move(goal.x,goal.y,goal.z);stage=13;age=0;return;
            }
            if(stage==13&&dozer!=null&&distance(dozer,goal)<15&&Math.abs(dozer.z()-2*WorldScale.UNITS_PER_BLOCK)<2) {
                checks.add("Original vehicle climbed a second one-block step and rested at height +2");view.cameraX=-14;view.cameraZ=-18;
                Screenshot.grab(mc.gameDirectory,"13-original-vehicle-on-minecraft-steps.png",mc.getMainRenderTarget(),c->{});
                terrainBefore=state.terrainRevision();
                setup=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);
                    for(int x=-8;x<=-2;x++)for(int z=-21;z<=-15;z++)if(x==-8||x==-2||z==-21||z==-15)for(int y=64;y<66;y++)level.setBlock(new BlockPos(x,y,z),Blocks.BRICKS.defaultBlockState(),3);
                },mc.getSingleplayerServer());stage=14;age=0;return;
            }
            if(stage==14&&setup.isDone()&&state.terrainRevision()>terrainBefore&&age>60) {
                setup.join();goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-5,64,-18));view.session.move(goal.x,goal.y,goal.z);barrierFrame=state.frame();stage=15;age=0;return;
            }
            if(stage==15&&state.frame()-barrierFrame>90&&dozer!=null) {
                if(distance(dozer,goal)<30)throw new IllegalStateException("Vehicle crossed a two-block barrier");
                checks.add("Two-block Minecraft barrier remained impassable to original native vehicle pathfinding");
                stopBefore=state.stopMessages();goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-24,64,-18));view.session.move(goal.x,goal.y,goal.z);stage=16;age=0;return;
            }
            if(stage==16&&age>15&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_S,0x1f,0);stage=17;age=0;return;}
            if(stage==17&&age>4&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_S,0x1f,0);stage=18;age=0;return;}
            if(stage==18&&age>20&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen) {
                if(state.stopMessages()<=stopBefore)throw new IllegalStateException("Original S binding did not dispatch MSG_DO_STOP");
                checks.add("Original S hotkey dispatched native stop through DirectInput queue and original MessageStream");
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,0x1d,0);screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_1,2,0);stage=19;age=0;return;
            }
            if(stage==19&&age>4&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_1,2,0);screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,0x1d,0);view.selectionCommand=view.session.send("SELECT "+baseId);stage=20;age=0;return;}
            if(stage==20&&age>20&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_1,2,0);stage=21;age=0;return;}
            if(stage==21&&age>4&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_1,2,0);stage=22;age=0;return;}
            if(stage==22&&age>20&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen) {
                if(state.selected()==null||!state.selected().contains(dozerId)||state.selected().contains(baseId))throw new IllegalStateException("Native Ctrl+1 / 1 control group did not restore vehicle selection: "+state.selected());
                checks.add("Native Ctrl+1 saved a vehicle group and 1 recalled it; host selection synchronized from original UI");
                view.cameraX=view.origin.world(dozer.x(),dozer.y(),dozer.z()).x;view.cameraZ=view.origin.world(dozer.x(),dozer.y(),dozer.z()).z;stage=23;age=0;return;
            }
            if(stage==23&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){
                if(age==15){var p=screen.project(view.bounds(dozerId).getCenter());screen.mouseMoved(p[0],p[1]);}
                if(age>20){cursorFrames.add(screen.cursorStep());cursorPixels.add(screen.cursorHash());}
            }
            if(stage==23&&age>80&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen) {
                if(screen.cursorSteps()<2||cursorFrames.size()<2||cursorPixels.size()<2)throw new IllegalStateException("Original select ANI did not animate: type="+screen.cursorType()+" steps="+screen.cursorSteps()+" distinct="+cursorPixels.size());
                checks.add("Original selection ANI advanced distinct timed frames and images without moving the mouse; original dozer control bar is selected");
                Screenshot.grab(mc.gameDirectory,"14-original-dozer-controls-and-cursor.png",mc.getMainRenderTarget(),c->{});
                screen.clickCommand("Command_ConstructAmericaPowerPlant",true);stage=24;age=0;return;
            }
            if(stage==24&&age>4&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){screen.clickCommand("Command_ConstructAmericaPowerPlant",false);stage=25;age=0;return;}
            if(stage==25&&age>25) {
                if(!state.pendingBuild()&&(state.pendingCommand()==null||state.pendingCommand().isEmpty()))throw new IllegalStateException("Dozer GUI ability did not enter original command mode; button="+state.pointerCommand());
                checks.add("Original dozer GUI ability entered native world-target command mode");
                buildType=state.pendingBuildType();buildingBefore=(int)state.objects().stream().filter(o->o.template().equals(buildType)).count();
                view.cameraX=-23;view.cameraZ=4;view.zoom=35;stage=26;age=0;return;
            }
            if(stage==26&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){
                var p=screen.project(new net.minecraft.world.phys.Vec3(-25,64,4));
                if(age==6)screen.mouseMoved(p[0],p[1]);
                if(age>10){cursorFrames.add(screen.cursorStep());cursorPixels.add(screen.cursorHash());}
                if(age>65){
                    if(view.scene.batches().stream().noneMatch(b->b.actor()<0))throw new IllegalStateException("Original building ghost geometry missing");
                    checks.add("Original building placement ghost exported in the Minecraft world; original hidden placement cursor has no invented replacement");
                    screen.mouseClicked(p[0],p[1],0);stage=27;age=0;return;
                }
            }
            if(stage==27&&age>4&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){var p=screen.project(new net.minecraft.world.phys.Vec3(-25,64,4));screen.mouseReleased(p[0],p[1],0);stage=28;age=0;return;}
            if(stage==28) {
                var building=state.objects().stream().filter(o->o.template().equals(buildType)).findFirst().orElse(null);
                if(building!=null&&building.constructionPercent()>20&&building.constructionPercent()<80) {
                    int hash=0;for(var batch:view.scene.batches())if(batch.actor()==building.id())hash=31*hash+Arrays.hashCode(batch.vertices());
                    if(constructionPose==0)constructionPose=hash;else if(hash!=constructionPose)constructionPoseChanged=true;
                    if(!constructionProgressSeen&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){constructionProgressSeen=true;screen.mouseMoved(20,20);Screenshot.grab(mc.gameDirectory,"16-original-construction-progress.png",mc.getMainRenderTarget(),c->{});}
                }
                if(building!=null&&constructionPoseChanged&&!constructionFar&&building.constructionPercent()>50&&building.constructionPercent()<80){constructionFar=true;view.cameraX=300;view.cameraZ=300;var player=mc.player.getUUID();mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayer(player).teleportTo(mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA),300.5,64,300.5,Set.of(),0,60,false));}
                if(building!=null&&building.constructionPercent()==-1){if(!constructionFar)throw new IllegalStateException("Far-camera construction checkpoint was not exercised");view.cameraX=-23;view.cameraZ=4;var player=mc.player.getUUID();mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayer(player).teleportTo(mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA),.5,64,.5,Set.of(),0,60,false));checks.add("A player-ordered original Dozer finishes its ongoing building after both the camera and the real Minecraft player move more than 400 blocks away");if(!constructionProgressSeen||!constructionPoseChanged)throw new IllegalStateException("Construction animation did not progress without hover");checks.add("Original construction mesh/animation continued changing without hover while native construction percentage advanced");stage=29;age=0;return;}
            }
            if(stage==29&&age>40) {
                checks.add("Original dozer construction ability accepted a Minecraft world target, paid the original price, completed construction and exported the original building geometry");
                Screenshot.grab(mc.gameDirectory,"15-original-building-placed-on-minecraft.png",mc.getMainRenderTarget(),c->{});
                var building=state.objects().stream().filter(o->o.template().equals(buildType)&&o.constructionPercent()==-1).findFirst().orElseThrow();buildingId=building.id();
                view.session.send("TEST_SPAWN AmericaInfantryRanger "+(building.x()+70)+" "+building.y()+" "+building.z());stage=30;age=0;return;
            }
            if(stage==30&&age>30&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){
                var ranger=state.objects().stream().filter(o->o.template().equals("AmericaInfantryRanger")&&o.owner()==state.localPlayerIndex()).findFirst().orElseThrow();rangerId=ranger.id();
                view.selectionCommand=view.session.send("SELECT "+rangerId);screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,0x1d,0);stage=31;age=0;return;
            }
            if(stage==31&&age>10&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){
                if(!state.forceAttack())throw new IllegalStateException("Ctrl did not activate original force attack mode");
                var b=state.objects().stream().filter(o->o.id()==buildingId).findFirst().orElseThrow();healthBefore=b.health();attackBefore=state.attackMessages();
                var p=unitPixel(screen,view,buildingId);screen.mouseMoved(p[0],p[1]);screen.mouseClicked(p[0],p[1],0);stage=32;age=0;return;
            }
            if(stage==32&&age>3&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){var p=unitPixel(screen,view,buildingId);screen.mouseReleased(p[0],p[1],0);stage=33;age=0;return;}
            if(stage==33&&age>10&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,0x1d,0);stage=34;age=0;return;}
            if(stage==34){
                var b=state.objects().stream().filter(o->o.id()==buildingId).findFirst().orElseThrow();
                if(b.health()<healthBefore&&state.attackMessages()>attackBefore){
                    checks.add("Ctrl+LMB activated original force attack, dispatched original attack and the Ranger weapon damaged a friendly building");
                    view.session.send("SELECT "+rangerId);view.session.key(0x1f,true);view.session.key(0x1f,false);view.session.send("TEST_DAMAGE "+buildingId+" 200");stage=35;age=0;return;
                }
                if(age>200)throw new IllegalStateException("Ctrl+LMB did not fire: force="+state.forceAttack()+" attack messages="+state.attackMessages()+" health="+b.health()+" before="+healthBefore);
            }
            if(stage==35&&age>30){var b=state.objects().stream().filter(o->o.id()==buildingId).findFirst().orElseThrow();healthBefore=b.health();view.selectionCommand=view.session.send("SELECT "+dozerId);stage=36;age=0;return;}
            if(stage==36&&age>15&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){var p=unitPixel(screen,view,buildingId);screen.mouseMoved(p[0],p[1]);screen.mouseClicked(p[0],p[1],0);stage=37;age=0;return;}
            if(stage==37&&age>3&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){var p=unitPixel(screen,view,buildingId);screen.mouseReleased(p[0],p[1],0);stage=38;age=0;return;}
            if(stage==38){var b=state.objects().stream().filter(o->o.id()==buildingId).findFirst().orElseThrow();if(b.health()>healthBefore+10){checks.add("LMB on a damaged friendly building issued original contextual dozer repair and restored real native health");Screenshot.grab(mc.gameDirectory,"17-original-lmb-repair.png",mc.getMainRenderTarget(),c->{});stage=39;age=0;return;}if(age>350)throw new IllegalStateException("Original contextual LMB repair did not restore health");}
            if(stage==39&&age>20){
                if(state.resources().stream().noneMatch(r->r.cash()>0)){if(age>400)throw new IllegalStateException("No seeded resource footprint reached the native engine");return;}
                var site=state.resources().stream().filter(r->r.cash()>0).min(Comparator.comparingDouble(r->{var o=state.objects().stream().filter(v->v.id()==r.id()).findFirst().orElseThrow();return Math.hypot(o.x()-1000,o.y()-580);})).orElseThrow();
                resourceKey=site.key();resourceId=site.id();resourceCash=site.cash();cashBefore=state.localCash();
                var dock=state.objects().stream().filter(o->o.id()==resourceId).findFirst().orElseThrow();
                if(!dock.template().equals("SupplyDock")||dock.owner()!=0||Math.abs(dock.z())>.1)throw new IllegalStateException("Resource site is not the original neutral structure on Minecraft terrain");
                checks.add("Seeded Minecraft resource generation created original neutral SupplyDock objects and original INI stock without replacing blocks");
                view.session.send("TEST_SPAWN AmericaSupplyCenter "+(dock.x()+160)+" "+(dock.y()+100)+" "+dock.z());
                view.session.send("TEST_SPAWN AmericaVehicleChinook "+(dock.x()+210)+" "+(dock.y()+150)+" "+(dock.z()+50));
                view.cameraX=view.origin.x()+dock.x()/WorldScale.UNITS_PER_BLOCK;view.cameraZ=view.origin.z()+dock.y()/WorldScale.UNITS_PER_BLOCK;view.zoom=35;stage=40;age=0;return;
            }
            if(stage==40&&age>30){var collector=state.objects().stream().filter(o->o.template().equals("AmericaVehicleChinook")&&o.owner()==state.localPlayerIndex()).findFirst().orElseThrow();collectorId=collector.id();view.selectionCommand=view.session.send("SELECT "+collectorId);stage=41;age=0;return;}
            if(stage==41&&age>20&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){var p=screen.project(view.bounds(resourceId).getCenter());screen.mouseMoved(p[0],p[1]);screen.mouseClicked(p[0],p[1],0);stage=42;age=0;return;}
            if(stage==42&&age>3&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){var p=screen.project(view.bounds(resourceId).getCenter());screen.mouseReleased(p[0],p[1],0);stage=43;age=0;return;}
            if(stage==43){
                var resource=state.resources().stream().filter(r->r.key().equals(resourceKey)).findFirst().orElseThrow();
                if(resource.cash()<resourceCash&&state.localCash()>cashBefore){
                    checks.add("Original Chinook collected boxes from a generated Minecraft site and unloaded through original supply economy, increasing native player money");
                    Screenshot.grab(mc.gameDirectory,"18-original-generated-supply-harvest.png",mc.getMainRenderTarget(),c->{});
                    view.session.send("SELECT "+collectorId);view.session.key(0x1f,true);view.session.key(0x1f,false);resourceCash=resource.cash();stage=44;age=0;return;
                }
                if(age>1200)throw new IllegalStateException("Original generated warehouse harvest failed: stock="+resource.cash()+" cash="+state.localCash());
            }
            if(stage==44&&age>40){
                int remaining=state.resources().stream().filter(r->r.key().equals(resourceKey)).findFirst().orElseThrow().cash();
                resourceSave=CompletableFuture.runAsync(()->{
                    var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var store=local.generalsmc.nativebridge.WorldResourceStore.get(level);
                    var tag=store.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());var nodes=tag.getList("nodes",10);boolean found=false;
                    for(int i=0;i<nodes.size();i++){var n=nodes.getCompound(i);if(n.getString("key").equals(resourceKey)){found=true;if(n.getInt("cash")!=remaining)throw new IllegalStateException("Minecraft SavedData did not receive harvested stock");}}
                    if(!found)throw new IllegalStateException("Generated resource site not persisted");level.getDataStorage().saveAndJoin();
                    try{var disk=level.getDataStorage().readTagFromDisk("generals_resources",null,net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
                        var saved=disk.getCompound("data").getList("nodes",10);boolean onDisk=false;
                        for(int i=0;i<saved.size();i++){var n=saved.getCompound(i);if(n.getString("key").equals(resourceKey)){onDisk=true;if(n.getInt("cash")!=remaining)throw new IllegalStateException("Saved resource stock changed on disk");}}
                        if(!onDisk)throw new IllegalStateException("Resource node absent from Minecraft data file");
                    }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
                },mc.getSingleplayerServer());stage=45;age=0;return;
            }
            if(stage==45&&resourceSave.isDone()){
                resourceSave.join();checks.add("Generated sites and remaining original resource stock persisted in the Minecraft dimension SavedData and were read back from disk after real harvesting");
                setup=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=128;x<136;x++)for(int z=128;z<136;z++){level.setChunkForced(x,z,true);level.getChunkAt(new BlockPos(x*16,64,z*16));}},mc.getSingleplayerServer());stage=46;age=0;return;
            }
            if(stage==46&&setup.isDone()&&age>80){
                setup.join();resourceSave=CompletableFuture.runAsync(()->{
                    var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var store=local.generalsmc.nativebridge.WorldResourceStore.get(level);
                    var nodes=store.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()).getList("nodes",10);boolean far=false;
                    for(int i=0;i<nodes.size();i++){var n=nodes.getCompound(i);if(n.getString("key").equals("s2_16_16")&&n.getInt("x")>=2048&&n.getInt("z")>=2048)far=true;}
                    if(!far)throw new IllegalStateException("Chunk generation did not create a persistent resource site beyond the native RTS grid");
                    var p=new BlockPos(2080,64,2080);for(int y=64;y<=69;y++)level.setBlock(p.atY(y),Blocks.OAK_LOG.defaultBlockState(),3);level.setBlock(p.atY(70),Blocks.OAK_LEAVES.defaultBlockState(),3);
                    var sample=local.generalsmc.nativebridge.TerrainExporter.sample(level,new local.generalsmc.nativebridge.TerrainExporter.Region(0,0,1,1,2080,64,2080)).getFirst();
                    if(Math.abs(sample.height())>.01||sample.type()!=6||sample.clearance()!=0||sample.obstacles().length<2)throw new IllegalStateException("Forest collision used tree height as walkable ground");
                    level.getDataStorage().saveAndJoin();
                },mc.getSingleplayerServer());stage=47;age=0;return;
            }
            if(stage==47&&resourceSave.isDone()){resourceSave.join();checks.add("Minecraft CHUNK_LOAD generated a persistent supply site at world coordinates beyond 2048 blocks, independently of the finite native grid");checks.add("Actual Minecraft oak trunk/leaves export ground height separately and block movement/build clearance instead of making units stand on treetops");mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=128;x<136;x++)for(int z=128;z<136;z++)level.setChunkForced(x,z,false);});stage=48;age=0;return;}
            if(stage==48){
                var dock=state.objects().stream().filter(o->o.id()==resourceId).findFirst().orElseThrow();targetX=dock.x()-150;targetY=dock.y()+150;
                view.session.send("TEST_SPAWN AmericaInfantryRanger "+targetX+" "+targetY+" 0");view.session.send("TEST_SPAWN AmericaInfantryRanger "+(targetX+30)+" "+targetY+" 0");view.session.send("TEST_SPAWN AmericaInfantryMissileDefender "+(targetX+90)+" "+targetY+" 0");
                view.cameraX=view.origin.x()+targetX/WorldScale.UNITS_PER_BLOCK;view.cameraZ=view.origin.z()+targetY/WorldScale.UNITS_PER_BLOCK;view.zoom=25;stage=49;age=0;return;
            }
            if(stage==49&&age>35){
                for(var o:state.objects())if(o.template().equals("AmericaInfantryRanger")&&Math.abs(o.x()-targetX)<35&&Math.abs(o.y()-targetY)<10)groupUnits.add(o.id());
                if(groupUnits.size()!=2)throw new IllegalStateException("Interaction fixture did not create two Rangers");
                missileUnit=state.objects().stream().filter(o->o.template().equals("AmericaInfantryMissileDefender")&&Math.abs(o.x()-targetX-90)<10).findFirst().orElseThrow().id();
                var missile=state.objects().stream().filter(o->o.id()==missileUnit).findFirst().orElseThrow();if(Math.abs(missile.weaponRange()-(175-WorldScale.PATH_CELL_UNITS*.25))>.01)throw new IllegalStateException("Missile defender current range differs from original engine INI175 minus current quarter-cell: "+missile.weaponRange());
                checks.add("Original current MissileDefender range derives from owned INI175 minus the original quarter-cell formula applied to current path cell size; original geometry radii remain part of the attack distance");
                view.session.select(0);stage=50;age=0;return;
            }
            if(stage==50&&age>10&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){
                var p=screen.project(view.origin.world(targetX-35,targetY-30,0));var q=screen.project(view.origin.world(targetX+60,targetY+30,0));dragX=Math.max(p[0],q[0]);dragY=Math.max(p[1],q[1]);screen.mouseMoved(Math.min(p[0],q[0]),Math.min(p[1],q[1]));screen.mouseClicked(Math.min(p[0],q[0]),Math.min(p[1],q[1]),0);stage=51;age=0;return;
            }
            if(stage==51&&age>5&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){screen.mouseDragged(dragX,dragY,0,80,60);stage=52;age=0;return;}
            if(stage==52&&age>5&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){Screenshot.grab(mc.gameDirectory,"20-original-rectangle-selection.png",mc.getMainRenderTarget(),c->{});screen.mouseReleased(dragX,dragY,0);stage=53;age=0;return;}
            if(stage==53&&age>15){if(!state.selected().containsAll(groupUnits))throw new IllegalStateException("Original rectangle selection did not select both Rangers: "+state.selected());checks.add("Original drag rectangle uses Minecraft camera projection and selects both native Rangers through the original callbacks");view.session.select(0);stage=54;age=0;return;}
            if(stage==54&&age>10&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){var p=unitPixel(screen,view,groupUnits.getFirst());dragX=p[0];dragY=p[1];screen.mouseMoved(dragX,dragY);screen.mouseClicked(dragX,dragY,0);screen.mouseReleased(dragX,dragY,0);stage=55;age=0;return;}
            if(stage==55&&age>2&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){if(!state.selected().contains(groupUnits.getFirst())){if(age<8)return;throw new IllegalStateException("First unit click failed before double click: "+state.selected());}screen.mouseClicked(dragX,dragY,0);screen.mouseReleased(dragX,dragY,0);stage=56;age=0;return;}
            if(stage==56&&age>15&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){if(!state.selected().containsAll(groupUnits))throw new IllegalStateException("Original double click failed similar-unit selection: "+state.selected());checks.add("Double click emits original Win32 double-click input and selects visible Rangers of the same type");screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_G,34,0);screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_G,34,0);var p=screen.project(view.origin.world(targetX,targetY+40,0));screen.mouseMoved(p[0],p[1]);stage=57;age=0;return;}
            if(stage==57&&age>20&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){if(state.exportedDecals()<1)throw new IllegalStateException("Original guard radius decal was not exported after G");checks.add("G guard command exports the original textured pulsing radius decal conformed to Minecraft ground");Screenshot.grab(mc.gameDirectory,"21-original-guard-radius.png",mc.getMainRenderTarget(),c->{});screen.mouseClicked(dragX,dragY,1);screen.mouseReleased(dragX,dragY,1);view.session.send("TEST_FX WeaponFX_RocketBuggyMissileDetonation "+targetX+" "+(targetY+30)+" 10");stage=58;age=0;return;}
            if(stage==58&&state.testFXParticles()>0&&age>2){checks.add("Original missile detonation FX emitted original textured particles with original size/color/alpha and host-camera billboards");Screenshot.grab(mc.gameDirectory,"22-original-missile-explosion.png",mc.getMainRenderTarget(),c->{});view.session.select(dozerId);stage=59;age=0;return;}
            if(stage==58&&age>60)throw new IllegalStateException("Original explosion particle export missing");
            if(stage==59&&age>20&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){if(state.scrolling())throw new IllegalStateException("Short RMB command cancellation left camera scrolling");checks.add("Short RMB command cancellation releases original SCROLL_RMB mode instead of leaving camera running");view.cameraX=view.origin.x()+targetX/WorldScale.UNITS_PER_BLOCK;view.cameraZ=view.origin.z()+targetY/WorldScale.UNITS_PER_BLOCK;screen.clickCommand("Command_ConstructAmericaPowerPlant",true);stage=60;age=0;return;}
            if(stage==60&&age>4&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){screen.clickCommand("Command_ConstructAmericaPowerPlant",false);stage=61;age=0;return;}
            if(stage==61&&state.pendingBuild()&&age>30&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){var p=screen.project(view.origin.world(targetX-70,targetY-30,0));dragX=p[0];dragY=p[1];screen.mouseMoved(dragX,dragY);screen.mouseClicked(dragX,dragY,0);stage=62;age=0;return;}
            if(stage==62&&age>10&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){if(!state.placementAnchored())throw new IllegalStateException("Original building anchor not active");rotateInitial=state.placementAngle();rotateBuildingCount=(int)state.objects().stream().filter(o->o.template().equals("AmericaPowerPlant")).count();screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,29,0);var p=screen.project(view.origin.world(targetX-150,targetY+10,0));screen.mouseDragged(p[0],p[1],0,p[0]-dragX,p[1]-dragY);stage=63;age=0;return;}
            if(stage==63&&age>15&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){double expected=Math.atan2(40,-80);if(Math.abs(Math.atan2(Math.sin(state.placementAngle()-expected),Math.cos(state.placementAngle()-expected)))>.06)throw new IllegalStateException("Original free building rotation did not follow the two terrain points: "+state.placementAngle()+" expected "+expected);if(Math.abs(state.placementAngle()-rotateInitial)<.2)throw new IllegalStateException("Building did not rotate");checks.add("Original building anchor and Locater arrow follow two distinct ground points; free rotation matches supplied EA source, including Ctrl without the fork's 45-degree snap");Screenshot.grab(mc.gameDirectory,"23-original-building-rotation.png",mc.getMainRenderTarget(),c->{});screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,29,0);screen.mouseClicked(dragX,dragY,1);screen.mouseReleased(dragX,dragY,1);screen.mouseReleased(dragX,dragY,0);stage=64;age=0;return;}
            if(stage==64&&age>20){if(state.pendingBuild())throw new IllegalStateException("Original RMB did not cancel rotated building placement");if(state.objects().stream().filter(o->o.template().equals("AmericaPowerPlant")).count()!=rotateBuildingCount)throw new IllegalStateException("RMB cancellation followed by LMB release placed a building");checks.add("Original RMB cancellation closes anchored building placement");view.session.send("TEST_SPAWN AmericaWarFactory "+targetX+" "+(targetY-110)+" 0");view.cameraX=view.origin.x()+targetX/WorldScale.UNITS_PER_BLOCK;view.cameraZ=view.origin.z()+(targetY-110)/WorldScale.UNITS_PER_BLOCK;stage=65;age=0;return;}
            if(stage==65&&age>30){hoverFactory=state.objects().stream().filter(o->o.template().equals("AmericaWarFactory")&&Math.abs(o.x()-targetX)<10).findFirst().orElseThrow().id();var box=view.bounds(hoverFactory);if(box==null)return;var p=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(box.minX+(box.maxX-box.minX)*.04,view.origin.y(),box.maxZ-(box.maxZ-box.minZ)*.04));view.session.send("TEST_SPAWN AmericaInfantryRanger "+p.x+" "+p.y+" 0");goal=p;stage=66;age=0;return;}
            if(stage==66&&age>20&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){hoverUnit=state.objects().stream().filter(o->o.template().equals("AmericaInfantryRanger")&&distance(o,goal)<2).findFirst().orElseThrow().id();var p=unitPixel(screen,view,hoverUnit);screen.mouseMoved(p[0],p[1]);screen.mouseClicked(p[0],p[1],0);screen.mouseReleased(p[0],p[1],0);stage=67;age=0;return;}
            if(stage==67&&age>20){if(!state.selected().contains(hoverUnit)||state.selected().contains(hoverFactory))throw new IllegalStateException("Large factory bounds stole the Ranger pick: "+state.selected());checks.add("Triangle picking selects a Ranger inside a factory bounding box instead of stealing the hit for the building");Screenshot.grab(mc.gameDirectory,"24-original-unit-pick-near-factory.png",mc.getMainRenderTarget(),c->{});
                var fixture=System.getProperty("generalsmc.nativeSceneFixture");if(fixture!=null){var replay=NativeWorldView.decode(Path.of(fixture));if(replay.batches().isEmpty())throw new IllegalStateException("Crash scene replay empty");for(var b:replay.batches())for(float v:b.vertices())if(!Float.isFinite(v))throw new IllegalStateException("Crash replay still contains NaN");checks.add("Replayed the user's actual failed protocol3 scene containing two NaN UVs; decoded geometry remains finite and the session stays active");}
                stage=68;age=0;return;}
            if(stage==68&&age>25&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){cameraRotationBefore=state.rotationSerial();cameraAngleBefore=state.cameraAngle();screen.mouseClicked(180,120,2);stage=69;age=0;return;}
            if(stage==69&&age>4&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){screen.mouseDragged(240,120,2,60,0);stage=70;age=0;return;}
            if(stage==70&&age>25&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){if(state.rotationSerial()<=cameraRotationBefore||Math.abs(state.cameraAngle()-cameraAngleBefore)<.05)throw new IllegalStateException("Original middle-button camera rotation did not reach Minecraft");screen.mouseReleased(240,120,2);checks.add("Held middle mouse rotated the original tactical view and Minecraft camera, without desktop input");stage=71;age=0;return;}
            if(stage==71&&age>10&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){screen.mouseClicked(180,120,2);screen.mouseReleased(180,120,2);stage=72;age=0;return;}
            if(stage==72&&age>25){if(Math.abs(state.cameraAngle())>.01)throw new IllegalStateException("Original middle click did not reset camera angle: "+state.cameraAngle());checks.add("Original short middle click reset the Minecraft tactical camera angle");goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-15.5,66,-18.5));view.session.send("TEST_SPAWN AmericaInfantryRanger "+goal.x+" "+goal.y+" "+goal.z);stage=73;age=0;return;}
            if(stage==73&&age>30){walker=state.objects().stream().filter(o->o.template().equals("AmericaInfantryRanger")&&distance(o,goal)<2).findFirst().orElseThrow().id();var box=view.bounds(walker);if(box==null)return;performance.put("worldUnitsPerBlock",WorldScale.UNITS_PER_BLOCK);performance.put("infantryHeightBlocks",box.maxY-box.minY);goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-21.5,65,-18.5));view.session.select(walker);view.session.move(goal.x,goal.y,goal.z);stage=74;age=0;return;}
            if(stage==74){var o=state.objects().stream().filter(v->v.id()==walker).findFirst().orElseThrow();if(distance(o,goal)<3*WorldScale.UNITS_PER_BLOCK&&Math.abs(o.z()-WorldScale.UNITS_PER_BLOCK)<1){goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-25.5,64,-18.5));view.session.move(goal.x,goal.y,goal.z);stage=75;age=0;return;}if(age>300)throw new IllegalStateException("Infantry did not descend first block: "+o);}
            if(stage==75){var o=state.objects().stream().filter(v->v.id()==walker).findFirst().orElseThrow();if(distance(o,goal)<3*WorldScale.UNITS_PER_BLOCK&&Math.abs(o.z())<1){checks.add("Original infantry locomotor descended two Minecraft one-block steps and rested on the outdoor ground");stage=76;age=0;return;}if(age>300)throw new IllegalStateException("Infantry did not descend second block: "+o);}
            if(stage==100){if(!state.pagedWorld())throw new IllegalStateException("Sparse original grids are not enabled");savedCameraX=view.cameraX;savedCameraZ=view.cameraZ;view.cameraX=2048.5;view.cameraZ=2048.5;stage=101;age=0;return;}
            if(stage==101){if(age>140&&mc.level.hasChunkAt(new BlockPos(2048,64,2048))&&state.radarMinX()>10000&&state.terrainColumns()>4096){goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(2048.5,64,2048.5));view.session.send("TEST_SPAWN AmericaInfantryRanger "+goal.x+" "+goal.y+" 0");checks.add("RTS camera loaded actual Minecraft chunks beyond 2048 blocks without teleporting the commander; radar window moved to those same world coordinates");stage=102;age=0;return;}if(age>600)throw new IllegalStateException("Remote camera chunks or sparse terrain did not load");}
            if(stage==102&&age>30){walker=state.objects().stream().filter(o->o.template().equals("AmericaInfantryRanger")&&distance(o,goal)<3).findFirst().orElseThrow().id();goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(2060.5,64,2048.5));view.session.select(walker);view.session.move(goal.x,goal.y,goal.z);stage=103;age=0;return;}
            if(stage==103){var actor=state.objects().stream().filter(o->o.id()==walker).findFirst().orElseThrow();if(distance(actor,goal)<2*WorldScale.UNITS_PER_BLOCK&&Math.abs(actor.z())<1){if(!view.sceneActors().contains(walker)||state.partitionPages()==0)throw new IllegalStateException("Remote original model or spatial partition missing");checks.add("Original infantry A* and locomotor moved across sparse native path cells and spatial partitions outside the stock map, on real Minecraft ground");setup=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var cow=new net.minecraft.world.entity.animal.Cow(net.minecraft.world.entity.EntityType.COW,level);cow.setNoAi(true);cow.setPos(2048.5,64,2048.5);combatZombie=cow.getUUID();level.addFreshEntity(cow);},mc.getSingleplayerServer());stage=108;age=0;return;}if(age>400)throw new IllegalStateException("Sparse positive-coordinate original path failed: "+actor);}
            if(stage==108&&setup.isDone()&&age>40&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){setup.join();net.minecraft.world.entity.Entity mob=null;for(var entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(combatZombie)){mob=entity;break;}int id=NativeRtsController.combat.nativeMobId(combatZombie);if(mob!=null&&id!=0){var pixel=screen.project(mob.getBoundingBox().getCenter());if(screen.actorAt(pixel[0],pixel[1])!=id)throw new IllegalStateException("Remote Minecraft cow did not map to original target picking");checks.add("A real Minecraft cow beyond 2048 blocks received vanilla entity packets at the RTS camera and mapped to an original native target without teleporting the player");Screenshot.grab(mc.gameDirectory,"25-distant-original-unit-minecraft-mob.png",mc.getMainRenderTarget(),c->{});view.cameraX=-2048.5;view.cameraZ=-2048.5;stage=104;age=0;return;}if(age>200)throw new IllegalStateException("Remote Minecraft cow or original mob target missing at the RTS camera");}
            if(stage==104&&age>140&&mc.level.hasChunkAt(new BlockPos(-2049,64,-2049))){goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-2048.5,64,-2048.5));view.session.send("TEST_SPAWN AmericaInfantryRanger "+goal.x+" "+goal.y+" 0");stage=105;age=0;return;}
            if(stage==105&&age>30){walker=state.objects().stream().filter(o->o.template().equals("AmericaInfantryRanger")&&distance(o,goal)<3).findFirst().orElseThrow().id();goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-2060.5,64,-2048.5));view.session.select(walker);view.session.move(goal.x,goal.y,goal.z);stage=106;age=0;return;}
            if(stage==106){var actor=state.objects().stream().filter(o->o.id()==walker).findFirst().orElseThrow();if(distance(actor,goal)<3*WorldScale.UNITS_PER_BLOCK&&Math.hypot(actor.x()-actor.goalX(),actor.y()-actor.goalY())<3&&Math.abs(actor.z())<1){checks.add("Original ground navigation also worked on signed negative world coordinates, without unsigned-cell wraparound or replacing Minecraft terrain");view.cameraX=savedCameraX;view.cameraZ=savedCameraZ;NativeRtsController.toggle();stage=107;age=0;return;}if(age>400)throw new IllegalStateException("Sparse negative-coordinate original path failed: "+actor);}
            if(stage==107&&age>60){if(!mc.level.hasChunkAt(mc.player.blockPosition()))throw new IllegalStateException("F8 did not restore player chunk subscription");if(Math.abs(mc.player.getY()-64)>.2)throw new IllegalStateException("Remote RTS camera changed commander height: "+mc.player.position());checks.add("F8 restored the original player's local chunks and body position after distant camera exploration; both distant native units remained in simulation");finish(mc,"PASS",null);return;}
            if(stage==76){

                goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-28.5,64,28.5));view.session.send("TEST_SPAWN AmericaVehicleDozer "+goal.x+" "+goal.y+" 0");combatOutboundBefore=NativeRtsController.combat.outgoingMobDamage;combatInboundBefore=NativeRtsController.combat.incomingNativeDamage;stage=77;age=0;return;
            }
            if(stage==77&&age>30){combatDozer=state.objects().stream().filter(o->o.template().equals("AmericaVehicleDozer")&&distance(o,goal)<2).findFirst().orElseThrow().id();healthBefore=state.objects().stream().filter(o->o.id()==combatDozer).findFirst().orElseThrow().health();if(NativeRtsController.combat.actor(combatDozer)==null)return;setup=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);level.getServer().setDifficulty(Difficulty.NORMAL,true);var zombie=new net.minecraft.world.entity.monster.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE,level);zombie.setPos(-25.5,64,28.5);zombie.setPersistenceRequired();zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);zombie.setHealth(200);zombie.setRemainingFireTicks(0);zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));combatZombie=zombie.getUUID();level.addFreshEntity(zombie);},mc.getSingleplayerServer());stage=78;age=0;return;}
            if(stage==78){if(age%50==0)mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var entity=level.getEntity(combatZombie);var proxy=NativeRtsController.combat.actor(combatDozer);if(entity instanceof net.minecraft.world.entity.Mob mob)GeneralsMod.LOG.info("Combat oracle zombie {} target {} proxy {} hp {} LOS {} melee {} damage {}",mob.position(),mob.getTarget()==null?null:mob.getTarget().getId(),proxy==null?null:proxy.getId()+":"+proxy.nativeId+":"+proxy.position(),proxy==null?null:proxy.getHealth(),proxy!=null&&mob.getSensing().hasLineOfSight(proxy),proxy!=null&&mob.isWithinMeleeAttackRange(proxy),NativeRtsController.combat.outgoingMobDamage);});var o=state.objects().stream().filter(v->v.id()==combatDozer).findFirst().orElse(null);if(o!=null&&o.health()<healthBefore&&NativeRtsController.combat.outgoingMobDamage>combatOutboundBefore){checks.add("Actual vanilla Zombie selected a native dozer server presence, attacked it and reduced original native body health");mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var entity=level.getEntity(combatZombie);if(entity instanceof net.minecraft.world.entity.Mob mob){mob.setNoAi(true);mob.setPos(-20.5,64,28.5);}});goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-24.5,64,28.5));view.session.send("TEST_SPAWN AmericaInfantryRanger "+goal.x+" "+goal.y+" 0");stage=79;age=0;return;}if(age>300)throw new IllegalStateException("Vanilla Zombie did not damage native dozer: "+o);}
            if(stage==79&&age>30&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){combatRanger=state.objects().stream().filter(o->o.template().equals("AmericaInfantryRanger")&&distance(o,goal)<2).findFirst().orElseThrow().id();view.session.select(combatRanger);view.cameraX=-26;view.cameraZ=28;view.zoom=20;stage=80;age=0;return;}
            if(stage==80&&age>25&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){net.minecraft.world.entity.Entity zombie=null;for(var entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(combatZombie)){zombie=entity;break;}if(zombie==null)throw new IllegalStateException("Zombie combat fixture disappeared");var p=screen.project(zombie.getBoundingBox().getCenter());int id=NativeRtsController.combat.nativeMobId(combatZombie);if(id==0||screen.actorAt(p[0],p[1])!=id)throw new IllegalStateException("Minecraft mob ray did not map to original attack target");combatMobHealth=state.objects().stream().filter(o->o.id()==id).findFirst().orElseThrow().health();attackBefore=state.attackMessages();combatInboundBefore=NativeRtsController.combat.incomingNativeDamage;screen.mouseMoved(p[0],p[1]);screen.mouseClicked(p[0],p[1],0);screen.mouseReleased(p[0],p[1],0);stage=81;age=0;return;}
            if(stage==81){var target=state.objects().stream().filter(o->combatZombie.toString().equals(o.externalKey())).findFirst().orElse(null);if(NativeRtsController.combat.incomingNativeDamage>combatInboundBefore&&state.attackMessages()>attackBefore&&(target==null||target.health()<combatMobHealth)){checks.add("LMB targeted a real Minecraft Zombie through the original enemy context; original Ranger weapon impacts passed damage to vanilla health");healthBefore=state.objects().stream().filter(o->o.id()==combatDozer).findFirst().orElseThrow().health();setup=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);level.explode(null,-28.5,65,28.5,2,Level.ExplosionInteraction.TNT);},mc.getSingleplayerServer());stage=82;age=0;return;}if(age>300)throw new IllegalStateException("Original weapon did not damage Minecraft Zombie");}
            if(stage==82&&setup.isDone()&&age>30){setup.join();var o=state.objects().stream().filter(v->v.id()==combatDozer).findFirst().orElse(null);if(o!=null&&o.health()>=healthBefore)throw new IllegalStateException("Minecraft explosion did not damage native dozer");checks.add("Actual Minecraft explosion reached native original body damage, including native explosion armor");stage=83;age=0;return;}
            if(stage==83){setup=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=-26;x<=-22;x++)for(int z=26;z<=30;z++)for(int y=63;y<=65;y++)level.setBlock(new BlockPos(x,y,z),Blocks.OAK_PLANKS.defaultBlockState(),3);},mc.getSingleplayerServer());goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-5,64,28));view.session.send("TEST_SPAWN AmericaVehicleTomahawk "+goal.x+" "+goal.y+" 0");worldExplosionsBefore=NativeRtsController.combat.worldExplosions;stage=84;age=0;return;}
            if(stage==84&&setup.isDone()&&age>40){setup.join();combatLauncher=state.objects().stream().filter(o->o.template().equals("AmericaVehicleTomahawk")&&distance(o,goal)<2).findFirst().orElseThrow().id();var impact=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-24,65,28));view.session.send("TEST_FIRE "+combatLauncher+" "+impact.x+" "+impact.y+" "+impact.z);stage=85;age=0;return;}
            if(stage==85){if(NativeRtsController.combat.worldExplosions>worldExplosionsBefore){setup=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);int wood=0;for(int x=-26;x<=-22;x++)for(int z=26;z<=30;z++)for(int y=63;y<=65;y++)if(level.getBlockState(new BlockPos(x,y,z)).is(Blocks.OAK_PLANKS))wood++;if(wood>=75)throw new IllegalStateException("Original missile explosion did not destroy host blocks");},mc.getSingleplayerServer());stage=86;age=0;return;}if(age>300)throw new IllegalStateException("Original Tomahawk projectile did not emit a world explosion");}
            if(stage==86&&setup.isDone()){setup.join();checks.add("Original Tomahawk projectile detonation exported its original damage radius and destroyed real Minecraft blocks without applying native AoE twice");beginIssueCases(mc,view);return;}
            if(stage>=97&&stage<=99&&view.scene!=null&&view.scene.batches().stream().anyMatch(b->b.actor()<0&&b.texture()==0&&b.count()==36))tracerFrames.add((long)view.scene.frame());
            if(stage==87&&setup.isDone()&&age>100&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){setup.join();if(mc.level.getDayTime()<=dayBefore+50)throw new IllegalStateException("RTS world daylight clock froze");checks.add("Minecraft daylight advances while RTS interface stays open");verifyCameraBobbing(mc);uiStepper=state.objects().stream().filter(o->o.template().equals("AmericaInfantryRanger")&&distance(o,goal)<2).findFirst().orElseThrow().id();var pixel=unitPixel(screen,view,uiStepper);screen.mouseClicked(pixel[0],pixel[1],0);screen.mouseReleased(pixel[0],pixel[1],0);stage=88;age=0;return;}
            if(stage==88&&age>30&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){if(!state.selected().contains(uiStepper))throw new IllegalStateException("Step fixture ranger did not select through original UI");goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-9.5,65,-15.5));var pixel=screen.project(view.origin.world(goal.x,goal.y,goal.z));screen.mouseMoved(pixel[0],pixel[1]);screen.mouseClicked(pixel[0],pixel[1],0);screen.mouseReleased(pixel[0],pixel[1],0);stage=89;age=0;return;}
            if(stage==89){var unit=state.objects().stream().filter(o->o.id()==uiStepper).findFirst().orElseThrow();if(distance(unit,goal)<12&&unit.z()>6){checks.add("Actual original LMB terrain order climbed onto a one-block Minecraft wool platform");setup=CompletableFuture.runAsync(()->{var server=mc.getSingleplayerServer();server.setDifficulty(Difficulty.NORMAL,true);var level=server.getLevel(GeneralsMod.ARENA);var mob=new net.minecraft.world.entity.boss.wither.WitherBoss(net.minecraft.world.entity.EntityType.WITHER,level);mob.setNoAi(true);mob.setNoGravity(true);mob.setPos(-14,69,-8);combatWither=mob.getUUID();level.addFreshEntity(mob);},mc.getSingleplayerServer());stage=90;age=0;return;}if(age==180)view.session.send("TEST_PATH "+uiStepper+" "+goal.x+" "+goal.y+" "+goal.z);if(age>240)throw new IllegalStateException("LMB step order failed: "+unit);}
            if(stage==90&&setup.isDone()&&age>60&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){setup.join();view.cameraX=-14;view.cameraZ=-8;view.zoom=30;var mob=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->combatWither.equals(e.getUUID())).findFirst().orElse(null);if(mob==null)throw new IllegalStateException("Wither did not replicate to client");var nativeMob=state.objects().stream().filter(o->combatWither.toString().equals(o.externalKey())).findFirst().orElseThrow();if(!nativeMob.template().equals("AmericaJetRaptor")||Math.abs(nativeMob.maxHealth()/state.healthUnitsPerPoint()-300)>1)throw new IllegalStateException("Wither native air classification/max HP incorrect: "+nativeMob);checks.add("Real Wither imports as an original aircraft target with vanilla 300 HP preserved");var pixel=screen.project(mob.getBoundingBox().getCenter());if(screen.actorAt(pixel[0],pixel[1])!=nativeMob.id())throw new IllegalStateException("Visible mob did not win host picking");screen.mouseClicked(pixel[0],pixel[1],1);screen.mouseReleased(pixel[0],pixel[1],1);stage=91;age=0;return;}
            if(stage==91&&age>20&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){if(!state.selected().isEmpty())throw new IllegalStateException("Original short RMB failed to clear own selection before mob info click");checks.add("Actual short RMB clears own selection, then ordinary LMB inspects the enemy mob");var mob=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->combatWither.equals(e.getUUID())).findFirst().orElseThrow();var pixel=screen.project(mob.getBoundingBox().getCenter());screen.mouseMoved(pixel[0],pixel[1]);screen.mouseClicked(pixel[0],pixel[1],0);screen.mouseReleased(pixel[0],pixel[1],0);stage=92;age=0;return;}
            if(stage==92&&age>40){var nativeMob=state.objects().stream().filter(o->combatWither.toString().equals(o.externalKey())).findFirst().orElseThrow();if(!state.selected().contains(nativeMob.id()))throw new IllegalStateException("Enemy mob could not select through original LMB UI");if(state.healthBars()==null||state.healthBars().stream().noneMatch(b->b.id()==nativeMob.id())){if(age>120)throw new IllegalStateException("Original world health-bar commands were not exported");return;}if(++enemySelectionPasses<3){var screen=(NativeWorldScreen)MenuScreens.active(mc.screen);var mob=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->combatWither.equals(e.getUUID())).findFirst().orElseThrow();var pixel=screen.project(mob.getBoundingBox().getCenter());screen.mouseClicked(pixel[0],pixel[1],1);screen.mouseReleased(pixel[0],pixel[1],1);stage=213;age=0;return;}checks.add("Original LMB selected the Minecraft boss as an enemy in three repeated clicks");checks.add("Original health-bar geometry/style exported independently from the static GUI texture");if(state.portrait()==null)throw new IllegalStateException("Selected mob has no original controlbar portrait window");Screenshot.grab(mc.gameDirectory,"25-original-selected-minecraft-enemy.png",mc.getMainRenderTarget(),c->{});goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-15,64,-2));view.session.send("TEST_SPAWN AmericaTankAvenger "+goal.x+" "+goal.y+" 0");combatInboundBefore=NativeRtsController.combat.incomingNativeDamage;stage=93;age=0;return;}
            if(stage==213&&age>20&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){if(!state.selected().isEmpty())throw new IllegalStateException("Actual RMB failed to deselect the enemy before repeated info click");var mob=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->combatWither.equals(e.getUUID())).findFirst().orElseThrow();var pixel=screen.project(mob.getBoundingBox().getCenter());screen.mouseMoved(pixel[0],pixel[1]);screen.mouseClicked(pixel[0],pixel[1],0);screen.mouseReleased(pixel[0],pixel[1],0);stage=92;age=0;return;}
            if(stage==93&&age>30&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){combatAvenger=state.objects().stream().filter(o->o.template().equals("AmericaTankAvenger")).findFirst().orElseThrow().id();view.session.send("SELECT "+combatAvenger);stage=94;age=0;return;}
            if(stage==94&&age>20&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){var mob=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->combatWither.equals(e.getUUID())).findFirst().orElseThrow();var pixel=screen.project(mob.getBoundingBox().getCenter());screen.mouseMoved(pixel[0],pixel[1]);screen.mouseClicked(pixel[0],pixel[1],0);screen.mouseReleased(pixel[0],pixel[1],0);stage=95;age=0;return;}
            if(stage==95){var boss=state.objects().stream().filter(o->combatWither.toString().equals(o.externalKey())).findFirst().orElseThrow();if(boss.health()<boss.maxHealth()-1&&NativeRtsController.combat.incomingNativeDamage>combatInboundBefore){checks.add("Original Avenger anti-air weapon accepts the real Wither from original LMB context targeting and damages vanilla boss HP");view.session.send("HOST_DAMAGE "+combatAvenger+" 1000 0");goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-15,64,-5));view.session.send("TEST_SPAWN ChinaTankGattling "+goal.x+" "+goal.y+" 0");stage=96;age=0;return;}if(age>200)throw new IllegalStateException("Original Avenger failed to attack a Minecraft aircraft target");}
            if(stage==96&&age>30){combatGattling=state.objects().stream().filter(o->o.template().equals("ChinaTankGattling")).findFirst().orElseThrow().id();view.session.send("SELECT "+combatGattling);stage=97;age=0;return;}
            if(stage==97&&age>20&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){acceptedDamageBefore=NativeRtsController.combat.acceptedNativeDamage;combatMobHealth=state.objects().stream().filter(o->combatWither.toString().equals(o.externalKey())).findFirst().orElseThrow().health();var mob=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->combatWither.equals(e.getUUID())).findFirst().orElseThrow();var pixel=screen.project(mob.getBoundingBox().getCenter());screen.mouseMoved(pixel[0],pixel[1]);screen.mouseClicked(pixel[0],pixel[1],0);screen.mouseReleased(pixel[0],pixel[1],0);stage=98;age=0;return;}
            if(stage==98){var boss=state.objects().stream().filter(o->combatWither.toString().equals(o.externalKey())).findFirst().orElseThrow();if(age>40&&boss.health()<combatMobHealth-1){long accepted=NativeRtsController.combat.acceptedNativeDamage-acceptedDamageBefore;if(accepted<5)throw new IllegalStateException("Rapid fire was lost to vanilla hurt cooldown: accepted="+accepted);checks.add("Original anti-air bullets retain rapid-fire cadence against unarmored Wither: "+accepted+" accepted hits in "+age+" ticks");Screenshot.grab(mc.gameDirectory,"28-original-gattling-tracers.png",mc.getMainRenderTarget(),c->{});setup=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var mob=(net.minecraft.world.entity.LivingEntity)level.getEntity(combatWither);mob.setHealth(100);mob.invulnerableTime=0;},mc.getSingleplayerServer());combatInboundBefore=NativeRtsController.combat.incomingNativeDamage;stage=99;age=0;return;}if(age>200)throw new IllegalStateException("Original anti-air bullets did not damage the unarmored Wither");}
            if(stage==99&&setup.isDone()&&age>100){setup.join();var boss=state.objects().stream().filter(o->combatWither.toString().equals(o.externalKey())).findFirst().orElseThrow();if(boss.health()/state.healthUnitsPerPoint()<99||NativeRtsController.combat.incomingNativeDamage<=combatInboundBefore)throw new IllegalStateException("Wither projectile shield was bypassed or no actual shots arrived: "+boss);checks.add("Wither below half HP rejects actual original anti-air bullet impacts using vanilla projectile immunity");if(tracerFrames.size()<2)throw new IllegalStateException("Original moving Line3D tracers never reached separate scene frames: "+tracerFrames);checks.add("Original Line3D gun tracers exported in multiple changing native scene frames");view.session.send("HOST_DAMAGE "+combatGattling+" 1000 0");goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-24,64,15));view.session.send("TEST_SPAWN AmericaTankCrusader "+goal.x+" "+goal.y+" 0");stage=200;age=0;return;}
            if(stage==200&&age>30){combatTank=state.objects().stream().filter(o->o.template().equals("AmericaTankCrusader")&&distance(o,goal)<2).findFirst().orElseThrow().id();nativeDamageBefore=NativeRtsController.combat.outgoingMobDamage;setup=CompletableFuture.runAsync(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));},mc.getSingleplayerServer());stage=201;age=0;return;}
            if(stage==201){if(age%15==0)mc.getSingleplayerServer().execute(()->{var proxy=NativeRtsController.combat.actor(combatTank);var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());if(proxy!=null&&!proxy.isRemoved()){player.attack(proxy);}});if(state.objects().stream().anyMatch(o->o.template().equals("DeadCrusaderHulk")&&distance(o,goal)<24)){if(NativeRtsController.combat.outgoingMobDamage<=nativeDamageBefore)throw new IllegalStateException("Sword never reached native body");checks.add("Actual vanilla sword attacks kill native Crusader through original DEATH_NORMAL modules and create the original tank hulk");view.cameraX=-24;view.cameraZ=15;stage=202;age=0;return;}if(age>280)throw new IllegalStateException("Sword kill did not produce the original tank death/hulk");}
            if(stage==202&&age>20){Screenshot.grab(mc.gameDirectory,"26-original-tank-sword-death.png",mc.getMainRenderTarget(),c->{});goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-10,64,26));view.session.send("TEST_SPAWN AmericaBarracks "+goal.x+" "+goal.y+" 0");stage=203;age=0;return;}
            if(stage==203&&age>40){rallyFactory=state.objects().stream().filter(o->o.template().equals("AmericaBarracks")&&distance(o,goal)<2).findFirst().orElseThrow().id();view.session.send("SELECT "+rallyFactory);view.cameraX=-3;view.cameraZ=26;view.zoom=25;stage=204;age=0;return;}
            if(stage==204&&age>20&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){screen.clickCommand("Command_SetRallyPoint",true);screen.clickCommand("Command_SetRallyPoint",false);stage=205;age=0;return;}
            if(stage==205&&age>15&&MenuScreens.active(mc.screen) instanceof NativeWorldScreen screen){if(state.pendingCommand()==null||!state.pendingCommand().contains("Rally"))throw new IllegalStateException("Original rally GUI did not enter world-target mode: "+state.pendingCommand()+", selection="+state.selected());var pixel=screen.project(new net.minecraft.world.phys.Vec3(5,64,26));screen.mouseMoved(pixel[0],pixel[1]);screen.mouseClicked(pixel[0],pixel[1],0);screen.mouseReleased(pixel[0],pixel[1],0);stage=206;age=0;return;}
            if(stage==206){if(view.scene.batches().stream().anyMatch(b->(b.actor()&0xff000000)==0xf2000000)){checks.add("Original rally GUI produced original W3DWaypointBuffer textured line segments in the Minecraft world");Screenshot.grab(mc.gameDirectory,"27-original-rally-line.png",mc.getMainRenderTarget(),c->{});setup=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=-44;x<=-42;x++)for(int z=19;z<=21;z++)level.setBlock(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState(),3);},mc.getSingleplayerServer());goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-35,64,20));view.session.send("TEST_SPAWN AmericaTankCrusader "+goal.x+" "+goal.y+" 0");view.cameraX=-36;view.cameraZ=20;stage=207;age=0;return;}if(age>120)throw new IllegalStateException("Original rally line was not exported: selected="+state.selected()+", pending="+state.pendingCommand()+", actor="+rallyFactory+", nativeFrame="+state.frame());}
            if(stage==207&&setup.isDone()&&age>80){setup.join();combatTank=state.objects().stream().filter(o->o.template().equals("AmericaTankCrusader")&&distance(o,goal)<2).findFirst().orElseThrow().id();var tankBounds=view.bounds(combatTank);if(tankBounds==null||tankBounds.maxY-tankBounds.minY<.35)throw new IllegalStateException("Original tank geometry collapsed into a flat sprite: "+tankBounds);checks.add("Original Crusader mesh has nonzero world-space vertical extent, including original turret/body geometry");Screenshot.grab(mc.gameDirectory,"29-original-crusader-mesh.png",mc.getMainRenderTarget(),c->{});worldExplosionsBefore=NativeRtsController.combat.worldExplosions;var impact=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-42.5,65,20.5));view.session.send("TEST_FIRE "+combatTank+" "+impact.x+" "+impact.y+" "+impact.z);stage=208;age=0;return;}
            if(stage==208&&NativeRtsController.combat.worldExplosions>worldExplosionsBefore){setup=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);remainingBlocks=0;for(int x=-44;x<=-42;x++)for(int z=19;z<=21;z++)if(level.getBlockState(new BlockPos(x,64,z)).is(Blocks.STONE))remainingBlocks++;},mc.getSingleplayerServer());stage=209;age=0;return;}
            if(stage==208&&age>160)throw new IllegalStateException("Original armor-piercing tank shell produced no host impact");
            if(stage==209&&setup.isDone()){setup.join();if(remainingBlocks>=9)throw new IllegalStateException("Actual original tank shell blast could not destroy a stone block");checks.add("Original armor-piercing tank shell impact destroys a real Minecraft stone block");goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-30,64,28));view.session.send("TEST_SPAWN GLAInfantrySaboteur "+goal.x+" "+goal.y+" 0");view.cameraX=-28;view.cameraZ=28;stage=210;age=0;return;}
            if(stage==210&&age>100){var actor=state.objects().stream().filter(o->o.template().equals("GLAInfantrySaboteur")).findFirst().orElseThrow();if(!actor.cloaked())throw new IllegalStateException("Original innate stealth never activated");cloakedUnit=actor.id();var proxy=NativeRtsController.combat.actor(cloakedUnit);if(proxy==null||!proxy.isInvisible())throw new IllegalStateException("Native source stealth did not reach vanilla entity invisibility");setup=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var mob=new net.minecraft.world.entity.monster.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE,level);mob.setPos(-31,64,28);mob.setPersistenceRequired();mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));mob.setTarget(proxy);stealthZombie=mob.getUUID();level.addFreshEntity(mob);},mc.getSingleplayerServer());stage=211;age=0;return;}
            if(stage==211&&setup.isDone()&&age>120){setup.join();setup=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var mob=(net.minecraft.world.entity.monster.Zombie)level.getEntity(stealthZombie);var target=mob.getTarget();if(target instanceof local.generalsmc.nativebridge.NativeActorEntity proxy&&proxy.nativeId==cloakedUnit)throw new IllegalStateException("Zombie retained or reacquired undetected original stealth unit");},mc.getSingleplayerServer());stage=212;age=0;return;}
            if(stage==212&&setup.isDone()){setup.join();checks.add("Original innate Saboteur stealth makes its vanilla proxy invisible; a real Zombie drops its old target and does not reacquire it");finish(mc,"PASS",null);return;}
        }catch(Throwable e){GeneralsMod.LOG.error("Manual original world entry failed",e);finish(mc,"FAIL",e.toString());}
        finally{invoking=false;}
    }
    private static void verifyCameraBobbing(Minecraft mc) throws Exception {
        var hurt=net.minecraft.client.renderer.GameRenderer.class.getDeclaredMethod("bobHurt",com.mojang.blaze3d.vertex.PoseStack.class,float.class);
        var walk=net.minecraft.client.renderer.GameRenderer.class.getDeclaredMethod("bobView",com.mojang.blaze3d.vertex.PoseStack.class,float.class);hurt.setAccessible(true);walk.setAccessible(true);
        int oldTime=mc.player.hurtTime,oldDuration=mc.player.hurtDuration;var screen=MenuScreens.active(mc.screen);
        try{
            mc.player.hurtTime=10;mc.player.hurtDuration=10;var pose=new com.mojang.blaze3d.vertex.PoseStack();hurt.invoke(mc.gameRenderer,pose,.5f);walk.invoke(mc.gameRenderer,pose,.5f);
            if(!pose.last().pose().equals(new org.joml.Matrix4f()))throw new IllegalStateException("Commander bob leaked into RTS projection");
            mc.screen=null;var normal=new com.mojang.blaze3d.vertex.PoseStack();hurt.invoke(mc.gameRenderer,normal,.5f);
            if(normal.last().pose().equals(new org.joml.Matrix4f()))throw new IllegalStateException("Normal Minecraft hurt camera lost its animation");
            checks.add("RTS cancels commander walk/hurt projection bob; ordinary Minecraft retains actual hurt camera motion");
        }finally{mc.screen=screen;mc.player.hurtTime=oldTime;mc.player.hurtDuration=oldDuration;}
    }
    private static void beginIssueCases(Minecraft mc,NativeWorldView view) throws java.io.IOException {
        dayBefore=mc.level.getDayTime();setup=CompletableFuture.runAsync(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=-19;x<=-6;x++)for(int z=-20;z<=-11;z++){level.setBlock(new BlockPos(x,63,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int y=64;y<=67;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
            for(int x=-12;x<=-7;x++)for(int z=-19;z<=-12;z++)level.setBlock(new BlockPos(x,64,z),Blocks.WHITE_WOOL.defaultBlockState(),3);},mc.getSingleplayerServer());goal=view.origin.nativePoint(new net.minecraft.world.phys.Vec3(-17.5,64,-15.5));view.session.send("TEST_SPAWN AmericaInfantryRanger "+goal.x+" "+goal.y+" 0");view.cameraX=-13;view.cameraZ=-15;stage=87;age=0;
    }
    static double[] unitPixel(NativeWorldScreen screen,NativeWorldView view,int id){
        // Click a visible original triangle, rather than the empty centre of an animated mesh's bounding box.
        for(var b:view.scene.batches())if(b.actor()==id)for(int i=0;i<b.count();i+=3){double x=0,y=0,z=0;for(int k=0;k<3;k++){x+=b.vertices()[(i+k)*8];y+=b.vertices()[(i+k)*8+1];z+=b.vertices()[(i+k)*8+2];}var p=screen.project(view.origin.world(x/3,y/3,z/3));if(p[0]>0&&p[1]>0&&screen.actorAt(p[0],p[1])==id)return p;}
        throw new IllegalStateException("No visible mesh pixel for unit "+id);
    }
    private static boolean positiveUnexplored,positiveScouted,negativeScouted;
    private static void checkFarFog(NativeWorldView view){
        if(!Boolean.getBoolean("generalsmc.nativePagedBench")||!view.fog.enabled())return;
        if(stage==101&&age>130&&!positiveUnexplored){if(view.fog.at(2048.5,2048.5)!=255)throw new IllegalStateException("Distant camera opened unexplored positive terrain");checks.add("Original unexplored shroud follows the camera beyond+2048 Minecraft blocks without opening the terrain");positiveUnexplored=true;}
        if(stage==103&&age>5&&!positiveScouted){if(view.fog.at(2048.5,2048.5)!=0||!view.fog.shaderReady)throw new IllegalStateException("Positive distant Ranger failed to reveal host terrain");checks.add("Original Ranger vision reveals positive distant Minecraft terrain through signed partition pages and the active host shroud shader");positiveScouted=true;}
        if(stage==106&&age>5&&!negativeScouted){if(view.fog.at(-2048.5,-2048.5)!=0||!view.fog.shaderReady)throw new IllegalStateException("Negative distant Ranger failed to reveal host terrain");checks.add("Original Ranger vision reveals negative distant Minecraft terrain through the moving original shroud grid");negativeScouted=true;}
    }
    private static double distance(local.generalsmc.nativebridge.NativeSession.Actor actor,net.minecraft.world.phys.Vec3 p){return Math.hypot(actor.x()-p.x,actor.y()-p.y);}
    private static void finish(Minecraft mc,String result,String error) {
        try {
            var report=new LinkedHashMap<String,Object>();report.put("result",result);report.put("checks",checks);report.put("stage",stage);report.put("world",world);
            report.put("performance",performance);
            report.put("scope",Boolean.getBoolean("generalsmc.nativeEffectsBench")?"Real grass clearing, native GUI refresh, fire ignition, original nuclear target preview/launch/crater, debug chat money/timers, native Miles audio playback, RTS-camera mob despawn protection, resumed movement after terrain changes, sparse resource density/immediate known resource registration/persisted stock; not all-mechanics parity":Boolean.getBoolean("generalsmc.nativeRegressionBench")?"Ordinary cow/zombie LMB selection, actual HP pixels, moving hostile cursor, wide overhead deck with continuous altitude assertion, real RPG dirt penetration/Enderman hit, elevated nuclear AoE and building foundation after excavation; not all-mechanics parity":"Real chat Enter, four tiles, native GUI and completed construction, one-block vehicle steps, two-block barrier, slab, original cursor, stop/control-group keys, RMB camera, Minecraft/RTS switching, force-fire/repair and seeded resources/actual supply harvesting/persistence; not all-mechanics parity");
            if(NativeWorldView.current!=null)report.put("session",NativeWorldView.current.session.directory.toString());if(error!=null)report.put("error",error);
            if(Boolean.getBoolean("generalsmc.nativeOilBench"))report.put("scope","Actual Minecraft oil block, original Black Lotus capture GUI/LMB, original1000 bonus and periodic200 income, ordinary start-block interaction/editor and Fabric/SavedData persistence; not all-mechanics parity");
            if(Boolean.getBoolean("generalsmc.nativePolishBench"))report.put("scope","Real Technical enter/unload, calm Enderman/Golem retaliation, lava/water, oil block lifecycle, dim framebuffer, deep-floor native ray/LMB and walking64 blocks off camera into army-loaded chunks; not all-mechanics parity");
            if(Boolean.getBoolean("generalsmc.nativeFogBench"))report.put("scope","Actual Minecraft framebuffer fog/scout/exploration/F8, dim terrain and hidden mob picking, original ActionManager nuclear target restrictions, Minecraft audio options/Miles music/master mute and unmute; source readiness tested separately; not all-mechanics parity");
            if(Boolean.getBoolean("generalsmc.nativePagedBench"))report.put("scope","Real camera chunk subscription and original units/mob picking at signed world coordinates +/-2048, source shroud on those sparse pages when enabled, F8 restoring player chunks; not all-mechanics parity");
            Path path=Path.of(System.getProperty("generalsmc.evidence","../evidence"));Files.createDirectories(path);Files.writeString(path.resolve("native-world-access-tests.json"),GeneralsMod.JSON.toJson(report));
        }catch(Exception e){GeneralsMod.LOG.error("Unable to save world access report",e);}
        NativeRtsController.stop();System.setProperty("generalsmc.nativeManualBench","false");mc.stop();
    }
}
