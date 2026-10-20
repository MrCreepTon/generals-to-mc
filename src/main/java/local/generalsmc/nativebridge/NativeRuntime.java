package local.generalsmc.nativebridge;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** The release JAR carries only our source-built engine; retail assets stay in the user's game. */
public final class NativeRuntime {
    private static final String PREFIX="/generalsmc/runtime/windows-x86/";
    private NativeRuntime(){}
    public static boolean bundled(){return NativeRuntime.class.getResource(PREFIX+"runtime.properties")!=null;}
    public static synchronized Path prepare(Path root)throws IOException{
        if(!bundled()){
            Path portable=root.resolve("runtime/generalszh.exe");
            return Files.isRegularFile(portable)?portable:root.resolve("native-src/GeneralsGameCode/build/minecraft-bridge/GeneralsMD/Release/generalszh.exe");
        }
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))throw new IOException(local.generalsmc.LocalText.token("Для запуска Generals сейчас нужна Windows. Сам Minecraft можно продолжить без RTS."));
        var properties=new Properties();try(var in=NativeRuntime.class.getResourceAsStream(PREFIX+"runtime.properties")){properties.load(in);}
        String hash=properties.getProperty("sha256","");if(!hash.matches("[0-9a-f]{64}"))throw new IOException(local.generalsmc.LocalText.token("Файл мода повреждён. Установите JAR заново."));
        Path target=root.resolve("runtime").resolve(hash).resolve("generalszh.exe");
        if(Files.isRegularFile(target)&&hash.equals(sha256(target)))return target;
        Files.createDirectories(target.getParent());Path temp=Files.createTempFile(target.getParent(),"engine-",".tmp");
        try{
            try(var in=NativeRuntime.class.getResourceAsStream(PREFIX+"generalszh.exe")){if(in==null)throw new IOException(local.generalsmc.LocalText.token("В JAR отсутствует движок Generals. Скачайте полный файл мода."));Files.copy(in,temp,StandardCopyOption.REPLACE_EXISTING);}
            if(!hash.equals(sha256(temp)))throw new IOException(local.generalsmc.LocalText.token("Не удалось проверить файл мода. Установите JAR заново."));
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
            return target;
        }finally{Files.deleteIfExists(temp);}
    }
    public static String sha256(Path file)throws IOException{
        try{var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(file)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}return HexFormat.of().formatHex(digest.digest());}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
