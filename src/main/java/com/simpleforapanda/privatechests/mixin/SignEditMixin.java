package com.simpleforapanda.privatechests.mixin;

import com.simpleforapanda.privatechests.service.SignEditService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Mixin to intercept sign editing and handle [private] lock creation/updates.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SignEditMixin {

    @Shadow
    public abstract ServerPlayer getPlayer();

    /**
     * Inject into the sign update handler to process [private] locks.
     * Captures local variables and casts BlockEntity to SignBlockEntity.
     */
    @Inject(
        method = "updateSignText",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/SignBlockEntity;updateSignText(Lnet/minecraft/world/entity/player/Player;ZLjava/util/List;)V",
            shift = At.Shift.BEFORE
        ),
        locals = LocalCapture.CAPTURE_FAILHARD,
        cancellable = true
    )
    private void onSignUpdate(ServerboundSignUpdatePacket packet, java.util.List<net.minecraft.server.network.FilteredText> filteredLines, CallbackInfo ci,
                              ServerLevel level, BlockPos pos, BlockEntity blockEntity, SignBlockEntity signEntity) {
        ServerPlayer player = this.getPlayer();

        // Pass the new text lines AND which side is being edited to SignEditService
        boolean isFrontText = packet.isFrontText();
        boolean allowed = SignEditService.handleSignEdit(player, pos, signEntity, filteredLines, isFrontText);

        // Cancel the sign update if not allowed
        if (!allowed) {
            ci.cancel();
        }
    }
}
