package local.generalsmc.client;

import local.generalsmc.nativebridge.NativeActorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Arrow hits attach to original triangles, so animation and render interpolation stay shared. */
final class NativeArrowVisual {
    private record Hit(long part,int vertex,double u,double v,double dx,double dy,double dz){}
    private final Map<String,Hit> hits=new HashMap<>();
    List<NativeWorldView.Batch> batches(NativeWorldView view,Minecraft mc){
        var batches=new ArrayList<NativeWorldView.Batch>();var retained=new HashSet<String>();
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof NativeActorEntity proxy&&proxy.getArrowCount()>0&&!proxy.stuckArrows().isEmpty()){
            var actor=view.actor(proxy.nativeId);if(actor==null||actor.health()<=0||actor.cloaked()||NativeWorldView.controlling()&&!NativeFog.visible(proxy.getX(),proxy.getZ()))continue;
            var raw=proxy.stuckArrows().split(";");int first=Math.max(0,raw.length-proxy.getArrowCount());
            for(int i=first;i<raw.length;i++)try{
                var values=Arrays.stream(raw[i].split(",")).mapToDouble(Double::parseDouble).toArray();if(values.length!=6)continue;double c=Math.cos(actor.orientation()),s=Math.sin(actor.orientation());
                var direction=new Vec3(values[3]*c-values[5]*s,values[4],values[3]*s+values[5]*c).normalize();
                var point=view.overheadAnchor(actor.id(),0,0,0,true).add(values[0]*c-values[2]*s,values[1],values[0]*s+values[2]*c);
                String key=actor.id()+":"+i+":"+raw[i];retained.add(key);Hit hit=hits.get(key);
                if(hit==null){hit=find(view,actor.id(),point.subtract(direction.scale(2)),direction);if(hit!=null)hits.put(key,hit);}
                if(hit!=null)for(var batch:view.scene.batches())if(batch.part()==hit.part()&&batch.actor()==actor.id()&&hit.vertex()+2<batch.count()){
                    Vec3 a=view.renderVertex(batch,hit.vertex()),b=view.renderVertex(batch,hit.vertex()+1),d=view.renderVertex(batch,hit.vertex()+2);point=a.add(b.subtract(a).scale(hit.u())).add(d.subtract(a).scale(hit.v()));var tangent=b.subtract(a).normalize();var normal=tangent.cross(d.subtract(a)).normalize();var bitangent=normal.cross(tangent);direction=tangent.scale(hit.dx()).add(bitangent.scale(hit.dy())).add(normal.scale(hit.dz())).normalize();break;
                }
                var side=direction.cross(Math.abs(direction.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize().scale(.045);var up=direction.cross(side).normalize().scale(.045);
                Vec3 tip=point.add(direction.scale(.12)),tail=point.subtract(direction.scale(.65));
                batches.add(quad(view,tail.subtract(side),tip.subtract(side),tip.add(side),tail.add(side),0,0,32f/64,5f/32));
                batches.add(quad(view,tail.subtract(up),tip.subtract(up),tip.add(up),tail.add(up),0,0,32f/64,5f/32));
            }catch(NumberFormatException ignored){}
        }
        hits.keySet().retainAll(retained);return merge(batches);
    }
    static List<NativeWorldView.Batch> merge(List<NativeWorldView.Batch> batches){if(batches.isEmpty())return List.of();int count=batches.stream().mapToInt(NativeWorldView.Batch::count).sum();float[] vertices=new float[count*8];int[] colors=new int[count];int offset=0;for(var b:batches){System.arraycopy(b.vertices(),0,vertices,offset*8,b.vertices().length);System.arraycopy(b.colors(),0,colors,offset,b.count());offset+=b.count();}var first=batches.getFirst();return List.of(new NativeWorldView.Batch(0,first.texture(),0,first.mode(),vertices,colors,first.translucent()));}
    private static Hit find(NativeWorldView view,int actor,Vec3 start,Vec3 ray){
        Hit hit=null;double nearest=128;
        for(var batch:view.scene.batches())if(batch.actor()==actor&&(batch.mode()&0x40000)==0)for(int i=0;i<batch.count();i+=3){
            Vec3 a=view.renderVertex(batch,i),b=view.renderVertex(batch,i+1),c=view.renderVertex(batch,i+2);Vec3 e1=b.subtract(a),e2=c.subtract(a),h=ray.cross(e2);double det=e1.dot(h);if(Math.abs(det)<1e-9)continue;double inv=1/det;var delta=start.subtract(a);double u=inv*delta.dot(h);if(u<0||u>1)continue;var q=delta.cross(e1);double v=inv*ray.dot(q);if(v<0||u+v>1)continue;double distance=inv*e2.dot(q);if(distance>=0&&distance<nearest){nearest=distance;var tangent=e1.normalize();var normal=tangent.cross(e2).normalize();var bitangent=normal.cross(tangent);hit=new Hit(batch.part(),i,u,v,ray.dot(tangent),ray.dot(bitangent),ray.dot(normal));}
        }return hit;
    }
    private static NativeWorldView.Batch quad(NativeWorldView view,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float u,float v,float uw,float vh){
        var corners=new Vec3[]{a,b,c,d};int[] order={0,1,2,0,2,3};float[] vertices=new float[48];int[] colors=new int[6];Arrays.fill(colors,-1);
        for(int i=0;i<6;i++){int n=order[i],k=i*8;var p=view.origin.nativePoint(corners[n]);vertices[k]=(float)p.x;vertices[k+1]=(float)p.y;vertices[k+2]=(float)p.z;vertices[k+5]=1;vertices[k+6]=u+(n==1||n==2?uw:0);vertices[k+7]=v+(n>=2?vh:0);}
        return new NativeWorldView.Batch(0,-1,0,0x33f52,vertices,colors,true);
    }
}
