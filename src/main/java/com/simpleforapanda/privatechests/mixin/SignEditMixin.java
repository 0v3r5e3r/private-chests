package com.simpleforapanda.privatechests.mixin;

import com.simpleforapanda.privatechests.PrivateChests;
import com.simpleforapanda.privatechests.service.SignEditService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

/**
 * Mixin to intercept sign editing and handle [private] lock creation/updates.
 */
@Mixin(SignBlockEntity.class)
public abstract class SignEditMixin {

    /**
     * PLAYER-ORIGINATED sign edits
     * This replaces ServerGamePacketListenerImpl interception
     */
    @Inject(
            method = "updateSignText",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onPlayerSignEdit(
            Player player,
            boolean bl,
            List<FilteredText> list,
            CallbackInfo ci
    ) {
        // Only enforce for real server players
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        SignBlockEntity sign = (SignBlockEntity) (Object) this;
        BlockPos pos = sign.getBlockPos();

        // In this method signature:
        //  - `bl` == isFrontText
        //  - `list` == filteredLines
        boolean isFrontText = bl;
        List<FilteredText> filteredLines = list;

        boolean allowed = SignEditService.handleSignEdit(
                serverPlayer,
                pos,
                sign,
                filteredLines,
                isFrontText
        );

        if (!allowed) {
            ci.cancel();
        }
    }
}