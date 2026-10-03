package top.lacrus.zephyrzeno.electron.power;

import net.minecraft.block.BlockState;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.*;

/** Source selection is independent of rendering: hidden/off-camera machines remain audible. */
public record MachineSoundProfile(SoundEvent sound,float gain,Vec3d offset) {
    public static MachineSoundProfile of(BlockState s){
        if(!(s.getBlock() instanceof ComponentBlock b))return null;
        SoundEvent event;float volume=.25f;Vec3d at=new Vec3d(.5,.6,.5);
        if(s.isOf(Industrial.PUMP))event=ElectricalEffects.PUMP;
        else if(b instanceof ProcessingBlock p)event=switch(p.machine){
            case "ore_crusher"->ElectricalEffects.CRUSHER;
            case "centrifuge"->ElectricalEffects.CENTRIFUGE;
            case "electric_kiln","uv_exposure_unit"->ElectricalEffects.KILN;
            case "fuel_fabricator"->ElectricalEffects.FABRICATOR;
            case "pcb_drill"->ElectricalEffects.DRILL;
            default->ElectricalEffects.BATH;
        };
        else switch(b.kind){
            case WIND->{event=ElectricalEffects.WIND;at=new Vec3d(.5,2.5,.3);volume=.4f;}
            case THERMAL->{event=ElectricalEffects.TURBINE;at=new Vec3d(2.5,.8,-1.5);volume=.45f;}
            case NUCLEAR->{event=ElectricalEffects.REACTOR;at=new Vec3d(7.5,1,-3.5);volume=.5f;}
            case HYDRO->{event=ElectricalEffects.HYDRO;at=new Vec3d(.5,.7,-.5);volume=.42f;}
            case SUBSTATION->{event=ElectricalEffects.TRANSFORMER;at=new Vec3d(.5,.8,-.5);volume=.16f;}
            case MOTOR->event=ElectricalEffects.MOTOR;
            default->{return null;}
        }
        return new MachineSoundProfile(event,volume,top.lacrus.zephyrzeno.electron.multiblock.AssemblyGeometry.rotate(at,s.get(ComponentBlock.TURN)));
    }
    public static boolean active(BlockState s){
        return s.getBlock() instanceof ComponentBlock&&s.get(ComponentBlock.LEVEL)>0&&!s.get(ComponentBlock.BROKEN);
    }
}
