package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.WireConnection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class VanillaCalculator {
    private VanillaCalculator() {}
    private static final Direction[] DIRECTIONS={Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.NORTH};
    public static BlockPos pos(CompactBlueprint.Pos pos){return new BlockPos(pos.x(),pos.y(),pos.z());}
    public static BlockState state(CompactBlueprint.Tile tile) {
        return switch(tile.part()) {
            case WOOL -> (switch(tile.module()) {
                case INPUT->Blocks.LIME_WOOL;case CONTROL->Blocks.GRAY_WOOL;case CONVERT->Blocks.CYAN_WOOL;
                case ADD->Blocks.ORANGE_WOOL;case SUBTRACT->Blocks.RED_WOOL;case MULTIPLY->Blocks.YELLOW_WOOL;
                case DIVIDE->Blocks.BLUE_WOOL;case MEMORY->Blocks.PURPLE_WOOL;case DISPLAY->Blocks.WHITE_WOOL;
            }).getDefaultState();
            // Player placement starts from a cross. The raw default is an isolated dot,
            // which intentionally does not power horizontal wool when it has no neighbours.
            case WIRE -> Blocks.REDSTONE_WIRE.getDefaultState()
                .with(RedstoneWireBlock.WIRE_CONNECTION_NORTH,WireConnection.SIDE).with(RedstoneWireBlock.WIRE_CONNECTION_SOUTH,WireConnection.SIDE)
                .with(RedstoneWireBlock.WIRE_CONNECTION_EAST,WireConnection.SIDE).with(RedstoneWireBlock.WIRE_CONNECTION_WEST,WireConnection.SIDE);
            case REPEATER -> Blocks.REPEATER.getDefaultState().with(RepeaterBlock.FACING,DIRECTIONS[tile.direction()&3].getOpposite()).with(RepeaterBlock.DELAY,(tile.direction()>>2)+1);
            case TORCH -> Blocks.REDSTONE_WALL_TORCH.getDefaultState().with(WallRedstoneTorchBlock.FACING,DIRECTIONS[tile.direction()]);
            case STANDING_TORCH -> Blocks.REDSTONE_TORCH.getDefaultState();
            case LEVER -> Blocks.LEVER.getDefaultState().with(LeverBlock.FACE,BlockFace.FLOOR).with(LeverBlock.FACING,Direction.NORTH);
            case LAMP -> Blocks.REDSTONE_LAMP.getDefaultState();
            case BUTTON -> Blocks.STONE_BUTTON.getDefaultState().with(ButtonBlock.FACE,BlockFace.FLOOR).with(ButtonBlock.FACING,Direction.NORTH);
            case COPPER_BULB -> Blocks.WAXED_COPPER_BULB.getDefaultState();
            case COMPARATOR -> Blocks.COMPARATOR.getDefaultState().with(ComparatorBlock.FACING,DIRECTIONS[tile.direction()&3].getOpposite());
            case STONE -> Blocks.SMOOTH_STONE.getDefaultState();
            case DARK -> Blocks.BLACK_CONCRETE.getDefaultState();
            case WHITE -> Blocks.WHITE_CONCRETE.getDefaultState();
            case GLASS -> Blocks.BLACK_STAINED_GLASS.getDefaultState();
        };
    }
    public static Map<BlockPos,BlockState> blocks(CompactBlueprint plan) {
        Map<BlockPos,BlockState> result=new LinkedHashMap<>();
        plan.blocks.forEach((p,t)->result.put(pos(p),state(t)));return result;
    }
}
