package top.lacrus.zephyrzeno.electron.circuit;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.*;

/** Canonical face coordinates: opposite wall covers share channel numbering. */
public record SurfacePoint(BlockPos host,Direction face,int u,int v,int layer) {
    public SurfacePoint { host=host.toImmutable();if(u<1||u>15||v<1||v>15||(u&1)==0||(v&1)==0||layer<0||layer>1)throw new IllegalArgumentException("Invalid surface grid point"); }
    public Direction uDirection(){return face.getAxis()==Direction.Axis.X?Direction.SOUTH:Direction.EAST;}
    public Direction vDirection(){return face.getAxis()==Direction.Axis.Y?Direction.SOUTH:Direction.UP;}
    public SurfacePoint layer(int value){return new SurfacePoint(host,face,u,v,value);}
    public Vec3d position() {
        Vec3d p=Vec3d.of(host);double offset=.03125+layer*.0625;
        p=p.add(Vec3d.of(face.getVector()).multiply(offset));
        if(face.getDirection()==Direction.AxisDirection.POSITIVE)p=p.add(Vec3d.of(face.getVector()));
        return p.add(Vec3d.of(uDirection().getVector()).multiply(u/16d)).add(Vec3d.of(vDirection().getVector()).multiply(v/16d));
    }
    public int plane(){return face.getAxis().choose(host.getX(),host.getY(),host.getZ())+(face.getDirection()==Direction.AxisDirection.POSITIVE?1:0);}
    public SurfacePoint step(int du,int dv) {
        int a=u+du,b=v+dv;BlockPos p=host;
        if(a<1){p=p.offset(uDirection().getOpposite());a+=16;}if(a>15){p=p.offset(uDirection());a-=16;}
        if(b<1){p=p.offset(vDirection().getOpposite());b+=16;}if(b>15){p=p.offset(vDirection());b-=16;}
        return new SurfacePoint(p,face,a,b,layer);
    }
    public NbtCompound nbt(){var n=new NbtCompound();n.putLong("p",host.asLong());n.putInt("f",face.getId());n.putInt("u",u);n.putInt("v",v);n.putInt("l",layer);return n;}
    public static SurfacePoint read(NbtCompound n){return new SurfacePoint(BlockPos.fromLong(n.getLong("p")),Direction.byId(n.getInt("f")),n.getInt("u"),n.getInt("v"),n.getInt("l"));}
    public static int snap(double coord){return Math.clamp((int)Math.floor(coord*8)*2+1,1,15);}
    public static SurfacePoint hit(BlockPos host,Direction face,Vec3d hit,int layer){Vec3d p=hit.subtract(Vec3d.of(host));return new SurfacePoint(host,face,snap(face.getAxis()==Direction.Axis.X?p.z:p.x),snap(face.getAxis()==Direction.Axis.Y?p.z:p.y),layer);}
}
