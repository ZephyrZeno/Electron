package top.lacrus.zephyrzeno.electron.display;

import java.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** One serial framebuffer per connected rectangular group; every tile contributes 8x8 pixels. */
public final class PixelMatrix {
    public record Group(BlockPos root,int width,int height,Map<BlockPos,int[]> tiles){}
    private static final Map<World,Cache> caches=new WeakHashMap<>();
    private static final class Cache {long time;final Map<BlockPos,Group> groups=new HashMap<>();}
    public static Group group(DisplayEntity e){var world=e.getWorld();var cache=caches.computeIfAbsent(world,k->new Cache());if(cache.time!=world.getTime()){cache.time=world.getTime();cache.groups.clear();}var known=cache.groups.get(e.getPos());if(known!=null)return known;
        var s=e.getCachedState();var u=DisplayEntity.axis(s,false);var v=DisplayEntity.axis(s,true);var points=new LinkedHashMap<BlockPos,int[]>();var queue=new ArrayDeque<BlockPos>();points.put(e.getPos(),new int[]{0,0});queue.add(e.getPos());int minU=0,maxU=0,minV=0,maxV=0;
        while(!queue.isEmpty()&&points.size()<=256){var p=queue.remove();var at=points.get(p);for(int i=0;i<4;i++){int du=i==0?1:i==1?-1:0,dv=i==2?1:i==3?-1:0;var n=p.offset(u,du).offset(v,dv);if(!points.containsKey(n)&&e.joins(n)){int a=at[0]+du,b=at[1]+dv;points.put(n,new int[]{a,b});queue.add(n);minU=Math.min(minU,a);maxU=Math.max(maxU,a);minV=Math.min(minV,b);maxV=Math.max(maxV,b);}}}
        if(points.size()>256||points.size()!=(maxU-minU+1)*(maxV-minV+1)){for(var p:points.keySet())cache.groups.put(p,new Group(p,1,1,Map.of(p,new int[]{0,0})));return cache.groups.get(e.getPos());}
        for(var at:points.values()){at[0]-=minU;at[1]-=minV;}var group=new Group(e.getPos().offset(u,minU).offset(v,minV),maxU-minU+1,maxV-minV+1,Map.copyOf(points));for(var p:points.keySet())cache.groups.put(p,group);return group;
    }
    public static void update(DisplayEntity e,int levels,boolean powered){var group=group(e);if(!group.root.equals(e.getPos()))return;int count=group.tiles.size();if(e.pixelShift.length!=count){e.pixelShift=new long[count];e.pixelClock=e.pixelLatch=2;}
        boolean reset=!powered||levels>=0&&(levels&8)!=0;boolean publish=reset;
        int clock=levels<0?2:(levels>>1)&1,latch=levels<0?2:(levels>>2)&1;
        if(reset)Arrays.fill(e.pixelShift,0);else if(levels>=0){if(clock==1&&e.pixelClock==0){long carry=levels&1;for(int i=0;i<count;i++){long next=e.pixelShift[i]>>>63;e.pixelShift[i]=(e.pixelShift[i]<<1)|carry;carry=next;}}publish=latch==1&&e.pixelLatch==0;}
        e.pixelClock=clock;e.pixelLatch=latch;
        if(publish)for(var entry:group.tiles.entrySet())if(e.getWorld().getBlockEntity(entry.getKey()) instanceof DisplayEntity tile){long bits=0;var at=entry.getValue();var face=e.getCachedState().get(top.lacrus.zephyrzeno.electron.foundry.ComponentBlock.FACING);int tileX=face==net.minecraft.util.math.Direction.NORTH||face==net.minecraft.util.math.Direction.EAST?group.width-1-at[0]:at[0],tileY=face.getAxis()!=net.minecraft.util.math.Direction.Axis.Y?group.height-1-at[1]:at[1];for(int y=0;y<8;y++)for(int x=0;x<8;x++){int bit=count*64-1-((tileY*8+y)*group.width*8+tileX*8+x);if((e.pixelShift[bit/64]&(1L<<(bit%64)))!=0)bits|=1L<<(63-y*8-x);}if(tile.pixelBits!=bits){tile.pixelBits=bits;tile.sync();}}
    }
    private PixelMatrix(){}
}
