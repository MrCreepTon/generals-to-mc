package local.generalsmc.mixin;

import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.nio.file.Path;

@Mixin(SavedDataStorage.class)
public interface SavedDataFolderAccessor {
    @Accessor("dataFolder") Path generalsmc$dataFolder();
}
