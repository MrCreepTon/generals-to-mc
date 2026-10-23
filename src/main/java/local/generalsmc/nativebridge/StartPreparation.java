package local.generalsmc.nativebridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.io.IOException;
/** Bounded, preflighted edits, applied before any army starts. Never generates chunks synchronously. */
final class StartPreparation {
 private record Edit(BlockPos pos,BlockState state){}
 static final class Plan {
  private final ArrayDeque<Edit> edits;
  Plan(Collection<Edit> edits){this.edits=new ArrayDeque<>(edits);}
  boolean apply(ServerLevel level){for(int budget=256;budget>0&&!edits.isEmpty();budget--){var edit=edits.removeFirst();level.setBlock(edit.pos,edit.state,2);}return edits.isEmpty();}
 }
 static Plan plan(ServerLevel level,NativeMatchSpec spec,NativeSession.State state)throws IOException{
  var edits=new LinkedHashMap<BlockPos,Edit>();
  for(var army:spec.armies()){
   var base=state.objects().stream().filter(a->a.owner()==army.nativePlayer()&&a.structure()).findFirst().orElseThrow();
   double c=Math.abs(Math.cos(base.orientation())),sn=Math.abs(Math.sin(base.orientation()));int rx=(int)Math.ceil((c*base.geometryMajor()+sn*base.geometryMinor())/WorldScale.UNITS_PER_BLOCK)+3,rz=(int)Math.ceil((sn*base.geometryMajor()+c*base.geometryMinor())/WorldScale.UNITS_PER_BLOCK)+3;
   if(rx>24||rz>24)throw new IOException(local.generalsmc.LocalText.token("Стартовое здание слишком большое для автоматической расчистки"));
   int floor=army.start().y(),top=floor+Math.max(8,(int)Math.ceil(base.geometryHeight()/WorldScale.UNITS_PER_BLOCK)+2);
   for(int x=army.start().x()-rx;x<=army.start().x()+rx;x++)for(int z=army.start().z()-rz;z<=army.start().z()+rz;z++){
    if(level.getChunkSource().getChunkNow(Math.floorDiv(x,16),Math.floorDiv(z,16))==null)throw new IOException(local.generalsmc.LocalText.token("Стартовая площадка не загрузилась"));
    double ground=WorldSurface.ground(level,x,z);
    if(!Double.isFinite(ground)||Math.abs(ground-floor)>8)throw new IOException(local.generalsmc.LocalText.token("Для расчистки выберите старт без глубоких провалов и высоких гор"));
    var support=new BlockPos(x,floor-1,z);var under=level.getBlockState(support);
    if(under.isAir()||!under.getFluidState().isEmpty()||under.canBeReplaced())add(level,edits,support,Blocks.DIRT.defaultBlockState());
    for(int y=floor;y<Math.min(level.getMaxY(),top);y++){
     var pos=new BlockPos(x,y,z);var block=level.getBlockState(pos);
     if(block.isAir()||block.is(SiteBlocks.START))continue;
     add(level,edits,pos,Blocks.AIR.defaultBlockState());
    }
   }
  }
  return new Plan(edits.values());
 }
 private static void add(ServerLevel level,Map<BlockPos,Edit> edits,BlockPos pos,BlockState next)throws IOException{
  var old=level.getBlockState(pos);
  if(old.hasBlockEntity()||old.getDestroySpeed(level,pos)<0)throw new IOException(local.generalsmc.LocalText.token("Расчистка остановлена: перенесите старт подальше от контейнеров и защищённых блоков"));
  if(!old.equals(next))edits.put(pos,new Edit(pos,next));
 }
}
