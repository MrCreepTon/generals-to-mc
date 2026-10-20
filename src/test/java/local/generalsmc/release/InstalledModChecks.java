package local.generalsmc.release;
import local.generalsmc.nativebridge.*;
import java.nio.file.*;
import java.util.*;

/** Explicit local test of owned installed data; never runs in asset-free CI. */
public final class InstalledModChecks {
    public static void main(String[] args)throws Exception{
        Path original=NativeProfiles.root(),root=original.resolve("private/installed-rotr-"+UUID.randomUUID());
        Files.createDirectories(root.resolve("runtime"));Files.copy(NativeProfiles.engine(original),root.resolve("runtime/generalszh.exe"));System.setProperty("generalsmc.nativeRoot",root.toString());
        var checks=new ArrayList<String>();var report=new LinkedHashMap<String,Object>();report.put("root",root.toString());report.put("result","FAIL");
        try{
            var profile=NativeProfiles.connect("Rise of the Reds","C:/Games/ROTR","C:/Games/Generals","",false,System.out::println);
            if(profile.factions().size()<5||profile.factionNames().values().stream().noneMatch(n->n.contains("Russian"))||profile.factionNames().values().stream().noneMatch(n->n.contains("European")))throw new IllegalStateException("ROTR factions not loaded: "+profile.factions());
            checks.add("The actual installed ROTR launcher file map mounts renamed GIB archives privately and the original parser discovers Russia and Europe alongside America, China and GLA");
            report.put("factions",profile.factions());report.put("factionNames",profile.factionNames());
            for(var faction:profile.factions().stream().filter(f->profile.factionNames().getOrDefault(f,"").matches(".*(Russian|European).*" )).toList())try(var session=new NativeSession(root,false,800,600,NativeProfiles.lab(root,profile))){
                var state=waitState(session,false,0);String map=state.maps().stream().filter(m->m.toLowerCase(Locale.ROOT).contains("tournament desert")).findFirst().orElseThrow();
                session.send("MATCH_SLOT 0 0 0 0 \""+faction+"\"");session.send("MATCH_SLOT 1 2 1 1 \"FactionChina\"");session.start(map,faction,"FactionChina",735179,10000,0);
                state=waitState(session,true,180);if(state.objects().stream().noneMatch(a->a.owner()==2&&a.structure()))throw new IllegalStateException("Missing original base for "+faction);
                checks.add("Actual original ROTR "+faction+" skirmish advances180 logic frames with a medium China NPC and a live starting base");
                session.close();if(!session.awaitExit(8000))throw new IllegalStateException("ROTR did not exit normally");
            }
            report.put("result","PASS");
        }catch(Throwable e){report.put("error",e.toString());throw e;}
        finally{report.put("checks",checks);Path output=Path.of("evidence/installed-rotr-checks.json");Files.writeString(output,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(report);}
    }
    private static NativeSession.State waitState(NativeSession session,boolean game,int frame)throws Exception{long deadline=System.nanoTime()+60_000_000_000L;while(System.nanoTime()<deadline){var state=session.read();if(state!=null){if(!state.error().isEmpty())throw new IllegalStateException(state.error());if(game?state.inGame()&&state.frame()>=frame:state.maps()!=null&&!state.maps().isEmpty())return state;}Thread.sleep(50);}throw new IllegalStateException("Original ROTR startup timeout: "+session.directory);}
}
