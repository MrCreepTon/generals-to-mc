package local.generalsmc.client;

import local.generalsmc.nativebridge.*;
import local.generalsmc.GeneralsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.*;

/** Fire-contact appearance remains separate from real server/original damage. */
final class NativeFireBench {
    static int age,id;static double health;static CompletableFuture<?> work;
    static boolean tick(Minecraft mc,List<String> checks)throws Exception{
        age++;var v=NativeWorldView.current;
        if(age==1)NativeReleaseBench.spawn(v,"AmericaVehicleDozer",new Vec3(12.5,64,-20.5));
        if(age==40){var a=v.state.objects().stream().filter(o->o.template().equals("AmericaVehicleDozer")&&v.origin.world(o.x(),o.y(),o.z()).distanceTo(new Vec3(12.5,64,-20.5))<2).findFirst().orElseThrow();id=a.id();health=a.health();work=CompletableFuture.runAsync(()->{var l=mc.getSingleplayerServer().getLevel(GeneralsMod.ARENA);l.setBlock(new BlockPos(12,63,-21),Blocks.NETHERRACK.defaultBlockState(),3);l.setBlock(new BlockPos(12,64,-21),Blocks.FIRE.defaultBlockState(),3);},mc.getSingleplayerServer());}
        if(age==80){work.join();work=CompletableFuture.runAsync(()->{var proxy=NativeRtsController.combat.actor(id);if(proxy==null||!proxy.isOnFire()||proxy.displayFireAnimation())throw new IllegalStateException("Vanilla fire contact/appearance mismatch: "+proxy);},mc.getSingleplayerServer());}
        if(age>=120){work.join();var a=v.actor(id);if(a!=null&&a.health()>=health)throw new IllegalStateException("Vanilla contact fire never damaged original Dozer");checks.add("Real Minecraft fire ignites an original Dozer and reduces original health, while its invisible collision proxy suppresses disproportionate vanilla fire quads");return true;}
        return false;
    }
}
