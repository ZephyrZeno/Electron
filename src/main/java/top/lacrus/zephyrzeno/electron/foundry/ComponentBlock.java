package top.lacrus.zephyrzeno.electron.foundry;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.BlockView;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.BlockMirror;
import top.lacrus.zephyrzeno.electron.circuit.CircuitState;
import top.lacrus.zephyrzeno.electron.logic.LogicFunction;

/** Two visible, physically separate pins on the selected mounting face. */
public class ComponentBlock extends BlockWithEntity {
    public enum Kind { PCB, CRANK, RESISTOR, LED, SWITCH, GATE, DIODE, FUSE, BUTTON, CAPACITOR, BATTERY, TERMINAL_STRIP, REDSTONE_SWITCH, MOSFET, RELAY, BOOST, BUCK, GENERATOR, DISTRIBUTION, BREAKER, LAMP, MOTOR, THERMAL,HYDRO,WIND,LIGHTNING,NUCLEAR,SUBSTATION,MAINS;
        public boolean large(){return this==GENERATOR||this==DISTRIBUTION||this==BREAKER||this==LAMP||this==MOTOR||top.lacrus.zephyrzeno.electron.power.GridDevices.handles(this);}
    }
    public static final int BUTTON_TICKS=20;
    public static final DirectionProperty FACING=Properties.FACING;
    public static final BooleanProperty ENABLED=Properties.ENABLED,BROKEN=BooleanProperty.of("broken");
    public static final IntProperty LEVEL=Properties.POWER;
    public static final IntProperty TURN=IntProperty.of("turn",0,3);
    public final Kind kind;public final double resistance;public final LogicFunction logic;
    private final MapCodec<ComponentBlock> codec;
    public ComponentBlock(Settings settings,Kind kind,double resistance) {
        this(settings,kind,resistance,null);
    }
    public ComponentBlock(Settings settings,Kind kind,double resistance,LogicFunction logic) {
        super(settings);this.kind=kind;this.resistance=resistance;this.logic=logic;codec=createCodec(s->new ComponentBlock(s,kind,resistance,logic));
        setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH).with(TURN,0).with(ENABLED,kind!=Kind.BUTTON&&kind!=Kind.REDSTONE_SWITCH).with(BROKEN,false).with(LEVEL,0));
    }
    @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return codec;}
    /** A chassis connector, facing out of the rear wall instead of raised above the enclosure. */
    public boolean rearPowerPorts(){return false;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING,TURN,ENABLED,BROKEN,LEVEL);}
    @Override public BlockState getPlacementState(ItemPlacementContext c){
        Direction face=c.getSide();int turn=face.getAxis()==Direction.Axis.Y?(c.getHorizontalPlayerFacing().getHorizontal()+(face==Direction.DOWN?2:0))&3:0;
        return getDefaultState().with(FACING,face).with(TURN,turn);
    }
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return ComponentGeometry.shape(kind,state.get(FACING),state.get(TURN));}
    @Override protected BlockState rotate(BlockState state,BlockRotation rotation){
        if(state.get(FACING).getAxis()==Direction.Axis.Y){int n=switch(rotation){case CLOCKWISE_90->1;case CLOCKWISE_180->2;case COUNTERCLOCKWISE_90->3;default->0;};return state.with(TURN,(state.get(TURN)+n)&3);}
        return state.with(FACING,rotation.rotate(state.get(FACING)));
    }
    @Override protected BlockState mirror(BlockState state,BlockMirror mirror){return rotate(state,mirror.getRotation(state.get(FACING)));}
    @Override protected BlockRenderType getRenderType(BlockState state){return BlockRenderType.MODEL;}
    public static Direction redstoneSide(BlockState state){
        var face=state.get(FACING);int turn=state.get(TURN);
        var vector=ComponentGeometry.local(face,turn,8,16,13).subtract(ComponentGeometry.local(face,turn,8,8,13));
        return Direction.getFacing(vector.x,vector.y,vector.z);
    }
    @Override protected boolean emitsRedstonePower(BlockState state){return kind==Kind.REDSTONE_SWITCH;}
    @Override protected int getWeakRedstonePower(BlockState state,BlockView world,BlockPos pos,Direction direction){
        return kind==Kind.REDSTONE_SWITCH&&!state.get(BROKEN)&&world.getBlockEntity(pos) instanceof ComponentEntity entity&&!entity.paused&&entity.contactClosed&&direction==redstoneSide(state).getOpposite()?state.get(LEVEL):0;
    }
    @Override protected int getStrongRedstonePower(BlockState state,BlockView world,BlockPos pos,Direction direction){return getWeakRedstonePower(state,world,pos,direction);}
    @Override public net.minecraft.item.ItemStack getPickStack(net.minecraft.world.WorldView world,BlockPos pos,BlockState state){
        var stack=super.getPickStack(world,pos,state);
        if((kind==Kind.BATTERY||this==top.lacrus.zephyrzeno.electron.industrial.Automation.EMERGENCY)&&world.getBlockEntity(pos) instanceof ComponentEntity entity)stack.set(Foundry.BATTERY_ENERGY,state.get(BROKEN)?0:top.lacrus.zephyrzeno.electron.circuit.BatteryModel.clampEnergy(entity.energy));
        return stack;
    }
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new ComponentEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world,BlockState state,BlockEntityType<T> type){return world.isClient?null:validateTicker(type,Foundry.COMPONENT_ENTITY,ComponentEntity::tick);}
    @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit) {
        if(world.isClient)return ActionResult.SUCCESS;
        if(world.getBlockEntity(pos) instanceof ComponentEntity entity) {
            if(top.lacrus.zephyrzeno.electron.power.GridDevices.handles(kind)){
                top.lacrus.zephyrzeno.electron.power.PowerPlant.interact((ServerWorld)world,pos,state,entity,player,player.getMainHandStack());
            } else if(state.isOf(top.lacrus.zephyrzeno.electron.industrial.Industrial.PUMP)){player.sendMessage(Text.translatable("message.electron.pump_ports"),true);
            } else if(kind==Kind.CRANK) {
                if(player.isSneaking()){world.setBlockState(pos,state.cycle(ENABLED),Block.NOTIFY_ALL);ElectricalEffects.play((ServerWorld)world,pos,ElectricalEffects.SWITCH,.4f,state.get(ENABLED)?.85f:1.1f);player.sendMessage(Text.translatable(state.get(ENABLED)?"message.electron.source_off":"message.electron.source_on"),true);}
                else {boolean charged=entity.crank(world.getTime());if(charged){world.addSyncedBlockEvent(pos,this,ComponentEntity.EVENT_CRANK,0);ElectricalEffects.play((ServerWorld)world,pos,ElectricalEffects.CRANK,.6f,1);}
                    player.sendMessage(player.isCreative()?Text.translatable("message.electron.crank",String.format(java.util.Locale.ROOT,"%.1f",entity.energy),Text.translatable(charged?"message.electron.crank_added":"message.electron.crank_wait")):Text.translatable(charged?"message.electron.cranked":"message.electron.crank_ready_later"),true);}
            } else if(kind==Kind.BATTERY&&player.isSneaking()&&!state.get(BROKEN)){
                world.setBlockState(pos,state.cycle(ENABLED),Block.NOTIFY_ALL);ElectricalEffects.play((ServerWorld)world,pos,ElectricalEffects.SWITCH,.35f,1);
                player.sendMessage(Text.translatable(state.get(ENABLED)?"message.electron.battery_off":"message.electron.battery_on"),true);
            } else if(kind==Kind.SWITCH){world.setBlockState(pos,state.cycle(ENABLED),Block.NOTIFY_ALL);ElectricalEffects.play((ServerWorld)world,pos,ElectricalEffects.SWITCH,.35f,state.get(ENABLED)?.8f:1.1f);}
            else if(kind==Kind.BUTTON&&!state.get(BROKEN)){
                if(!state.get(ENABLED)){
                    world.setBlockState(pos,state.with(ENABLED,true),Block.NOTIFY_ALL);
                    world.scheduleBlockTick(pos,this,BUTTON_TICKS);
                    ElectricalEffects.play((ServerWorld)world,pos,ElectricalEffects.SWITCH,.35f,1.2f);
                }
                player.sendMessage(Text.translatable("message.electron.button_pulse"),true);
            }
            else if(kind==Kind.GENERATOR){
                if(player.isSneaking()){world.setBlockState(pos,state.cycle(ENABLED),Block.NOTIFY_ALL);player.sendMessage(Text.translatable(state.get(ENABLED)?"message.electron.source_off":"message.electron.source_on"),true);}
                else if(!state.get(BROKEN)){boolean charged=entity.crank(world.getTime(),2000,50);if(charged){world.addSyncedBlockEvent(pos,this,ComponentEntity.EVENT_CRANK,0);ElectricalEffects.play((ServerWorld)world,pos,ElectricalEffects.CRANK,.7f,.75f);}player.sendMessage(Text.translatable(charged?"message.electron.cranked":"message.electron.crank_ready_later"),true);}
            }else if(kind==Kind.BREAKER&&!state.get(BROKEN)){entity.overload=0;world.setBlockState(pos,state.cycle(ENABLED),Block.NOTIFY_ALL);ElectricalEffects.play((ServerWorld)world,pos,ElectricalEffects.SWITCH,.6f,.7f);player.sendMessage(Text.translatable(state.get(ENABLED)?"message.electron.breaker_open":"message.electron.breaker_reset"),true);}
            else if(kind==Kind.MOTOR&&!state.get(BROKEN)){entity.loadMode=(entity.loadMode+1)%3;entity.markDirty();player.sendMessage(Text.translatable("message.electron.motor_load",Text.translatable("motor.electron.load_"+entity.loadMode)),true);}
            else if(kind==Kind.BOOST||kind==Kind.BUCK){
                if(player.isSneaking()){world.setBlockState(pos,state.cycle(ENABLED),Block.NOTIFY_ALL);player.sendMessage(Text.translatable(state.get(ENABLED)?"message.electron.source_off":"message.electron.source_on"),true);}
                else player.sendMessage(player.isCreative()?entity.reading():Text.translatable("message.electron.fixed_converter"),true);
            }
            else player.sendMessage(player.isCreative()?entity.reading():Text.translatable("message.electron.use_meter"),true);
        }
        return ActionResult.CONSUME;
    }
    @Override protected void scheduledTick(BlockState state,ServerWorld world,BlockPos pos,net.minecraft.util.math.random.Random random){
        if(kind==Kind.BUTTON&&state.get(ENABLED)){
            world.setBlockState(pos,state.with(ENABLED,false),Block.NOTIFY_ALL);
            ElectricalEffects.play(world,pos,ElectricalEffects.SWITCH,.3f,.85f);
        }
    }
    @Override protected void onBlockAdded(BlockState state,World world,BlockPos pos,BlockState old,boolean notify){super.onBlockAdded(state,world,pos,old,notify);if(world instanceof ServerWorld server)CircuitState.get(server).track(pos,state);}
    @Override protected void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){
        if(!next.isOf(this)&&!world.isClient&&world.getBlockEntity(pos) instanceof ComponentEntity core){
            for(var stack:core.plantItems)net.minecraft.util.ItemScatterer.spawn(world,pos.getX(),pos.getY(),pos.getZ(),stack);
            core.plantItems.clear();
        }
        if(!next.isOf(this)&&top.lacrus.zephyrzeno.electron.multiblock.Assembly.formed(state)&&world instanceof ServerWorld server)top.lacrus.zephyrzeno.electron.multiblock.Assembly.dismantle(server,pos,pos);
        super.onStateReplaced(state,world,pos,next,moved);
        if(kind==Kind.REDSTONE_SWITCH&&(!next.isOf(this)||redstoneSide(next)!=redstoneSide(state)))world.updateNeighborsAlways(pos.offset(redstoneSide(state)),this);
    }
}
