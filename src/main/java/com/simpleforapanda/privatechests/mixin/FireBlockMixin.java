package com.simpleforapanda.privatechests.mixin;

import com.simpleforapanda.privatechests.service.ProtectionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to prevent fire from burning protected blocks.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    /**
     * Prevent protected blocks from catching fire.
     * Injects into checkBurnOut() which is called when fire tries to burn adjacent blocks.
     */
    @Inject(
        method = "checkBurnOut",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onCheckBurnOut(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }

        // If the block at this position is protected, cancel the burn attempt
        if (ProtectionService.isProtected(level, pos)) {
            ci.cancel();
        }
    }
}
