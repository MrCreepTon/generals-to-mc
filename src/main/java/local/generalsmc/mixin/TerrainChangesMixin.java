package local.generalsmc.mixin;

import local.generalsmc.nativebridge.TerrainSynchronizer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publish changed collision tiles before the background terrain sweep reaches them. */
@Mixin(ServerLevel.class)
public abstract class TerrainChangesMixin {
    @Inject(method="sendBlockUpdated",at=@At("RETURN"))
    private void generalsTerrainChanged(BlockPos pos,BlockState before,BlockState after,int flags,CallbackInfo info){
        if(before!=after)TerrainSynchronizer.changed((ServerLevel)(Object)this,pos);
    }
}
