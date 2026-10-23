package local.generalsmc.client;
import local.generalsmc.nativebridge.WorldScale;

import com.mojang.blaze3d.platform.NativeImage;
import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.NativeSession;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.*;
import java.nio.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Actual original WW3D triangles drawn in Minecraft's world pass and depth buffer.
 * Original engine owns transforms/animation. First material pass with native lighting, particle billboards and projected decals; full shadows/material coverage remains incomplete.
 */
public final class NativeWorldView implements AutoCloseable {
    public record Origin(int x,int y,int z) {
        public Vec3 world(double nx,double ny,double nz){return new Vec3(x+nx/WorldScale.UNITS_PER_BLOCK,y+nz/WorldScale.UNITS_PER_BLOCK,z+ny/WorldScale.UNITS_PER_BLOCK);}
        public Vec3 nativePoint(Vec3 world){return new Vec3((world.x-x)*WorldScale.UNITS_PER_BLOCK,(world.z-z)*WorldScale.UNITS_PER_BLOCK,(world.y-y)*WorldScale.UNITS_PER_BLOCK);}
    }
    public record Batch(int actor,int texture,long part,int mode,float[] vertices,int[] colors,boolean translucent) { public int count(){return colors.length;} }
    public record Scene(int frame,List<Batch> batches) {}
    public static NativeWorldView current;
    public final NativeSession session;
    public final Origin origin;
    public final NativeFog fog;
    public final Set<Integer> selection=new LinkedHashSet<>();
    public double cameraX,cameraZ,cameraY,zoom=25;
    public double renderCameraX,renderCameraZ,renderCameraY,renderZoom=25;
    public double cameraYaw,renderYaw;private long rotationSerial;
    private long cameraTime,cameraSerial;
    private double scrollX,scrollY;
    private double eyeHeight=Double.NaN,cameraDelta=1./60;
    public NativeSession.State state;
    public Scene scene;
    public long drawnVertices,drawCalls;
    public long sceneUpdateNanos,drawNanos;public int lightSamples;
    private final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap frameLights=new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
    public long selectionCommand;
    private Scene previousScene;
    private Map<Integer,NativeSession.Actor> actors=Map.of();
    private Map<Long,Batch> interpolationParts=Map.of();
    public NativeSession.Actor actor(int id){return actors.get(id);}
    private record Memory(double x,double z,List<Batch> batches) {}
    private final Map<Integer,Memory> rememberedStructures=new HashMap<>();
    private List<Batch> rememberedBatches=List.of();
    private Map<Integer,AABB> sceneBounds=Map.of();
    private long sceneReceived;
    private static final int[] CORNERS=OriginalWorldMaterial.CORNERS;
    private CompletableFuture<Scene> pending;
    private final Map<Integer,Identifier> textures=new HashMap<>();
    private long nextRead,nextScope,nextMinecraftBasis;
    private boolean closed;

    public NativeWorldView(NativeSession session,Origin origin,double x,double y) throws IOException {
        if(current!=null)current.close();
        this.session=session;this.origin=origin;cameraX=origin.x+x/WorldScale.UNITS_PER_BLOCK;cameraZ=origin.z+y/WorldScale.UNITS_PER_BLOCK;cameraY=origin.y;
        renderCameraX=cameraX;renderCameraZ=cameraZ;renderCameraY=cameraY;
        session.send("SCENE "+x+" "+y+" 450");fog=new NativeFog(this);current=this;
    }
    public static boolean active(){return current!=null&&!current.closed;}
    public static boolean controlling(){return active()&&Minecraft.getInstance().gui.screen() instanceof NativeWorldScreen;}
    private Double startYaw;
    public void face(double x,double z){startYaw=Math.toDegrees(Math.atan2(cameraX-x,z-cameraZ));cameraYaw=renderYaw=startYaw;}
    public void tick(NativeSession.State state) {
        if(this.state==null){cameraSerial=state.cameraSerial();rotationSerial=state.rotationSerial();scrollX=state.scrollX();scrollY=state.scrollY();cameraYaw=renderYaw=Math.toDegrees(state.cameraAngle());if(startYaw!=null)cameraYaw=renderYaw=startYaw;}
        if(this.state!=state){var next=new HashMap<Integer,NativeSession.Actor>();for(var a:state.objects())next.put(a.id(),a);actors=next;}
        this.state=state;fog.tick();
        if(System.currentTimeMillis()>=nextScope){nextScope=System.currentTimeMillis()+250;
            var mc=Minecraft.getInstance();double x=controlling()?cameraX:mc.player.getX(),z=controlling()?cameraZ:mc.player.getZ();
            try{session.send("SCENE "+(x-origin.x)*WorldScale.UNITS_PER_BLOCK+" "+(z-origin.z)*WorldScale.UNITS_PER_BLOCK+" 800");}catch(IOException e){GeneralsMod.LOG.error("Scene scope update failed",e);close();}
        }
        if(state.cameraSerial()!=cameraSerial){cameraSerial=state.cameraSerial();cameraX=origin.x+state.cameraTargetX()/WorldScale.UNITS_PER_BLOCK;cameraZ=origin.z+state.cameraTargetY()/WorldScale.UNITS_PER_BLOCK;}
        cameraX+=(state.scrollX()-scrollX)/WorldScale.UNITS_PER_BLOCK;cameraZ+=(state.scrollY()-scrollY)/WorldScale.UNITS_PER_BLOCK;scrollX=state.scrollX();scrollY=state.scrollY();
        if(state.rotationSerial()!=rotationSerial){rotationSerial=state.rotationSerial();cameraYaw=Math.toDegrees(state.cameraAngle());}
        var mc=Minecraft.getInstance();var focus=BlockPos.containing(cameraX,cameraY,cameraZ);
        if(controlling()&&mc.level.hasChunkAt(focus)){double surface=local.generalsmc.nativebridge.WorldSurface.ground(mc.level,focus.getX(),focus.getZ());if(Double.isFinite(surface))cameraY=surface;}
        if(state.selected()!=null&&state.consumed()>=selectionCommand){selection.clear();selection.addAll(state.selected());}
        pumpScene();
    }
    private long nextStateRead;
    private java.nio.file.attribute.FileTime sceneStamp;
    private void renderFrame(){
        long now=System.nanoTime();
        if(now>=nextStateRead){nextStateRead=now+8_000_000L;try{var fresh=session.read();if(fresh!=null&&fresh!=state)tick(fresh);}catch(IOException error){GeneralsMod.LOG.error("Native render snapshot unavailable",error);close();}}
        pumpScene();
    }
    private void pumpScene(){
        if(pending!=null&&pending.isDone()) {
            try {var value=pending.join();if(value!=null&&(scene==null||value.frame()!=scene.frame())){previousScene=scene;scene=value;interpolationParts=interpolation(previousScene,scene);sceneBounds=buildBounds(value);rememberStructures();sceneReceived=System.nanoTime();}}
            catch(Exception e){GeneralsMod.LOG.error("Original scene decode failed",e);close();}
            pending=null;
        }
        if(!closed&&pending==null&&System.currentTimeMillis()>=nextRead) {
            nextRead=System.currentTimeMillis()+8;
            pending=CompletableFuture.supplyAsync(()->{
                try{var file=session.directory.resolve("scene.bin");var stamp=Files.getLastModifiedTime(file);if(stamp.equals(sceneStamp))return null;var decoded=decode(file);sceneStamp=stamp;return decoded;}
                catch(NoSuchFileException|AccessDeniedException transientReplace){return null;}
                catch(IOException e){throw new java.io.UncheckedIOException(e);}
            });
        }
    }
    private void rememberStructures(){
        if(state==null||state.objects()==null)return;
        var grouped=new HashMap<Integer,List<Batch>>();for(var batch:scene.batches)grouped.computeIfAbsent(batch.actor,k->new ArrayList<>()).add(batch);
        // Scene culling can arrive before the matching fog grid. Missing geometry alone is
        // not evidence that a previously seen building disappeared from a visible cell.
        rememberedStructures.entrySet().removeIf(e->fog.frame()>=scene.frame()&&fog.at(e.getValue().x,e.getValue().z)==0&&!grouped.containsKey(e.getKey())&&state.objects().stream().noneMatch(a->a.id()==e.getKey()&&a.health()>0));
        for(var actor:state.objects())if(actor.structure()&&actor.owner()!=state.localPlayerIndex()&&!NativeFog.resource(actor)){
            var p=origin.world(actor.x(),actor.y(),actor.z());if(fog.at(p.x,p.z)==0&&grouped.containsKey(actor.id()))rememberedStructures.put(actor.id(),new Memory(p.x,p.z,List.copyOf(grouped.get(actor.id()))));
        }
        var retained=new ArrayList<Batch>();for(var entry:rememberedStructures.entrySet())if(fog.at(entry.getValue().x,entry.getValue().z)!=0)retained.addAll(entry.getValue().batches);
        rememberedBatches=List.copyOf(retained);
    }
    int rememberedVertices(int actor){return rememberedBatches.stream().filter(b->b.actor()==actor).mapToInt(Batch::count).sum();}
    public void advanceCamera(){long now=System.nanoTime();double dt=cameraTime==0?1./60:Math.min(.1,(now-cameraTime)/1e9);cameraDelta=dt;cameraTime=now;double blend=1-Math.exp(-dt/.14);double angle=Math.IEEEremainder(cameraYaw-renderYaw,360);renderYaw+=angle*blend;renderCameraX+=(cameraX-renderCameraX)*blend;renderCameraZ+=(cameraZ-renderCameraZ)*blend;renderZoom+=(zoom-renderZoom)*blend;renderCameraY+=(cameraY-renderCameraY)*blend;}
    public double cameraEyeHeight(double desired,double floor){double target=Math.max(desired,floor);if(!Double.isFinite(eyeHeight))eyeHeight=target;else eyeHeight+=(target-eyeHeight)*(1-Math.exp(-cameraDelta/.14));return Math.max(floor-1.5,eyeHeight);}
    static Scene decode(Path file) throws IOException {
        long size=Files.size(file);if(size<12||size>16*1024*1024)throw new IOException("Invalid native scene size "+size);
        var data=ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
        int magic=data.getInt();if(magic!=0x344d485a&&magic!=0x334d485a&&magic!=0x354d485a)throw new IOException("Unsupported native scene protocol");
        int headerSize=(magic==0x344d485a||magic==0x354d485a)?24:20;
        int frame=data.getInt(),count=data.getInt(),total=0;
        if(count<0||count>16384)throw new IOException("Too many scene batches");
        var batches=new ArrayList<Batch>();
        for(int b=0;b<count;b++) {
            if(data.remaining()<headerSize)throw new IOException("Incomplete native batch header");
            int actor=data.getInt(),texture=data.getInt(),n=data.getInt();long part=data.getLong();int mode=(magic==0x344d485a||magic==0x354d485a)?data.getInt():0;total+=n;
            if((mode<0||(mode>4&&(mode&0x10000)==0))||n<0||n%3!=0||total>300000||texture<0||texture>4096||data.remaining()<(long)n*36)throw new IOException("Invalid native batch");
            float[] vertices=new float[n*8];int[] colors=new int[n];boolean translucent=actor<0;
            for(int i=0;i<n;i++) {
                for(int j=0;j<8;j++){float value=data.getFloat();if(!Float.isFinite(value)){if(j<6)throw new IOException("Non-finite native pose");value=0;}vertices[i*8+j]=value;}
                colors[i]=data.getInt();translucent|=(colors[i]>>>24)<255;
            }
            batches.add(new Batch(actor,texture,part,mode,vertices,colors,translucent));
        }
        if(data.hasRemaining())throw new IOException("Trailing native scene bytes");
        return new Scene(frame,List.copyOf(batches));
    }
    private Identifier texture(int id) throws IOException {
        var cached=textures.get(id);if(cached!=null)return cached;
        NativeImage image;
        if(id==0){image=new NativeImage(1,1,false);image.setPixel(0,0,0xffffffff);}
        else {
            var data=ByteBuffer.wrap(Files.readAllBytes(session.directory.resolve("textures/"+id+".rgba"))).order(ByteOrder.LITTLE_ENDIAN);
            int w=data.getInt(),h=data.getInt();
            if(w<1||h<1||w>2048||h>2048||data.remaining()!=(long)w*h*4)throw new IOException("Invalid original texture");
            image=new NativeImage(w,h,false);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)image.setPixel(x,y,data.getInt());
        }
        var name=Identifier.fromNamespaceAndPath("generalsmc","original_world/"+session.directory.getFileName()+"/"+id);
        var texture=new OriginalTexture(image,true);
        Minecraft.getInstance().getTextureManager().register(name,texture);textures.put(id,name);return name;
    }
    /** Conservative projected footprint, including a margin for input/IPC latency and camera motion. */
    public boolean resourceVisible(local.generalsmc.nativebridge.WorldResourceStore.Node node){
        var mc=Minecraft.getInstance();if(mc.level==null||state==null||state.resourceTemplate()==null)return true;
        var camera=mc.gameRenderer.mainCamera();var eye=camera.position();
        double rx=state.resourceTemplate().major()/WorldScale.UNITS_PER_BLOCK+2,rz=state.resourceTemplate().minor()/WorldScale.UNITS_PER_BLOCK+2,h=state.resourceTemplate().height()/WorldScale.UNITS_PER_BLOCK+2;
        if(controlling()&&fog.enabled()&&fog.at(node.x(),node.z())==255&&fog.at(node.x()-rx,node.z()-rz)==255&&fog.at(node.x()+rx,node.z()-rz)==255&&fog.at(node.x()-rx,node.z()+rz)==255&&fog.at(node.x()+rx,node.z()+rz)==255)return false;
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
        double aspect=(double)mc.getWindow().getWidth()/mc.getWindow().getHeight();
        double tan=controlling()?Math.tan(Math.toRadians(30)):1.0;
        for(int ix=-1;ix<=1;ix+=2)for(int iz=-1;iz<=1;iz+=2)for(int iy=0;iy<=1;iy++){
            var d=new Vec3(node.x()+ix*rx,node.y()+iy*h,node.z()+iz*rz).subtract(eye);
            double depth=d.dot(new Vec3(camera.forwardVector()));
            if(depth<=.1){if(eye.distanceTo(new Vec3(node.x(),node.y(),node.z()))<Math.max(rx,rz)+h)return true;continue;}
            double x=-d.dot(new Vec3(camera.leftVector()))/depth/tan/aspect,y=d.dot(new Vec3(camera.upVector()))/depth/tan;
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        }
        return maxX>=-1.25&&minX<=1.25&&maxY>=-1.25&&minY<=1.25;
    }
    public Set<Integer> sceneActors(){var ids=new HashSet<Integer>();if(scene!=null)for(var batch:scene.batches)if(batch.actor>0)ids.add(batch.actor);return ids;}
    /** Closest original triangle; large structure bounding boxes must not steal unit hits. */
    public int pick(Vec3 eye,Vec3 ray,double nearest){
        if(scene==null)return 0;int chosen=0;var candidates=new HashSet<Integer>();
        if(state!=null)for(var a:state.objects())candidates.add(a.id());
        var broad=new HashSet<Integer>();var end=eye.add(ray.scale(nearest));
        for(int id:candidates){var box=bounds(id);if(box!=null&&(box.contains(eye)||box.inflate(.01).clip(eye,end).isPresent()))broad.add(id);}
        for(var batch:scene.batches){if(!broad.contains(batch.actor))continue;
            for(int i=0;i<batch.count();i+=3){Vec3 a=point(batch,i),b=point(batch,i+1),c=point(batch,i+2);var e1=b.subtract(a);var e2=c.subtract(a);var h=ray.cross(e2);double det=e1.dot(h);if(Math.abs(det)<1e-9)continue;
                double inv=1/det;var s=eye.subtract(a);double u=inv*s.dot(h);if(u<0||u>1)continue;var q=s.cross(e1);double v=inv*ray.dot(q);if(v<0||u+v>1)continue;double t=inv*e2.dot(q);if(t>=0&&t<nearest){nearest=t;chosen=batch.actor;}
            }
        }return chosen;
    }
    private Vec3 point(Batch b,int i){return origin.world(b.vertices[i*8],b.vertices[i*8+1],b.vertices[i*8+2]);}
    public int textureCount(){return textures.size();}
    private Map<Integer,AABB> buildBounds(Scene snapshot){
        var ranges=new HashMap<Integer,double[]>();
        for(var batch:snapshot.batches)if(batch.actor>0){
            var r=ranges.computeIfAbsent(batch.actor,id->new double[]{Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY});
            for(int i=0;i<batch.count();i++){
                double x=origin.x+batch.vertices[i*8]/WorldScale.UNITS_PER_BLOCK,y=origin.y+batch.vertices[i*8+2]/WorldScale.UNITS_PER_BLOCK,z=origin.z+batch.vertices[i*8+1]/WorldScale.UNITS_PER_BLOCK;
                r[0]=Math.min(r[0],x);r[1]=Math.min(r[1],y);r[2]=Math.min(r[2],z);r[3]=Math.max(r[3],x);r[4]=Math.max(r[4],y);r[5]=Math.max(r[5],z);
            }
        }
        var result=new HashMap<Integer,AABB>();ranges.forEach((id,r)->result.put(id,new AABB(r[0],r[1],r[2],r[3],r[4],r[5])));return result;
    }
    public AABB bounds(int actor){return sceneBounds.get(actor);}
    private static Map<Long,Batch> interpolation(Scene previous,Scene current){
        if(previous==null)return Map.of();var parts=new HashMap<Long,Batch>();for(var b:previous.batches)if(b.actor>0)parts.put(b.part,b);var result=new HashMap<Long,Batch>();
        for(var b:current.batches){if(b.actor<=0)continue;var p=parts.get(b.part);if(p==null||p.actor!=b.actor||p.texture!=b.texture||p.mode!=b.mode||p.count()!=b.count())continue;boolean same=true;for(int k=6;k<b.vertices.length;k+=8)if(p.vertices[k]!=b.vertices[k]||p.vertices[k+1]!=b.vertices[k+1]){same=false;break;}if(same)result.put(b.part,p);}return result;
    }
    private int light(NativeSession.Actor actor,Batch batch,net.minecraft.client.multiplayer.ClientLevel level){
        if(batch.mode!=0&&(batch.mode&0x800)==0)return net.minecraft.util.LightCoordsUtil.FULL_BRIGHT;
        if(actor!=null&&frameLights.containsKey(actor.id()))return frameLights.get(actor.id());
        Vec3 anchor=actor==null?origin.world(batch.vertices[0],batch.vertices[1],batch.vertices[2]):origin.world(actor.x(),actor.y(),actor.z());
        int value=net.minecraft.util.LightCoordsUtil.getLightCoords(level,BlockPos.containing(anchor.add(0,.5,0)));lightSamples++;
        if(actor!=null)frameLights.put(actor.id(),value);return value;
    }
    public static void render(LevelRenderContext context) {
        long started=System.nanoTime();var view=current;if(view!=null){view.renderFrame();view.sceneUpdateNanos=System.nanoTime()-started;}
        if(view==null||view.closed||view.scene==null||context.poseStack()==null||context.submitNodeCollector()==null)return;
        var mc=Minecraft.getInstance();var eye=context.levelState().cameraRenderState.pos;
        if(!controlling()&&System.currentTimeMillis()>=view.nextMinecraftBasis){
            view.nextMinecraftBasis=System.currentTimeMillis()+50;
            var c=context.gameRenderer().mainCamera();var e=view.origin.nativePoint(c.position());var f=c.forwardVector();var l=c.leftVector();var u=c.upVector();
            try{view.session.send("HOST_VIEW "+e.x+" "+e.y+" "+e.z+" "+f.x()+" "+f.z()+" "+f.y()+" "+l.x()+" "+l.z()+" "+l.y()+" "+u.x()+" "+u.z()+" "+u.y()+" "+Math.tan(Math.toRadians(mc.options.fov().get()/2.0))+" "+(double)mc.getWindow().getWidth()/mc.getWindow().getHeight()+" 1");}catch(IOException error){GeneralsMod.LOG.debug("Native effect camera update failed",error);}
        }
        double interval=view.previousScene==null?1:Math.max(1,(view.scene.frame-view.previousScene.frame)*1e9/30);
        float blend=(float)Math.min(1,(System.nanoTime()-view.sceneReceived)/interval);
        var actors=view.actors;view.frameLights.clear();view.lightSamples=0;long drawStarted=System.nanoTime();
        int liveBatches=view.scene.batches.size(),totalBatches=liveBatches+(controlling()?view.rememberedBatches.size():0);
        for(int batchIndex=0;batchIndex<totalBatches;batchIndex++) {
            boolean remembered=batchIndex>=liveBatches;var batch=remembered?view.rememberedBatches.get(batchIndex-liveBatches):view.scene.batches.get(batchIndex);
            Batch previous=view.interpolationParts.get(batch.part);
            try {
                boolean translucent=batch.translucent;
                var type=(batch.mode&0x10000)!=0?OriginalWorldMaterial.get(view.texture(batch.texture),batch.mode):batch.mode==2?RenderTypes.eyes(view.texture(batch.texture)):batch.mode!=0?RenderTypes.entityTranslucentEmissive(view.texture(batch.texture)):translucent?RenderTypes.entityTranslucent(view.texture(batch.texture)):RenderTypes.entityCutout(view.texture(batch.texture));
                var actor=actors.get(batch.actor);
                if(!remembered&&actor!=null&&actor.owner()!=view.state.localPlayerIndex()&&!NativeFog.resource(actor)){var p=view.origin.world(actor.x(),actor.y(),actor.z());if(!NativeFog.visible(p.x,p.z))continue;}
                if(remembered)previous=null;
                int light=view.light(actor,batch,mc.level);
                var prior=previous;
                context.submitNodeCollector().submitCustomGeometry(context.poseStack(),type,(pose,buffer)->{
                var vertexScratch=new org.joml.Vector3f();var normalScratch=new org.joml.Vector3f();
                // Entity render type uses QUADS. A degenerate fourth corner emits the original triangle.
                for(int i=0;i<batch.count();i+=3)for(int corner:CORNERS) {
                    int v=i+corner,k=v*8,color=batch.colors[v];
                    float px=batch.vertices[k],py=batch.vertices[k+1],pz=batch.vertices[k+2];
                    if(prior!=null){px=prior.vertices[k]+(px-prior.vertices[k])*blend;py=prior.vertices[k+1]+(py-prior.vertices[k+1])*blend;pz=prior.vertices[k+2]+(pz-prior.vertices[k+2])*blend;}
                    pose.pose().transformPosition((float)(view.origin.x+px/WorldScale.UNITS_PER_BLOCK-eye.x),(float)(view.origin.y+pz/WorldScale.UNITS_PER_BLOCK-eye.y),(float)(view.origin.z+py/WorldScale.UNITS_PER_BLOCK-eye.z),vertexScratch);
                    pose.transformNormal(batch.vertices[k+3],batch.vertices[k+5],batch.vertices[k+4],normalScratch);
                    buffer.addVertex(vertexScratch.x,vertexScratch.y,vertexScratch.z)
                        .setColor((color>>16)&255,(color>>8)&255,color&255,(color>>>24)&255)
                        .setUv(batch.vertices[k+6],batch.vertices[k+7]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(normalScratch.x,normalScratch.y,normalScratch.z);
                    view.drawnVertices++;
                }
                });view.drawCalls++;
            }catch(IOException e){GeneralsMod.LOG.error("Original world texture load failed",e);view.close();return;}
        }
        view.drawNanos=System.nanoTime()-drawStarted;
        NativePerformance.frame(view,started);
    }
    @Override public void close() {
        if(closed)return;closed=true;NativePerformance.close(this);if(pending!=null)pending.cancel(false);
        var manager=Minecraft.getInstance().getTextureManager();for(var texture:textures.values())manager.release(texture);textures.clear();
        fog.close();OriginalWorldMaterial.clear();if(current==this)current=null;
    }
}
