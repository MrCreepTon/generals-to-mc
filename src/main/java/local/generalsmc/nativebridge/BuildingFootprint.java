package local.generalsmc.nativebridge;

import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Conservative quarter-block strips around the native oriented footprint, not its broad-phase box. */
final class BuildingFootprint {
    static VoxelShape shape(Vec3 origin,double halfWidth,double halfDepth,double yaw,double height){
        double c=Math.cos(yaw),s=Math.sin(yaw);double[] xs=new double[4],zs=new double[4];
        double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
        int[] signsX={-1,1,1,-1},signsZ={-1,-1,1,1};
        for(int i=0;i<4;i++){double x=signsX[i]*halfWidth,z=signsZ[i]*halfDepth;xs[i]=origin.x+x*c-z*s;zs[i]=origin.z+x*s+z*c;low=Math.min(low,zs[i]);high=Math.max(high,zs[i]);}
        if(Math.abs(c)<1e-6||Math.abs(s)<1e-6){double min=java.util.Arrays.stream(xs).min().orElse(origin.x),max=java.util.Arrays.stream(xs).max().orElse(origin.x);return Shapes.create(new AABB(min,origin.y,low,max,origin.y+height,high));}
        int count=Math.clamp((int)Math.ceil((high-low)*4),1,256);VoxelShape result=Shapes.empty();
        for(int i=0;i<count;i++){
            double a=low+(high-low)*i/count,b=low+(high-low)*(i+1)/count,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(double z:new double[]{a,(a+b)/2,b})for(int k=0;k<4;k++){int next=(k+1)%4;double dz=zs[next]-zs[k];if(Math.abs(dz)<1e-9)continue;double t=(z-zs[k])/dz;if(t<0||t>1)continue;double x=xs[k]+t*(xs[next]-xs[k]);min=Math.min(min,x);max=Math.max(max,x);}
            if(max>min)result=Shapes.joinUnoptimized(result,Shapes.create(new AABB(min,origin.y,a,max,origin.y+height,b)),BooleanOp.OR);
        }
        return result.optimize();
    }
}
