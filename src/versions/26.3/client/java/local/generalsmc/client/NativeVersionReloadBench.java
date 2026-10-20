package local.generalsmc.client;

import net.minecraft.client.Minecraft;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class NativeVersionReloadBench {
    static int completed;static CompletableFuture<Void> pending;
    static boolean tick(Minecraft mc,List<String> checks){
        int cycles=Integer.getInteger("generalsmc.reloadCycles",0);
        if(cycles==0)return true;
        if(pending!=null){if(!pending.isDone())return false;pending.join();if(mc.gui.overlay()!=null)return false;pending=null;completed++;local.generalsmc.GeneralsMod.LOG.info("Shader reload stability {}/{}; prepared threads {}",completed,cycles,ShaderCallbackStack.preparedThreads());}
        if(completed==cycles){if(Boolean.parseBoolean(System.getProperty("generalsmc.shaderStackGuard","true"))&&ShaderCallbackStack.preparedThreads()<1)throw new IllegalStateException("Shader callback guard never ran");checks.add("Completed "+cycles+" full resource/shader reloads; prepared threads: "+ShaderCallbackStack.preparedThreads());return true;}
        if(mc.gui.overlay()==null)pending=mc.reloadResourcePacks();
        return false;
    }
}
