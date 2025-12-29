package com.simpleforapanda.privatechests.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Utility methods for working with signs and detecting [private] markers.
 */
public class SignUtils {
    private static final String PRIVATE_MARKER = "[private]";

    /**
     * Check if a block is a wall sign.
     */
    public static boolean isWallSign(BlockState state) {
        return state.getBlock() instanceof WallSignBlock;
    }

    /**
     * Check if a sign contains the [private] marker on either front or back.
     */
    public static boolean isPrivateSign(Level level, BlockPos signPos) {
        BlockState state = level.getBlockState(signPos);
        if (!isWallSign(state)) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(signPos);
        if (!(blockEntity instanceof SignBlockEntity signEntity)) {
            return false;
        }

        // Check front side (lines 0-3)
        if (containsPrivateMarker(signEntity, true)) {
            return true;
        }

        // Check back side (lines 0-3)
        return containsPrivateMarker(signEntity, false);
    }

    /**
     * Check if a sign side contains the [private] marker on line 1.
     * The marker must be alone on the line (exact match, case-insensitive).
     */
    public static boolean containsPrivateMarker(SignBlockEntity signEntity, boolean isFront) {
        String firstLine = signEntity.getText(isFront).getMessage(0, false).getString().trim();
        return firstLine.equalsIgnoreCase(PRIVATE_MARKER);
    }

    /**
     * Check if a list of text lines contains the [private] marker on line 1.
     * The marker must be alone on the line (exact match, case-insensitive).
     */
    public static boolean containsPrivateMarker(java.util.List<net.minecraft.server.network.FilteredText> lines) {
        if (lines.isEmpty()) {
            return false;
        }
        String firstLine = lines.get(0).raw().trim();
        return firstLine.equalsIgnoreCase(PRIVATE_MARKER);
    }

    /**
     * Get the block position that a wall sign is attached to.
     */
    public static Optional<BlockPos> getAttachedBlock(Level level, BlockPos signPos) {
        BlockState state = level.getBlockState(signPos);
        if (!isWallSign(state)) {
            return Optional.empty();
        }

        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return Optional.of(signPos.relative(facing.getOpposite()));
    }

    /**
     * Extract allowed usernames from a sign.
     * Extracts from ALL lines on BOTH front and back sides.
     * Skips line 1 if it contains [private] marker.
     * Each line can contain one or more usernames separated by commas.
     * Supports formats: "name", "name,name", "name, name", etc.
     */
    public static Set<String> extractAllowedUsers(SignBlockEntity signEntity) {
        Set<String> users = new HashSet<>();

        // Check front side - skip line 0 if it has [private]
        boolean frontHasPrivate = containsPrivateMarker(signEntity, true);
        int frontStartLine = frontHasPrivate ? 1 : 0;
        for (int i = frontStartLine; i < 4; i++) {
            String line = signEntity.getText(true).getMessage(i, false).getString().trim();
            extractUsernamesFromLine(line, users);
        }

        // Check back side - skip line 0 if it has [private]
        boolean backHasPrivate = containsPrivateMarker(signEntity, false);
        int backStartLine = backHasPrivate ? 1 : 0;
        for (int i = backStartLine; i < 4; i++) {
            String line = signEntity.getText(false).getMessage(i, false).getString().trim();
            extractUsernamesFromLine(line, users);
        }

        return users;
    }

    /**
     * Extract allowed usernames from sign text lines (one side only).
     * Skips line 1 if it contains [private] marker, otherwise processes all lines.
     * Each line can contain one or more usernames separated by commas.
     * Supports formats: "name", "name,name", "name, name", etc.
     */
    public static Set<String> extractAllowedUsers(java.util.List<net.minecraft.server.network.FilteredText> lines) {
        Set<String> users = new HashSet<>();

        // Skip line 0 if it has [private], otherwise start from 0
        boolean hasPrivate = containsPrivateMarker(lines);
        int startLine = hasPrivate ? 1 : 0;

        for (int i = startLine; i < Math.min(lines.size(), 4); i++) {
            String line = lines.get(i).raw().trim();
            extractUsernamesFromLine(line, users);
        }

        return users;
    }

    /**
     * Helper method to extract usernames from a single line.
     * Handles comma-separated lists with optional spaces.
     * Examples: "name", "name,name", "name, name", "name,name, name"
     *
     * @param line The line to parse
     * @param users The set to add usernames to
     */
    private static void extractUsernamesFromLine(String line, Set<String> users) {
        if (line.isEmpty()) {
            return;
        }

        // Split by comma and process each part
        String[] parts = line.split(",");
        for (String part : parts) {
            String username = part.trim();
            // Skip empty parts and parts containing [private]
            if (!username.isEmpty() && !username.toLowerCase().contains(PRIVATE_MARKER)) {
                users.add(username);
            }
        }
    }

    /**
     * Check if a wall sign is attached to a specific container position.
     */
    public static boolean isAttachedToContainer(Level level, BlockPos signPos, BlockPos containerPos) {
        Optional<BlockPos> attached = getAttachedBlock(level, signPos);
        if (attached.isEmpty()) {
            return false;
        }

        // Direct attachment
        if (attached.get().equals(containerPos)) {
            return true;
        }

        // Check if attached to any block in the container group
        Set<BlockPos> containerGroup = ContainerUtils.getContainerGroup(level, containerPos);
        return containerGroup.contains(attached.get());
    }

    /**
     * Validate that a private sign is still valid:
     * - Sign block exists
     * - Sign is a wall sign
     * - Sign contains [private] marker
     * - Sign is attached to the expected container
     */
    public static boolean isValidPrivateSign(Level level, BlockPos signPos, Set<BlockPos> containerPositions) {
        BlockState state = level.getBlockState(signPos);
        if (!isWallSign(state)) {
            return false;
        }

        if (!isPrivateSign(level, signPos)) {
            return false;
        }

        Optional<BlockPos> attached = getAttachedBlock(level, signPos);
        if (attached.isEmpty()) {
            return false;
        }

        // Check if attached to any block in the container group
        return containerPositions.contains(attached.get());
    }

    /**
     * Find all wall signs attached to a container group.
     */
    public static Set<BlockPos> findAttachedSigns(Level level, Set<BlockPos> containerPositions) {
        Set<BlockPos> signs = new HashSet<>();

        for (BlockPos containerPos : containerPositions) {
            // Check all 4 horizontal directions
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos signPos = containerPos.relative(direction);
                BlockState signState = level.getBlockState(signPos);

                if (isWallSign(signState)) {
                    // Verify it's actually attached to this container
                    Direction signFacing = signState.getValue(BlockStateProperties.HORIZONTAL_FACING);
                    if (signFacing == direction.getOpposite()) {
                        signs.add(signPos);
                    }
                }
            }
        }

        return signs;
    }
}
