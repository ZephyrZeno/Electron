package top.lacrus.zephyrzeno.electron.client;

import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import top.lacrus.zephyrzeno.electron.foundry.ComponentEntity;
import top.lacrus.zephyrzeno.electron.power.MachineSoundProfile;

/** One continuous mono loop per audible device; no periodic server broadcasts or cross-world remnants. */
final class MachineSounds {
    private static final Map<ComponentEntity,Loop> LOOPS=new IdentityHashMap<>();
    static void register(){
        ComponentEntity.CLIENT_TICK=MachineSounds::visit;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.world==null){clear();return;}
            LOOPS.entrySet().removeIf(entry->{var e=entry.getKey();var sound=entry.getValue();
                if(e.isRemoved()||e.getWorld()!=client.world||!client.world.isChunkLoaded(e.getPos())){client.getSoundManager().stop(sound);return true;}
                return sound.isDone();
            });
        });
    }
    static void clear(){var manager=MinecraftClient.getInstance().getSoundManager();LOOPS.values().forEach(manager::stop);LOOPS.clear();}
    private static void visit(ComponentEntity e){
        var client=MinecraftClient.getInstance();if(client.world!=e.getWorld()||client.player==null||!MachineSoundProfile.active(e.getCachedState()))return;
        // Cap work/audio voices, but never scan all loaded chunks every frame.
        if(Math.floorMod(e.getWorld().getTime()+e.getPos().asLong(),10)!=0)return;
        var profile=MachineSoundProfile.of(e.getCachedState());if(profile==null)return;
        var at=Vec3d.of(e.getPos()).add(profile.offset());if(client.player.squaredDistanceTo(at)>32*32)return;
        var old=LOOPS.get(e);
        if(old!=null&&!old.isDone()&&(client.getSoundManager().isPlaying(old)||client.world.getTime()-old.started<30))return;
        if(old!=null)client.getSoundManager().stop(old);
        if(old==null&&LOOPS.size()>=48)return;
        var loop=new Loop(e,profile,at);LOOPS.put(e,loop);client.getSoundManager().play(loop);
    }
    private static final class Loop extends MovingSoundInstance {
        private final ComponentEntity machine;private final MachineSoundProfile profile;private final long started;
        Loop(ComponentEntity e,MachineSoundProfile profile,Vec3d at){
            super(profile.sound(),SoundCategory.BLOCKS,net.minecraft.util.math.random.Random.create());
            machine=e;this.profile=profile;started=e.getWorld().getTime();
            x=at.x;y=at.y;z=at.z;repeat=true;repeatDelay=0;relative=false;
            attenuationType=SoundInstance.AttenuationType.LINEAR;volume=.01f;pitch=1;
        }
        @Override public void tick(){
            var c=MinecraftClient.getInstance();
            boolean valid=c.world==machine.getWorld()&&c.player!=null&&!machine.isRemoved()&&c.world.isChunkLoaded(machine.getPos())&&c.world.getBlockEntity(machine.getPos())==machine;
            boolean running=valid&&MachineSoundProfile.active(machine.getCachedState())&&c.player.squaredDistanceTo(x,y,z)<36*36;
            volume+=(running?profile.gain()-volume:-volume)*.2f;
            if(!running&&volume<.006f)setDone();
        }
    }
    private MachineSounds(){}
}
