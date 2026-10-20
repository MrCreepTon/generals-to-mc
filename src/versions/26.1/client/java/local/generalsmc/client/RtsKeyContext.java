package local.generalsmc.client;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import java.util.Map;
/** RTS actions are dispatched by their screen, never by the vanilla gameplay map. */
public final class RtsKeyContext {
    private RtsKeyContext(){}
    public static boolean contextual(KeyMapping key){String name=key.getName();return name.startsWith("key.generalsmc.")&&!name.equals("key.generalsmc.native_toggle")&&!name.equals("key.generalsmc.menu");}
    public static int contexts(KeyMapping key){return contextual(key)?2:key.getName().equals("key.generalsmc.native_toggle")||key.getName().equals("key.generalsmc.menu")?3:1;}
    public static void restore(Map<String,KeyMapping> all,Map<InputConstants.Key,java.util.List<KeyMapping>> gameplay){
        if(all.values().stream().noneMatch(RtsKeyContext::contextual))return;
        gameplay.clear();for(var key:all.values())if(!contextual(key))gameplay.computeIfAbsent(KeyMappingHelper.getBoundKeyOf(key),ignored->new java.util.ArrayList<>()).add(key);
    }
}
