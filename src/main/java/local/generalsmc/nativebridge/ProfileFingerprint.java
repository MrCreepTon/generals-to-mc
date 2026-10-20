package local.generalsmc.nativebridge;

import local.generalsmc.GeneralsMod;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import java.util.*;

/** Streaming file hashes cached by immutable mount provenance; no retail content is transmitted. */
public final class ProfileFingerprint {
    private record Cached(long size,long modified,String hash) {}
    private record Cache(Map<String,Cached> files) {}
    private ProfileFingerprint(){}
    public static String calculate(Path root,NativeProfiles.Profile profile)throws IOException{
        try{
            var digest=MessageDigest.getInstance("SHA-256");digest.update(("world-scale="+WorldScale.UNITS_PER_BLOCK+";paged="+WorldScale.PAGED_WORLD+";").getBytes(java.nio.charset.StandardCharsets.US_ASCII));var files=new TreeMap<String,Path>();
            var lab=NativeProfiles.lab(root,profile);
            // Relative mount names determine load order; game directories can differ between machines.
            for(String folder:List.of("base","zh"))try(var stream=Files.walk(lab.resolve(folder))){for(var p:stream.filter(Files::isRegularFile).toList()){
                String name=lab.relativize(p).toString().replace('\\','/').toLowerCase(Locale.ROOT);if(name.endsWith(".big")||name.contains("/data/"))files.put(name,p);
            }}
            files.put("engine",NativeProfiles.engine(root));var cacheFile=lab.resolve("fingerprints.json");Map<String,Cached> old=Map.of();
            if(Files.isRegularFile(cacheFile))try{old=GeneralsMod.JSON.fromJson(Files.readString(cacheFile),Cache.class).files;}catch(RuntimeException ignored){}
            var next=new LinkedHashMap<String,Cached>();byte[] buffer=new byte[1024*1024];
            for(var entry:files.entrySet()){
                if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException(local.generalsmc.LocalText.token("Проверка отменена"));var p=entry.getValue();long size=Files.size(p),modified=Files.getLastModifiedTime(p).toMillis();var cached=old.get(entry.getKey());
                if(cached==null||cached.size!=size||cached.modified!=modified){var hash=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(p)){int n;while((n=in.read(buffer))>=0){if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException(local.generalsmc.LocalText.token("Проверка отменена"));hash.update(buffer,0,n);}}cached=new Cached(size,modified,HexFormat.of().formatHex(hash.digest()));}
                next.put(entry.getKey(),cached);digest.update(entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update(cached.hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
            var temp=Files.createTempFile(lab,"fingerprints-",".tmp");try{Files.writeString(temp,GeneralsMod.JSON.toJson(new Cache(next)));Files.move(temp,cacheFile,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
