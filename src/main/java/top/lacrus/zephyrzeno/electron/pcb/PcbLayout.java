package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import top.lacrus.zephyrzeno.electron.logic.LogicFunction;

/** A single copper layer. Intersecting tracks join; packages and pads rotate together. */
public record PcbLayout(List<Part> parts,List<Track> tracks,int width,int height,Set<Integer> cutouts) {
    public static final int WIDTH=16,HEIGHT=12,MAX_PARTS=16;
    public static final int[] PORTS={point(0,1),point(0,10),point(0,4),point(0,7),point(15,4),point(15,7)};
    public enum Type {
        R1K(null),R10K(null),LED(null),NOT(LogicFunction.NOT),AND(LogicFunction.AND),OR(LogicFunction.OR),XOR(LogicFunction.XOR),NAND(LogicFunction.NAND);
        public final LogicFunction logic;
        Type(LogicFunction logic){this.logic=logic;}
        public int pins(){return logic==null?2:5;}
    }
    public record Part(Type type,int x,int y,int turn){
        public int pin(int pin){
            int u=type.logic==null?(pin==0?0:2):new int[]{0,2,0,2,1}[pin];
            int v=type.logic==null?1:new int[]{0,0,2,2,1}[pin];
            for(int i=0;i<turn;i++){int old=u;u=2-v;v=old;}
            return point(x+u,y+v);
        }
        public boolean contains(int u,int v){return u>=x&&u<=x+2&&v>=y&&v<=y+2;}
    }
    public record Track(int a,int b){public Track{if(a>b){int swap=a;a=b;b=swap;}if(a<0||b>=WIDTH*HEIGHT||!adjacent(a,b))throw new IllegalArgumentException("track");}}
    public PcbLayout(List<Part> parts,List<Track> tracks){this(parts,tracks,WIDTH,HEIGHT,Set.of());}
    public PcbLayout {
        parts=List.copyOf(parts);tracks=List.copyOf(new LinkedHashSet<>(tracks));
        cutouts=Set.copyOf(cutouts);
        if(width<10||width>WIDTH||height<8||height>HEIGHT)throw new IllegalArgumentException("size");
        for(int at:cutouts)if(at<0||x(at)>=width||y(at)>=height)throw new IllegalArgumentException("outline");
        for(int at:ports(width,height))if(cutouts.contains(at))throw new IllegalArgumentException("connector");
        if(parts.size()>MAX_PARTS||tracks.size()>356)throw new IllegalArgumentException("capacity");
        for(int i=0;i<parts.size();i++){
            var p=parts.get(i);if(p.type==null||p.x<1||p.x>width-4||p.y<0||p.y>height-3||p.turn<0||p.turn>3)throw new IllegalArgumentException("footprint");
            for(int u=p.x;u<=p.x+2;u++)for(int v=p.y;v<=p.y+2;v++)if(cutouts.contains(point(u,v)))throw new IllegalArgumentException("cutout");
            for(int j=0;j<i;j++){var q=parts.get(j);if(p.x<=q.x+2&&p.x+2>=q.x&&p.y<=q.y+2&&p.y+2>=q.y)throw new IllegalArgumentException("overlap");}
        }
        for(var t:tracks)for(int at:new int[]{t.a,t.b})if(x(at)>=width||y(at)>=height||cutouts.contains(at))throw new IllegalArgumentException("copper_outside");
        // A milled board must remain one piece; disjoint islands are not manufactured for free.
        var remaining=new HashSet<Integer>();for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(!cutouts.contains(point(x,y)))remaining.add(point(x,y));
        var queue=new ArrayDeque<Integer>();int start=remaining.iterator().next();remaining.remove(start);queue.add(start);
        while(!queue.isEmpty()){int at=queue.remove();for(int step:new int[]{-1,1,-WIDTH,WIDTH}){int next=at+step;if(next>=0&&next<WIDTH*HEIGHT&&adjacent(at,next)&&remaining.remove(next))queue.add(next);}}
        if(!remaining.isEmpty())throw new IllegalArgumentException("disconnected_outline");
    }
    public static int[] ports(int w,int h){return new int[]{point(0,1),point(0,h-2),point(0,h/3),point(0,2*h/3-1),point(w-1,h/3),point(w-1,2*h/3-1)};}
    public int[] ports(){return ports(width,height);}
    public boolean contains(int x,int y){return x>=0&&y>=0&&x<width&&y<height&&!cutouts.contains(point(x,y));}
    public PcbLayout resize(int w,int h){return new PcbLayout(parts,tracks,w,h,cutouts);}
    public PcbLayout cut(int x,int y){if(x<0||y<0||x>=width||y>=height)throw new IllegalArgumentException();var c=new HashSet<>(cutouts);if(!c.remove(point(x,y)))c.add(point(x,y));return new PcbLayout(parts,tracks,width,height,c);}
    public PcbLayout move(int index,int x,int y,int turn){var list=new ArrayList<>(parts);var old=list.get(index);list.set(index,new Part(old.type,x,y,turn));return new PcbLayout(list,tracks,width,height,cutouts);}
    public static PcbLayout empty(){return new PcbLayout(List.of(),List.of());}
    public static int point(int x,int y){return y*WIDTH+x;}
    public static int x(int point){return point%WIDTH;}
    public static int y(int point){return point/WIDTH;}
    private static boolean adjacent(int a,int b){return Math.abs(x(a)-x(b))+Math.abs(y(a)-y(b))==1;}
    public PcbLayout place(Type type,int x,int y,int turn){var list=new ArrayList<>(parts);list.add(new Part(type,x,y,turn));return new PcbLayout(list,tracks,width,height,cutouts);}
    public PcbLayout erase(int x,int y){int at=point(x,y);return new PcbLayout(parts.stream().filter(p->!p.contains(x,y)).toList(),tracks.stream().filter(t->t.a!=at&&t.b!=at).toList(),width,height,cutouts);}
    public PcbLayout route(int a,int b,boolean verticalFirst){
        if(a<0||a>=WIDTH*HEIGHT||b<0||b>=WIDTH*HEIGHT)throw new IllegalArgumentException("bounds");
        var list=new ArrayList<>(tracks);int u=x(a),v=y(a);
        for(int axis=0;axis<2;axis++)if((axis==0)==verticalFirst){while(v!=y(b)){int next=v+Integer.signum(y(b)-v);list.add(new Track(point(u,v),point(u,next)));v=next;}}
        else while(u!=x(b)){int next=u+Integer.signum(x(b)-u);list.add(new Track(point(u,v),point(next,v)));u=next;}
        return new PcbLayout(parts,list,width,height,cutouts);
    }
    public int[] nets(){int[] root=new int[WIDTH*HEIGHT];Arrays.setAll(root,i->i);for(var t:tracks)root[find(root,t.b)]=find(root,t.a);for(int i=0;i<root.length;i++)root[i]=find(root,i);return root;}
    private static int find(int[] root,int at){while(root[at]!=at){root[at]=root[root[at]];at=root[at];}return at;}
    public boolean supplyShort(){var n=nets();return n[ports()[0]]==n[ports()[1]];}
    public int solderCost(){return Math.max(1,(parts.stream().mapToInt(p->p.type.pins()).sum()+7)/8);}
    public int copperCost(){return Math.max(1,(tracks.size()+23)/24);}
    public double joules(){return 40+parts.size()*12+tracks.size()*.5;}
    public String encode(){var s=new StringBuilder("2|");for(var p:parts)s.append(p.type.ordinal()).append(',').append(p.x).append(',').append(p.y).append(',').append(p.turn).append(';');s.append('|');for(var t:tracks)s.append(t.a).append(',').append(t.b).append(';');s.append('|').append(width).append(',').append(height).append('|');cutouts.stream().sorted().forEach(p->s.append(p).append(','));return s.toString();}
    public static PcbLayout decode(String value){
        if(value.length()>8000)throw new IllegalArgumentException("size");var sections=value.split("\\|",-1);if(sections.length!=5||!sections[0].equals("2"))throw new IllegalArgumentException("version");
        var parts=new ArrayList<Part>();var tracks=new ArrayList<Track>();
        try{for(String part:sections[1].split(";"))if(!part.isEmpty()){var v=part.split(",");if(v.length!=4)throw new IllegalArgumentException();parts.add(new Part(Type.values()[Integer.parseInt(v[0])],Integer.parseInt(v[1]),Integer.parseInt(v[2]),Integer.parseInt(v[3])));}
            for(String track:sections[2].split(";"))if(!track.isEmpty()){var v=track.split(",");if(v.length!=2)throw new IllegalArgumentException();tracks.add(new Track(Integer.parseInt(v[0]),Integer.parseInt(v[1])));}
        }catch(IndexOutOfBoundsException|NumberFormatException e){throw new IllegalArgumentException("format",e);}
        try{var dims=sections[3].split(",");if(dims.length!=2)throw new IllegalArgumentException();var cut=new HashSet<Integer>();for(String v:sections[4].split(","))if(!v.isEmpty())cut.add(Integer.parseInt(v));return new PcbLayout(parts,tracks,Integer.parseInt(dims[0]),Integer.parseInt(dims[1]),cut);}catch(NumberFormatException e){throw new IllegalArgumentException("outline",e);}
    }
    /** Editable teaching design: A -> NOT -> Y, with explicit supply rails. */
    public static PcbLayout inverter(){return empty().place(Type.NOT,6,4,0)
        .route(PORTS[0],point(4,1),false).route(point(4,1),point(6,4),true)
        .route(PORTS[1],point(8,8),false).route(point(8,8),point(9,3),false).route(point(9,3),point(8,4),false)
        .route(PORTS[2],point(2,4),false).route(point(2,4),point(6,6),true)
        .route(point(7,5),point(7,0),true).route(point(7,0),PORTS[4],false);}
}
