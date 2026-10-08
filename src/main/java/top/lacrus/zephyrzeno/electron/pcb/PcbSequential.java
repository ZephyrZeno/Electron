package top.lacrus.zephyrzeno.electron.pcb;

import java.util.Arrays;

/** Volatile latch memory. X is retained until a real reset or known data resolves it. */
final class PcbSequential {
    static final int X=2, SNAPSHOT_SIZE=8;
    private final PcbLayout.Type type;
    private final int[] bits={X,X,X,X};
    private final int[] next=new int[4];
    private int previousClock=X;
    private boolean powered;

    PcbSequential(PcbLayout.Type type){if(!type.sequential())throw new IllegalArgumentException("sequential type");this.type=type;}
    int output(int index){return bits[index];}
    void reset(){Arrays.fill(bits,X);previousClock=X;powered=false;}
    private static int level(int value){return value==0||value==1?value:X;}
    private static int merge(int a,int b){return a==b?a:X;}
    private static int xor(int a,int b){return a<X&&b<X?a^b:X;}
    private static int and(int a,int b){return a==0||b==0?0:a==1&&b==1?1:X;}

    void update(boolean supply,int a,int b,int reset){
        if(!supply){reset();return;}
        a=level(a);b=level(b);reset=level(reset);
        if(type==PcbLayout.Type.SR_LATCH){
            if(a==1&&b==0)bits[0]=1;
            else if(a==0&&b==1)bits[0]=0;
            else if(a!=0||b!=0)bits[0]=X;
            powered=true;return;
        }
        int clock=type==PcbLayout.Type.COUNTER4?a:b;
        // On a newly powered device, an already-high clock is not a fabricated edge.
        boolean edge=powered&&previousClock==0&&clock==1;
        boolean uncertain=powered&&(previousClock==0&&clock==X||previousClock==X&&clock!=0);
        System.arraycopy(bits,0,next,0,4);
        if(edge||uncertain)switch(type){
            case DFF->next[0]=a;
            case TFF->next[0]=a==0?bits[0]:a==1?xor(bits[0],1):X;
            case COUNTER4->{int carry=1;for(int i=0;i<4;i++){next[i]=xor(bits[i],carry);carry=and(bits[i],carry);}}
            case SHIFT4->{next[0]=a;System.arraycopy(bits,0,next,1,3);}
            default->throw new IllegalStateException();
        }
        if(uncertain)for(int i=0;i<type.outputs();i++)next[i]=merge(bits[i],next[i]);
        int clear=type==PcbLayout.Type.COUNTER4?b:type==PcbLayout.Type.TFF?reset:0;
        for(int i=0;i<type.outputs();i++)bits[i]=clear==1?0:clear==X?merge(0,next[i]):next[i];
        previousClock=clock;powered=true;
    }
    int[] snapshot(){return new int[]{1,type.ordinal(),powered?1:0,previousClock,bits[0],bits[1],bits[2],bits[3]};}
    boolean restore(int[] state){
        if(state.length!=SNAPSHOT_SIZE||state[0]!=1||state[1]!=type.ordinal()||state[2]<0||state[2]>1)return false;
        for(int i=3;i<state.length;i++)if(state[i]<0||state[i]>X)return false;
        if(state[2]==0)for(int i=3;i<state.length;i++)if(state[i]!=X)return false;
        powered=state[2]==1;previousClock=state[3];System.arraycopy(state,4,bits,0,4);return true;
    }
}
