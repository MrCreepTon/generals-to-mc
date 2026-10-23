package local.generalsmc.release;

import local.generalsmc.nativebridge.*;
import net.minecraft.nbt.CompoundTag;
import java.nio.file.*;
import java.util.*;

/** Asset-free release checks: migration, persistence and installation from the actual bundled payload. */
public final class ReleaseChecks {
    private static int checks;
    private static void require(boolean ok,String detail){if(!ok)throw new AssertionError(detail);checks++;}
    private static void oldPreparationCheck(WorldRules.Values value){var rules=new WorldRules();rules.update(value);require(WorldRules.load(rules.save(new CompoundTag(),null)).values().equals(value),"Starting area preparation survives save/load");}
    public static void main(String[] args)throws Exception{
        require(Files.isDirectory(Path.of(NativeProfiles.browseStart(""))),"Empty folder picker starts in an existing directory");
        require(Files.isDirectory(Path.of(NativeProfiles.browseStart("\0"))),"Invalid picker input falls back without a crash");
        var layout=Files.createTempDirectory("generals-folders-");try{
            var base=Files.createDirectory(layout.resolve("Base"));var zh=Files.createDirectory(layout.resolve("Expansion"));
            Files.createFile(base.resolve("INI.big"));Files.createFile(base.resolve("W3D.big"));Files.createFile(zh.resolve("INIZH.big"));Files.createFile(zh.resolve("W3DZH.big"));
            var found=NativeProfiles.locate(layout.toString());require(found.zeroHour().equals(zh.toString())&&found.generals().equals(base.toString()),"A collection folder detects both games without relying on folder names");
            require(NativeProfiles.suggestBase(zh.toString()).equals(base.toString()),"Zero Hour resolves an adjacent base game");
        }finally{try(var files=Files.walk(layout)){for(var file:files.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}}
        var emp=WorldRules.Values.defaults().toggle("empMobs").toggle("empRedstone");var empRules=new WorldRules();empRules.update(emp);
        require(WorldRules.load(empRules.save(new CompoundTag(),null)).values().equals(emp),"EMP settings round trip in world data");
        require(!emp.toggle("fireSpread").emp().mobs()&&!emp.toggle("blastPower").emp().redstone(),"Unrelated rules preserve independent EMP settings");

        byte[] radar=new byte[65572];Arrays.fill(radar,(byte)64);var compressed=local.generalsmc.net.NativePayloadCompression.encode(radar);
        for(String marker:List.of("army_start_marker","supply_marker","oil_derrick_marker")){
            try(var stream=ReleaseChecks.class.getResourceAsStream("/assets/generalsmc/models/block/"+marker+".json")){
                if(stream==null)throw new AssertionError("Missing marker model: "+marker);
                var model=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for(var element:model.getAsJsonArray("elements"))for(var face:element.getAsJsonObject().getAsJsonObject("faces").asMap().values()){
                    var uv=face.getAsJsonObject().getAsJsonArray("uv");if(uv==null||uv.size()!=4)throw new AssertionError("Tall marker face has implicit atlas UV: "+marker);
                    for(var value:uv)if(!Double.isFinite(value.getAsDouble())||value.getAsDouble()<0||value.getAsDouble()>16)throw new AssertionError("Marker UV escapes its sprite: "+marker);
                }require(true,"Tall marker faces stay within their own atlas sprite: "+marker);
            }
        }
        require(compressed.length<radar.length/10&&Arrays.equals(radar,local.generalsmc.net.NativePayloadCompression.decode(compressed,65572)),"Radar is lossless and more than90% smaller on uniform terrain");
        byte[] noisy=new byte[4096];new Random(42).nextBytes(noisy);require(Arrays.equals(noisy,local.generalsmc.net.NativePayloadCompression.decode(local.generalsmc.net.NativePayloadCompression.encode(noisy),4096)),"Incompressible frame data retains exact bytes");
        try{local.generalsmc.net.NativePayloadCompression.decode(compressed,10);throw new AssertionError("Oversized expansion accepted");}catch(java.io.IOException expected){checks++;}
        try{local.generalsmc.net.NativePayloadCompression.decode(Arrays.copyOf(compressed,compressed.length-1),65572);throw new AssertionError("Truncated stream accepted");}catch(java.io.IOException expected){checks++;}
        var pacer=new NativeTurnPacer(0);int next=0;
        for(int i=1;i<=200;i++){int turns=pacer.allowance(i*50_000_000L,next,next,next);for(int n=0;n<turns;n++){next++;pacer.committed();}}
        require(next==300,"Healthy20Hz server schedules exactly30 logic turns per second");
        var stalled=new NativeTurnPacer(0);int queued=0;
        for(int i=1;i<=300;i++){int turns=stalled.allowance(i*50_000_000L,queued,0,queued);for(int n=0;n<turns;n++){queued++;stalled.committed();}}
        require(queued<=NativeTurnPacer.MAX_PEER_LEAD,"A15second stalled peer cannot accumulate an unbounded simulation queue");
        int burst=stalled.allowance(15_050_000_000L,queued,queued,queued);for(int n=0;n<burst;n++)stalled.committed();
        require(burst<=3&&stalled.allowance(15_100_000_000L,queued+burst,queued+burst,queued+burst)<=2,"Recovery returns to normal pacing without replaying elapsed stall debt");
        var networkWindow=new NativeTurnPacer(0);int[] history=new int[201];int delivered=0;
        for(int i=1;i<=200;i++){int acknowledged=history[Math.max(0,i-6)];int turns=networkWindow.allowance(i*50_000_000L,delivered,acknowledged,delivered);for(int n=0;n<turns;n++){delivered++;networkWindow.committed();}history[i]=delivered;}
        require(delivered==300,"A300ms acknowledgement path retains30Hz without increasing the authority execution queue");
        var markerData=new WorldSiteStore();var markerPos=new net.minecraft.core.BlockPos(1024,64,-2048);markerData.putStart(markerPos,0,"",0,-1);markerData.annotate(markerPos,"Village","Defend the villagers");var loadSites=WorldSiteStore.class.getDeclaredMethod("load",CompoundTag.class);loadSites.setAccessible(true);var restored=(WorldSiteStore)loadSites.invoke(null,markerData.save(new CompoundTag(),null));require(restored.annotation(markerPos).equals(markerData.annotation(markerPos)),"Marker names and notes survive a full NBT load");restored.remove(markerPos);require(!restored.contains(markerPos)&&restored.annotation(markerPos).name().isEmpty(),"Deleting a marker removes its annotation");
        var d=WorldRules.Values.defaults();require(d.blockDestruction()&&d.fireSpread()&&d.naturalResources(),"Compatible defaults");
        require(!d.affectsTerrain(false)&&d.affectsTerrain(true),"Ordinary combat preserves blocks; force attack can destroy cover");
        var any=d.toggle("blockDestruction");var off=any.toggle("blockDestruction");
        require(any.affectsTerrain(false)&&any.affectsTerrain(true)&&!off.affectsTerrain(false)&&!off.affectsTerrain(true)&&off.toggle("blockDestruction").equals(d),"Three terrain modes cycle without altering unrelated rules");
        var prepared=d.toggle("prepareStarts");oldPreparationCheck(prepared);
        var old=WorldRules.load(new CompoundTag());require(old.values().equals(d),"Old worlds without settings retain defaults");
        require(d.mobGeneralExperience()&&d.eliminationVictory(),"Old worlds enable general experience and normal skirmish victory");
        var optional=d.toggle("mobGeneralExperience").toggle("eliminationVictory");old.update(optional);
        require(WorldRules.load(old.save(new CompoundTag(),null)).values().equals(optional),"Optional experience and victory choices persist");
        for(String key:List.of("blockDestruction","fireSpread","hostileMobs","neutralRetaliation","environmentDamage","naturalResources","blastRadius","blastPower")){
            var toggled=optional.toggle(key);if(toggled.mobGeneralExperience()||toggled.eliminationVictory())throw new AssertionError("Another control reset optional choices: "+key);
        }
        require(true,"All other world controls preserve disabled experience and victory choices");
        var changed=d.toggle("fireSpread").toggle("naturalResources").toggle("neutralRetaliation");old.update(changed);
        require(WorldRules.load(old.save(new CompoundTag(),null)).values().equals(changed),"Rules survive world persistence");
        var partial=new CompoundTag();partial.putBoolean("blockDestruction",false);var migrated=WorldRules.load(partial).values();require(!migrated.blockDestruction()&&migrated.fireSpread()&&migrated.hostileMobs(),"Adding settings preserves previous explicit choices");
        try{d.toggle("unknown");throw new AssertionError("Unknown rule accepted");}catch(IllegalArgumentException expected){checks++;}
        var strength=d.toggle("blastRadius").toggle("blastPower");old.update(strength);
        require(WorldRules.load(old.save(new CompoundTag(),null)).values().equals(strength),"Block radius and penetration persist with existing rules");
        var invalid=new CompoundTag();invalid.putInt("blastRadius",999);invalid.putInt("blastPower",-9);
        require(WorldRules.load(invalid).values().blastRadius()==4&&WorldRules.load(invalid).values().blastPower()==1,"Corrupt block multipliers are bounded on world load");
        require(NativeRuntime.bundled(),"Release includes its engine");
        String os=System.getProperty("os.name");Path fixture=Files.createTempDirectory("generalsmc-release-");
        try{
            System.setProperty("os.name","Windows fixture");Path first=NativeRuntime.prepare(fixture);String hash=NativeRuntime.sha256(first);
            require(Files.size(first)>1000000&&hash.equals(first.getParent().getFileName().toString()),"Bundled engine verified in its immutable hash directory");
            require(NativeRuntime.prepare(fixture).equals(first),"Repeated installation is idempotent");
            Files.writeString(first,"damaged fixture payload");require(NativeRuntime.sha256(NativeRuntime.prepare(fixture)).equals(hash),"Damaged cached engine is repaired from JAR");
            Path other=NativeRuntime.prepare(fixture.resolve("other"));require(!other.equals(first)&&NativeRuntime.sha256(other).equals(hash),"Independent profiles install the same verified payload");
            System.setProperty("os.name","Unsupported fixture");try{NativeRuntime.prepare(fixture);throw new AssertionError("Unsupported platform silently launched");}catch(java.io.IOException expected){require(expected.getMessage().contains("{#generalsmc.text."),"Unsupported platform reports a useful error");}
        }finally{System.setProperty("os.name",os);try(var paths=Files.walk(fixture)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
        System.out.println("RELEASE_CHECKS PASS: "+checks+" checks; no retail files or accounts used");
    }
}
