package local.generalsmc.client;
import local.generalsmc.nativebridge.WorldScale;

import com.mojang.blaze3d.platform.NativeImage;
import local.generalsmc.GeneralsMod;
import local.generalsmc.nativebridge.NativeSession;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
    public record Scene(int frame,List<Batch> batches,Map<Integer,Vec3> positions) {public Scene(int frame,List<Batch> batches){this(frame,batches,Map.of());}}
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
    public Vec3 placementTarget,drawnPlacement;
    public Vec3 placementOffset(){return placementTarget!=null&&!state.placementAnchored()?origin.nativePoint(placementTarget):new Vec3(state.placementX(),state.placementY(),state.placementZ());}
    public long drawnVertices,drawCalls;
    public long sceneUpdateNanos,drawNanos;public int lightSamples;
    private final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap frameLights=new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
    public long selectionCommand;
    private final NativeArrowVisual arrows=new NativeArrowVisual();
    Vec3 renderVertex(Batch batch,int vertex){int k=vertex*8;var before=interpolationParts.get(batch.part());double blend=visualBlend(),x=batch.vertices()[k],y=batch.vertices()[k+1],z=batch.vertices()[k+2];if(before!=null){x=before.vertices()[k]+(x-before.vertices()[k])*blend;y=before.vertices()[k+1]+(y-before.vertices()[k+1])*blend;z=before.vertices()[k+2]+(z-before.vertices()[k+2])*blend;}return origin.world(x,y,z);}
    private Scene previousScene;
    private Map<Integer,AABB> previousBounds=Map.of();
    private Map<Integer,NativeSession.Actor> actors=Map.of();
    private Map<Long,Batch> interpolationParts=Map.of();
    public NativeSession.Actor actor(int id){return actors.get(id);}
    private record Memory(double x,double z,List<Batch> batches) {}
    private final Map<Integer,Memory> rememberedStructures=new HashMap<>();
    private List<Batch> rememberedBatches=List.of();
    private Map<Integer,AABB> sceneBounds=Map.of();
    private long sceneReceived;
    private static final int[] CORNERS={0,1,2,2},CULLED_CORNERS={0,2,1,1};
    private CompletableFuture<Scene> pending;
    private final Map<Integer,ResourceLocation> textures=new HashMap<>();
    private long nextRead,nextScope,nextMinecraftBasis;
    private boolean closed;

    public NativeWorldView(NativeSession session,Origin origin,double x,double y) throws IOException {
        if(current!=null)current.close();
        this.session=session;this.origin=origin;cameraX=origin.x+x/WorldScale.UNITS_PER_BLOCK;cameraZ=origin.z+y/WorldScale.UNITS_PER_BLOCK;cameraY=origin.y;
        renderCameraX=cameraX;renderCameraZ=cameraZ;renderCameraY=cameraY;
        session.send("SCENE "+x+" "+y+" 450");fog=new NativeFog(this);current=this;
    }
    public static boolean active(){return current!=null&&!current.closed;}
    public static boolean controlling(){return active()&&NativeRtsController.controlling();}
    private Double startYaw;
    public void face(double x,double z){startYaw=Math.toDegrees(Math.atan2(cameraX-x,z-cameraZ));cameraYaw=renderYaw=startYaw;}
    public void tick(NativeSession.State state) {
        if(this.state==null){cameraSerial=state.cameraSerial();rotationSerial=state.rotationSerial();scrollX=state.scrollX();scrollY=state.scrollY();cameraYaw=renderYaw=Math.toDegrees(state.cameraAngle());if(startYaw!=null)cameraYaw=renderYaw=startYaw;}
        if(this.state!=state){var next=new HashMap<Integer,NativeSession.Actor>();for(var a:state.objects())next.put(a.id(),a);actors=next;}
        this.state=state;fog.tick();
        if(System.currentTimeMillis()>=nextScope){nextScope=System.currentTimeMillis()+100;
            var mc=Minecraft.getInstance();double x=controlling()?cameraX:mc.player.getX(),z=controlling()?cameraZ:mc.player.getZ();
            double aspect=(double)mc.getWindow().getWidth()/Math.max(1,mc.getWindow().getHeight());
            double reach=controlling()?Math.max(zoom,renderZoom)*Math.hypot(aspect,1.16)+24+Math.hypot(cameraX-renderCameraX,cameraZ-renderCameraZ):100;
            double radius=Math.min(512,Math.max(100,reach))*WorldScale.UNITS_PER_BLOCK;
            try{session.send("SCENE "+(x-origin.x)*WorldScale.UNITS_PER_BLOCK+" "+(z-origin.z)*WorldScale.UNITS_PER_BLOCK+" "+radius);}catch(IOException e){GeneralsMod.LOG.error("Scene scope update failed",e);close();}
        }
        if(state.cameraSerial()!=cameraSerial){cameraSerial=state.cameraSerial();cameraX=origin.x+state.cameraTargetX()/WorldScale.UNITS_PER_BLOCK;cameraZ=origin.z+state.cameraTargetY()/WorldScale.UNITS_PER_BLOCK;}
        cameraX+=(state.scrollX()-scrollX)/WorldScale.UNITS_PER_BLOCK;cameraZ+=(state.scrollY()-scrollY)/WorldScale.UNITS_PER_BLOCK;scrollX=state.scrollX();scrollY=state.scrollY();
        if(state.rotationSerial()!=rotationSerial){rotationSerial=state.rotationSerial();cameraYaw=Math.toDegrees(state.cameraAngle());}
        var mc=Minecraft.getInstance();var focus=BlockPos.containing(cameraX,cameraY,cameraZ);
        if(controlling()&&mc.level.hasChunkAt(focus)){double surface=local.generalsmc.nativebridge.WorldSurface.ground(mc.level,focus.getX(),focus.getZ());if(Double.isFinite(surface))cameraY=Math.max(cameraY,surface);}
        if(state.selected()!=null&&state.consumed()>=selectionCommand){selection.clear();selection.addAll(state.selected());}
        pumpScene();
    }
    private long nextStateRead;
    private java.nio.file.attribute.FileTime sceneStamp;
    private void renderFrame(){
        if(controlling()){var pickMc=Minecraft.getInstance();pickMc.hitResult=null;pickMc.crosshairPickEntity=null;}
        long now=System.nanoTime();
        if(now>=nextStateRead){nextStateRead=now+8_000_000L;try{var fresh=session.read();if(fresh!=null&&fresh!=state)tick(fresh);}catch(IOException error){GeneralsMod.LOG.error("Native render snapshot unavailable",error);close();}}
        pumpScene();
    }
    private void pumpScene(){
        if(pending!=null&&pending.isDone()) {
            try {var value=pending.join();if(value!=null&&(scene==null||value.frame()!=scene.frame())){previousScene=scene;scene=value;interpolationParts=interpolation(previousScene,scene);previousBounds=sceneBounds;sceneBounds=buildBounds(value);rememberStructures();sceneReceived=System.nanoTime();}}
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
        rememberedStructures.entrySet().removeIf(e->fog.frame()>=scene.frame()&&fog.at(e.getValue().x,e.getValue().z)==0&&!grouped.containsKey(e.getKey())&&(actor(e.getKey())==null||actor(e.getKey()).health()<=0));
        for(var actor:state.objects())if(actor.structure()&&actor.owner()!=state.localPlayerIndex()&&!NativeFog.resource(actor)){
            var p=origin.world(actor.x(),actor.y(),actor.z());if(fog.at(p.x,p.z)==0&&grouped.containsKey(actor.id()))rememberedStructures.put(actor.id(),new Memory(p.x,p.z,List.copyOf(grouped.get(actor.id()))));
        }
        var retained=new ArrayList<Batch>();for(var entry:rememberedStructures.entrySet())if(fog.at(entry.getValue().x,entry.getValue().z)!=0)retained.addAll(entry.getValue().batches);
        rememberedBatches=List.copyOf(retained);
    }
    int rememberedVertices(int actor){return rememberedBatches.stream().filter(b->b.actor()==actor).mapToInt(Batch::count).sum();}
    public void zoomBy(double steps){double requested=zoom-steps*2;if(steps>0&&requested<10){var mc=Minecraft.getInstance();double ground=local.generalsmc.nativebridge.WorldSurface.ground(mc.level,(int)Math.floor(cameraX),(int)Math.floor(cameraZ));if(Double.isFinite(ground))cameraY=Math.max(ground,cameraY-(10-requested));}zoom=Math.max(10,Math.min(60,requested));}
    public void advanceCamera(){long now=System.nanoTime();double dt=cameraTime==0?1./60:Math.min(.1,(now-cameraTime)/1e9);cameraDelta=dt;cameraTime=now;double blend=1-Math.exp(-dt/.14);double angle=Math.IEEEremainder(cameraYaw-renderYaw,360);renderYaw+=angle*blend;renderCameraX+=(cameraX-renderCameraX)*blend;renderCameraZ+=(cameraZ-renderCameraZ)*blend;renderZoom+=(zoom-renderZoom)*blend;renderCameraY+=(cameraY-renderCameraY)*blend;}
    public double cameraEyeHeight(double desired,double floor){double target=Math.max(desired,floor);if(!Double.isFinite(eyeHeight))eyeHeight=target;else eyeHeight+=(target-eyeHeight)*(1-Math.exp(-cameraDelta/.14));return Math.max(floor-1.5,eyeHeight);}
    static Scene decode(Path file) throws IOException {
        long size=Files.size(file);if(size<12||size>16*1024*1024)throw new IOException("Invalid native scene size "+size);
        var data=ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
        int magic=data.getInt();if(magic!=0x344d485a&&magic!=0x334d485a&&magic!=0x354d485a&&magic!=0x364d485a)throw new IOException("Unsupported native scene protocol");
        int headerSize=(magic==0x344d485a||magic==0x354d485a||magic==0x364d485a)?24:20;
        int frame=data.getInt(),count=data.getInt(),total=0;
        if(count<0||count>16384)throw new IOException("Too many scene batches");
        var batches=new ArrayList<Batch>();
        for(int b=0;b<count;b++) {
            if(data.remaining()<headerSize)throw new IOException("Incomplete native batch header");
            int actor=data.getInt(),texture=data.getInt(),n=data.getInt();long part=data.getLong();int mode=(magic==0x344d485a||magic==0x354d485a||magic==0x364d485a)?data.getInt():0;total+=n;
            if((mode<0||(mode>4&&(mode&0x10000)==0))||n<0||n%3!=0||total>300000||texture<0||texture>4096||data.remaining()<(long)n*36)throw new IOException("Invalid native batch");
            float[] vertices=new float[n*8];int[] colors=new int[n];boolean translucent=actor<0;
            for(int i=0;i<n;i++) {
                for(int j=0;j<8;j++){float value=data.getFloat();if(!Float.isFinite(value)){if(j<6)throw new IOException("Non-finite native pose");value=0;}vertices[i*8+j]=value;}
                colors[i]=data.getInt();translucent|=(colors[i]>>>24)<255;
            }
            batches.add(new Batch(actor,texture,part,mode,vertices,colors,translucent));
        }
        var positions=new HashMap<Integer,Vec3>();if(magic==0x364d485a){if(data.remaining()<4)throw new IOException("Missing scene anchors");int anchors=data.getInt();if(anchors<0||anchors>65536||data.remaining()!=anchors*16L)throw new IOException("Invalid scene anchors");for(int i=0;i<anchors;i++){int id=data.getInt();float x=data.getFloat(),y=data.getFloat(),z=data.getFloat();if(id<=0||!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||positions.put(id,new Vec3(x,y,z))!=null)throw new IOException("Invalid scene actor pose");}}
        if(data.hasRemaining())throw new IOException("Trailing native scene bytes");
        return new Scene(frame,List.copyOf(batches),Map.copyOf(positions));
    }
    ResourceLocation texture(int id) throws IOException {
        if(id==-1)return ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");
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
        var name=ResourceLocation.fromNamespaceAndPath("generalsmc","original_world/"+session.directory.getFileName()+"/"+id);
        var texture=new OriginalTexture(image,true);
        Minecraft.getInstance().getTextureManager().register(name,texture);textures.put(id,name);return name;
    }
    /** Conservative projected footprint, including a margin for input/IPC latency and camera motion. */
    public boolean resourceVisible(local.generalsmc.nativebridge.WorldResourceStore.Node node){
        var mc=Minecraft.getInstance();if(mc.level==null||state==null||state.resourceTemplate()==null)return true;
        var camera=mc.gameRenderer.getMainCamera();var eye=camera.getPosition();
        double rx=state.resourceTemplate().major()/WorldScale.UNITS_PER_BLOCK+2,rz=state.resourceTemplate().minor()/WorldScale.UNITS_PER_BLOCK+2,h=state.resourceTemplate().height()/WorldScale.UNITS_PER_BLOCK+2;
        if(controlling()&&fog.enabled()&&fog.at(node.x(),node.z())==255&&fog.at(node.x()-rx,node.z()-rz)==255&&fog.at(node.x()+rx,node.z()-rz)==255&&fog.at(node.x()-rx,node.z()+rz)==255&&fog.at(node.x()+rx,node.z()+rz)==255)return false;
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
        double aspect=(double)mc.getWindow().getWidth()/mc.getWindow().getHeight();
        double tan=controlling()?Math.tan(Math.toRadians(30)):1.0;
        for(int ix=-1;ix<=1;ix+=2)for(int iz=-1;iz<=1;iz+=2)for(int iy=0;iy<=1;iy++){
            var d=new Vec3(node.x()+ix*rx,node.y()+iy*h,node.z()+iz*rz).subtract(eye);
            double depth=d.dot(new Vec3(camera.getLookVector()));
            if(depth<=.1){if(eye.distanceTo(new Vec3(node.x(),node.y(),node.z()))<Math.max(rx,rz)+h)return true;continue;}
            double x=-d.dot(new Vec3(camera.getLeftVector()))/depth/tan/aspect,y=d.dot(new Vec3(camera.getUpVector()))/depth/tan;
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
    public Vec3 riderPosition(net.minecraft.world.entity.Entity rider){
        if(rider==null||!(rider.getVehicle() instanceof local.generalsmc.nativebridge.NativeActorEntity vehicle)||actor(vehicle.nativeId)==null)return null;
        var seat=vehicle.getPassengerRidingPosition(rider).subtract(vehicle.position()).subtract(rider.getVehicleAttachmentPoint(vehicle));
        return overheadAnchor(vehicle.nativeId,0,0,0,true).add(seat);
    }
    public Vec3 riderCameraOffset(net.minecraft.world.entity.Entity rider,float partial){var position=riderPosition(rider);return position==null?Vec3.ZERO:position.subtract(rider.getPosition(partial));}
    public Vec3 riderCameraPosition(net.minecraft.world.entity.Entity rider,Vec3 camera,float partial){
        if(riderPosition(rider)==null)return null;
        var offset=riderCameraOffset(rider,partial);var result=camera.add(offset);var mc=Minecraft.getInstance();
        if(!mc.options.getCameraType().isFirstPerson()){
            var direction=rider.getViewVector(partial);var shoulder=new Vec3(-direction.z,0,direction.x).normalize().scale(.85);
            var eye=rider.getEyePosition(partial).add(offset);var wanted=result.add(shoulder);
            var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(eye,wanted,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,rider));
            result=hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS?wanted:hit.getLocation().subtract(wanted.subtract(eye).normalize().scale(.15));
        }
        return result;
    }
    private float renderBlend=1;
    public static void prepareFrame(){NativeDrivingClient.beginFrame();var view=current;if(view!=null){long start=System.nanoTime();view.renderFrame();view.sceneUpdateNanos=System.nanoTime()-start;view.renderBlend=view.calculateBlend();}}
    float visualBlend(){return renderBlend;}
    private float calculateBlend(){double interval=previousScene==null||scene==null?33_333_333.:Math.max(16_666_667.,(scene.frame-previousScene.frame)*1e9/30);return (float)Math.min(1,(System.nanoTime()-sceneReceived)/interval);}
    Vec3 overheadAnchor(int id,double x,double y,double z,boolean relative){
        if(!relative)return visualAnchor(id,x,y,z);var current=scene==null?null:scene.positions().get(id);var old=previousScene==null?null:previousScene.positions().get(id);if(current==null){var a=actor(id);return a==null?origin.world(x,y,z):origin.world(a.x()+x,a.y()+y,a.z()+z);}var pose=old==null?current:old.lerp(current,visualBlend());return origin.world(pose.x+x,pose.y+y,pose.z+z);
    }
    Vec3 visualAnchor(int actor,double x,double y,double z){var anchor=origin.world(x,y,z);var before=previousBounds.get(actor);var after=sceneBounds.get(actor);return before==null||after==null?anchor:anchor.add(before.getCenter().subtract(after.getCenter()).scale(1-visualBlend()));}
    private List<Batch> disabledBatches(Minecraft mc){
        if(state==null||state.disabledIcons()==null||state.disabledIcons().isEmpty())return List.of();var icon=state.disabledIcons().get((int)((System.currentTimeMillis()/66)%state.disabledIcons().size()));var list=new ArrayList<Batch>();
        var camera=mc.gameRenderer.getMainCamera();var right=new Vec3(camera.getLeftVector()).scale(-.4);var up=new Vec3(camera.getUpVector()).scale(.4);float partial=mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof local.generalsmc.nativebridge.MobDisableState status&&status.generalsDisabled()&&NativeFog.visible(entity.getX(),entity.getZ())){
            var center=entity.getPosition(partial).add(0,entity.getBbHeight()+.6,0);var vertices=new float[48];var colors=new int[6];java.util.Arrays.fill(colors,-1);int[] corners={0,1,2,0,2,3};
            for(int i=0;i<6;i++){int c=corners[i];boolean r=c>=2,b=c==1||c==2;var point=origin.nativePoint(center.add(right.scale(r?1:-1)).add(up.scale(b?-1:1)));int k=i*8;vertices[k]=(float)point.x;vertices[k+1]=(float)point.y;vertices[k+2]=(float)point.z;vertices[k+5]=1;vertices[k+6]=icon.u()+(r?icon.uw():0);vertices[k+7]=icon.v()+(b?icon.vh():0);}
            list.add(new Batch(0,icon.texture(),0,2,vertices,colors,true));
        }return list;
    }
    public AABB bounds(int actor){return sceneBounds.get(actor);}
    private static Map<Long,Batch> interpolation(Scene previous,Scene current){
        if(previous==null)return Map.of();var parts=new HashMap<Long,Batch>();for(var b:previous.batches)if(b.actor>0)parts.put(b.part,b);var result=new HashMap<Long,Batch>();
        for(var b:current.batches){if(b.actor<=0)continue;var p=parts.get(b.part);if(p==null||p.actor!=b.actor||p.texture!=b.texture||p.mode!=b.mode||p.count()!=b.count())continue;boolean same=true;for(int k=6;k<b.vertices.length;k+=8)if(p.vertices[k]!=b.vertices[k]||p.vertices[k+1]!=b.vertices[k+1]){same=false;break;}if(same)result.put(b.part,p);}return result;
    }
    private int light(NativeSession.Actor actor,Batch batch,net.minecraft.client.multiplayer.ClientLevel level){
        if(batch.mode!=0&&(batch.mode&0x800)==0)return LightTexture.FULL_BRIGHT;
        if(actor!=null&&frameLights.containsKey(actor.id()))return frameLights.get(actor.id());
        Vec3 anchor=actor==null?origin.world(batch.vertices[0],batch.vertices[1],batch.vertices[2]):origin.world(actor.x(),actor.y(),actor.z());
        if((batch.mode&0x40000)!=0){var p=placementOffset();anchor=origin.world(batch.vertices[0]+p.x,batch.vertices[1]+p.y,batch.vertices[2]+p.z);}
        int value=LevelRenderer.getLightColor(level,BlockPos.containing(anchor.add(0,.5,0)));lightSamples++;
        if(actor!=null)frameLights.put(actor.id(),value);return value;
    }
    public static void render(WorldRenderContext context) {
        long started=System.nanoTime();var view=current;
        if(view==null||view.closed||view.scene==null||context.matrixStack()==null||context.consumers()==null)return;
        var mc=Minecraft.getInstance();var eye=context.camera().getPosition();var pose=context.matrixStack().last();
        if(!controlling()&&System.currentTimeMillis()>=view.nextMinecraftBasis){
            view.nextMinecraftBasis=System.currentTimeMillis()+50;
            var c=context.camera();var e=view.origin.nativePoint(c.getPosition());var f=c.getLookVector();var l=c.getLeftVector();var u=c.getUpVector();
            try{view.session.send("HOST_VIEW "+e.x+" "+e.y+" "+e.z+" "+f.x+" "+f.z+" "+f.y+" "+l.x+" "+l.z+" "+l.y+" "+u.x+" "+u.z+" "+u.y+" "+Math.tan(Math.toRadians(mc.options.fov().get()/2.0))+" "+(double)mc.getWindow().getWidth()/mc.getWindow().getHeight()+" 1");}catch(IOException error){GeneralsMod.LOG.debug("Native effect camera update failed",error);}
        }
        float blend=view.visualBlend();
        var actors=view.actors;view.frameLights.clear();view.lightSamples=0;long drawStarted=System.nanoTime();
        var statusBatches=new ArrayList<Batch>(view.disabledBatches(mc));statusBatches.addAll(view.arrows.batches(view,mc));statusBatches.addAll(NativeFootprintVisual.batches(view,mc));statusBatches.addAll(NativeDrivingVisual.batches(view,mc));int liveBatches=view.scene.batches.size(),statusStart=liveBatches+(controlling()?view.rememberedBatches.size():0),totalBatches=statusStart+statusBatches.size();
        var vertexScratch=new org.joml.Vector3f();var normalScratch=new org.joml.Vector3f();
        for(int batchIndex=0;batchIndex<totalBatches;batchIndex++) {
            boolean remembered=batchIndex>=liveBatches&&batchIndex<statusStart;var batch=batchIndex>=statusStart?statusBatches.get(batchIndex-statusStart):remembered?view.rememberedBatches.get(batchIndex-liveBatches):view.scene.batches.get(batchIndex);
            Batch previous=view.interpolationParts.get(batch.part);
            try {
                boolean translucent=batch.translucent;
                var type=(batch.mode&0x10000)!=0?OriginalWorldMaterial.get(view.texture(batch.texture),batch.mode):batch.mode==2?RenderType.eyes(view.texture(batch.texture)):batch.mode!=0?RenderType.entityTranslucentEmissive(view.texture(batch.texture)):translucent?RenderType.entityTranslucent(view.texture(batch.texture)):RenderType.entityCutoutNoCull(view.texture(batch.texture));
                var buffer=context.consumers().getBuffer(type);
                var actor=actors.get(batch.actor);
                if(!remembered&&actor!=null&&actor.owner()!=view.state.localPlayerIndex()&&!NativeFog.resource(actor)){var p=view.origin.world(actor.x(),actor.y(),actor.z());if(!NativeFog.visible(p.x,p.z))continue;}
                if(remembered||(batch.mode&0x40000)!=0)previous=null;
                int light=view.light(actor,batch,mc.level);
                boolean placement=(batch.mode&0x40000)!=0;if(placement&&(!controlling()||!view.state.pendingBuild()))continue;
                var offset=placement?view.placementOffset():Vec3.ZERO;if(placement)view.drawnPlacement=view.origin.world(offset.x,offset.y,offset.z);
                // Entity render type uses QUADS. A degenerate fourth corner emits the original triangle.
                for(int i=0;i<batch.count();i+=3)for(int corner:(batch.mode&0x80000)!=0?CULLED_CORNERS:CORNERS) {
                    int v=i+corner,k=v*8,color=batch.colors[v];
                    float px=batch.vertices[k]+(float)offset.x,py=batch.vertices[k+1]+(float)offset.y,pz=batch.vertices[k+2]+(float)offset.z;
                    if(previous!=null){px=previous.vertices[k]+(px-previous.vertices[k])*blend;py=previous.vertices[k+1]+(py-previous.vertices[k+1])*blend;pz=previous.vertices[k+2]+(pz-previous.vertices[k+2])*blend;}
                    pose.pose().transformPosition((float)(view.origin.x+px/WorldScale.UNITS_PER_BLOCK-eye.x),(float)(view.origin.y+pz/WorldScale.UNITS_PER_BLOCK-eye.y),(float)(view.origin.z+py/WorldScale.UNITS_PER_BLOCK-eye.z),vertexScratch);
                    pose.transformNormal(batch.vertices[k+3],batch.vertices[k+5],batch.vertices[k+4],normalScratch);
                    buffer.addVertex(vertexScratch.x,vertexScratch.y,vertexScratch.z)
                        .setColor((color>>16)&255,(color>>8)&255,color&255,(color>>>24)&255)
                        .setUv(batch.vertices[k+6],batch.vertices[k+7]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(normalScratch.x,normalScratch.y,normalScratch.z);
                    view.drawnVertices++;
                }
                view.drawCalls++;
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
