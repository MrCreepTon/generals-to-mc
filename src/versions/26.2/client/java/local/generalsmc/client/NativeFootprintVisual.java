package local.generalsmc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import local.generalsmc.nativebridge.WorldScale;
import java.util.*;

final class NativeFootprintVisual {
    static List<NativeWorldView.Batch> batches(NativeWorldView view,Minecraft mc){
        if(!mc.debugEntries.isCurrentlyEnabled(net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES))return List.of();var out=new ArrayList<NativeWorldView.Batch>();var visible=view.sceneActors();
        for(var actor:view.state.objects())if(actor.structure()&&actor.health()>0&&visible.contains(actor.id())){
            var points=new Vec3[8];double c=Math.cos(actor.orientation()),s=Math.sin(actor.orientation());var center=view.overheadAnchor(actor.id(),0,0,0,true);
            for(int i=0;i<8;i++){double x=(i%4==0||i%4==3?-1:1)*actor.geometryMajor()/WorldScale.UNITS_PER_BLOCK,z=(i%4<2?-1:1)*actor.geometryMinor()/WorldScale.UNITS_PER_BLOCK;points[i]=center.add(x*c-z*s,i<4?0:Math.max(2.1,actor.geometryHeight()/WorldScale.UNITS_PER_BLOCK),x*s+z*c);}
            for(int i=0;i<4;i++){line(view,mc,out,points[i],points[(i+1)%4]);line(view,mc,out,points[i+4],points[(i+1)%4+4]);line(view,mc,out,points[i],points[i+4]);}
        }return NativeArrowVisual.merge(out);
    }
    private static void line(NativeWorldView view,Minecraft mc,List<NativeWorldView.Batch> out,Vec3 a,Vec3 b){
        var eye=mc.gameRenderer.mainCamera().position();var side=b.subtract(a).cross(eye.subtract(a)).normalize().scale(Math.max(.012,eye.distanceTo(a)*.0006));var points=new Vec3[]{a.subtract(side),b.subtract(side),b.add(side),a.add(side)};int[] indices={0,1,2,0,2,3},colors=new int[6];float[] vertices=new float[48];Arrays.fill(colors,-1);
        for(int i=0;i<6;i++){var p=view.origin.nativePoint(points[indices[i]]);int k=i*8;vertices[k]=(float)p.x;vertices[k+1]=(float)p.y;vertices[k+2]=(float)p.z;vertices[k+5]=1;}out.add(new NativeWorldView.Batch(0,0,0,0x33f52,vertices,colors,true));
    }
}
