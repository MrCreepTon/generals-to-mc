package local.generalsmc.client;

import local.generalsmc.GeneralsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.concurrent.*;

/** Actual vanilla input and interaction after F8; only operates in the caller's fresh test world. */
final class NativeUsabilityBench {
    static int age;static Vec3 start;static BlockPos block;static CompletableFuture<?> work;
    static boolean tick(Minecraft mc,List<String> checks){
        age++;
        if(MenuScreens.active(mc.screen)!=null||NativeWorldView.controlling())throw new IllegalStateException("F8 left an input/camera screen active");
        if(age==1){if(mc.isWindowActive()&&!mc.mouseHandler.isMouseGrabbed())throw new IllegalStateException("Active Minecraft window did not recapture its mouse after F8");net.minecraft.client.KeyMapping.releaseAll();mc.player.setDeltaMovement(Vec3.ZERO);start=mc.player.position();mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,start.add(2,-.01,0));mc.gameRenderer.pick(1);if(!(mc.hitResult instanceof BlockHitResult))throw new IllegalStateException("Ordinary crosshair cannot target floor from original base: "+mc.hitResult);}
        if(age==21){if(mc.player.position().subtract(start).horizontalDistance()>.05)throw new IllegalStateException("Stationary commander was pushed by a source proxy: "+start+" -> "+mc.player.position());checks.add("A stationary Minecraft commander remains still inside the source base; invisible proxies do not apply vanilla crowd pushing");mc.player.setYRot(0);mc.player.setXRot(25);}
        if(age>=21&&age<121)net.minecraft.client.KeyMapping.set(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(mc.options.keyUp),true);
        if(age==121){net.minecraft.client.KeyMapping.set(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(mc.options.keyUp),false);var moved=mc.player.position().subtract(start);if(moved.horizontalDistance()<2)throw new IllegalStateException("Ordinary Minecraft walking blocked after F8: "+start+" -> "+mc.player.position()+", input="+mc.player.input.keyPresses+", mouse="+mc.mouseHandler.isMouseGrabbed());
            var pos=mc.player.position();var id=mc.player.getUUID();work=CompletableFuture.runAsync(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);if(p.position().distanceTo(pos)>1)throw new IllegalStateException("Walking was only client-side");},mc.getSingleplayerServer());
            checks.add("After F8, vanilla forward-key input really walks over two blocks and the Minecraft server agrees; no player-position teleport is used");
        }
        if(age==125){work.join();block=mc.player.blockPosition().offset(2,0,0);var held=new ItemStack(Items.COBBLESTONE,16);mc.player.setItemInHand(InteractionHand.MAIN_HAND,held);var id=mc.player.getUUID();work=CompletableFuture.runAsync(()->{var l=mc.getSingleplayerServer().getLevel(mc.level.dimension());l.setBlock(block,Blocks.AIR.defaultBlockState(),3);l.setBlock(block.below(),Blocks.STONE.defaultBlockState(),3);mc.getSingleplayerServer().getPlayerList().getPlayer(id).setItemInHand(InteractionHand.MAIN_HAND,held.copy());},mc.getSingleplayerServer());}
        if(age==132){work.join();mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,Vec3.atBottomCenterOf(block).add(0,-.01,0));mc.gameRenderer.pick(1);if(!(mc.hitResult instanceof BlockHitResult hit)||!hit.getBlockPos().equals(block.below()))throw new IllegalStateException("Actual Minecraft crosshair cannot place a block: "+mc.hitResult);var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);GeneralsMod.LOG.info("Vanilla placement after F8: {} at {}",result,block);}
        if(age==145){work=CompletableFuture.runAsync(()->{var l=mc.getSingleplayerServer().getLevel(mc.level.dimension());if(!l.getBlockState(block).is(Blocks.COBBLESTONE))throw new IllegalStateException("Minecraft placement blocked after F8: "+l.getBlockState(block));},mc.getSingleplayerServer());}
        if(age==150){work.join();mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,Vec3.atCenterOf(block));mc.gameRenderer.pick(1);if(!(mc.hitResult instanceof BlockHitResult hit)||!hit.getBlockPos().equals(block))throw new IllegalStateException("Actual Minecraft crosshair cannot break the placed block: "+mc.hitResult);mc.gameMode.startDestroyBlock(block,hit.getDirection());}
        if(age==160){work=CompletableFuture.runAsync(()->{var l=mc.getSingleplayerServer().getLevel(mc.level.dimension());if(!l.getBlockState(block).isAir())throw new IllegalStateException("Minecraft breaking blocked after F8");},mc.getSingleplayerServer());}
        if(age>=165&&work.isDone()){work.join();checks.add("Actual Minecraft crosshair targets the floor from inside the source base; after walking outdoors, right-click placement and creative left-click breaking both reach the server");return true;}
        return false;
    }
}
