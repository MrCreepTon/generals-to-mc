package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.NativeProfiles;
import java.nio.file.*;
import java.util.*;

/** Last accepted choices, scoped to the connected game profile. */
final class ArmyPreferences {
    record Choice(String faction,int color,int team,int start) {}
    private static final Choice DEFAULT=new Choice("",-1,-1,-1);
    private static final Path FILE=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("generalsmc-army.json");
    private static final Map<String,Choice> choices=new HashMap<>();private static boolean loaded;
    static Choice get(NativeProfiles.Profile profile){
        if(!loaded){loaded=true;try{if(Files.isRegularFile(FILE)&&Files.size(FILE)<65536){var data=GeneralsMod.JSON.fromJson(Files.readString(FILE),com.google.gson.JsonObject.class);for(var entry:data.entrySet())choices.put(entry.getKey(),GeneralsMod.JSON.fromJson(entry.getValue(),Choice.class));}}catch(Exception e){GeneralsMod.LOG.warn("Army preferences could not be read",e);}}
        if(profile==null)return DEFAULT;var c=choices.getOrDefault(profile.id(),DEFAULT);if(c==null)return DEFAULT;
        return new Choice(profile.factions()!=null&&profile.factions().contains(c.faction())?c.faction():"",profile.colors()!=null&&profile.colors().stream().anyMatch(color->color.id()==c.color())?c.color():-1,Math.clamp(c.team(),-2,7),Math.clamp(c.start(),-1,7));
    }
    static void save(NativeProfiles.Profile profile,Choice value){
        if(profile==null)return;get(profile);if(value.equals(choices.get(profile.id())))return;choices.put(profile.id(),value);
        try{Files.createDirectories(FILE.getParent());var temp=FILE.resolveSibling(FILE.getFileName()+".tmp");Files.writeString(temp,GeneralsMod.JSON.toJson(choices));Files.move(temp,FILE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){GeneralsMod.LOG.warn("Army preferences could not be saved",e);}
    }
}
