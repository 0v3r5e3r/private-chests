package com.simpleforapanda.privatechests.mixin;

import com.simpleforapanda.privatechests.service.AutomationBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to block hoppers from accessing locked containers.
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockMixin {

    /**
     * Block hopper from extracting items from locked containers.
     */
    @Inject(
        method = "suckInItems",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void onSuckInItems(Level level, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide()) {
            return;
        }

        // Get the container above the hopper
        BlockPos pos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1, hopper.getLevelZ());

        if (AutomationBlockService.isAutomationBlocked(level, pos)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Block hopper from inserting items into locked containers.
     */
    @Inject(
        method = "ejectItems",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void onEjectItems(Level level, BlockPos pos, HopperBlockEntity hopper, CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide()) {
            return;
        }

        // Check the position the hopper is trying to insert into
        Direction facing = hopper.getBlockState().getValue(net.minecraft.world.level.block.HopperBlock.FACING);
        BlockPos targetPos = pos.relative(facing);

        if (AutomationBlockService.isAutomationBlocked(level, targetPos)) {
            cir.setReturnValue(false);
        }
    }
}
