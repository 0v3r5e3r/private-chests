package com.simpleforapanda.privatechests.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.simpleforapanda.privatechests.PrivateChests;
import com.simpleforapanda.privatechests.config.ModConfig;
import com.simpleforapanda.privatechests.model.LockRecord;
import com.simpleforapanda.privatechests.state.LockState;
import com.simpleforapanda.privatechests.util.ContainerUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Admin commands for managing private chests.
 */
public class PrivateChestsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Register main command: /private_chests
        dispatcher.register(
            Commands.literal("private_chests")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .then(Commands.literal("unlock")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(PrivateChestsCommand::executeUnlock)
                    )
                )
                .then(Commands.literal("list")
                    .executes(PrivateChestsCommand::executeList)
                )
                .then(Commands.literal("list_in_area")
                    .executes(ctx -> executeListInArea(ctx, 1)) // Default 2x2 chunks (radius 1)
                    .then(Commands.argument("radius", IntegerArgumentType.integer(0, 10))
                        .executes(ctx -> executeListInArea(ctx, IntegerArgumentType.getInteger(ctx, "radius")))
                    )
                )
                .then(Commands.literal("info")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(PrivateChestsCommand::executeInfo)
                    )
                )
        );

        // Register shorter alias: /pchests
        dispatcher.register(
            Commands.literal("pchests")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                .redirect(dispatcher.getRoot().getChild("private_chests"))
        );
    }

    /**
     * Execute /private_chests unlock <pos>
     */
    private static int executeUnlock(CommandContext<CommandSourceStack> ctx) {
        try {
            BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
            CommandSourceStack source = ctx.getSource();
            MinecraftServer server = source.getServer();
            ServerLevel level = source.getLevel();
            LockState lockState = LockState.get(server);

            // Get container group at position
            Set<BlockPos> containerGroup = ContainerUtils.getContainerGroup(level, pos);

            if (containerGroup.isEmpty()) {
                source.sendFailure(Component.literal("No lockable container found at " + ContainerUtils.positionToString(pos)));
                return 0;
            }

            // Check if locked
            Optional<LockRecord> lockOpt = lockState.getLock(pos);
            if (lockOpt.isEmpty()) {
                source.sendFailure(Component.literal("No lock found at " + ContainerUtils.positionToString(pos)));
                return 0;
            }

            // Remove lock
            lockState.removeLock(pos);

            String containerType = ContainerUtils.getContainerTypeName(level, containerGroup);
            source.sendSuccess(() -> Component.literal(
                "Unlocked " + containerType + " at " + ContainerUtils.positionToString(pos)
            ), true);

            PrivateChests.LOGGER.info("Admin {} unlocked container at {}", source.getTextName(), pos);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Execute /private_chests list
     */
    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        LockState lockState = LockState.get(server);

        Collection<LockRecord> locks = lockState.getAllLocks();
        int totalCount = locks.size();

        if (totalCount == 0) {
            source.sendSuccess(() -> Component.literal("No private chests found."), false);
            return 0;
        }

        ModConfig config = PrivateChests.getConfig();
        int maxEntries = config.getListMaxEntries();
        int previewEntries = config.getListPreviewEntries();

        source.sendSuccess(() -> Component.literal("===== Private Chests (" + totalCount + " total) ====="), false);

        if (totalCount > maxEntries) {
            // Show abbreviated list
            List<LockRecord> lockList = locks.stream().limit(previewEntries).toList();

            for (LockRecord lock : lockList) {
                sendLockInfo(source, lock, server);
            }

            source.sendSuccess(() -> Component.literal(
                "... and " + (totalCount - previewEntries) + " more. Use /private_chests list_in_area to filter by location."
            ), false);
        } else {
            // Show full list
            for (LockRecord lock : locks) {
                sendLockInfo(source, lock, server);
            }
        }

        return totalCount;
    }

    /**
     * Execute /private_chests info <pos>
     */
    private static int executeInfo(CommandContext<CommandSourceStack> ctx) {
        try {
            BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
            CommandSourceStack source = ctx.getSource();
            MinecraftServer server = source.getServer();
            ServerLevel level = source.getLevel();
            LockState lockState = LockState.get(server);

            // Get container group at position
            Set<BlockPos> containerGroup = ContainerUtils.getContainerGroup(level, pos);

            if (containerGroup.isEmpty()) {
                source.sendFailure(Component.literal("No lockable container found at " + ContainerUtils.positionToString(pos)));
                return 0;
            }

            // Check if locked
            Optional<LockRecord> lockOpt = lockState.getLock(pos);
            if (lockOpt.isEmpty()) {
                source.sendFailure(Component.literal("No lock found at " + ContainerUtils.positionToString(pos)));
                return 0;
            }

            LockRecord lock = lockOpt.get();

            // Build info message
            String containerType = ContainerUtils.getContainerTypeName(level, containerGroup);
            String position = ContainerUtils.positionToString(ContainerUtils.getPrimaryPosition(lock.getContainerPositions()));
            String ownerName = lock.getOwnerName();
            Set<String> allowedUsers = lock.getAllowedUsers();

            source.sendSuccess(() -> Component.literal("===== Lock Information ====="), false);
            source.sendSuccess(() -> Component.literal("Container: " + containerType), false);
            source.sendSuccess(() -> Component.literal("Location: " + position), false);
            source.sendSuccess(() -> Component.literal("Owner: " + ownerName), false);

            if (allowedUsers.isEmpty()) {
                source.sendSuccess(() -> Component.literal("Allowed Users: (none - owner only)"), false);
            } else {
                source.sendSuccess(() -> Component.literal("Allowed Users: " + String.join(", ", allowedUsers)), false);
            }

            // Display timestamps
            String createdDate = formatTimestamp(lock.getCreatedAt());
            String updatedDate = formatTimestamp(lock.getLastUpdatedAt());
            source.sendSuccess(() -> Component.literal("Created: " + createdDate), false);
            source.sendSuccess(() -> Component.literal("Last Updated: " + updatedDate), false);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Execute /private_chests list_in_area [radius]
     */
    private static int executeListInArea(CommandContext<CommandSourceStack> ctx, int chunkRadius) {
        CommandSourceStack source = ctx.getSource();

        // Get source position
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by a player or from a specific location."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        LockState lockState = LockState.get(server);
        BlockPos centerPos = player.blockPosition();

        List<LockRecord> locks = lockState.getLocksInArea(centerPos, chunkRadius);

        if (locks.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                "No private chests found in " + (chunkRadius * 2) + "x" + (chunkRadius * 2) + " chunks around you."
            ), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
            "===== Private Chests in Area (" + locks.size() + " found) ====="
        ), false);

        for (LockRecord lock : locks) {
            sendLockInfo(source, lock, server);
        }

        return locks.size();
    }

    /**
     * Send lock information to the command source.
     */
    private static void sendLockInfo(CommandSourceStack source, LockRecord lock, MinecraftServer server) {
        // Use cached owner name from lock record
        String ownerName = lock.getOwnerName();

        // Get container type (need to get level from one of the container positions)
        BlockPos containerPos = lock.getContainerPositions().iterator().next();
        ServerLevel level = source.getLevel();
        String containerType = ContainerUtils.getContainerTypeName(level, lock.getContainerPositions());
        String position = ContainerUtils.positionToString(ContainerUtils.getPrimaryPosition(lock.getContainerPositions()));

        source.sendSuccess(() -> Component.literal(
            "- " + containerType + " at " + position + " | Owner: " + ownerName
        ), false);
    }

    /**
     * Format a timestamp (milliseconds) to a human-readable date/time string.
     * Includes timezone to help players in different timezones.
     */
    private static String formatTimestamp(long timestamp) {
        if (timestamp == 0) {
            return "Unknown (legacy lock)";
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        return formatter.format(new Date(timestamp));
    }
}
