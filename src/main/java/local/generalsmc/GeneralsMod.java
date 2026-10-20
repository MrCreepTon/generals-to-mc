package local.generalsmc;

import com.google.gson.Gson;
import local.generalsmc.nativebridge.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;
import org.slf4j.*;

/** Minecraft world integration; all RTS rules are owned by the original engine. */
public final class GeneralsMod implements ModInitializer {
    public static final Logger LOG=LoggerFactory.getLogger("generalsmc");
    public static final Gson JSON=new Gson();
    public static final ResourceKey<Level> ARENA=ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath("generalsmc","battlefield"));
    @Override public void onInitialize(){
        NativeNetworkServer.install();NativeActorEntity.register();SiteBlocks.register();
        ServerEntityEvents.ENTITY_LOAD.register(WorldCombat::entityLoaded);
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(WorldCombat::entityDied);
        ServerTickEvents.END_WORLD_TICK.register(WorldCombat::worldTick);
        ServerTickEvents.END_WORLD_TICK.register(WorldViewSubscription::tick);
        ServerChunkEvents.CHUNK_LOAD.register((level,chunk)->WorldResourceStore.get(level).chunkLoaded(chunk.getPos().x,chunk.getPos().z));
        ServerTickEvents.END_WORLD_TICK.register(level->{if(level.getGameTime()%4==0&&WorldRules.get(level).values().naturalResources())WorldResourceStore.get(level).generateLoaded(level);});
    }
}
