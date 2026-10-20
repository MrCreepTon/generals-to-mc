package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
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

/** Uses real screen callbacks and the real native loader; isolated configs and fresh world only. */
final class NativeSetupBench {
    static int ticks,stage,age,before;static boolean done;static String oldRoot;static Path root;
    static NativeProfiles.Profile stock;static int requestedColor;static CompletableFuture<NativeProfiles.Profile> modTask;
    static final List<String> checks=new ArrayList<>();static final String world="GeneralsSetupBench-"+System.currentTimeMillis();
    static void click(Screen screen,String text){String translated=L.s(text),label=translated.startsWith("generalsmc.")?text:translated;for(int tries=0;tries<16;tries++){var button=screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals(label)).map(w->(Button)w).findFirst().orElseThrow(()->new IllegalStateException("Missing button: "+label));if(button.visible){if(!button.active)throw new IllegalStateException("Inactive button: "+label);button.onPress();return;}screen.mouseScrolled(screen.width/2.,screen.height/2.,0,button.getY()<48?1:-1);}throw new IllegalStateException("Button remains outside viewport: "+label);}

    static void tick(Minecraft mc){
        if(done||!Boolean.getBoolean("generalsmc.nativeSetupBench"))return;ticks++;age++;
        try{
            if(ticks>20*240)throw new IllegalStateException("Setup bench timeout at "+stage);
            if(stage==0&&ticks>60&&(mc.screen instanceof TitleScreen||mc.screen instanceof AccessibilityOnboardingScreen)){
                NativeKeyConflictBench.check(mc,checks);
                oldRoot=System.getProperty("generalsmc.nativeRoot");Path original=NativeProfiles.root();root=original.resolve("private/setup-fixture-"+UUID.randomUUID());Files.createDirectories(root.resolve("runtime"));Files.copy(NativeProfiles.engine(original),root.resolve("runtime/generalszh.exe"));System.setProperty("generalsmc.nativeRoot",root.toString());
                mc.options.guiScale().set(3);mc.options.pauseOnLostFocus=false;mc.setScreen(new TitleScreen());click(mc.screen,"Generals");
                if(!(mc.screen instanceof GeneralsSetupScreen))throw new IllegalStateException("Title menu button failed");checks.add("Actual title-screen Generals button opens setup without a command or script");
                Screenshot.grab(mc.gameDirectory,"50-generals-menu-first-run.png",mc.getMainRenderTarget(),c->{});
                click(mc.screen,"+ Подключить установленную игру");var edit=(GeneralsSetupScreen.Connect)mc.screen;
                if(!edit.game.getValue().equals("C:/Games/Generals ZH")||!edit.base.getValue().replace('\\','/').equals("C:/Games/Generals"))throw new IllegalStateException("Installed folders not discovered");
                checks.add("Setup auto-detects owned Zero Hour and sibling Generals folders");
                edit.base.setValue(root.resolve("missing-base").toString());edit.connect();next(1);
            }else if(stage==1&&mc.screen instanceof GeneralsSetupScreen.Connect edit&&!edit.busy){
                if(edit.status.isEmpty()||!NativeProfiles.read().profiles().isEmpty())throw new IllegalStateException("Invalid input committed a profile");checks.add("Missing game folder shows an error and does not commit a profile");
                edit.base.setValue("C:/Games/Generals");edit.connect();next(2);
            }else if(stage==2&&mc.screen instanceof GeneralsSetupScreen&&NativeProfiles.active()!=null){
                stock=NativeProfiles.active();if(stock.factions().size()<12||stock.colors().isEmpty())throw new IllegalStateException("Original sides/colors not loaded");checks.add("Real native headless preflight loads source factions/colors before atomic profile commit");
                if(!Files.isRegularFile(NativeProfiles.lab(root,stock).resolve("mounts.json")))throw new IllegalStateException("Missing provenance");checks.add("Private profile mounts and user data stay outside the original game folders");
                Screenshot.grab(mc.gameDirectory,"51-generals-connected.png",mc.getMainRenderTarget(),c->{});
                Path mod=root.resolve("fixture-mod/Data/INI");Files.createDirectories(mod);Files.writeString(mod.resolve("PlayerTemplate.ini"),playerTemplateIni()+"\nPlayerTemplate FactionBridgeProfileProbe\n  Side = America\n  PlayableSide = Yes\nEnd\n");
                modTask=CompletableFuture.supplyAsync(()->{try{return NativeProfiles.connect("Fixture mod",stock.game(),stock.base(),root.resolve("fixture-mod").toString(),false,s->{});}catch(Exception e){throw new CompletionException(e);}});next(3);
            }else if(stage==3&&modTask.isDone()){
                var mod=modTask.join();if(!mod.factions().contains("FactionBridgeProfileProbe"))throw new IllegalStateException("Mod loose INI was not loaded by original engine");checks.add("A separate mod folder's real PlayerTemplate INI is parsed by the original engine and appears in its playable factions");
                if(!Files.isDirectory(NativeProfiles.lab(root,stock))||NativeProfiles.lab(root,stock).equals(NativeProfiles.lab(root,mod)))throw new IllegalStateException("Profiles mixed");NativeProfiles.select(stock.id());if(!NativeProfiles.active().id().equals(stock.id()))throw new IllegalStateException("Selection not persisted");checks.add("Two profiles have isolated mounts/userdata and persistent switching");
                Path archiveDir=root.resolve("fixture-big-mod");Files.createDirectories(archiveDir);byte[] payload=(playerTemplateIni()+"\nPlayerTemplate FactionBridgeArchiveProbe\n Side = China\n PlayableSide = Yes\nEnd\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);byte[] entry="Data\\INI\\PlayerTemplate.ini".getBytes(java.nio.charset.StandardCharsets.US_ASCII);int header=16+8+entry.length+1;
                Path archive=archiveDir.resolve("Probe.big");
                try(var out=new java.io.DataOutputStream(Files.newOutputStream(archive))){out.writeBytes("BIGF");out.writeInt(Integer.reverseBytes(header+payload.length));out.writeInt(1);out.writeInt(header);out.writeInt(header);out.writeInt(payload.length);out.write(entry);out.writeByte(0);out.write(payload);}
                modTask=CompletableFuture.supplyAsync(()->{try{return NativeProfiles.connect("BIG fixture",stock.game(),stock.base(),archiveDir.toString(),false,s->{});}catch(Exception e){throw new CompletionException(e);}});next(31);
            }else if(stage==31&&modTask.isDone()){
                var mod=modTask.join();if(!mod.factions().contains("FactionBridgeArchiveProbe"))throw new IllegalStateException("Original loader did not prefer mod BIG over INIZH");checks.add("Explicit mod BIG is validated and wins over stock INIZH through original archive lookup");
                NativeProfiles.select(stock.id());
                next(310);
            }else if(stage==310&&NativeProfilesCrudBench.tick(mc,checks)){
                mc.options.guiScale().set(3);mc.resizeDisplay();mc.setScreen(new GeneralsSetupScreen(null));next(32);
            }else if(stage==32&&age>20){bounds(mc.screen);Screenshot.grab(mc.gameDirectory,"77-generals-compact-menu.png",mc.getMainRenderTarget(),c->{});click(mc.screen,"Управление");next(33);
            }else if(stage==33&&age>20){bounds(mc.screen);Screenshot.grab(mc.gameDirectory,"78-generals-help.png",mc.getMainRenderTarget(),c->{});mc.screen.mouseScrolled(0,0,0,-20);if(((GeneralsSetupScreen.Help)mc.screen).scroll<=0)throw new IllegalStateException("Long help cannot scroll in compact GUI");next(34);
            }else if(stage==34&&age>20){Screenshot.grab(mc.gameDirectory,"79-generals-help-scrolled.png",mc.getMainRenderTarget(),c->{});click(mc.screen,"Назад");var play=mc.screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals("Начать RTS здесь")).map(w->(Button)w).findFirst().orElseThrow();if(play.active||play.getTooltip()==null)throw new IllegalStateException("World-only Start lacks disabled-button tooltip");next(35);
            }else if(stage==35&&age>20){bounds(mc.screen);Screenshot.grab(mc.gameDirectory,"80-generals-readable-faction.png",mc.getMainRenderTarget(),c->{});if(!GeneralsSetupScreen.colorName("SkyBlue").equals("Небесно-голубой")||GeneralsSetupScreen.friendly("FactionAmericaSuperWeaponGeneral").contains("General"))throw new IllegalStateException("Internal names leaked into setup");checks.add("Compact real GUI keeps all menu controls above the footer; long help scrolls with a fixed Back button; faction and SkyBlue names are readable Russian");mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings(world,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(FeatureFlags.DEFAULT_FLAGS),WorldDataConfiguration.DEFAULT),new WorldOptions(1263217L,false,false),WorldPresets::createNormalWorldDimensions,mc.screen);next(4);
            }else if(stage==4&&mc.player!=null&&mc.getSingleplayerServer()!=null){
                mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);for(int x=-64;x<=64;x++)for(int z=-64;z<=64;z++)level.setBlock(new BlockPos(x,63,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).teleportTo(level,.5,64,.5,Set.of(),0,60,false);});next(5);
            }else if(stage==5&&age>100&&mc.level!=null&&mc.level.dimension().equals(GeneralsMod.ARENA)){
                mc.setScreen(new PauseScreen(true));click(mc.screen,"Generals");click(mc.screen,"Начать RTS здесь");click(mc.screen,"Сторона: Случайная сторона");click(mc.screen,"ГЛА");click(mc.screen,"Цвет: случайный");
                if(mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.AbstractWidget).map(w->(net.minecraft.client.gui.components.AbstractWidget)w).anyMatch(w->w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>mc.screen.width||w.getY()+w.getHeight()>mc.screen.height))throw new IllegalStateException("Selection dialog clipped its controls");
                checks.add("Choice dialog controls fit the actual Minecraft GUI viewport");requestedColor=stock.colors().stream().filter(c->GeneralsSetupScreen.colorName(c.label()).equals("Красный")).findFirst().orElseThrow().id();click(mc.screen,"Красный");click(mc.screen,"Начать");next(6);
            }else if(stage==6&&NativeWorldView.current!=null&&NativeWorldView.current.state!=null&&NativeWorldView.current.state.terrainColumns()>=4096&&age>120){
                var v=NativeWorldView.current;if(!v.session.directory.startsWith(NativeProfiles.lab(root,stock)))throw new IllegalStateException("Match used old lab");checks.add("Pause-menu Start launches the selected private profile in the current Minecraft world without /zh-native");
                if(v.state.teamColor()!=requestedColor||v.state.objects().stream().noneMatch(o->o.owner()==v.state.localPlayerIndex()&&o.template().equals("GLACommandCenter")))throw new IllegalStateException("Source faction/color choice did not reach match");checks.add("Faction/color pickers start an actual original GLA base in the selected red source team color");
                before=v.state.frame();((NativeWorldScreen)mc.screen).keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F8,0,0);next(60);
            }else if(stage==60&&age>10&&NativeUsabilityBench.tick(mc,checks)){NativeRtsController.toggle();next(61);
            }else if(stage==61&&age>20&&mc.screen instanceof NativeWorldScreen){((NativeWorldScreen)mc.screen).keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F9,0,0);if(!(mc.screen instanceof GeneralsSetupScreen))throw new IllegalStateException("F9 failed");next(7);
            }else if(stage==7&&age>80){
                if(NativeWorldView.current==null||NativeWorldView.current.state.frame()<=before+30)throw new IllegalStateException("Setup paused the original match");checks.add("F9 opens the menu while the same original process continues simulating");
                var add=mc.screen.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals("+")).map(w->(Button)w).findFirst().orElseThrow();if(add.active)throw new IllegalStateException("Can change game under active match");checks.add("Game switching/import is disabled during an active match");
                Screenshot.grab(mc.gameDirectory,"52-generals-active-menu.png",mc.getMainRenderTarget(),c->{});click(mc.screen,"Вернуться к RTS");next(8);
            }else if(stage==8&&age>20){if(!(mc.screen instanceof NativeWorldScreen)||NativeWorldView.current.state.frame()<=before)throw new IllegalStateException("Resume failed");checks.add("Resume returns to original RTS HUD with its existing units and simulation");finish(mc,"PASS",null);}
        }catch(Throwable e){GeneralsMod.LOG.error("Setup fixture failed",e);finish(mc,"FAIL",e.toString());}
    }
    static void bounds(Screen screen){for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget w&&w.visible)if(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>screen.width||w.getY()+w.getHeight()>screen.height)throw new IllegalStateException("Compact GUI control overlaps footer: "+w.getMessage().getString());}
    static void next(int value){stage=value;age=0;GeneralsMod.LOG.info("Setup bench stage {}",stage);}
    static String playerTemplateIni()throws Exception{try(var file=new java.io.RandomAccessFile("C:/Games/Generals ZH/INIZH.big","r")){file.seek(8);int count=file.readInt();file.readInt();for(int i=0;i<count;i++){long offset=Integer.toUnsignedLong(file.readInt());int size=file.readInt();var name=new StringBuilder();int b;while((b=file.readUnsignedByte())!=0)name.append((char)b);if(name.toString().replace('\\','/').equalsIgnoreCase("Data/INI/PlayerTemplate.ini")){file.seek(offset);byte[] bytes=new byte[size];file.readFully(bytes);return new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1);}}throw new IllegalStateException("No source PlayerTemplate.ini");}}
    static void finish(Minecraft mc,String result,String error){done=true;try{Map<String,Object> r=new LinkedHashMap<>();r.put("result",result);r.put("checks",checks);r.put("stage",stage);r.put("root",root==null?"":root.toString());r.put("scope","Actual Minecraft title/pause/editor/menu callbacks; owned stock ZH preflight, private loose-INI and BIG mod fixtures, separate profiles, launch/resume in fresh world. Contra absent and untested.");if(error!=null)r.put("error",error);Path out=Path.of(System.getProperty("generalsmc.evidence","evidence"),"native-setup-client-tests.json");Files.writeString(out,GeneralsMod.JSON.toJson(r));}catch(Exception e){GeneralsMod.LOG.error("Setup report failed",e);}NativeRtsController.stop();if(oldRoot!=null)System.setProperty("generalsmc.nativeRoot",oldRoot);mc.stop();}
}
