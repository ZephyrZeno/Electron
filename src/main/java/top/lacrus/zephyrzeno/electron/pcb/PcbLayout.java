package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import top.lacrus.zephyrzeno.electron.logic.LogicFunction;

/** Immutable manufactured geometry. Copper only crosses layers at explicit plated through vias. */
public record PcbLayout(List<Part> parts,List<Track> tracks,int width,int height,Set<Integer> cutouts,
                        int layers,Set<Integer> vias,List<Integer> connectors) {
    public static final int WIDTH=128,HEIGHT=96,CELLS=WIDTH*HEIGHT,MAX_PARTS=256,MAX_LAYERS=4,MAX_ENCODING=524288;
    public static final int[] PORTS=ports(16,12);
    // Existing ordinals are persistent item/blueprint data: append, never reorder.
    public enum Type {
        R1K(null),R10K(null),LED(null),NOT(LogicFunction.NOT),AND(LogicFunction.AND),OR(LogicFunction.OR),XOR(LogicFunction.XOR),NAND(LogicFunction.NAND),HALF_ADDER(null),DISPLAY(null),
        NOR(LogicFunction.NOR),XNOR(LogicFunction.XNOR),BUFFER(LogicFunction.BUFFER),RGB(null),PIXEL(null),DIAL(null),SWITCH(null),DFF(null),CLOCK(null),TRISTATE(null),MUX(null),SIGN(null);
        public final LogicFunction logic;
        Type(LogicFunction logic){this.logic=logic;}
        public boolean digital(){return logic!=null||ordinal()>=HALF_ADDER.ordinal();}
        public boolean input(){return this==DIAL||this==SWITCH||this==CLOCK;}
        public int pins(){return !digital()?2:logic!=null||this==RGB||this==DFF||this==TRISTATE||this==SIGN||this==SWITCH||this==CLOCK?5:6;}
        public int outputs(){return this==DIAL?4:this==HALF_ADDER?2:logic!=null||this==SWITCH||this==DFF||this==CLOCK||this==TRISTATE||this==MUX?1:0;}
        public String pinName(int pin){return switch(this){
            case HALF_ADDER->new String[]{"VCC","GND","A","B","SUM","CARRY"}[pin];
            case DISPLAY,DIAL->new String[]{"VCC","GND","D0","D1","D2","D3"}[pin];
            case PIXEL->new String[]{"VCC","GND","DATA","CLK","LATCH","CLEAR"}[pin];
            case RGB->new String[]{"VCC","GND","R","G","B"}[pin];
            case SIGN->new String[]{"VCC","GND","SIGN","NC","NC"}[pin];
            case DFF->new String[]{"VCC","GND","D","CLK","Q"}[pin];
            case TRISTATE->new String[]{"VCC","GND","A","EN","Y"}[pin];
            case MUX->new String[]{"VCC","GND","A","B","SEL","Y"}[pin];
            case SWITCH,CLOCK->new String[]{"VCC","GND","NC","NC","Y"}[pin];
            default->digital()?new String[]{"VCC","GND","A","B","Y"}[pin]:pin==0?"A":"K/B";
        };}
    }
    public record Part(Type type,int x,int y,int turn){
        public int pin(int pin){
            int u,v;
            if(type==Type.HALF_ADDER){u=new int[]{0,1,0,0,2,2}[pin];v=new int[]{0,2,1,2,1,2}[pin];}
            else if(type.pins()==6){u=new int[]{0,2,0,2,0,2}[pin];v=new int[]{0,0,1,1,2,2}[pin];}
            else if(!type.digital()){u=pin==0?0:2;v=1;}
            else{u=new int[]{0,2,0,2,1}[pin];v=new int[]{0,0,2,2,1}[pin];}
            for(int i=0;i<turn;i++){int old=u;u=2-v;v=old;}return point(x+u,y+v);
        }
        public boolean contains(int u,int v){return u>=x&&u<=x+2&&v>=y&&v<=y+2;}
    }
    public record Track(int a,int b){public Track{if(a>b){int swap=a;a=b;b=swap;}if(a<0||b>=CELLS*MAX_LAYERS||layer(a)!=layer(b)||!adjacent(a,b))throw new IllegalArgumentException("track");}}
    public PcbLayout(List<Part> parts,List<Track> tracks){this(parts,tracks,16,12,Set.of());}
    public PcbLayout(List<Part> parts,List<Track> tracks,int w,int h,Set<Integer> cut){this(parts,tracks,w,h,cut,1,Set.of(),Arrays.stream(ports(w,h)).boxed().toList());}
    public PcbLayout {
        parts=List.copyOf(parts);tracks=List.copyOf(new LinkedHashSet<>(tracks));cutouts=Set.copyOf(cutouts);vias=Set.copyOf(vias);connectors=List.copyOf(connectors);
        if(width<10||width>WIDTH||height<8||height>HEIGHT||layers<1||layers>MAX_LAYERS)throw new IllegalArgumentException("size");
        if(parts.size()>MAX_PARTS||tracks.size()>32000||vias.size()>4096||connectors.size()!=6||new HashSet<>(connectors).size()!=6)throw new IllegalArgumentException("capacity");
        for(int at:cutouts)if(at<0||layer(at)!=0||x(at)>=width||y(at)>=height)throw new IllegalArgumentException("outline");
        for(int at:connectors)if(at<0||layer(at)!=0||x(at)>=width||y(at)>=height||cutouts.contains(at)||x(at)!=0&&x(at)!=width-1&&y(at)!=0&&y(at)!=height-1)throw new IllegalArgumentException("connector");
        for(int at:vias)if(layers==1||at<0||layer(at)!=0||x(at)>=width||y(at)>=height||cutouts.contains(at))throw new IllegalArgumentException("via");
        for(int i=0;i<parts.size();i++){
            var p=parts.get(i);if(p.type==null||p.x<1||p.x>width-4||p.y<0||p.y>height-3||p.turn<0||p.turn>3)throw new IllegalArgumentException("footprint");
            for(int u=p.x;u<=p.x+2;u++)for(int v=p.y;v<=p.y+2;v++)if(cutouts.contains(point(u,v)))throw new IllegalArgumentException("cutout");
            for(int j=0;j<i;j++){var q=parts.get(j);if(p.x<=q.x+2&&p.x+2>=q.x&&p.y<=q.y+2&&p.y+2>=q.y)throw new IllegalArgumentException("overlap");}
        }
        for(var t:tracks)for(int at:new int[]{t.a,t.b})if(layer(at)>=layers||x(at)>=width||y(at)>=height||cutouts.contains(at%CELLS))throw new IllegalArgumentException("copper_outside");
        if(!cutouts.isEmpty()){
            var remaining=new HashSet<Integer>();for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(!cutouts.contains(point(x,y)))remaining.add(point(x,y));
            var queue=new ArrayDeque<Integer>();int start=remaining.iterator().next();remaining.remove(start);queue.add(start);
            while(!queue.isEmpty()){int at=queue.remove();for(int step:new int[]{-1,1,-WIDTH,WIDTH}){int next=at+step;if(next>=0&&next<CELLS&&adjacent(at,next)&&remaining.remove(next))queue.add(next);}}if(!remaining.isEmpty())throw new IllegalArgumentException("disconnected_outline");
        }
    }
    public static int[] ports(int w,int h){return new int[]{point(0,1),point(0,h-2),point(0,h/3),point(0,2*h/3-1),point(w-1,h/3),point(w-1,2*h/3-1)};}
    public int[] ports(){return connectors.stream().mapToInt(Integer::intValue).toArray();}
    public boolean contains(int x,int y){return x>=0&&y>=0&&x<width&&y<height&&!cutouts.contains(point(x,y));}
    private PcbLayout copy(List<Part> p,List<Track> t,Set<Integer> cut,Set<Integer> holes,List<Integer> pins){return new PcbLayout(p,t,width,height,cut,layers,holes,pins);}
    public PcbLayout resize(int w,int h){var pins=connectors.equals(Arrays.stream(ports(width,height)).boxed().toList())?Arrays.stream(ports(w,h)).boxed().toList():connectors;return new PcbLayout(parts,tracks,w,h,cutouts,layers,vias,pins);}
    public PcbLayout withLayers(int n){return new PcbLayout(parts,tracks,width,height,cutouts,n,vias,connectors);}
    public PcbLayout via(int x,int y){var holes=new HashSet<>(vias);if(!holes.remove(point(x,y)))holes.add(point(x,y));return copy(parts,tracks,cutouts,holes,connectors);}
    public PcbLayout port(int pin,int x,int y){var pins=new ArrayList<>(connectors);pins.set(pin,point(x,y));return copy(parts,tracks,cutouts,vias,pins);}
    public PcbLayout cut(int x,int y){if(!contains(x,y)&&!cutouts.contains(point(x,y)))throw new IllegalArgumentException();var c=new HashSet<>(cutouts);if(!c.remove(point(x,y)))c.add(point(x,y));return copy(parts,tracks,c,vias,connectors);}
    public PcbLayout move(int index,int x,int y,int turn){var list=new ArrayList<>(parts);var old=list.get(index);list.set(index,new Part(old.type,x,y,turn));return copy(list,tracks,cutouts,vias,connectors);}
    public PcbLayout removePart(int index){var list=new ArrayList<>(parts);list.remove(index);return copy(list,tracks,cutouts,vias,connectors);}
    public static PcbLayout empty(){return new PcbLayout(List.of(),List.of(),32,24,Set.of());}
    public static int point(int x,int y){return y*WIDTH+x;}
    public static int point(int x,int y,int layer){return layer*CELLS+point(x,y);}
    public static int x(int point){return point%WIDTH;}
    public static int y(int point){return point%CELLS/WIDTH;}
    public static int layer(int point){return point/CELLS;}
    private static boolean adjacent(int a,int b){return Math.abs(x(a)-x(b))+Math.abs(y(a)-y(b))==1;}
    public PcbLayout place(Type type,int x,int y,int turn){var list=new ArrayList<>(parts);list.add(new Part(type,x,y,turn));return copy(list,tracks,cutouts,vias,connectors);}
    public PcbLayout erase(int x,int y){return erase(x,y,0);}
    public PcbLayout erase(int x,int y,int layer){int at=point(x,y,layer);return copy(layer==0?parts.stream().filter(p->!p.contains(x,y)).toList():parts,tracks.stream().filter(t->t.a!=at&&t.b!=at).toList(),cutouts,vias,connectors);}
    public PcbLayout route(int a,int b,boolean verticalFirst){
        if(a<0||b<0||layer(a)!=layer(b)||layer(a)>=layers||!contains(x(a),y(a))||!contains(x(b),y(b)))throw new IllegalArgumentException("bounds");
        var list=new ArrayList<>(tracks);int u=x(a),v=y(a),l=layer(a);
        for(int axis=0;axis<2;axis++)if((axis==0)==verticalFirst){while(v!=y(b)){int next=v+Integer.signum(y(b)-v);list.add(new Track(point(u,v,l),point(u,next,l)));v=next;}}
        else while(u!=x(b)){int next=u+Integer.signum(x(b)-u);list.add(new Track(point(u,v,l),point(next,v,l)));u=next;}
        return copy(parts,list,cutouts,vias,connectors);
    }
    public int[] nets(){int[] root=new int[CELLS*layers];Arrays.setAll(root,i->i);for(var t:tracks)root[find(root,t.b)]=find(root,t.a);for(int via:vias)for(int l=1;l<layers;l++)root[find(root,via+l*CELLS)]=find(root,via);for(int i=0;i<root.length;i++)root[i]=find(root,i);return root;}
    private static int find(int[] root,int at){while(root[at]!=at){root[at]=root[root[at]];at=root[at];}return at;}
    public boolean supplyShort(){var n=nets();return n[connectors.get(0)]==n[connectors.get(1)];}
    public int tilesX(){return (width+31)/32;}
    public int tilesY(){return (height+23)/24;}
    public int areaUnits(){return Math.max(1,(width*height+767)/768);}
    public int laminateCost(){return areaUnits()*layers;}
    public int solderCost(){return Math.max(1,(parts.stream().mapToInt(p->p.type.pins()).sum()+31)/32);}
    public int copperCost(){return Math.max(laminateCost(),(tracks.size()+127)/128);}
    public double joules(){return 40*laminateCost()+parts.size()*12+tracks.size()*.5+vias.size()*4;}
    public String encode(){var s=new StringBuilder("3|");for(var p:parts)s.append(p.type.ordinal()).append(',').append(p.x).append(',').append(p.y).append(',').append(p.turn).append(';');s.append('|');for(var t:tracks)s.append(t.a).append(',').append(t.b).append(';');s.append('|').append(width).append(',').append(height).append(',').append(layers).append('|');cutouts.stream().sorted().forEach(p->s.append(p).append(','));s.append('|');vias.stream().sorted().forEach(p->s.append(p).append(','));s.append('|');connectors.forEach(p->s.append(p).append(','));return s.toString();}
    private static int oldPoint(int at){if(at<0||at>=192)throw new IllegalArgumentException("legacy point");return point(at%16,at/16);}
    private static Set<Integer> integers(String s,boolean old){var set=new LinkedHashSet<Integer>();for(String v:s.split(","))if(!v.isEmpty()){int n=Integer.parseInt(v);set.add(old?oldPoint(n):n);}return set;}
    public static PcbLayout decode(String value){
        if(value.length()>MAX_ENCODING)throw new IllegalArgumentException("size");var sections=value.split("\\|",-1);boolean old=sections.length==5&&sections[0].equals("2");if(!old&&(sections.length!=7||!sections[0].equals("3")))throw new IllegalArgumentException("version");
        try{var parts=new ArrayList<Part>();var tracks=new ArrayList<Track>();for(String part:sections[1].split(";"))if(!part.isEmpty()){var v=part.split(",");if(v.length!=4)throw new IllegalArgumentException();parts.add(new Part(Type.values()[Integer.parseInt(v[0])],Integer.parseInt(v[1]),Integer.parseInt(v[2]),Integer.parseInt(v[3])));}
            for(String track:sections[2].split(";"))if(!track.isEmpty()){var v=track.split(",");if(v.length!=2)throw new IllegalArgumentException();int a=Integer.parseInt(v[0]),b=Integer.parseInt(v[1]);tracks.add(new Track(old?oldPoint(a):a,old?oldPoint(b):b));}
            var dims=sections[3].split(",");if(dims.length!=(old?2:3))throw new IllegalArgumentException();int w=Integer.parseInt(dims[0]),h=Integer.parseInt(dims[1]);return new PcbLayout(parts,tracks,w,h,integers(sections[4],old),old?1:Integer.parseInt(dims[2]),old?Set.of():integers(sections[5],false),old?Arrays.stream(ports(w,h)).boxed().toList():new ArrayList<>(integers(sections[6],false)));
        }catch(IndexOutOfBoundsException|NumberFormatException e){throw new IllegalArgumentException("format",e);}
    }
    public static PcbLayout inverter(){return new PcbLayout(List.of(),List.of()).place(Type.NOT,6,4,0)
        .route(PORTS[0],point(4,1),false).route(point(4,1),point(6,4),true)
        .route(PORTS[1],point(8,8),false).route(point(8,8),point(9,3),false).route(point(9,3),point(8,4),false)
        .route(PORTS[2],point(2,4),false).route(point(2,4),point(6,6),true)
        .route(point(7,5),point(7,0),true).route(point(7,0),PORTS[4],false);}
    public static PcbLayout halfAdder(){return new PcbLayout(List.of(),List.of()).place(Type.HALF_ADDER,6,4,0)
        .route(PORTS[0],point(6,4),false).route(PORTS[2],point(3,4),false).route(point(3,4),point(6,5),true)
        .route(PORTS[3],point(3,7),false).route(point(3,7),point(6,6),true).route(PORTS[1],point(7,6),false)
        .route(point(8,5),PORTS[4],false).route(point(8,6),PORTS[5],false);}
}
