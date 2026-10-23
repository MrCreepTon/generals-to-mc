package local.generalsmc.mixin;
import local.generalsmc.nativebridge.WorldEmp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class EmpSignalMixin {
    @Inject(method={"getSignal","getDirectSignal"},at=@At("HEAD"),cancellable=true)
    private void generalsEmpSignal(BlockGetter world,BlockPos pos,Direction direction,CallbackInfoReturnable<Integer> result){if(WorldEmp.suppressed(world,pos))result.setReturnValue(0);}
}
