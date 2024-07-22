package com.therighthon.rnr.common.block;

import com.therighthon.rnr.RoadsAndRoofs;
import com.therighthon.rnr.common.RNRTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.LazyOptional;

import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.dries007.tfc.common.blockentities.TickCounterBlockEntity;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.devices.DryingBricksBlock;

public class WetConcretePathBlock extends PathHeightDeviceBlock
{
    private static final float defaultSpeedFactor = 0.7f;

    public static final IntegerProperty CONCRETE_LEVEL = RNRBlockStateProperties.CONCRETE_LEVEL;
    private final Block base;
    private final BlockState baseState;

    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BaseCourseHeightBlock.SHAPE;
    }

    public static float getDefaultSpeedFactor()
    {
        return defaultSpeedFactor;
    }

    public WetConcretePathBlock(ExtendedProperties properties)
    {
        super(properties.speedFactor(defaultSpeedFactor).randomTicks(), InventoryRemoveBehavior.NOOP);
        //TODO: maybe remove?
        this.registerDefaultState(this.defaultBlockState().setValue(CONCRETE_LEVEL, 4));
        this.base = Blocks.AIR; // These are unused, fields are redirected
        this.baseState = Blocks.AIR.defaultBlockState();
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(CONCRETE_LEVEL);
    }

    //Based on minecraft pressure plates
    //TODO: doesn't need to run for already trodden blocks
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide) {
            if (entity instanceof LivingEntity)
            {
                level.setBlock(pos, RNRBlocks.TRODDEN_WET_CONCRETE_ROAD.get().withPropertiesOf(state), 3);
            }
        }
    }

    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        level.scheduleTick(pos, this, 10);
        TickCounterBlockEntity.reset(level, pos);
    }

    //TODO: Pretty janky setup, but it does work for now
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        //Spreading
        int concreteLevel = state.getValue(CONCRETE_LEVEL);
        if (concreteLevel > 0)
        {
            spreadFluid(level, pos.north(), concreteLevel);
            spreadFluid(level, pos.west(), concreteLevel);
            spreadFluid(level, pos.east(), concreteLevel);
            spreadFluid(level, pos.south(), concreteLevel);
            level.setBlock(pos, RNRBlocks.WET_CONCRETE_ROAD.get().defaultBlockState().setValue(CONCRETE_LEVEL, 0), 3);
        }

        //Drying
        //TODO: Cracking
        level.getBlockEntity(pos, TFCBlockEntities.TICK_COUNTER.get()).ifPresent(counter -> {
            final int ticks = 24000; //24 hours
            if (counter.getTicksSinceUpdate() > ticks)
            {
                level.setBlockAndUpdate(pos, getOutputState(state));
            }

            final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (Direction d : Direction.Plane.HORIZONTAL)
            {
                cursor.setWithOffset(pos, d);
                final BlockState stateAt = level.getBlockState(cursor);
                if (stateAt.getBlock() instanceof WetConcretePathBlock)
                {
                    level.scheduleTick(cursor, stateAt.getBlock(), 1);
                }
            }
        });
    }

    //TODO: Make this use actual recipes rather than hard-code?
    private BlockState getOutputState(BlockState input)
    {
        if (input.is(RNRBlocks.WET_CONCRETE_ROAD.get()))
        {
            return RNRBlocks.CONCRETE_ROAD.get().defaultBlockState();
        }
        else if (input.is(RNRBlocks.TRODDEN_WET_CONCRETE_ROAD.get()))
        {
            return RNRBlocks.TRODDEN_CONCRETE_ROAD.get().defaultBlockState();
        }
        else
        {
            return Blocks.AIR.defaultBlockState();
        }
    }

    private void spreadFluid(Level level, BlockPos spreadPos, int sourceLevel)
    {
        if(level.getBlockState(spreadPos).is(RNRTags.Blocks.CONCRETE_SPREADABLE))
        {
            level.setBlock(spreadPos, RNRBlocks.WET_CONCRETE_ROAD.get().defaultBlockState().setValue(CONCRETE_LEVEL, sourceLevel - 1), 3);
        }
    }

    //Needed for drying
    @Override
    public boolean isRandomlyTicking(BlockState state)
    {
        return true;
    }
}
