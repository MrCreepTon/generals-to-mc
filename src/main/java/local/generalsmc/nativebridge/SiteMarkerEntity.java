package local.generalsmc.nativebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** Renderer-only editor landmark. Match settings remain in world saved data. */
public final class SiteMarkerEntity extends BlockEntity {
    public static BlockEntityType<SiteMarkerEntity> TYPE;
    public SiteMarkerEntity(BlockPos pos,BlockState state){super(TYPE,pos,state);}
}
