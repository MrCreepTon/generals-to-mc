package local.generalsmc.client;

import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.util.*;

/** Bounded QA workspace; never backs up or touches a user's world or game installation. */
final class NativeVersionReuseBench {
    private static net.minecraft.client.gui.screens.Screen handledWorldPrompt;
    static Path root(Minecraft mc)throws Exception {
        Path root=Path.of(System.getProperty("generalsmc.testRoot",mc.gameDirectory.toPath().resolve("generals-test").toString())).toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path marker=root.resolve(".qa-owned");
        if(!Files.exists(marker)){
            if(Files.exists(root.resolve("profiles.json")))throw new IllegalStateException("Refusing to reuse a non-QA profile: "+root);
            Files.writeString(marker,"Generals private test workspace\n");
        }
        System.setProperty("generalsmc.nativeRoot",root.toString());
        // Discard only completed sessions in our explicitly marked QA profile; retain profile mounts.
        Path profiles=root.resolve("profiles");
        if(Files.isDirectory(profiles))try(var dirs=Files.walk(profiles,4)){
            for(Path session:dirs.filter(p->p.getParent()!=null&&p.getParent().getFileName().toString().equals("sessions")&&p.getFileName().toString().matches("[0-9a-f-]{36}")).toList()){
                Path state=session.resolve("state.json");
                if(Files.exists(state)){
                    var json=com.google.gson.JsonParser.parseString(Files.readString(state)).getAsJsonObject();
                    if(json.has("pid")&&ProcessHandle.of(json.get("pid").getAsLong()).filter(ProcessHandle::isAlive).isPresent())throw new IllegalStateException("Previous QA engine is still running: "+session);
                }
                if(!session.toRealPath().startsWith(root.toRealPath()))throw new IllegalStateException("QA session escaped its workspace");
                try(var files=Files.walk(session)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
            }
        }
        Path captures=mc.gameDirectory.toPath().resolve("screenshots");
        if(Files.isDirectory(captures))try(var files=Files.newDirectoryStream(captures,"feedback-*.png")){for(Path file:files)Files.deleteIfExists(file);}
        return root;
    }
    static void connect()throws Exception{
        if(NativeProfiles.active()==null)NativeProfiles.connect("Zero Hour","C:/Games/Generals ZH","C:/Games/Generals","",false,s->{});
    }
    static boolean openExisting(Minecraft mc,String name){
        if(!Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(name).resolve("level.dat")))return false;
        handledWorldPrompt=null;
        var screen=NativeVersionScreenBench.current(mc);mc.createWorldOpenFlows().openWorld(name,()->NativeVersionScreenBench.show(mc,screen));return true;
    }
    static void advanceOwnedWorld(Minecraft mc,java.util.function.Consumer<String> click){
        var current=NativeVersionScreenBench.current(mc);
        if(current==handledWorldPrompt)return;
        String action=current instanceof net.minecraft.client.gui.screens.BackupConfirmScreen?"selectWorld.backupJoinSkipButton":
            current instanceof net.minecraft.client.gui.screens.worldselection.ConfirmExperimentalFeaturesScreen?"gui.proceed":null;
        if(action==null)return;
        // Loading runs asynchronously; a second click can close the same save lock mid-load.
        handledWorldPrompt=current;
        String label=net.minecraft.client.resources.language.I18n.get(action);
        click.accept(label);
    }
}
