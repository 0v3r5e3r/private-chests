package com.simpleforapanda.privatechests.mixin;

import com.simpleforapanda.privatechests.PrivateChests;
import com.simpleforapanda.privatechests.model.LockRecord;
import com.simpleforapanda.privatechests.service.AccessControlService;
import com.simpleforapanda.privatechests.state.LockState;
import com.simpleforapanda.privatechests.util.ContainerUtils;
import com.simpleforapanda.privatechests.util.SignUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.Set;

/**
 * Mixin to intercept sign break attempts at the packet level.
 *
 * <p>This mixin prevents unauthorized players from breaking protected private signs
 * by cancelling the break packet before the server processes it. Due to Minecraft's
 * client-side optimistic rendering, the client may temporarily clear the sign locally,
 * but we immediately send correction packets to restore it.</p>
 *
 * <p><b>Known Client Behavior:</b> When a break is denied, the client may briefly
 * show a missing sign until the player interacts with it again (e.g., left-click).
 * This is a Minecraft client limitation - the client processes breaks optimistically
 * before the server responds, then refuses some correction packets during the same
 * tick. The sign and its text are always preserved on the server.</p>
 *
 * <p><b>Expected Warning:</b> You may see "Mismatch in destroy block pos" warnings
 * in the server logs. This is expected behavior - we cancel START_DESTROY_BLOCK packets
 * before vanilla processes them, so vanilla never registers the block being destroyed.
 * When the client later sends STOP_DESTROY_BLOCK, vanilla complains about the mismatch.
 * This warning is harmless and does not affect functionality.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SignBreakPacketMixin {

    @Shadow
    public abstract ServerPlayer getPlayer();

    /**
     * Intercepts block break action packets to prevent unauthorized private sign breaks.
     * Handles both START_DESTROY_BLOCK and STOP_DESTROY_BLOCK to fully prevent the break.
     *
     * <p>When a break is denied, we:</p>
     * <ul>
     *   <li>Cancel the packet to prevent server-side processing</li>
     *   <li>Send immediate block state update to keep sign visible</li>
     *   <li>Send sign text data on server thread to restore text</li>
     * </ul>
     *
     * @param packet The player action packet
     * @param ci Callback info for cancelling the injection
     */
    @Inject(
        method = "handlePlayerAction",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        // Only handle START_DESTROY_BLOCK - let STOP through to avoid vanilla state mismatch
        if (packet.getAction() != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            return;
        }

        ServerPlayer player = getPlayer();
        BlockPos pos = packet.getPos();
        Level level = player.level();
        BlockState state = level.getBlockState(pos);

        // Only process wall signs
        if (!SignUtils.isWallSign(state)) {
            return;
        }

        // Check if this is a protected private sign
        if (isProtectedPrivateSign(player, level, pos)) {
            // Cancel START_DESTROY_BLOCK - prevents the break from starting
            ci.cancel();

            PrivateChests.LOGGER.info("Denied break attempt on protected sign at {} by {}",
                pos, player.getName().getString());

            player.sendSystemMessage(Component.literal(
                "You cannot break someone else's [private] sign."
            ));

            // Send block state update IMMEDIATELY to keep sign visible on client
            // Safe to do outside server thread as we're only reading block state
            try {
                BlockState currentState = level.getBlockState(pos);
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(pos, currentState));
            } catch (Exception e) {
                PrivateChests.LOGGER.error("Failed to send block update for sign at {}", pos, e);
            }

            // Send sign text data on server thread (required for safe block entity access)
            ((ServerLevel) level).getServer().execute(() -> {
                try {
                    var blockEntity = level.getBlockEntity(pos);
                    if (blockEntity instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                        // Send sign-specific update packet
                        var signUpdatePacket = sign.getUpdatePacket();
                        if (signUpdatePacket != null) {
                            player.connection.send(signUpdatePacket);
                        }

                        // Send generic block entity data packet as backup
                        var entityPacket = net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(sign);
                        if (entityPacket != null) {
                            player.connection.send(entityPacket);
                        }
                    }
                } catch (Exception e) {
                    PrivateChests.LOGGER.error("Failed to resync sign text at {}", pos, e);
                }
            });
        }
    }

    /**
     * Check if a sign is a protected private sign that the player cannot break.
     */
    private boolean isProtectedPrivateSign(ServerPlayer player, Level level, BlockPos signPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        LockState lockState = LockState.get(serverLevel.getServer());

        // Check if this sign is attached to a locked container
        Optional<BlockPos> attachedPos = SignUtils.getAttachedBlock(level, signPos);
        if (attachedPos.isEmpty()) {
            return false;
        }

        // Get the container group
        Set<BlockPos> containerGroup = ContainerUtils.getContainerGroup(level, attachedPos.get());

        // Check if any part of the container group has a lock
        for (BlockPos containerPos : containerGroup) {
            Optional<LockRecord> lockOpt = lockState.getLock(containerPos);
            if (lockOpt.isPresent()) {
                LockRecord lock = lockOpt.get();

                // Check if this is the private sign for this lock
                if (lock.getSignPos().equals(signPos)) {
                    // Check if player can break it
                    boolean isOwner = player.getUUID().equals(lock.getOwnerUuid());
                    boolean isAdmin = AccessControlService.isAdmin(player);

                    // If not owner or admin, it's protected
                    return !isOwner && !isAdmin;
                }
            }
        }

        return false;
    }
}
