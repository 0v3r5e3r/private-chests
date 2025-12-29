package com.simpleforapanda.privatechests.mixin;

import com.simpleforapanda.privatechests.service.ProtectionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin to prevent explosions from destroying protected blocks.
 */
@Mixin(ServerExplosion.class)
public abstract class ExplosionMixin {

    @Shadow
    @Final
    private ServerLevel level;

    /**
     * Remove protected blocks from the explosion's affected blocks list.
     * Injects into interactWithBlocks() which receives the list of blocks to destroy.
     */
    @Inject(
        method = "interactWithBlocks",
        at = @At("HEAD")
    )
    private void onInteractWithBlocks(List<BlockPos> affectedBlocks, CallbackInfo ci) {
        // Remove protected blocks from the explosion list
        affectedBlocks.removeIf(pos -> ProtectionService.isProtected(level, pos));
    }
}
