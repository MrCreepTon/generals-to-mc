package local.generalsmc.nativebridge;

import local.generalsmc.GeneralsMod;
import local.generalsmc.mixin.SavedDataFolderAccessor;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.ArrayList;

/** Imports our pre-26 saved data once; normal saving owns the new namespaced file. */
final class LegacySavedData {
    private LegacySavedData() {}

    static <T extends SavedData> T get(ServerLevel level,SavedDataType<T> type){
        var storage=level.getDataStorage();
        T current=storage.get(type);
        if(current!=null)return current;
        var folder=((SavedDataFolderAccessor)storage).generalsmc$dataFolder();
        // Even an unreadable current file must not resurrect stale legacy state.
        if(Files.exists(type.id().withSuffix(".dat").resolveAgainst(folder)))return storage.computeIfAbsent(type);
        String file=type.id().getPath()+".dat";
        var candidates=new ArrayList<Path>();candidates.add(folder.resolve(file));
        var root=level.getServer().getWorldPath(LevelResource.ROOT);
        if(level.dimension().equals(Level.OVERWORLD))candidates.add(root.resolve("data").resolve(file));
        else if(level.dimension().equals(Level.NETHER))candidates.add(root.resolve("DIM-1/data").resolve(file));
        else if(level.dimension().equals(Level.END))candidates.add(root.resolve("DIM1/data").resolve(file));
        for(var legacy:candidates){
            if(!Files.isRegularFile(legacy))continue;
            try{
                var tag=NbtIo.readCompressed(legacy,NbtAccounter.create(64L*1024*1024));
                var ops=level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                T migrated=type.codec().parse(ops,tag.getCompoundOrEmpty("data")).getOrThrow();
                migrated.setDirty();storage.set(type,migrated);
                GeneralsMod.LOG.info("Imported legacy world data {}",type.id());
                return migrated;
            }catch(Exception error){GeneralsMod.LOG.error("Could not import legacy world data {} from {}; original retained",type.id(),legacy,error);}
        }
        return storage.computeIfAbsent(type);
    }
}
