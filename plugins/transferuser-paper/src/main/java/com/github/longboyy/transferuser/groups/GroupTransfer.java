package com.github.longboyy.transferuser.groups;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import vg.civcraft.mc.namelayer.GroupManager;
import vg.civcraft.mc.namelayer.GroupManager.PlayerType;
import vg.civcraft.mc.namelayer.NameLayerAPI;
import vg.civcraft.mc.namelayer.group.Group;

/**
 * Moves a player's NameLayer group memberships and ranks, including primary ownership, to another player.
 */
public final class GroupTransfer {

    private final Logger logger;

    public GroupTransfer(Logger logger) {
        this.logger = logger;
    }

    public record Result(List<String> joined, List<String> ownershipTransferred, List<String> failed) {
    }

    public Result transfer(UUID oldPlayer, UUID newPlayer) {
        GroupManager groupManager = NameLayerAPI.getGroupManager();
        List<String> joined = new ArrayList<>();
        List<String> ownershipTransferred = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        // Merged groups can return the same name more than once
        for (String groupName : new LinkedHashSet<>(groupManager.getAllGroupNames(oldPlayer))) {
            Group group = GroupManager.getGroup(groupName);
            if (group == null) {
                continue;
            }
            PlayerType oldRank = group.getCurrentRank(oldPlayer);
            if (oldRank == null) {
                continue;
            }

            PlayerType currentRank = group.getCurrentRank(newPlayer);
            // Never demote the new player if they already hold a higher rank in this group
            if (currentRank == null || currentRank.ordinal() < oldRank.ordinal()) {
                if (currentRank != null) {
                    // faction_member has no unique key, so remove the old row rather than adding a duplicate
                    group.removeMember(newPlayer);
                }
                group.addMember(newPlayer, oldRank);
                joined.add(group.getName() + " (" + oldRank.name() + ")");
            }

            if (group.isOwner(oldPlayer)) {
                groupManager.transferGroup(group, newPlayer);
                if (group.isOwner(newPlayer)) {
                    ownershipTransferred.add(group.getName());
                } else {
                    // Keep the old player in the group so it isn't left with a primary owner who isn't a member
                    this.logger.warning("Primary ownership transfer of " + group.getName() + " to " + newPlayer + " was cancelled");
                    failed.add(group.getName());
                    continue;
                }
            }

            group.removeMember(oldPlayer);
        }

        return new Result(joined, ownershipTransferred, failed);
    }
}
