package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.*;
import top.lacrus.zephyrzeno.electron.foundry.ComponentGeometry;

/** Coarse physical parts, cached by orientation; cosmetic screws do not inflate collision complexity. */
public final class WorkshopGeometry {
    private static final Map<String,VoxelShape> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
    public static VoxelShape shape(String machine,Direction face,int turn){return CACHE.computeIfAbsent(machine+face+turn,k->{
        var boxes=new ArrayList<double[]>();
        boxes.add(new double[]{.8,5.2,0,13.2,8.8,1.5});
        if(machine.equals("precision_workbench")){
            boxes.add(new double[]{.45,10.85,.45,15.55,11.8,15.55});
            for(double x:new double[]{1.2,13.4})for(double z:new double[]{1.2,13.4})boxes.add(new double[]{x,0,z,x+1,10.85,z+1});
            boxes.add(new double[]{10,7,3,15.25,10.85,14.5});
            boxes.add(new double[]{1.4,11.8,1.8,6.9,15.4,3.6});boxes.add(new double[]{8,11.8,1.5,14.8,15.4,6.8});
        }else if(machine.equals("emergency_lamp")){
            boxes.add(new double[]{1,1.4,1.5,15,9.1,7.3});
            for(double x:new double[]{2.4,9.9})boxes.add(new double[]{x,9.1,2.3,x+4.4,14.1,7.3});
        }else{
            boxes.add(new double[]{.7,0,.7,15.3,2.2,15.3});
            switch(machine){
                case "pcb_drill"->{boxes.add(new double[]{1.8,2.2,11.8,3.3,13.7,14.8});boxes.add(new double[]{12.7,2.2,11.8,14.2,13.7,14.8});boxes.add(new double[]{1.8,12.8,6.7,14.2,14.5,14.8});boxes.add(new double[]{1.7,2.2,1.7,4.7,10.7,4.2});boxes.add(new double[]{3,2.2,3.3,13,4.5,13.4});boxes.add(new double[]{7,4.4,7,9.5,14.4,10});}
                case "immersion_gold_line"->{boxes.add(new double[]{1.5,2.2,3.4,14.5,8.9,14.5});boxes.add(new double[]{.75,8.9,12.1,1.4,15.2,12.8});boxes.add(new double[]{14.6,8.9,12.1,15.25,15.2,12.8});boxes.add(new double[]{.75,14.4,10.4,15.25,15.2,12.8});boxes.add(new double[]{12.8,10.1,1.8,15.3,13.1,3.9});}
                case "solder_mask_applicator"->{boxes.add(new double[]{1.6,2.2,3,14.4,3.9,14.5});boxes.add(new double[]{1.5,3.9,5,2.8,12.5,11});boxes.add(new double[]{13.2,3.9,5,14.5,12.5,11});boxes.add(new double[]{1.5,11.6,5,14.5,12.5,11});boxes.add(new double[]{2.65,12.5,5.55,5.35,15.2,8.25});boxes.add(new double[]{11.7,3.9,1.7,14.8,10.8,4});}
                default->boxes.add(new double[]{1.2,2.2,1.4,14.8,13.3,15.25});
            }
        }
        VoxelShape shape=VoxelShapes.empty();
        for(var r:boxes){var a=ComponentGeometry.local(face,turn,r[0],r[2],16-r[1]).multiply(16);var b=ComponentGeometry.local(face,turn,r[3],r[5],16-r[4]).multiply(16);shape=VoxelShapes.union(shape,Block.createCuboidShape(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.min(a.z,b.z),Math.max(a.x,b.x),Math.max(a.y,b.y),Math.max(a.z,b.z)));}
        return shape;
    });}
    private WorkshopGeometry(){}
}
