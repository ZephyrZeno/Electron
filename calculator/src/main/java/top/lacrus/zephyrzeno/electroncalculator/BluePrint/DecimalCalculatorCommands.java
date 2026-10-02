package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.util.*;
import java.util.concurrent.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import static net.minecraft.server.command.CommandManager.*;

/** Generation only: after placement no listeners monitor calculator inputs or compute results. */
public final class DecimalCalculatorCommands {
    private record Placement(BlockPos relative,BlockState state) {}
    private enum Phase { PLAN, CHECK, PLACE, INITIALIZE, CONNECT }
    private static Build active;
    private static final List<ServerCommandSource> previews=new ArrayList<>();
    private static final class Build {
        final ServerCommandSource source;
        final ServerWorld world;
        final BlockPos origin;
        final boolean repair;
        CompactBlueprint plan;
        List<Placement> entries;
        List<Placement> initial;
        List<Placement> original;
        Map<BlockPos,BlockState> originalStates;
        Phase phase=Phase.PLAN;
        long checked;
        int index;
        Build(ServerCommandSource source,BlockPos origin,boolean repair){this.source=source;this.world=source.getWorld();this.origin=origin.toImmutable();this.repair=repair;}
        long volume(){return (long)plan.width*plan.height*plan.depth;}
    }
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(
            literal("electron").requires(s->s.hasPermissionLevel(2))
                .then(literal("calculator")
                    .then(literal("preview").executes(ctx->{CalculatorCompiler.prepare();previews.add(ctx.getSource());ctx.getSource().sendFeedback(()->Text.literal("Electron：正在准备蓝图，完成后显示尺寸。"),false);return 1;}))
                    .then(literal("build").then(argument("origin",BlockPosArgumentType.blockPos()).executes(ctx->start(ctx.getSource(),BlockPosArgumentType.getBlockPos(ctx,"origin"),false))))
                    .then(literal("repair").then(argument("origin",BlockPosArgumentType.blockPos()).executes(ctx->start(ctx.getSource(),BlockPosArgumentType.getBlockPos(ctx,"origin"),true))))
                    .then(literal("cancel").executes(ctx->{active=null;previews.clear();CalculatorCompiler.stop();ctx.getSource().sendFeedback(()->Text.literal("已停止生成；已放置的方块保留。"),false);return 1;})))
                .then(literal("status").executes(ctx->{ctx.getSource().sendFeedback(()->Text.literal(status()),false);return 1;}))
        ));
        ServerTickEvents.END_SERVER_TICK.register(server->tick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{active=null;previews.clear();CalculatorCompiler.stop();});
    }
    private static int start(ServerCommandSource source,BlockPos origin,boolean repair) {
        if(active!=null){source.sendError(Text.literal("已有生成任务，请用 /electron status 查看。"));return 0;}
        CalculatorCompiler.prepare();active=new Build(source,origin,repair);
        source.sendFeedback(()->Text.literal(repair?"Electron：准备修复此起点的计算器，将核对原结构、清理旧屏幕并清空本次输入与结果。":"Electron：准备生成原版红石按钮计算器与立式灯屏，将先检查整块区域是否为空气。"),true);return 1;
    }
    private static String description(CompactBlueprint plan) {
        return "Electron："+plan.width+" × "+plan.height+" × "+plan.depth+"，约 "+plan.blocks.size()+" 个原版方块；向 +X / +Z 延伸。按钮和立式灯屏位于前方右侧。保守事务预算 "+plan.estimatedTicks+" 游戏刻（不含服务器卡顿）。整个范围须加载并保持红石运行。";
    }
    private static String status() {
        if(active==null)return "Electron：没有生成任务；"+CalculatorCompiler.progress();
        return "Electron："+switch(active.phase){case PLAN->CalculatorCompiler.progress();case CHECK->active.repair?"核对原结构 "+active.checked+"/"+active.original.size():"检查空间 "+active.checked+"/"+active.volume();case PLACE->"放置方块 "+active.index+"/"+active.entries.size();case INITIALIZE->"初始化空白状态 "+active.index+"/"+active.initial.size();case CONNECT->"激活线路 "+active.index+"/"+active.entries.size();};
    }
    private static void tick() {
        if(active==null&&previews.isEmpty())return;
        Future<CompactBlueprint> future=CalculatorCompiler.prepare();
        if(!future.isDone())return;
        CompactBlueprint plan;
        try{plan=future.get();}catch(Exception error){
            if(active!=null)active.source.sendError(Text.literal(CalculatorCompiler.progress()));
            previews.forEach(s->s.sendError(Text.literal(CalculatorCompiler.progress())));previews.clear();active=null;return;
        }
        previews.forEach(s->s.sendFeedback(()->Text.literal(description(plan)),false));previews.clear();
        Build b=active;if(b==null)return;
        if(b.phase==Phase.PLAN) {
            b.plan=plan;
            BlockPos end=b.origin.add(plan.width-1,plan.height-1,plan.depth-1);
            if(!b.world.isInBuildLimit(b.origin)||!b.world.isInBuildLimit(end)||!b.world.getWorldBorder().contains(b.origin)||!b.world.getWorldBorder().contains(end)){abort(b,"生成范围超出世界边界或高度，未放置方块。");return;}
            if(b.repair) {
                CompactBlueprint old=CalculatorCompiler.legacy();
                if(old==null){abort(b,"缺少原结构修复参考，未修改方块。");return;}
                BlockPos run=b.origin.add(VanillaCalculator.pos(old.inputs.get("KEY_RUN")));
                if(!b.world.isChunkLoaded(run)||!b.world.getBlockState(run).isOf(Blocks.STONE_BUTTON)){abort(b,"这个起点没有对应的按钮计算器，未修改方块。请输入原生成起点。");return;}
                Map<BlockPos,BlockState> original=VanillaCalculator.blocks(old);
                for(var label:old.labels)original.put(VanillaCalculator.pos(label.pos()),Blocks.OAK_SIGN.getDefaultState().with(SignBlock.ROTATION,8));
                b.original=original.entrySet().stream().map(e->new Placement(e.getKey(),e.getValue())).toList();
                // Map.copyOf uses a flat hash table, which degenerates with this dense
                // grid's colliding BlockPos hashes. Keep the existing hash map instead.
                b.originalStates=Collections.unmodifiableMap(original);
            }
            b.source.sendFeedback(()->Text.literal(description(plan)),false);b.phase=Phase.CHECK;
        }
        if(b.phase==Phase.CHECK) {
            long end=Math.min(b.checked+(b.repair?4096:32768),b.repair?b.original.size():b.volume());
            while(b.checked<end) {
                long index=b.checked++;
                BlockPos pos=b.repair?b.origin.add(b.original.get((int)index).relative()):b.origin.add((int)(index%plan.width),(int)(index/plan.width%plan.height),(int)(index/(plan.width*(long)plan.height)));
                if(!b.world.isChunkLoaded(pos)){abort(b,"检查区域未全部加载，未放置方块。请先加载完整范围。");return;}
                BlockState actual=b.world.getBlockState(pos);
                if(b.repair) {
                    var replacement=plan.blocks.get(new CompactBlueprint.Pos(b.original.get((int)index).relative().getX(),b.original.get((int)index).relative().getY(),b.original.get((int)index).relative().getZ()));
                    boolean current=replacement!=null&&actual.getBlock()==VanillaCalculator.state(replacement).getBlock();
                    if(!actual.isAir()&&actual.getBlock()!=b.original.get((int)index).state().getBlock()&&!current){abort(b,"原结构位置存在其他方块 "+pos.toShortString()+"，修复未开始。");return;}
                }else if(!actual.isAir()){abort(b,"需要整块空中区域；发现方块 "+pos.toShortString()+"，未放置方块。");return;}
            }
            if(b.checked<(b.repair?b.original.size():b.volume()))return;
            Map<BlockPos,BlockState> blocks=VanillaCalculator.blocks(plan);
            CalculatorStartup startup=CalculatorCompiler.startup();
            if(startup!=null) {
                b.initial=new ArrayList<>();int n=0;
                for(var p:plan.blocks.keySet())b.initial.add(new Placement(VanillaCalculator.pos(p),startup.state(n++)));
            }
            for(var label:plan.labels) {
                BlockPos p=VanillaCalculator.pos(label.pos());
                blocks.putIfAbsent(p.down(),Blocks.GRAY_WOOL.getDefaultState());
                if(blocks.putIfAbsent(p,Blocks.OAK_SIGN.getDefaultState().with(SignBlock.ROTATION,8))!=null){abort(b,"蓝图标牌冲突，未放置方块。");return;}
            }
            if(b.repair)for(Placement old:b.original)blocks.putIfAbsent(old.relative(),Blocks.AIR.getDefaultState());
            if(b.originalStates==null)b.originalStates=Collections.unmodifiableMap(blocks);
            b.entries=blocks.entrySet().stream().sorted(Comparator.comparingInt(e->e.getKey().getY())).map(e->new Placement(e.getKey(),e.getValue())).toList();
            b.phase=Phase.PLACE;
            if(b.repair)clearPlacementTicks(b);
        }
        if(b.phase==Phase.INITIALIZE) {
            // Cancel calculator placement transients, preserving ticks on unrelated
            // blocks between modules. All later timing and arithmetic are vanilla.
            clearPlacementTicks(b);
            int end=Math.min(b.index+4096,b.initial.size());
            while(b.index<end) {
                Placement entry=b.initial.get(b.index++);BlockPos pos=b.origin.add(entry.relative());
                if(!b.world.isChunkLoaded(pos)||b.world.getBlockState(pos).getBlock()!=entry.state().getBlock()){abort(b,"初始化区域卸载或被修改，生成停止。");return;}
                b.world.setBlockState(pos,entry.state(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
                if(b.world.getBlockEntity(pos) instanceof net.minecraft.block.entity.ComparatorBlockEntity comparator){comparator.setOutputSignal(0);comparator.markDirty();}
            }
            clearPlacementTicks(b);
            if(b.index<b.initial.size())return;
            b.phase=Phase.CONNECT;b.index=0;
        }
        int end=Math.min(b.index+512,b.entries.size());
        while(b.index<end) {
            Placement entry=b.entries.get(b.index++);BlockPos pos=b.origin.add(entry.relative());
            if(!b.world.isChunkLoaded(pos)){abort(b,"区域卸载，生成停止；已放置的方块保留。");return;}
            if(b.phase==Phase.PLACE) {
                if(!b.repair&&!b.world.getBlockState(pos).isAir()){abort(b,"生成区域被修改，已停止以避免覆盖方块。");return;}
                if(b.repair) {
                    BlockState actual=b.world.getBlockState(pos);
                    BlockState expected=b.originalStates.get(entry.relative());
                    boolean current=!entry.state().isAir()&&actual.getBlock()==entry.state().getBlock();
                    if(!actual.isAir()&&(expected==null||actual.getBlock()!=expected.getBlock())&&!current){abort(b,"修复区域被修改，已停止以避免覆盖方块。");return;}
                }
                b.world.setBlockState(pos,entry.state(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
            } else {
                BlockState state=b.world.getBlockState(pos);
                if(state.isOf(Blocks.REDSTONE_WIRE))b.world.setBlockState(pos,Block.postProcessState(state,b.world,pos),Block.NOTIFY_ALL);
                b.world.updateNeighborsAlways(pos,state.getBlock());
            }
        }
        if(b.phase==Phase.PLACE&&b.repair)clearPlacementTicks(b);
        if(b.index<b.entries.size())return;
        if(b.phase==Phase.PLACE){b.phase=b.initial==null?Phase.CONNECT:Phase.INITIALIZE;b.index=0;return;}
        for(var label:plan.labels) {
            BlockPos p=b.origin.add(VanillaCalculator.pos(label.pos()));
            if(b.world.getBlockEntity(p) instanceof SignBlockEntity sign){
                String detail=label.detail();int split=Math.min(8,detail.length());
                sign.setText(sign.getFrontText().withMessage(0,Text.literal(label.title())).withMessage(1,Text.literal(detail.substring(0,split))).withMessage(2,Text.literal(detail.substring(split))),true);sign.markDirty();
                b.world.updateListeners(p,b.world.getBlockState(p),b.world.getBlockState(p),Block.NOTIFY_LISTENERS);
            }
        }
        b.source.sendFeedback(()->Text.literal("Electron 按钮计算器已"+(b.repair?"修复，旧屏幕和废弃支线已清理":"生成")+"并初始化为空白状态。等待输入就绪灯后可直接输入，无需先按 C。数字→加减乘除→数字→运行 =；每键等待就绪灯。告示牌为固定说明，只有对应指示灯亮才表示忙或错误。甲乙数各零至九十九；上屏输入、下屏结果，除法两位小数。"),true);active=null;
    }
    private static void abort(Build b,String reason){b.source.sendError(Text.literal(reason));active=null;}
    private static void clearPlacementTicks(Build b) {
        var box=region(b);
        List<net.minecraft.world.tick.OrderedTick<Block>> preserved=new ArrayList<>();
        for(int cx=box.getMinX()>>4;cx<=box.getMaxX()>>4;cx++)for(int cz=box.getMinZ()>>4;cz<=box.getMaxZ()>>4;cz++) {
            if(!b.world.isChunkLoaded(new BlockPos(cx<<4,b.origin.getY(),cz<<4)))continue;
            var scheduler=(net.minecraft.world.tick.ChunkTickScheduler<Block>)b.world.getChunk(cx,cz).getBlockTickScheduler();
            scheduler.getQueueAsStream()
                .filter(t->box.contains(t.pos())&&!b.originalStates.containsKey(t.pos().subtract(b.origin)))
                .forEach(preserved::add);
        }
        b.world.getBlockTickScheduler().clearNextTicks(box);
        preserved.forEach(b.world.getBlockTickScheduler()::scheduleTick);
    }
    private static net.minecraft.util.math.BlockBox region(Build b) {
        return new net.minecraft.util.math.BlockBox(b.origin.getX(),b.origin.getY(),b.origin.getZ(),b.origin.getX()+b.plan.width-1,b.origin.getY()+b.plan.height-1,b.origin.getZ()+b.plan.depth-1);
    }
}
