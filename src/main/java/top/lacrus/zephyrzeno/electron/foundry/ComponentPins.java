package top.lacrus.zephyrzeno.electron.foundry;

import net.minecraft.text.Text;
import top.lacrus.zephyrzeno.electron.circuit.*;
import net.minecraft.util.math.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

/** Canonical unrotated pin layout shared by routing, hit tests, selection and labels. */
public final class ComponentPins {
    public static int count(ComponentBlock.Kind kind){return switch(kind){case GATE->5;case BATTERY,BOOST,BUCK,SUBSTATION,MAINS,RELAY->4;case MOSFET->3;case TERMINAL_STRIP,DISTRIBUTION->8;default->2;};}
    public static int u(ComponentBlock.Kind kind,int pin){
        return switch(kind){case GATE->new int[]{3,11,3,11,7}[pin];case BATTERY,BOOST,BUCK,SUBSTATION,MAINS,RELAY->pin%2==0?3:11;case MOSFET->new int[]{3,11,7}[pin];case TERMINAL_STRIP,DISTRIBUTION->pin<4?3:11;default->pin==0?3:11;};
    }
    public static int v(ComponentBlock.Kind kind,int pin){return switch(kind){case GATE->new int[]{13,13,3,3,7}[pin];case BATTERY,BOOST,BUCK,SUBSTATION,MAINS,RELAY,MOSFET->pin<2?13:3;case TERMINAL_STRIP,DISTRIBUTION->3+4*(pin%4);default->7;};}
    public static String label(ComponentBlock.Kind kind,int pin){return switch(kind){
        case GATE->new String[]{"VCC","GND","A","B","Y"}[pin];
        case BATTERY,BOOST,BUCK,SUBSTATION,MAINS->new String[]{"IN+","IN-","OUT+","OUT-"}[pin];
        case RELAY->new String[]{"COIL+","COIL-","COM","NO"}[pin];
        case MOSFET->new String[]{"G","D","S"}[pin];
        case TERMINAL_STRIP,DISTRIBUTION->(pin<4?"A":"B")+(pin%4+1);
        case RESISTOR,SWITCH,BUTTON,FUSE,BREAKER->pin==0?"A":"B";
        case DIODE->pin==0?"A":"K";
        default->pin==0?"+":"-";
    };}
    public static Text name(ComponentBlock.Kind kind,int pin){
        if(kind==ComponentBlock.Kind.SUBSTATION||kind==ComponentBlock.Kind.MAINS||kind==ComponentBlock.Kind.BATTERY||kind==ComponentBlock.Kind.BOOST||kind==ComponentBlock.Kind.BUCK)return Text.translatable("pin.electron."+new String[]{"charge_plus","charge_minus","output_plus","output_minus"}[pin]);
        if(kind==ComponentBlock.Kind.RELAY)return Text.translatable("pin.electron."+new String[]{"coil_plus","coil_minus","com","no"}[pin]);
        if(kind==ComponentBlock.Kind.TERMINAL_STRIP||kind==ComponentBlock.Kind.DISTRIBUTION)return Text.translatable("pin.electron.bus",pin<4?"A":"B",pin%4+1);
        String label=label(kind,pin);return Text.translatable("pin.electron."+switch(label){case "+"->"plus";case "-"->"minus";default->label.toLowerCase(java.util.Locale.ROOT);});
    }
    public static Endpoint endpoint(BlockPos pos,Direction face,int turn,ComponentBlock.Kind kind,int pin){
        var point=ComponentGeometry.rotate(new SurfacePoint(pos,face,u(kind,pin),v(kind,pin),0),turn);
        return new Endpoint(node(pos,pin),point,name(kind,pin));
    }
    private ComponentPins(){}
}
