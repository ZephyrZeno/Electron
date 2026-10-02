package top.lacrus.zephyrzeno.electron.foundry;

import java.util.Locale;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import top.lacrus.zephyrzeno.electron.circuit.CircuitState;
import top.lacrus.zephyrzeno.electron.circuit.CapacitorModel;
import top.lacrus.zephyrzeno.electron.circuit.BatteryModel;

public final class ComponentEntity extends BlockEntity {
    public static final int EVENT_CRANK=1,EVENT_FAILURE=2;
    public double energy,voltage,current,overload,capacitorVoltage;
    public double inputVoltage,inputCurrent;
    public double converterEnergy,omega;
    public int loadMode;
    public int signal;
    public boolean contactClosed,pendingContact;
    public long contactDue;
    public boolean paused;
    /** 0/1 are driven levels, 2 is unknown, 3 is unpowered high impedance. */
    public int logicState=3,pendingLogic=3;
    public long logicDue;
    public long lastEffectTick=Long.MIN_VALUE/2,lastLedSoundTick=Long.MIN_VALUE/2;
    public long animationStart=Long.MIN_VALUE/2,failureStart=Long.MIN_VALUE/2;
    public double displayedLight,displayedSwitch,lastRenderTime=Double.NaN;
    public double rotorAngle;
    public double fuelEnergy,temperature=20,meterEnergy;
    public boolean plantReady,fuelLoaded;
    public String plantStatus="foundation",lastStrike="";
    private long lastCrank=Long.MIN_VALUE/2;
    public ComponentEntity(BlockPos pos,BlockState state){super(Foundry.COMPONENT_ENTITY,pos,state);}
    public static void tick(World world,BlockPos pos,BlockState state,ComponentEntity entity){CircuitState.get((ServerWorld)world).track(pos,state);}
    public boolean crank(long tick){return crank(tick,200,5);}
    public boolean crank(long tick,double capacity,double added){if(tick-lastCrank<20||energy>=capacity)return false;lastCrank=tick;energy=Math.min(capacity,energy+added);markDirty();return true;}
    @Override public boolean onSyncedBlockEvent(int type,int data){
        if(type==EVENT_CRANK||type==EVENT_FAILURE){if(world!=null&&world.isClient){if(type==EVENT_CRANK)animationStart=world.getTime();else failureStart=world.getTime();}return true;}
        return super.onSyncedBlockEvent(type,data);
    }
    public MutableText reading(){
        if(paused)return Text.translatable("reading.electron.paused");
        if(getCachedState().getBlock() instanceof ComponentBlock c&&(c.kind==ComponentBlock.Kind.TERMINAL_STRIP||c.kind==ComponentBlock.Kind.DISTRIBUTION))return Text.translatable("reading.electron.terminal_strip");
        var text=Text.translatable("reading.electron.values",String.format(Locale.ROOT,"%.3f",voltage),String.format(Locale.ROOT,"%.2f",current*1000));
        if(getCachedState().getBlock() instanceof ComponentBlock c&&c.kind==ComponentBlock.Kind.GATE)text.append(Text.translatable("reading.electron.gate",new String[]{"0","1","X","Z"}[logicState]));
        if(getCachedState().getBlock() instanceof ComponentBlock c&&c.kind==ComponentBlock.Kind.CAPACITOR)text.append(Text.translatable("reading.electron.capacitor",String.format(Locale.ROOT,"%.3f",capacitorVoltage),String.format(Locale.ROOT,"%.3f",CapacitorModel.joules(capacitorVoltage))));
        if(getCachedState().getBlock() instanceof ComponentBlock c&&c.kind==ComponentBlock.Kind.BATTERY)text.append(Text.translatable("reading.electron.battery",String.format(Locale.ROOT,"%.2f",energy),String.format(Locale.ROOT,"%.3f",inputVoltage),String.format(Locale.ROOT,"%.2f",inputCurrent*1000)));
        if(getCachedState().getBlock() instanceof ComponentBlock c&&c.kind==ComponentBlock.Kind.REDSTONE_SWITCH)text.append(Text.translatable(contactClosed?"reading.electron.contact_closed":"reading.electron.contact_open",signal));
        if(getCachedState().getBlock() instanceof ComponentBlock c&&(c.kind==ComponentBlock.Kind.BOOST||c.kind==ComponentBlock.Kind.BUCK||c.kind==ComponentBlock.Kind.RELAY||c.kind==ComponentBlock.Kind.MOSFET))text.append(Text.translatable("reading.electron.control_input",String.format(Locale.ROOT,"%.3f",inputVoltage),String.format(Locale.ROOT,"%.2f",inputCurrent*1000)));
        if(getCachedState().getBlock() instanceof ComponentBlock c&&c.kind==ComponentBlock.Kind.MOTOR)text.append(Text.translatable("reading.electron.motor",String.format(Locale.ROOT,"%.0f",top.lacrus.zephyrzeno.electron.circuit.MotorModel.rpm(omega)),Text.translatable("motor.electron.load_"+loadMode)));
        if(getCachedState().getBlock() instanceof ComponentBlock c&&(c.kind==ComponentBlock.Kind.CRANK||c.kind==ComponentBlock.Kind.GENERATOR))text.append(Text.translatable("reading.electron.energy",String.format(Locale.ROOT,"%.2f",energy)));
        if(getCachedState().getBlock() instanceof ComponentBlock c&&top.lacrus.zephyrzeno.electron.power.GridDevices.handles(c.kind))text.append(Text.translatable("reading.electron.plant",String.format(Locale.ROOT,"%.1f",energy),String.format(Locale.ROOT,"%.1f",fuelEnergy),String.format(Locale.ROOT,"%.1f",meterEnergy/3600)));
        if(getCachedState().get(ComponentBlock.BROKEN))text.append(Text.translatable("reading.electron.broken"));else if(overload>0)text.append(Text.translatable("reading.electron.overload"));
        return text;
    }
    @Override protected void writeNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup registries){super.writeNbt(nbt,registries);nbt.putDouble("energy",energy);nbt.putDouble("overload",overload);nbt.putInt("logic",logicState);nbt.putDouble("capacitor_voltage",capacitorVoltage);nbt.putDouble("converter_energy",converterEnergy);nbt.putDouble("omega",omega);nbt.putInt("load_mode",loadMode);nbt.putDouble("fuel_energy",fuelEnergy);nbt.putDouble("temperature",temperature);nbt.putDouble("meter_energy",meterEnergy);nbt.putBoolean("fuel_loaded",fuelLoaded);nbt.putString("last_strike",lastStrike);}
    @Override protected void readNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup registries){super.readNbt(nbt,registries);energy=finite(nbt.getDouble("energy"),getCachedState().getBlock() instanceof ComponentBlock g&&top.lacrus.zephyrzeno.electron.power.GridDevices.handles(g.kind)?4000:getCachedState().getBlock() instanceof ComponentBlock c&&c.kind==ComponentBlock.Kind.GENERATOR?2000:200);overload=finite(nbt.getDouble("overload"),10);logicState=Math.clamp(nbt.getInt("logic"),0,3);pendingLogic=logicState;double saved=nbt.getDouble("capacitor_voltage");capacitorVoltage=Double.isFinite(saved)?Math.clamp(saved,-64,64):0;converterEnergy=finite(nbt.getDouble("converter_energy"),getCachedState().isOf(Foundry.SUBSTATION)?24:.5);double speed=nbt.getDouble("omega");omega=Double.isFinite(speed)?Math.clamp(speed,-600,600):0;loadMode=Math.clamp(nbt.getInt("load_mode"),0,2);fuelEnergy=finite(nbt.getDouble("fuel_energy"),200000);temperature=Math.max(20,finite(nbt.getDouble("temperature"),150));meterEnergy=finite(nbt.getDouble("meter_energy"),1e15);fuelLoaded=nbt.getBoolean("fuel_loaded");lastStrike=nbt.getString("last_strike");}
    private static double finite(double x,double max){return Double.isFinite(x)?Math.clamp(x,0,max):0;}
    @Override protected void addComponents(net.minecraft.component.ComponentMap.Builder builder){
        super.addComponents(builder);
        if(getCachedState().getBlock() instanceof ComponentBlock c&&c.kind==ComponentBlock.Kind.BATTERY)builder.add(Foundry.BATTERY_ENERGY,getCachedState().get(ComponentBlock.BROKEN)?0:BatteryModel.clampEnergy(energy));
    }
    @Override protected void readComponents(ComponentsAccess components){
        super.readComponents(components);
        if(getCachedState().getBlock() instanceof ComponentBlock c&&c.kind==ComponentBlock.Kind.BATTERY)energy=BatteryModel.clampEnergy(components.getOrDefault(Foundry.BATTERY_ENERGY,0d));
    }
    @Override public void removeFromCopiedStackNbt(NbtCompound nbt){super.removeFromCopiedStackNbt(nbt);if(getCachedState().getBlock() instanceof ComponentBlock c&&c.kind==ComponentBlock.Kind.BATTERY)nbt.remove("energy");}
}
