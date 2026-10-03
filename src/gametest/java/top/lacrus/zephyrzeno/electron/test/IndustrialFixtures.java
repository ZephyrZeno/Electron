package top.lacrus.zephyrzeno.electron.test;

import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.*;

final class IndustrialFixtures {
    static void plant(ServerWorld w,BlockPos p,ComponentBlock block){
        boolean nuclear=block==Foundry.NUCLEAR;int xmin=nuclear?-16:-4,xmax=nuclear?16:4,zmin=nuclear?-30:-7;
        for(var q:BlockPos.iterate(p.add(xmin,0,zmin),p.add(xmax,nuclear?21:8,2)))w.setBlockState(q,Blocks.AIR.getDefaultState());
        for(int x=xmin;x<=xmax;x++)for(int z=zmin;z<=1;z++)w.setBlockState(p.add(x,-1,z),Blocks.SMOOTH_STONE.getDefaultState());
        var state=block.getDefaultState().with(ComponentBlock.FACING,Direction.UP);w.setBlockState(p,state);
        PlantStructures.modules(p,state).forEach((q,b)->w.setBlockState(q,b.getDefaultState()));
        var tank=(FluidEntity)w.getBlockEntity(PlantStructures.tankPos(p,state));tank.change(nuclear?200000:15000);
    }
    static BlockPos wind(ServerWorld w,BlockPos p,int height){
        for(var q:BlockPos.iterate(p.add(-6,-height-2,-3),p.add(6,8,3)))w.setBlockState(q,Blocks.AIR.getDefaultState());
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)w.setBlockState(p.add(x,-height-2,z),Blocks.SMOOTH_STONE.getDefaultState());
        var base=p.down(height+1);w.setBlockState(base,Industrial.WIND_BASE.getDefaultState().with(ComponentBlock.FACING,Direction.NORTH));
        for(int i=1;i<=height;i++)w.setBlockState(p.down(i),Industrial.MAST.getDefaultState());
        w.setBlockState(p,Foundry.WIND.getDefaultState().with(ComponentBlock.FACING,Direction.UP));return base;
    }
    private IndustrialFixtures(){}
}
