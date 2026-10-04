package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;

/** Merge collinear top-layer copper once, instead of submitting one prism per lattice edge each frame. */
public final class PcbCopperMesh {
    public record Run(int x0,int y0,int x1,int y1){}
    public static List<Run> compile(PcbLayout layout){var bits=new byte[PcbLayout.CELLS];for(var edge:layout.tracks())if(PcbLayout.layer(edge.a())==0)bits[edge.a()]|=edge.b()==edge.a()+1?1:2;var runs=new ArrayList<Run>();
        for(int y=0;y<layout.height();y++)for(int x=0;x<layout.width();x++)if((bits[PcbLayout.point(x,y)]&1)!=0){int start=x;while(x<layout.width()&&(bits[PcbLayout.point(x,y)]&1)!=0)x++;runs.add(new Run(start,y,x,y));}
        for(int x=0;x<layout.width();x++)for(int y=0;y<layout.height();y++)if((bits[PcbLayout.point(x,y)]&2)!=0){int start=y;while(y<layout.height()&&(bits[PcbLayout.point(x,y)]&2)!=0)y++;runs.add(new Run(x,start,x,y));}
        return List.copyOf(runs);
    }
    private PcbCopperMesh(){}
}
