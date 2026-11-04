package local.generalsmc.client;

final class NativeVersionInteractionBench {
    static void board(net.minecraft.client.Minecraft mc,net.minecraft.world.entity.Entity vehicle){mc.gameMode.interact(mc.player,vehicle,new net.minecraft.world.phys.EntityHitResult(vehicle),net.minecraft.world.InteractionHand.MAIN_HAND);}
}
