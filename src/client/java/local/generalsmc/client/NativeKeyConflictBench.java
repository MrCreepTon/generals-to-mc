package local.generalsmc.client;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.*;
import java.util.List;
/** Uses the physical-key dispatcher, rather than directly setting keyUp. */
final class NativeKeyConflictBench {
    static void check(Minecraft mc,List<String> checks){
        KeyMapping.resetMapping();KeyMapping.releaseAll();
        for(var binding:List.of(mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyAttack,mc.options.keyUse)){
            var physical=KeyBindingHelper.getBoundKeyOf(binding);KeyMapping.set(physical,true);
            if(!binding.isDown())throw new IllegalStateException("RTS bindings shadow ordinary Minecraft input: "+binding.getName()+" / "+physical.getName());
            KeyMapping.set(physical,false);if(binding.isDown())throw new IllegalStateException("Minecraft key release was swallowed");
        }
        checks.add("Actual KeyMapping physical-key dispatch and release reach vanilla WASD, attack and use despite overlapping RTS bindings, including after resetMapping");
    }
}
