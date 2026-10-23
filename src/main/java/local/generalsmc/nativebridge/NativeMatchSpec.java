package local.generalsmc.nativebridge;

import java.util.*;

/** Own stable DTO names; no serialized Minecraft implementation fields or client-chosen player indexes. */
public record NativeMatchSpec(UUID id,String map,int seed,int cash,String fingerprint,List<Army> armies) {
    public record Position(int x,int y,int z) {}
    public record Army(UUID player,String name,int slot,String faction,int color,int team,int difficulty,Position start) {
        public int nativePlayer(){return slot+2;}
        public boolean computer(){return player==null;}
    }
    public void configure(NativeSession session)throws java.io.IOException{
        for(var a:armies)session.send("MATCH_NAME "+a.slot+" "+local.generalsmc.GeneralsMod.JSON.toJson(a.name));
        for(var a:armies)session.send("MATCH_SLOT "+a.slot+" "+(a.computer()?a.difficulty+1:0)+" "+a.color+" "+a.team+" "+local.generalsmc.GeneralsMod.JSON.toJson(a.faction));
        var first=armies.getFirst();var other=armies.size()>1?armies.get(1):first;
        session.start(map,first.faction,other.faction,seed,cash,first.color);
    }
}
