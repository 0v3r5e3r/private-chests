package com.simpleforapanda.privatechests.model;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Represents a lock on a container group (chest, double chest, or barrel).
 * Stores ownership, allowed users, and positions of the container and sign.
 */
public class LockRecord {
    private final UUID ownerUuid;
    private final String ownerName;
    private final BlockPos signPos;
    private final Set<BlockPos> containerPositions;
    private final Set<String> allowedUsers;
    private final long createdAt;      // Timestamp in milliseconds
    private final long lastUpdatedAt;  // Timestamp in milliseconds

    public LockRecord(UUID ownerUuid, String ownerName, BlockPos signPos, Set<BlockPos> containerPositions, Set<String> allowedUsers) {
        this(ownerUuid, ownerName, signPos, containerPositions, allowedUsers, System.currentTimeMillis(), System.currentTimeMillis());
    }

    public LockRecord(UUID ownerUuid, String ownerName, BlockPos signPos, Set<BlockPos> containerPositions, Set<String> allowedUsers, long createdAt, long lastUpdatedAt) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.signPos = signPos;
        this.containerPositions = new HashSet<>(containerPositions);
        this.allowedUsers = new HashSet<>(allowedUsers);
        this.createdAt = createdAt;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public BlockPos getSignPos() {
        return signPos;
    }

    public Set<BlockPos> getContainerPositions() {
        return Collections.unmodifiableSet(containerPositions);
    }

    public Set<String> getAllowedUsers() {
        return Collections.unmodifiableSet(allowedUsers);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    /**
     * Check if a username is in the allowed list (case-insensitive).
     * Also handles Floodgate prefix stripping and whitespace normalization.
     */
    public boolean isUserAllowed(String username, String floodgatePrefix) {
        if (username == null) {
            return false;
        }

        String normalized = normalizeUsername(username, floodgatePrefix);

        for (String allowed : allowedUsers) {
            if (normalizeUsername(allowed, floodgatePrefix).equals(normalized)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Normalize a username for comparison:
     * - Case-insensitive
     * - Trim whitespace
     * - Strip Floodgate prefix if present
     * - Treat spaces/underscores equivalently (replace _ with space)
     */
    private String normalizeUsername(String username, String floodgatePrefix) {
        String normalized = username.trim().toLowerCase();

        // Strip Floodgate prefix if configured and present
        if (floodgatePrefix != null && !floodgatePrefix.isEmpty() && normalized.startsWith(floodgatePrefix.toLowerCase())) {
            normalized = normalized.substring(floodgatePrefix.length());
        }

        // Treat underscores as spaces
        normalized = normalized.replace('_', ' ');

        return normalized;
    }

    /**
     * Serialize this lock record to NBT.
     */
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();

        tag.putLong("OwnerMost", ownerUuid.getMostSignificantBits());
        tag.putLong("OwnerLeast", ownerUuid.getLeastSignificantBits());
        tag.putString("OwnerName", ownerName);
        tag.putInt("SignPosX", signPos.getX());
        tag.putInt("SignPosY", signPos.getY());
        tag.putInt("SignPosZ", signPos.getZ());

        ListTag containerList = new ListTag();
        for (BlockPos pos : containerPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", pos.getX());
            posTag.putInt("Y", pos.getY());
            posTag.putInt("Z", pos.getZ());
            containerList.add(posTag);
        }
        tag.put("Containers", containerList);

        ListTag userList = new ListTag();
        for (String user : allowedUsers) {
            CompoundTag userTag = new CompoundTag();
            userTag.putString("Name", user);
            userList.add(userTag);
        }
        tag.put("AllowedUsers", userList);

        tag.putLong("CreatedAt", createdAt);
        tag.putLong("LastUpdatedAt", lastUpdatedAt);

        return tag;
    }

    /**
     * Deserialize a lock record from NBT.
     */
    public static LockRecord fromNbt(CompoundTag tag) {
        UUID ownerUuid = new UUID(
            tag.getLong("OwnerMost").orElse(0L),
            tag.getLong("OwnerLeast").orElse(0L)
        );
        String ownerName = tag.getString("OwnerName").orElse("Unknown");
        BlockPos signPos = new BlockPos(
            tag.getInt("SignPosX").orElse(0),
            tag.getInt("SignPosY").orElse(0),
            tag.getInt("SignPosZ").orElse(0)
        );

        Set<BlockPos> containerPositions = new HashSet<>();
        tag.getList("Containers").ifPresent(containerList -> {
            for (int i = 0; i < containerList.size(); i++) {
                containerList.getCompound(i).ifPresent(posTag -> {
                    containerPositions.add(new BlockPos(
                        posTag.getInt("X").orElse(0),
                        posTag.getInt("Y").orElse(0),
                        posTag.getInt("Z").orElse(0)
                    ));
                });
            }
        });

        Set<String> allowedUsers = new HashSet<>();
        tag.getList("AllowedUsers").ifPresent(userList -> {
            for (int i = 0; i < userList.size(); i++) {
                userList.getCompound(i).ifPresent(userTag -> {
                    userTag.getString("Name").ifPresent(allowedUsers::add);
                });
            }
        });

        // Load timestamps (default to 0 for old locks that don't have this data)
        long createdAt = tag.getLong("CreatedAt").orElse(0L);
        long lastUpdatedAt = tag.getLong("LastUpdatedAt").orElse(0L);

        return new LockRecord(ownerUuid, ownerName, signPos, containerPositions, allowedUsers, createdAt, lastUpdatedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LockRecord that)) return false;
        return Objects.equals(ownerUuid, that.ownerUuid) &&
               Objects.equals(signPos, that.signPos) &&
               Objects.equals(containerPositions, that.containerPositions) &&
               Objects.equals(allowedUsers, that.allowedUsers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerUuid, signPos, containerPositions, allowedUsers);
    }

    @Override
    public String toString() {
        return "LockRecord{" +
               "ownerUuid=" + ownerUuid +
               ", signPos=" + signPos +
               ", containerPositions=" + containerPositions +
               ", allowedUsers=" + allowedUsers +
               '}';
    }
}
