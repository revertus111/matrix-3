package com.rs.game.player.content.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.rs.game.World;
import com.rs.game.WorldObject;
import com.rs.game.WorldTile;
import com.rs.game.npc.NPC;
import com.rs.game.player.Player;

/**
 * Runtime-only ownership tracker for entities created through Dev Mode.
 *
 * Matrix3 remains authoritative for world/entity behavior. This helper only
 * remembers exact Dev-created instances so destructive tooling can prove Dev
 * ownership before removing/replacing anything. Nothing here persists map or
 * spawn data.
 */
public final class DevModeRuntimeManager {

    private static final Map<Player, List<DevPlacement>> PLACEMENTS = Collections
            .synchronizedMap(new WeakHashMap<Player, List<DevPlacement>>());

    private DevModeRuntimeManager() {
    }

    public static void trackNpc(Player player, NPC npc) {
        if (player == null || npc == null) {
            return;
        }
        placements(player).add(DevPlacement.forNpc(npc));
    }

    public static void trackObject(Player player, WorldObject object) {
        if (player == null || object == null) {
            return;
        }
        placements(player).add(DevPlacement.forObject(object));
    }

    public static boolean moveNpc(Player player, int npcIndex, int expectedId, WorldTile source, WorldTile destination) {
        NPC npc = resolveNpc(npcIndex, expectedId, source);
        if (npc == null) {
            send(player, "Dev Mode could not resolve that live NPC target anymore.");
            return false;
        }

        boolean devOwned = isTrackedNpc(player, npc);
        npc.resetWalkSteps();
        npc.setNextWorldTile(new WorldTile(destination));
        if (devOwned) {
            npc.setRespawnTile(new WorldTile(destination));
        }
        send(player, "Dev Mode moved NPC " + npc.getId() + " to " + tileText(destination)
                + (devOwned ? "." : " (runtime only; its original spawn remains authoritative)."));
        return true;
    }

    public static boolean duplicateNpc(Player player, int npcIndex, int expectedId, WorldTile source,
            WorldTile destination) {
        NPC sourceNpc = resolveNpc(npcIndex, expectedId, source);
        if (sourceNpc == null) {
            send(player, "Dev Mode could not resolve that live NPC target anymore.");
            return false;
        }

        NPC copy = World.spawnNPC(sourceNpc.getId(), destination, -1, true, true);
        if (copy == null) {
            send(player, "Matrix3 could not duplicate NPC " + sourceNpc.getId() + " on that tile.");
            return false;
        }
        trackNpc(player, copy);
        send(player, "Dev Mode duplicated NPC " + sourceNpc.getId() + " at " + tileText(destination) + ".");
        return true;
    }

    public static boolean deleteNpc(Player player, int npcIndex, int expectedId, WorldTile source) {
        NPC npc = resolveNpc(npcIndex, expectedId, source);
        if (npc == null) {
            send(player, "Dev Mode could not resolve that live NPC target anymore.");
            return false;
        }
        DevPlacement placement = findTrackedNpc(player, npc);
        if (placement == null) {
            send(player, "Dev Mode blocked delete: that NPC was not created by Dev Mode.");
            return false;
        }

        npc.finish();
        removePlacement(player, placement);
        send(player, "Dev Mode deleted runtime NPC " + expectedId + ".");
        return true;
    }

    public static boolean moveObject(Player player, int objectId, WorldTile source, WorldTile destination) {
        DevPlacement placement = findTrackedObject(player, objectId, source);
        if (placement == null) {
            send(player, "Dev Mode blocked move: that object is not a live Dev-owned placement. Duplicate it first.");
            return false;
        }

        WorldObject before = placement.object;
        World.removeObject(before);
        WorldObject moved = new WorldObject(before.getId(), before.getType(), before.getRotation(), destination);
        World.spawnObject(moved);
        placement.object = moved;
        send(player, "Dev Mode moved object " + objectId + " to " + tileText(destination) + ".");
        return true;
    }

    public static boolean rotateObject(Player player, int objectId, WorldTile source, int delta) {
        DevPlacement placement = findTrackedObject(player, objectId, source);
        if (placement == null) {
            send(player, "Dev Mode blocked rotate: that object is not a live Dev-owned placement. Duplicate it first.");
            return false;
        }

        WorldObject before = placement.object;
        int rotation = (before.getRotation() + delta) & 0x3;
        World.removeObject(before);
        WorldObject rotated = new WorldObject(before.getId(), before.getType(), rotation, before);
        World.spawnObject(rotated);
        placement.object = rotated;
        send(player, "Dev Mode rotated object " + objectId + " to rotation " + rotation + ".");
        return true;
    }

    public static boolean duplicateObject(Player player, int objectId, WorldTile source, WorldTile destination) {
        WorldObject sourceObject = World.getObjectWithId(source, objectId);
        if (sourceObject == null) {
            send(player, "Dev Mode could not resolve that live object target anymore.");
            return false;
        }

        WorldObject copy = new WorldObject(sourceObject.getId(), sourceObject.getType(), sourceObject.getRotation(),
                destination);
        World.spawnObject(copy);
        trackObject(player, copy);
        send(player, "Dev Mode duplicated object " + objectId + " at " + tileText(destination) + ".");
        return true;
    }

    public static boolean deleteObject(Player player, int objectId, WorldTile source) {
        DevPlacement placement = findTrackedObject(player, objectId, source);
        if (placement == null) {
            send(player, "Dev Mode blocked delete: that object is not a live Dev-owned placement.");
            return false;
        }

        World.removeObject(placement.object);
        removePlacement(player, placement);
        send(player, "Dev Mode deleted runtime object " + objectId + ".");
        return true;
    }

    private static NPC resolveNpc(int npcIndex, int expectedId, WorldTile source) {
        if (npcIndex < 0) {
            return null;
        }
        NPC npc = World.getNPCs().get(npcIndex);
        if (npc == null || npc.hasFinished() || npc.getId() != expectedId || !sameTile(npc, source)) {
            return null;
        }
        return npc;
    }

    private static boolean isTrackedNpc(Player player, NPC npc) {
        return findTrackedNpc(player, npc) != null;
    }

    private static DevPlacement findTrackedNpc(Player player, NPC npc) {
        List<DevPlacement> list = placements(player);
        synchronized (list) {
            prune(list);
            for (DevPlacement placement : list) {
                if (placement.npc == npc) {
                    return placement;
                }
            }
        }
        return null;
    }

    private static DevPlacement findTrackedObject(Player player, int objectId, WorldTile source) {
        List<DevPlacement> list = placements(player);
        synchronized (list) {
            prune(list);
            WorldObject live = World.getObjectWithId(source, objectId);
            if (live == null) {
                return null;
            }
            for (DevPlacement placement : list) {
                if (placement.object == live && isLiveOwnedObject(placement.object)) {
                    return placement;
                }
            }
        }
        return null;
    }

    private static boolean isLiveOwnedObject(WorldObject object) {
        return object != null && World.isSpawnedObject(object)
                && World.getObjectWithId(object, object.getId()) == object;
    }

    private static void removePlacement(Player player, DevPlacement target) {
        List<DevPlacement> list = placements(player);
        synchronized (list) {
            list.remove(target);
        }
    }

    private static List<DevPlacement> placements(Player player) {
        synchronized (PLACEMENTS) {
            List<DevPlacement> list = PLACEMENTS.get(player);
            if (list == null) {
                list = Collections.synchronizedList(new ArrayList<DevPlacement>());
                PLACEMENTS.put(player, list);
            }
            return list;
        }
    }

    private static void prune(List<DevPlacement> list) {
        Iterator<DevPlacement> iterator = list.iterator();
        while (iterator.hasNext()) {
            DevPlacement placement = iterator.next();
            if (placement.npc != null) {
                if (placement.npc.hasFinished()) {
                    iterator.remove();
                }
            } else if (placement.object == null || !isLiveOwnedObject(placement.object)) {
                iterator.remove();
            }
        }
    }

    private static boolean sameTile(WorldTile a, WorldTile b) {
        return a != null && b != null && a.getX() == b.getX() && a.getY() == b.getY()
                && a.getPlane() == b.getPlane();
    }

    private static String tileText(WorldTile tile) {
        return tile.getX() + ", " + tile.getY() + ", " + tile.getPlane();
    }

    private static void send(Player player, String message) {
        if (player != null) {
            player.getPackets().sendGameMessage(message);
        }
    }

    private static final class DevPlacement {
        private final NPC npc;
        private WorldObject object;

        private DevPlacement(NPC npc, WorldObject object) {
            this.npc = npc;
            this.object = object;
        }

        private static DevPlacement forNpc(NPC npc) {
            return new DevPlacement(npc, null);
        }

        private static DevPlacement forObject(WorldObject object) {
            return new DevPlacement(null, object);
        }
    }
}
