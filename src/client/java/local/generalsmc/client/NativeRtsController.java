package local.generalsmc.client;
import local.generalsmc.nativebridge.WorldScale;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ArrayList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Original engine and world view lifecycle for a local singleplayer world. */
public final class NativeRtsController {
    private static NativeSession session;
    private static NativeAudio audio;
    private static boolean pendingOpen;
    private static boolean suspended,menuOpen;
    private static long deadline;
    private static boolean started;
    private static java.util.concurrent.CompletableFuture<WorldRules.Options> matchOptions;
    private static TerrainSynchronizer terrain;
    private static RadarSynchronizer radar;
    private static ResourceSynchronizer resources;
    private static SiteSynchronizer sites;
    static long resourceDeferrals(){return resources==null?0:resources.deferredVisible;}
    public static WorldCombat combat;
    private static WorldViewSubscription subscription;
    private static ResourceKey<Level> dimension;
    private static java.util.List<String> knownFactions=java.util.List.of();
    public static boolean hasLocalSession(){return session!=null||pendingOpen;}
    public static boolean hasSession(){return hasLocalSession()||NativeNetworkClient.active();}
    public static java.util.List<String> availableFactions(){try{var profile=NativeProfiles.active();if(profile!=null)return profile.factions();}catch(Exception ignored){}if(!knownFactions.isEmpty())return knownFactions;
        var root=NativeProfiles.root().resolve("native-lab/sessions");
        try(var dirs=java.nio.file.Files.list(root)){for(var path:dirs.sorted(java.util.Comparator.comparingLong((java.nio.file.Path p)->p.toFile().lastModified()).reversed()).limit(8).toList()){var file=path.resolve("state.json");if(java.nio.file.Files.isRegularFile(file)){var s=GeneralsMod.JSON.fromJson(java.nio.file.Files.readString(file),NativeSession.State.class);if(s.factions()!=null&&!s.factions().isEmpty()){knownFactions=(s.playableFactions()!=null?s.playableFactions():s.factions());return knownFactions;}}}}catch(Exception e){GeneralsMod.LOG.debug("Original faction suggestions unavailable",e);}return knownFactions;
    }
    public static java.util.List<NativeSession.TeamColor> availableColors(){try{var profile=NativeProfiles.active();if(profile!=null&&profile.colors()!=null)return profile.colors();}catch(Exception ignored){}return java.util.List.of();}
    private static String faction="FactionAmerica";
    private static int teamColor;
    public static int open(String side,int color){if(session!=null||pendingOpen){message(L.s("Сначала завершите текущую сессию: /zh-native-stop. F8 сохраняет текущую фракцию и базу."));return 0;}faction=side;teamColor=color;return open();}
    public static int open() {
        var mc=Minecraft.getInstance();
        if(NativeNetworkClient.active()){NativeNetworkClient.resume();return 1;}
        if(NativeNetworkClient.lobby!=null){message(L.s("Сначала выйдите из комнаты сетевой схватки."));return 0;}
        if(NativeNetworkServer.busy()){message(L.s("В мире уже готовится сетевая схватка. Откройте её комнату в меню Generals."));return 0;}
        if(mc.getSingleplayerServer()==null||mc.level==null||mc.player==null) {message("Native lab requires a local singleplayer world.");return 0;}
        try{var profile=NativeProfiles.active();if(profile!=null&&!NativeProfiles.available(profile)){mc.setScreen(new ProfileStatusScreen(new GeneralsSetupScreen(null),profile,true));return 0;}}catch(Exception e){GeneralsMod.LOG.debug("Profile lookup failed",e);}
        if(pendingOpen)return 0;
        // ChatScreen closes itself after command dispatch. Open on END_CLIENT_TICK,
        // after that cleanup, or it removes our screen and immediately sends QUIT.
        pendingOpen=true;dimension=mc.level.dimension();return 1;
    }
    public static void toggle() {
        if(NativeNetworkClient.active()){NativeNetworkClient.toggle();return;}
        var mc=Minecraft.getInstance();
        if(session==null)return; // F8 only switches an existing match; launching is explicit in the menu.
        if(suspended){open();return;}
        try {session.send("MODE 0");suspended=true;mc.hitResult=null;mc.crosshairPickEntity=null;mc.setScreen(null);}
        catch(Exception e){GeneralsMod.LOG.error("Unable to suspend RTS",e);stop();}
    }
    public static void resume(){if(NativeNetworkClient.active()){NativeNetworkClient.resume();return;}if(session==null)return;var mc=Minecraft.getInstance();try{menuOpen=false;suspended=false;session.send("MODE 1");mc.setScreen(new NativeWorldScreen(session));}catch(Exception e){failed(e);}}
    public static void menu(){var mc=Minecraft.getInstance();if(!hasSession())return;menuOpen=true;var view=NativeWorldView.current;if(view!=null)try{view.session.mouse(0x200,view.session.width/2,view.session.height/2,0,0);}catch(Exception error){failed(error);return;}if(view!=null)mc.setScreen(new GeneralsSetupScreen(new NativeWorldScreen(view.session)));}
    public static int stop(){if(NativeNetworkClient.active())return NativeNetworkClient.leave();close();var mc=Minecraft.getInstance();if(MenuScreens.active(mc.screen) instanceof NativeWorldScreen)mc.setScreen(null);return 1;}
    public static boolean controlling(){return NativeNetworkClient.active()?NativeNetworkClient.controlling():session!=null&&!suspended;}
    public static int debug(String command){
        if(!NativeDebugScreen.available())return 0;
        if(NativeNetworkClient.active()){NativeNetworkClient.request("debug",j->j.addProperty("command",command));return 1;}
        if(session==null){message(L.s("Сначала запустите /zh-native. Для debug-команд переключитесь F8 в Minecraft; симуляция продолжит работать."));return 0;}
        try{session.send(command);return 1;}catch(Exception e){GeneralsMod.LOG.error("Native debug command failed",e);GeneralsClient.notify(L.s("Не удалось выполнить действие"));return 0;}
    }
    public static int debugStatus(){var v=NativeWorldView.current;if(v==null||v.state==null){message(L.s("Нет активной сессии Zero Hour."));return 0;}var powers=v.state.specialPowers();message(L.s("Zero Hour: кадр ")+v.state.frame()+L.s(", деньги ")+v.state.localCash()+L.s(", объектов ")+v.state.objects().size()+L.s(", выбранные ")+v.state.selected()+L.s(", колонок рельефа ")+v.state.terrainColumns()+L.s(", готовых способностей ")+(powers==null?0:powers.stream().filter(NativeSession.SpecialPower::ready).count())+"/"+(powers==null?0:powers.size())+L.s(", аудиоканалов ")+(v.state.audio2DUsed()+v.state.audio3DUsed()));return 1;}
    public static void tick(Minecraft mc) {
        try {
            if(pendingOpen) {
                pendingOpen=false;
                if(mc.getSingleplayerServer()==null||mc.level==null||mc.player==null||!mc.level.dimension().equals(dimension))return;
                Path root=NativeProfiles.root();
                if(session==null){session=new NativeSession(root,true,mc.getWindow().getWidth(),mc.getWindow().getHeight());audio=new NativeAudio();started=false;var owner=mc.getSingleplayerServer();var world=dimension;matchOptions=java.util.concurrent.CompletableFuture.supplyAsync(()->WorldRules.get(owner.getLevel(world)).values().options(),owner);deadline=System.currentTimeMillis()+60000;}
                else if(suspended)session.send("MODE 1");
                suspended=false;
                mc.setScreen(new NativeWorldScreen(session));
                GeneralsMod.LOG.info("Original engine session opened: {}",session.directory);
            }
            if(session==null)return;
            if(mc.level==null||!mc.level.dimension().equals(dimension)){close();return;}
            if(!suspended&&!menuOpen&&!(MenuScreens.active(mc.screen) instanceof NativeFrameScreen)){close();return;}
            if(menuOpen&&MenuScreens.active(mc.screen) instanceof NativeFrameScreen)menuOpen=false;
            if(audio!=null)audio.tick(mc,session);
            var state=session.read();if(state==null) {if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Engine startup timed out");return;}
            if(state.factions()!=null)knownFactions=(state.playableFactions()!=null?state.playableFactions():state.factions());
            if(!state.error().isEmpty())throw new IllegalStateException(state.error());
            if(!started) {
                if(state.maps()==null||state.maps().isEmpty())throw new IllegalStateException(L.s("В подключённой игре не найдено карт. Проверьте профиль в меню Generals."));
                String map=state.maps().stream().filter(s->s.toLowerCase(Locale.ROOT).contains("tournament desert")).findFirst().orElse(state.maps().getFirst());
                var playable=state.playableFactions();if(playable==null||playable.isEmpty())throw new IllegalStateException(L.s("Игра не содержит доступных сторон"));
                if(!playable.contains(faction))faction=playable.getFirst();
                if(state.colors()!=null&&state.colors().stream().noneMatch(c->c.id()==teamColor))teamColor=state.colors().isEmpty()?0:state.colors().getFirst().id();
                // Preserve the stock diagnostic opponent; mods may replace every stock faction.
                String opponent=playable.contains("FactionChina")?"FactionChina":playable.getFirst();
                session.send("MATCH_NAME 0 "+GeneralsMod.JSON.toJson(mc.player.getName().getString()));
                if(matchOptions==null||!matchOptions.isDone())return;var options=matchOptions.join();
                session.send("MATCH_RULES "+(options.limitSuperweapons()?1:0));
                session.start(map,faction,opponent,12345,options.startingCash(),teamColor);started=true;
            }
            if(state.inGame() && terrain==null && WorldScale.blocksForCells(state.terrainExtentX())>0 && WorldScale.blocksForCells(state.terrainExtentY())>0) {
                var anchor=state.objects().stream().filter(o->o.owner()==state.localPlayerIndex()&&o.structure()).findFirst()
                    .orElseGet(()->state.objects().stream().filter(o->o.owner()==state.localPlayerIndex()).findFirst().orElseThrow(()->new IllegalStateException(L.s("У выбранной стороны нет стартовых объектов"))));
                int cx=(int)Math.floor(anchor.x()/WorldScale.UNITS_PER_BLOCK),cy=(int)Math.floor(anchor.y()/WorldScale.UNITS_PER_BLOCK);
                int sizeX=Math.min(64,WorldScale.blocksForCells(state.terrainExtentX())),sizeY=Math.min(64,WorldScale.blocksForCells(state.terrainExtentY()));
                int startX=Math.max(0,Math.min(cx-sizeX/2,WorldScale.blocksForCells(state.terrainExtentX())-sizeX));
                int startY=Math.max(0,Math.min(cy-sizeY/2,WorldScale.blocksForCells(state.terrainExtentY())-sizeY));
                var origin=mc.player.blockPosition();
                var tiles=new ArrayList<TerrainExporter.Region>();
                for(int x=0;x<sizeX;x+=32)for(int y=0;y<sizeY;y+=32)
                    tiles.add(new TerrainExporter.Region(startX+x,startY+y,Math.min(32,sizeX-x),Math.min(32,sizeY-y),
                        origin.getX()-cx,origin.getY(),origin.getZ()-cy));
                if(!WorldScale.PAGED_WORLD)for(int x=0;x<=WorldScale.blocksForCells(state.terrainExtentX());x+=32)for(int y=0;y<=WorldScale.blocksForCells(state.terrainExtentY());y+=32){
                    var tile=new TerrainExporter.Region(x,y,Math.min(32,WorldScale.blocksForCells(state.terrainExtentX())+1-x),Math.min(32,WorldScale.blocksForCells(state.terrainExtentY())+1-y),origin.getX()-cx,origin.getY(),origin.getZ()-cy);
                    if(!tiles.contains(tile))tiles.add(tile);
                }
                terrain=new TerrainSynchronizer(session,mc.getSingleplayerServer().getLevel(dimension),tiles,state.terrainRevision());
                new NativeWorldView(session,new NativeWorldView.Origin(origin.getX()-cx,origin.getY(),origin.getZ()-cy),anchor.x(),anchor.y());
                radar=new RadarSynchronizer(session,mc.getSingleplayerServer().getLevel(dimension),origin.getX()-cx,origin.getZ()-cy);
                subscription=new WorldViewSubscription(mc.getSingleplayerServer().getLevel(dimension),mc.player.getUUID());
                combat=new WorldCombat(session,mc.getSingleplayerServer().getLevel(dimension),origin.getX()-cx,origin.getY(),origin.getZ()-cy);
                resources=new ResourceSynchronizer(session,mc.getSingleplayerServer().getLevel(dimension),origin.getX()-cx,origin.getY(),origin.getZ()-cy);
                sites=new SiteSynchronizer(session,mc.getSingleplayerServer().getLevel(dimension),origin.getX()-cx,origin.getY(),origin.getZ()-cy);
                GeneralsMod.LOG.info("Original RTS linked to Minecraft at {}",origin);
            }
            if(sites!=null)sites.tick(state);
            if(terrain!=null){var locations=new ArrayList<WorldResourceStore.Node>();if(resources!=null)locations.addAll(resources.discovered());if(sites!=null)locations.addAll(sites.terrainSites());terrain.resourceSites(locations);var v=NativeWorldView.current;if(v!=null)terrain.focus((v.cameraX-v.origin.x()),(v.cameraZ-v.origin.z()));terrain.tick(state);}
            if(terrain!=null&&!NativeWorldView.active())throw new IllegalStateException("Original world renderer stopped; see client log");
            if(NativeWorldView.current!=null)NativeWorldView.current.tick(state);
            if(resources!=null){var v=NativeWorldView.current;if(v!=null){resources.focus(suspended?mc.player.getX():v.cameraX,suspended?mc.player.getZ():v.cameraZ);resources.visibility(v::resourceVisible);}resources.tick(state);}
            if(combat!=null)combat.snapshot(state);
            if(subscription!=null&&NativeWorldView.current!=null){var v=NativeWorldView.current;subscription.focus(v.cameraX,v.cameraZ,mc.options.renderDistance().get(),!suspended);}
            if(radar!=null&&NativeWorldView.current!=null){var v=NativeWorldView.current;radar.tick(suspended?mc.player.getX():v.cameraX,suspended?mc.player.getZ():v.cameraZ);}
        }catch(Exception e){failed(e);}
    }
    static void failed(Exception error){if(NativeNetworkClient.active()){NativeNetworkClient.fail(error);return;}GeneralsMod.LOG.error("Original engine session stopped",error);Path log=session==null?NativeProfiles.root():session.directory;close();Minecraft.getInstance().setScreen(new GeneralsFailureScreen(error.getMessage(),log));}
    private static void close(){audio=null;matchOptions=null;if(sites!=null){sites.close();sites=null;}if(subscription!=null){subscription.close();subscription=null;}if(combat!=null){combat.close();combat=null;}if(resources!=null){resources.close();resources=null;}if(radar!=null){radar.close();radar=null;}pendingOpen=false;suspended=false;menuOpen=false;if(NativeWorldView.current!=null)NativeWorldView.current.close();if(terrain!=null){terrain.close();terrain=null;}if(session!=null){session.close();session=null;}}
    private static void message(String text){var player=Minecraft.getInstance().player;if(player!=null)player.displayClientMessage(L.component(text),false);}
}
