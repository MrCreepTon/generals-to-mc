package local.generalsmc.client;
import local.generalsmc.nativebridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.PrimedTnt;
import java.util.*;
/** An actual fused Minecraft TNT explosion beside the edge of an original large base. */
final class NativeBlastBench {
    static int age,id;static double health;static volatile boolean placed;static boolean done;
    static void tick(Minecraft mc,List<String> checks)throws Exception{
        if(done)return;age++;var v=NativeWorldView.current;
        if(age==1){var base=v.state.objects().stream().filter(a->a.owner()==2&&a.structure()).findFirst().orElseThrow();id=base.id();health=base.health();
            mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().getLevel(mc.level.dimension());var actor=level.getAllEntities().iterator();NativeActorEntity body=null;while(actor.hasNext()){var e=actor.next();if(e instanceof NativeActorEntity n&&n.nativeId==id){body=n;break;}}if(body==null)throw new IllegalStateException("Missing base collision body");var box=body.getBoundingBox();double x=box.maxX+1.2,y=box.minY+.5,z=(box.minZ+box.maxZ)/2;var tnt=new PrimedTnt(level,x,y,z,null);tnt.setDeltaMovement(0,0,0);tnt.setFuse(3);level.addFreshEntity(tnt);placed=true;});
        }else if(age>20&&placed&&v.actor(id)!=null&&v.actor(id).health()<health){checks.add("A real fused Minecraft TNT damages the original large command centre at its outer wall; damage is measured in the authoritative native snapshot");done=true;}
        else if(age>100)throw new IllegalStateException("Real TNT at building edge did not reduce original health: "+v.actor(id));
    }
}
