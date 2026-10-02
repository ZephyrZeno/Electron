package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import top.lacrus.zephyrzeno.electroncalculator.BluePrint.CompactBlueprint.*;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.DecimalCircuit.Module;

/** Vertical two-XNOR full adder, with buffered pins replacing the demonstration levers/lamps.
 * Circuit reference: RubenHillier, "Compact, 2-tick carry, 2-wide, full adder":
 * https://www.reddit.com/r/redstone/comments/1vld1df/
 * The template is a functional circuit, not a result lookup table.
 */
public final class VerticalFullAdder {
    // Each pair is north/south. # wool, w dust, t standing torch, arrows wall-torch facing.
    private static final String[][] LAYERS={
        {"      ","# # # "},
        {" ##>#^","t w#w#"},
        {" L<#w#","# #t#v"},
        {"#^  # ","w#w#w#"},
        {"t# ###","   w w"},
        {"#w#www","v# ## "},
        {"^#t#  ","#w<#w "},
        {"#L#   "," # w  "}
    };
    private static void put(CompactBlueprint p,Pos pos,Part part,int dir,Module module) {
        Tile old=p.blocks.putIfAbsent(pos,new Tile(part,module,dir,-1));
        if(old!=null)throw new IllegalStateException("Full-adder overlap at "+pos);
    }
    private static void supported(CompactBlueprint p,Pos pos,Part part,int dir,Module module) {
        p.blocks.putIfAbsent(pos.add(0,-1,0),new Tile(Part.WOOL,module,0,-1));put(p,pos,part,dir,module);
    }
    private static void core(CompactBlueprint p,Pos origin) {
        for(int y=0;y<8;y++)for(int z=0;z<2;z++)for(int x=0;x<6;x++) {
            char c=LAYERS[y][z].charAt(x);if(c==' ')continue;
            Part part=switch(c){case '#'->Part.WOOL;case 'w','L'->Part.WIRE;case 't'->Part.STANDING_TORCH;default->Part.TORCH;};
            int dir=switch(c){case 'v'->1;case '<'->2;case '^'->3;default->0;};
            put(p,origin.add(x,y,z),part,dir,Module.ADD);
        }
        for(int y:new int[]{2,7}) {
            supported(p,origin.add(1,y,-1),Part.REPEATER,3,Module.ADD);
            supported(p,origin.add(1,y,-2),Part.WIRE,0,Module.ADD);
        }
    }
    private static void wire(CompactBlueprint p,Pos pos,Module m) {supported(p,pos,Part.WIRE,0,m);}
    private static void lamp(CompactBlueprint p,String name,Pos dust) {
        Pos pos=dust.add(0,-1,0);p.blocks.put(pos,new Tile(Part.LAMP,Module.DISPLAY,0,-1));p.outputs.put(name,pos);
    }
    public static CompactBlueprint fixture() {return rippleFixture(1);}

    /** Same tested carry chain, with isolated bus pins in place of the test controls. */
    public static CompactBlueprint template(int bits) {
        CompactBlueprint fixture=rippleFixture(bits),p=new CompactBlueprint();
        // Lift the slice to leave an insulated input bus below the carry wiring.
        fixture.blocks.forEach((pos,tile)->{
            if(tile.part()!=Part.LEVER)p.blocks.put(pos.add(1,3,0),tile.part()==Part.LAMP?new Tile(Part.WOOL,Module.ADD,0,-1):tile);
        });
        for(int bit=0;bit<bits;bit++) {
            Pos origin=new Pos(10,6,8+(bits-bit-1)*8);
            wire(p,origin.add(-3,-1,3),Module.INPUT);wire(p,origin.add(-4,-2,3),Module.INPUT);wire(p,origin.add(-5,-3,3),Module.INPUT);
            supported(p,origin.add(-6,-3,3),Part.REPEATER,0,Module.INPUT);wire(p,origin.add(-7,-3,3),Module.INPUT);
            supported(p,origin.add(8,0,3),Part.REPEATER,2,Module.INPUT);wire(p,origin.add(9,0,3),Module.INPUT);
            p.inputs.put("A"+bit,origin.add(-7,-3,3));p.inputs.put("B"+bit,origin.add(9,0,3));
            for(int x=2;x<=6;x++)wire(p,origin.add(x,7,-2),Module.ADD);
            supported(p,origin.add(7,7,-2),Part.REPEATER,0,Module.ADD);
            wire(p,origin.add(8,7,-2),Module.ADD);wire(p,origin.add(9,7,-2),Module.ADD);p.outputs.put("R"+bit,origin.add(9,7,-2));
            if(bit==0)p.inputs.put("CIN",origin.add(-2,0,1));
            if(bit==bits-1) {
                for(int x=0;x>=-4;x--)wire(p,origin.add(x,2,-2),Module.ADD);
                supported(p,origin.add(-5,2,-2),Part.REPEATER,2,Module.ADD);
                wire(p,origin.add(-6,2,-2),Module.ADD);wire(p,origin.add(-7,2,-2),Module.ADD);p.outputs.put("COUT",origin.add(-7,2,-2));
            }
        }
        p.width=22;p.height=17;p.depth=14+(bits-1)*8;p.estimatedTicks=16*bits+32;return p;
    }

    /** A fixed bit slice repeated at an eight-block pitch, with a predetermined carry connection. */
    public static CompactBlueprint rippleFixture(int bits) {
        if(bits<1||bits>16)throw new IllegalArgumentException("1..16 bits");
        CompactBlueprint p=new CompactBlueprint();
        for(int bit=0;bit<bits;bit++) {
            Pos origin=new Pos(9,3,8+(bits-bit-1)*8);core(p,origin);
            supported(p,origin.add(-1,0,1),Part.REPEATER,0,Module.ADD);
            wire(p,origin.add(-2,0,1),Module.ADD);
            for(int x:new int[]{2,4})supported(p,origin.add(x,0,2),Part.REPEATER,3,Module.INPUT);
            for(int x=-2;x<=2;x++)wire(p,origin.add(x,0,3),Module.INPUT);
            for(int x=4;x<=7;x++)wire(p,origin.add(x,0,3),Module.INPUT);
            for(boolean a:new boolean[]{true,false}) {
                Pos lever=origin.add(a?-2:7,0,4);supported(p,lever,Part.LEVER,0,Module.INPUT);
                p.inputs.put((a?"A":"B")+(bits==1?"":bit),lever);
            }
            if(bit==0) {
                Pos lever=origin.add(-3,0,1);supported(p,lever,Part.LEVER,0,Module.INPUT);p.inputs.put("CIN",lever);
            }
            lamp(p,bits==1?"SUM":"R"+bit,origin.add(1,7,-2));
            if(bit==bits-1)lamp(p,"COUT",origin.add(1,2,-2));
            else {
                for(int x=0;x>=-5;x--)wire(p,origin.add(x,2,-2),Module.ADD);
                wire(p,origin.add(-5,1,-3),Module.ADD);
                for(int z=-4;z>=-7;z--)wire(p,origin.add(-5,0,z),Module.ADD);
                for(int x=-4;x<=-3;x++)wire(p,origin.add(x,0,-7),Module.ADD);
            }
        }
        p.width=19;p.height=13;p.depth=14+(bits-1)*8;p.estimatedTicks=16*bits+20;return p;
    }
}
