package local.generalsmc.client.mixin;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
public final class MenuMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String name){}
 public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String name){
  if(!name.endsWith("ModernMenuOpenMixin")&&!name.endsWith("ModernWidgetSuppressionMixin"))return true;
  var loader=FabricLoader.getInstance();String v=loader.getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();return loader.isModLoaded("modernui")&&Set.of("1.21.4","26.1","26.1.1","26.1.2").contains(v);
 }
 public void acceptTargets(Set<String> mine,Set<String> other){}
 public List<String> getMixins(){return null;}
 public void preApply(String target,ClassNode node,String name,IMixinInfo info){}
 public void postApply(String target,ClassNode node,String name,IMixinInfo info){}
}
