package top.lacrus.zephyrzeno.electron.mechanical;

import net.minecraft.util.math.*;
import net.minecraft.world.World;
import top.lacrus.zephyrzeno.electron.foundry.*;

public final class MechanicalNetwork {
    public static double speed(World w,BlockPos p,Direction.Axis axis){
        ComponentEntity motor=null;
        for(var direction:Direction.values())if(direction.getAxis()==axis){
            for(int i=1;i<=16;i++){
                var q=p.offset(direction,i);if(!w.isChunkLoaded(q))return 0;
                var s=w.getBlockState(q);
                if(s.getBlock() instanceof MechanicalBlock&&s.get(MechanicalBlock.FACING).getAxis()==axis)continue;
                if(s.isOf(Foundry.MOTOR)&&s.get(ComponentBlock.FACING)==direction.getOpposite()&&!s.get(ComponentBlock.BROKEN)&&w.getBlockEntity(q) instanceof ComponentEntity e&&!e.paused){if(motor!=null)return 0;motor=e;}
                break;
            }
        }
        return motor==null?0:motor.omega*motor.getCachedState().get(ComponentBlock.FACING).getDirection().offset();
    }
    public static int load(World w,BlockPos motor,Direction out){
        for(int i=1;i<=16;i++){
            var p=motor.offset(out,i);if(!w.isChunkLoaded(p))return 1;
            var s=w.getBlockState(p);if(!(s.getBlock() instanceof MechanicalBlock)||s.get(MechanicalBlock.FACING).getAxis()!=out.getAxis())break;
            if(w.getBlockEntity(p) instanceof MechanicalEntity e&&!e.mounted.isEmpty())return 1;
        }
        return 0;
    }
    private MechanicalNetwork(){}
}
