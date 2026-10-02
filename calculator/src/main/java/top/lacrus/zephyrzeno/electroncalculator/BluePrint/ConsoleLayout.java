package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.util.*;
import static top.lacrus.zephyrzeno.electroncalculator.BluePrint.CompactBlueprint.*;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.DecimalCircuit.Module;

/** Vanilla keypad, request memory and standing screen. No runtime input listeners. */
public final class ConsoleLayout {
    public static final int OFFSET_X=270,SCREEN_Z=55;
    public final CompactBlueprint fixed=new CompactBlueprint();
    public final Map<Integer,List<Pos>> sinks=new LinkedHashMap<>();
    public final Set<Pos> guards=new HashSet<>();
    public final Map<String,Pos> clockTargets=new LinkedHashMap<>();
    public final Map<String,List<Pos>> resultPins=new LinkedHashMap<>();
    public final Pos calcStart=new Pos(160,1,40),calcDone=new Pos(160,1,44);
    public final KeypadCircuit keypad;
    private ConsoleLayout(KeypadCircuit keypad) {
        this.keypad=keypad;var c=keypad.circuit;
        for(int x=0;x<171;x++)for(int z=0;z<62;z++)put(new Pos(x,0,z),Part.STONE,Module.CONTROL,0,-1);
        String[][] keys={{"7","8","9","DIVIDE"},{"4","5","6","MULTIPLY"},{"1","2","3","MINUS"},{"CLEAR","0","BACK","PLUS"},{"RUN"}};
        for(int row=0;row<keys.length;row++)for(int col=0;col<keys[row].length;col++)key(keys[row][col],new Pos(120+col*9,2,43-row*8));
        int i=0;
        for(String name:c.inputs.keySet())if(name.startsWith("CLOCK")) {
            int signal=c.inputs.get(name);Pos lever=new Pos(120+i++*5,8,8);
            component(lever,Part.LEVER,0,signal,Module.CONTROL);fixed.inputs.put(name,lever);
            component(lever.add(0,0,1),Part.REPEATER,1,signal,Module.CONTROL);
            component(lever.add(0,0,2),Part.WIRE,0,signal,Module.CONTROL);fixed.sources.put(signal,lever.add(0,0,2));clockTargets.put(name,lever.add(0,0,-1));
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)guards.add(lever.add(dx,0,dz));
        }
        Pos bulb=new Pos(160,1,42);component(bulb,Part.COPPER_BULB,0,-1,Module.CONTROL);
        component(bulb.add(0,0,-1),Part.REPEATER,1,-1,Module.CONTROL);component(calcStart,Part.WIRE,0,-1,Module.CONTROL);
        component(bulb.add(0,0,1),Part.REPEATER,3,-1,Module.CONTROL);component(calcDone,Part.WIRE,0,-1,Module.CONTROL);
        component(bulb.add(1,0,0),Part.COMPARATOR,0,-1,Module.CONTROL);
        Pos busy=bulb.add(2,0,0);component(busy,Part.WIRE,0,c.inputs.get("CALC_BUSY"),Module.CONTROL);
        component(busy.add(1,1,0),Part.WIRE,0,c.inputs.get("CALC_BUSY"),Module.CONTROL);fixed.sources.put(c.inputs.get("CALC_BUSY"),busy.add(1,1,0));
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)guards.add(bulb.add(dx,0,dz));
        for(int x=3;x<=107;x++)for(int y=5;y<=80;y++) {
            boolean edge=x==3||x==107||y==5||y==80;put(new Pos(x,y,SCREEN_Z),edge?Part.WHITE:Part.DARK,Module.DISPLAY,0,-1);
            guards.add(new Pos(x,y,SCREEN_Z));guards.add(new Pos(x,y,SCREEN_Z-1));
            if(edge)for(int z=56;z<=59;z++)put(new Pos(x,y,z),Part.WHITE,Module.DISPLAY,0,-1);
        }
        for(int x:new int[]{6,104})for(int y=1;y<5;y++)for(int z=55;z<=59;z++)put(new Pos(x,y,z),Part.WHITE,Module.DISPLAY,0,-1);
        for(int digit=0;digit<4;digit++)segments("SEG"+(3-digit)+"_",new Pos(10+digit*24,8,SCREEN_Z),3,null);
        int index=0;for(String p:List.of("AT","AU","BT","BU")){segments("INPUT_"+p+"_",new Pos(index<2?14+index*20:64+(index-2)*20,52,SCREEN_Z),2,c);index++;}
        index=0;for(String op:List.of("PLUS","MINUS","MULTIPLY","DIVIDE")) {
            lamp("SELECT_"+op,new Pos(55,58+index*6,SCREEN_Z),c);
            fixed.labels.add(new Label(new Pos(58,58+index*6,54),List.of("加 +","减 −","乘 ×","除 ÷").get(index),"亮灯为当前运算"));index++;
        }
        lamp("READY",new Pos(90,3,12),c);lamp("BUSY",new Pos(96,3,12),c);lamp("INPUT_ERROR",new Pos(102,3,12),c);
        resultLamp("NEG",new Pos(6,26,SCREEN_Z));resultLamp("ERROR",new Pos(103,26,SCREEN_Z));resultLamp("DOT",new Pos(55,7,SCREEN_Z));
        for(int bit=0;bit<14;bit++)resultLamp("R"+bit,new Pos(12+(13-bit)*3,2,51));
        fixed.labels.add(new Label(new Pos(8,2,4),"计算器操作平台","数字→运算→数字→="));
        fixed.labels.add(new Label(new Pos(8,2,8),"每个数零至九十九","一次按键追加一位"));
        fixed.labels.add(new Label(new Pos(8,2,12),"清空与退格","C清输入；←删末位"));
        fixed.labels.add(new Label(new Pos(8,2,16),"输入就绪灯","灯亮后再按下一键"));
        fixed.labels.add(new Label(new Pos(90,2,8),"输入就绪","请等待灯亮后按键"));
        fixed.labels.add(new Label(new Pos(96,2,8),"运算／按键处理中","原版电路需要等待"));
        fixed.labels.add(new Label(new Pos(102,2,8),"输入错误","缺项／超两位／多键"));
        fixed.labels.add(new Label(new Pos(50,2,48),"二进制结果","左高右低；除法×100"));
        fixed.labels.add(new Label(new Pos(52,81,55),"Circuit Foundry","上方输入；下方结果"));
        fixed.labels.add(new Label(new Pos(104,25,54),"运算错误","除数不能为零"));
        for(Label label:fixed.labels){putIfAbsent(label.pos().add(0,-1,0),Part.WOOL,Module.CONTROL,0,-1);guards.add(label.pos());guards.add(label.pos().add(0,-1,0));}
    }
    public static ConsoleLayout create(KeypadCircuit keypad){return new ConsoleLayout(keypad);}
    public FixedPanel panel(){return new FixedPanel(fixed,sinks,guards);}
    private void key(String name,Pos button) {
        int signal=keypad.key.get(name);Pos b=button.add(0,-1,0);
        component(button,Part.BUTTON,0,-1,Module.INPUT);fixed.inputs.put("KEY_"+name,button);
        component(b.add(0,0,1),Part.REPEATER,1,-1,Module.INPUT);component(b.add(0,0,2),Part.COPPER_BULB,0,-1,Module.INPUT);
        component(b.add(0,0,3),Part.COMPARATOR,1,signal,Module.INPUT);Pos source=b.add(0,0,4);component(source,Part.WIRE,0,signal,Module.INPUT);
        component(source.add(0,1,1),Part.WIRE,0,signal,Module.INPUT);fixed.sources.put(signal,source.add(0,1,1));
        component(b.add(1,0,2),Part.REPEATER,2,keypad.circuit.outputs.get("RESET_"+name),Module.CONTROL);Pos reset=b.add(2,0,2);
        component(reset,Part.WIRE,0,keypad.circuit.outputs.get("RESET_"+name),Module.CONTROL);sink(keypad.circuit.outputs.get("RESET_"+name),reset);
        component(b.add(-1,0,1),Part.REPEATER,0,keypad.circuit.outputs.get("BUSY"),Module.CONTROL);component(b.add(-2,0,1),Part.REPEATER,0,keypad.circuit.outputs.get("BUSY"),Module.CONTROL);
        Pos lock=b.add(-3,0,1);component(lock,Part.WIRE,0,keypad.circuit.outputs.get("BUSY"),Module.CONTROL);sink(keypad.circuit.outputs.get("BUSY"),lock);
        for(int x=-2;x<=2;x++)for(int y=-1;y<=1;y++)for(int z=0;z<=3;z++)guards.add(b.add(x,y,z));
        String title=switch(name){case "PLUS"->"加 +";case "MINUS"->"减 −";case "MULTIPLY"->"乘 ×";case "DIVIDE"->"除 ÷";case "CLEAR"->"清空 C";case "BACK"->"退格 ←";case "RUN"->"运行 =";default->name;};
        fixed.labels.add(new Label(button.add(0,0,-1),title,"请逐键输入"));
    }
    private void segments(String prefix,Pos origin,int scale,DecimalCircuit c) {
        for(int segment=0;segment<7;segment++)for(int i=1;i<=5;i++) {
            int x=segment==1||segment==2?6:segment==4||segment==5?0:i;
            int y=switch(segment){case 0->12;case 3->0;case 6->6;case 1,5->6+i;default->i;};
            Pos p=origin.add(x*scale,y*scale,0);if(c==null)resultLamp(prefix+segment,p);else lamp(prefix+segment,p,c);
        }
    }
    private void lamp(String name,Pos p,DecimalCircuit c) {
        int signal=c.outputs.get(name);fixed.blocks.put(p,new Tile(Part.LAMP,Module.DISPLAY,0,signal));
        Pos pin=p.add(0,0,1);component(pin,Part.WIRE,0,signal,Module.DISPLAY);sink(signal,pin);fixed.outputs.putIfAbsent(name,p);
    }
    private void resultLamp(String name,Pos p) {
        fixed.blocks.put(p,new Tile(Part.LAMP,Module.DISPLAY,0,-1));Pos pin=p.add(0,0,1);component(pin,Part.WIRE,0,-1,Module.DISPLAY);
        resultPins.computeIfAbsent(name,k->new ArrayList<>()).add(pin);fixed.outputs.putIfAbsent(name,p);
    }
    private void sink(int id,Pos p){sinks.computeIfAbsent(id,k->new ArrayList<>()).add(p);}
    private void component(Pos p,Part part,int dir,int net,Module m){putIfAbsent(p.add(0,-1,0),Part.WOOL,m,0,-1);put(p,part,m,dir,net);}
    private void putIfAbsent(Pos p,Part part,Module m,int dir,int net){fixed.blocks.putIfAbsent(p,new Tile(part,m,dir,net));}
    private void put(Pos p,Part part,Module m,int dir,int net) {
        Tile tile=new Tile(part,m,dir,net),old=fixed.blocks.putIfAbsent(p,tile);
        if(old!=null&&!old.equals(tile))throw new IllegalStateException("Console overlap "+p+" "+old+" / "+tile);
    }
}
