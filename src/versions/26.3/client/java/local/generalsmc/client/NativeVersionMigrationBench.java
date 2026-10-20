package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.worldselection.ConfirmExperimentalFeaturesScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Opens an isolated copy of the actual older-version oracle world. Never modifies its source. */
final class NativeVersionMigrationBench {
    static int ticks,stage; static boolean done; static CompletableFuture<?> work; static Path world; static byte[] oldSites;
    static final List<String> checks=new ArrayList<>();
    static void tick(Minecraft mc){if(done)return;try{
        if(++ticks>2400)throw new IllegalStateException("Upgrade oracle timeout at "+stage+" / "+mc.gui.screen());
        if(stage==0&&ticks>30){
            var source=Path.of(System.getProperty("generalsmc.upgradeWorld")).toAbsolutePath();
            world=mc.gameDirectory.toPath().resolve("saves/GeneralsUpgrade-"+UUID.randomUUID());
            try(var paths=Files.walk(source)){for(var p:paths.toList()){var out=world.resolve(source.relativize(p));if(Files.isDirectory(p))Files.createDirectories(out);else if(!p.getFileName().toString().equals("session.lock"))Files.copy(p,out);}}
            // Keep an explicit depleted-stock fixture in the legacy format, in this private copy only.
            var file=world.resolve("dimensions/generalsmc/battlefield/data/generals_resources.dat");
            oldSites=Files.readAllBytes(file.resolveSibling("generals_sites.dat"));
            var tag=NbtIo.readCompressed(file,NbtAccounter.defaultQuota());var data=tag.getCompoundOrEmpty("data");
            var nodes=new ListTag();var node=new CompoundTag();node.putString("key","upgrade-stock");node.putInt("x",128);node.putDouble("y",64);node.putInt("z",128);node.putString("template","SupplyDock");node.putInt("cash",1234);nodes.add(node);data.put("nodes",nodes);tag.put("data",data);NbtIo.writeCompressed(tag,file);
            stage=1;mc.createWorldOpenFlows().openWorld(world.getFileName().toString(),()->{throw new IllegalStateException("Upgrade cancelled");});
        }else if(stage==1){
            var screen=mc.gui.screen();
            if(screen instanceof BackupConfirmScreen||screen instanceof ConfirmExperimentalFeaturesScreen||(screen instanceof net.minecraft.client.gui.screens.ConfirmScreen&&screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("upgradeWorld.done").getString()))){var button=screen.children().stream().filter(w->w instanceof Button b&&b.active).map(w->(Button)w).findFirst().orElseThrow();NativeVersionBench.click(mc,button.getMessage().getString());}
            if(mc.player!=null&&mc.getSingleplayerServer()!=null){stage=2;work=CompletableFuture.runAsync(()->{
                var level=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);var pos=new BlockPos(24,64,24);var sites=WorldSiteStore.get(level);
                String expected=Boolean.getBoolean("generalsmc.upgradeCurrent")?"Current file wins":"Reserve for defence";
                if(!sites.contains(pos)||!sites.annotation(pos).note().equals(expected))throw new IllegalStateException("Marker note was lost: expected "+expected+", got "+sites.annotation(pos).note());
                checks.add(Boolean.getBoolean("generalsmc.upgradeCurrent")?"Reopening prefers edited current data over retained legacy data":"Real 1.21.11 world keeps its saved marker and note after opening in 26.3");
                if(WorldRules.get(level).values().naturalResources())throw new IllegalStateException("Legacy world rule was reset");
                checks.add("Older per-dimension rules survive the changed SavedData paths");
                if(WorldResourceStore.get(level).nodes().stream().noneMatch(n->n.key().equals("upgrade-stock")&&n.cash()==1234))throw new IllegalStateException("Legacy resource stock was lost");
                checks.add("Previously depleted supply stock survives migration");
                sites.annotate(pos,"Upgraded supplies","Current file wins");level.getDataStorage().saveAndJoin();
                if(!Files.exists(world.resolve("dimensions/generalsmc/battlefield/data/minecraft/generals_sites.dat")))throw new IllegalStateException("Migrated data was not saved in the new layout");
                checks.add("Normal saving writes the new layout and retains legacy files");
                try{if(!Arrays.equals(oldSites,Files.readAllBytes(world.resolve("dimensions/generalsmc/battlefield/data/generals_sites.dat"))))throw new IllegalStateException("Legacy file was modified");}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
                checks.add("Import and later editing leave the original legacy marker file byte-identical");
            },mc.getSingleplayerServer());}
        }else if(stage==2&&work.isDone()){work.join();finish(mc,"PASS","");}
    }catch(Throwable error){GeneralsMod.LOG.error("World upgrade oracle failed",error);finish(mc,"FAIL",error.toString());}}
    static void finish(Minecraft mc,String result,String error){done=true;try{Files.writeString(Path.of(System.getProperty("generalsmc.evidence"),"native-version-tests.json"),GeneralsMod.JSON.toJson(Map.of("minecraft","26.3","scope","real 1.21.11 world upgrade","result",result,"checks",checks,"world",String.valueOf(world),"error",error)));}catch(Exception e){GeneralsMod.LOG.error("Upgrade report failed",e);}mc.stop();}
}
