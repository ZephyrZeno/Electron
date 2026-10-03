package top.lacrus.zephyrzeno.electron.industrial;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.multiblock.Assembly;

/** Paid structural anchors; visual cells never create the functional parts for free. */
public final class PlantStructures {
    public static BlockPos at(BlockPos p,BlockState state,int x,int y,int z){return p.add(switch(state.get(ComponentBlock.TURN)){case 1->new BlockPos(-z,y,x);case 2->new BlockPos(-x,y,-z);case 3->new BlockPos(z,y,-x);default->new BlockPos(x,y,z);});}
    public static Map<BlockPos,Block> modules(BlockPos p,BlockState s){
        var out=new LinkedHashMap<BlockPos,Block>();var k=((ComponentBlock)s.getBlock()).kind;
        if(k==ComponentBlock.Kind.THERMAL){put(out,p,s,0,0,-4,Industrial.BOILER);put(out,p,s,2,0,-3,Industrial.TURBINE);put(out,p,s,2,0,-1,Industrial.ALTERNATOR);put(out,p,s,-2,0,-4,Industrial.CONDENSER);put(out,p,s,0,0,-1,Industrial.CONTROL);put(out,p,s,0,1,-4,Industrial.SAFETY_VALVE);put(out,p,s,-2,0,-1,Industrial.THERMAL_TANK);}
        if(k==ComponentBlock.Kind.NUCLEAR){put(out,p,s,-3,0,-20,Industrial.REACTOR_VESSEL);put(out,p,s,-3,1,-20,Industrial.CONTROL_RODS);put(out,p,s,-3,3,-20,Industrial.SAFETY_VALVE);put(out,p,s,7,0,-7,Industrial.TURBINE);put(out,p,s,10,0,-7,Industrial.TURBINE);put(out,p,s,7,0,-3,Industrial.ALTERNATOR);put(out,p,s,6,0,-15,Industrial.CONDENSER);put(out,p,s,-8,0,-3,Industrial.CONTROL);put(out,p,s,-14,0,-5,Industrial.NUCLEAR_TANK);for(int y=0;y<3;y++)for(int x=-8;x<=2;x++)for(int z=-25;z<=-15;z++)if(x==-8||x==2||z==-25||z==-15)put(out,p,s,x,y,z,Foundry.REACTOR_CASING);}
        if(k==ComponentBlock.Kind.THERMAL||k==ComponentBlock.Kind.NUCLEAR){
            out.put(consolePos(p,s),Industrial.CONTROL);out.put(fuelPos(p,s),k==ComponentBlock.Kind.NUCLEAR?Industrial.ROD_PORT:Industrial.FUEL_PORT);
            out.put(outputPos(p,s),k==ComponentBlock.Kind.NUCLEAR?Industrial.SPENT_PORT:Industrial.ASH_PORT);
            out.put(waterPos(p,s),Industrial.WATER_INLET);
            if(k==ComponentBlock.Kind.NUCLEAR)out.remove(at(p,s,-8,0,-3));
        }
        return out;
    }
    public static BlockPos consolePos(BlockPos p,BlockState s){return ((ComponentBlock)s.getBlock()).kind==ComponentBlock.Kind.NUCLEAR?at(p,s,16,0,-3):at(p,s,0,0,-1);}
    public static BlockPos fuelPos(BlockPos p,BlockState s){return ((ComponentBlock)s.getBlock()).kind==ComponentBlock.Kind.NUCLEAR?at(p,s,20,1,-3):at(p,s,-3,1,-2);}
    public static BlockPos outputPos(BlockPos p,BlockState s){return s.isOf(Foundry.NUCLEAR)?at(p,s,20,1,-5):at(p,s,-3,1,-4);}
    public static BlockPos waterPos(BlockPos p,BlockState s){return s.isOf(Foundry.NUCLEAR)?at(p,s,-14,0,-4):at(p,s,-3,0,-1);}
    public static Direction serviceFace(BlockState s,boolean control){
        var face=control?Direction.SOUTH:s.isOf(Foundry.NUCLEAR)?Direction.EAST:Direction.WEST;
        for(int i=0;i<s.get(ComponentBlock.TURN);i++)face=face.rotateYClockwise();return face;
    }
    private static void put(Map<BlockPos,Block> out,BlockPos p,BlockState s,int x,int y,int z,Block b){out.put(at(p,s,x,y,z),b);}
    public static BlockPos tankPos(BlockPos p,BlockState s){return ((ComponentBlock)s.getBlock()).kind==ComponentBlock.Kind.NUCLEAR?at(p,s,-14,0,-5):at(p,s,-2,0,-1);}
    public static String structure(ServerWorld w,BlockPos p,BlockState s){
        var k=((ComponentBlock)s.getBlock()).kind;
        if(k==ComponentBlock.Kind.THERMAL||k==ComponentBlock.Kind.NUCLEAR){
            int xmin=k==ComponentBlock.Kind.NUCLEAR?-15:-3,xmax=k==ComponentBlock.Kind.NUCLEAR?20:3,zmin=k==ComponentBlock.Kind.NUCLEAR?-35:-6;
            for(int x=xmin;x<=xmax;x++)for(int z=zmin;z<=0;z++){var q=at(p,s,x,-1,z);if(!w.isChunkLoaded(q))return "unloaded";if(!w.getBlockState(q).isOf(Blocks.SMOOTH_STONE))return k==ComponentBlock.Kind.NUCLEAR?"nuclear_foundation":"thermal_foundation";}
            for(var m:modules(p,s).entrySet()){if(!w.isChunkLoaded(m.getKey()))return "unloaded";if(!Assembly.original(w,m.getKey()).isOf(m.getValue()))return "modules";}
            if(k==ComponentBlock.Kind.NUCLEAR)for(int y=0;y<3;y++)for(int x=-8;x<=2;x++)for(int z=-25;z<=-15;z++)if(x==-8||x==2||z==-25||z==-15){var q=at(p,s,x,y,z);if(!w.isChunkLoaded(q))return "unloaded";if(!Assembly.original(w,q).isOf(Foundry.REACTOR_CASING))return "shielding";}
            return "ready";
        }
        if(k==ComponentBlock.Kind.WIND){
            int h=0;while(h<64&&Assembly.original(w,p.down(h+1)).isOf(Industrial.MAST))h++;
            if(h<6||h>=64||!w.getBlockState(p.down(h+1)).isOf(Industrial.WIND_BASE))return "wind_mast";
            if(!Block.isShapeFullCube(w.getBlockState(p.down(h+2)).getCollisionShape(w,p.down(h+2))))return "tower_base";
            return w.isSkyVisible(p.up(8))?"ready":"sky";
        }
        if(k==ComponentBlock.Kind.HYDRO)return w.getBlockState(p.down()).isOf(Blocks.SMOOTH_STONE)?"ready":"foundation";
        return "ready";
    }
    public static BlockPos windBase(ServerWorld w,BlockPos p){int h=0;while(h<64&&Assembly.original(w,p.down(h+1)).isOf(Industrial.MAST))h++;return p.down(h+1);}
    public static boolean flowingWater(ServerWorld w,BlockPos p,BlockState s){
        var head=at(p,s,0,0,-1);if(!w.isChunkLoaded(head))return false;var f=w.getFluidState(head);
        return f.isIn(net.minecraft.registry.tag.FluidTags.WATER)&&!f.isStill();
    }
    private PlantStructures(){}
}
