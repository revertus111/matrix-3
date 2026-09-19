package com.rs.game.player.content.construction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Player-owned persistent settlement data.
 *
 * Only plot-relative data is saved here. Runtime instance bounds and projected
 * Matrix3 world coordinates belong to SettlementInstance.
 */
public final class SettlementState implements Serializable {

    private static final long serialVersionUID = -3303744987274861157L;

    public static final int PLOT_TILES = 64;
    public static final int PLOT_PLANE = 0;

    private static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int STARTER_STORAGE_CAPACITY = 200;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private long nextPieceId = 1L;
    private List<SettlementPlacedPiece> pieces = new ArrayList<SettlementPlacedPiece>();
    private Map<String, Long> resources = new HashMap<String, Long>();
    private int storageCapacity = STARTER_STORAGE_CAPACITY;

    public synchronized void normalize() {
        if (schemaVersion <= 0) {
            schemaVersion = 1;
        }
        if (pieces == null) {
            pieces = new ArrayList<SettlementPlacedPiece>();
        }
        if (resources == null) {
            resources = new HashMap<String, Long>();
        }
        if (storageCapacity <= 0) {
            storageCapacity = STARTER_STORAGE_CAPACITY;
        }
        Iterator<Map.Entry<String, Long>> resourceIterator = resources.entrySet().iterator();
        while (resourceIterator.hasNext()) {
            Map.Entry<String, Long> entry = resourceIterator.next();
            SettlementResource resource = SettlementResource.forKey(entry.getKey());
            Long amount = entry.getValue();
            if (resource == null || amount == null || amount.longValue() <= 0L) {
                resourceIterator.remove();
            }
        }
        schemaVersion = CURRENT_SCHEMA_VERSION;

        long highestId = 0L;
        for (SettlementPlacedPiece piece : pieces) {
            if (piece != null && piece.getPieceId() > highestId) {
                highestId = piece.getPieceId();
            }
        }
        if (nextPieceId <= highestId) {
            nextPieceId = highestId + 1L;
        }
        if (nextPieceId <= 0L) {
            nextPieceId = 1L;
        }
    }

    public synchronized int size() {
        normalize();
        return pieces.size();
    }

    public synchronized List<SettlementPlacedPiece> snapshotPieces() {
        normalize();
        return new ArrayList<SettlementPlacedPiece>(pieces);
    }

    public synchronized SettlementPlacedPiece place(SettlementBuildPiece definition,
            int plotX, int plotY, int plane, int rotation) {
        normalize();
        if (definition == null || !isValidPlotLocation(plotX, plotY, plane)
                || isOccupied(plotX, plotY, plane, definition.getObjectType(), -1L)) {
            return null;
        }
        SettlementPlacedPiece piece = new SettlementPlacedPiece(
                nextPieceId++, definition.getKey(), plotX, plotY, plane, rotation);
        pieces.add(piece);
        return piece;
    }

    public synchronized SettlementPlacedPiece find(int objectId, int plotX, int plotY, int plane) {
        normalize();
        for (SettlementPlacedPiece piece : pieces) {
            if (piece == null || piece.getPlotX() != plotX || piece.getPlotY() != plotY
                    || piece.getPlane() != plane) {
                continue;
            }
            SettlementBuildPiece definition = SettlementBuildPiece.forKey(piece.getDefinitionKey());
            if (definition != null && definition.getObjectId() == objectId) {
                return piece;
            }
        }
        return null;
    }

    public synchronized SettlementPlacedPiece move(long pieceId, int plotX, int plotY, int plane) {
        normalize();
        int index = indexOf(pieceId);
        if (index < 0) {
            return null;
        }
        SettlementPlacedPiece current = pieces.get(index);
        SettlementBuildPiece definition = SettlementBuildPiece.forKey(current.getDefinitionKey());
        if (definition == null || !isValidPlotLocation(plotX, plotY, plane)
                || isOccupied(plotX, plotY, plane, definition.getObjectType(), pieceId)) {
            return null;
        }
        SettlementPlacedPiece moved = current.withPosition(plotX, plotY, plane);
        pieces.set(index, moved);
        return moved;
    }

    public synchronized SettlementPlacedPiece rotate(long pieceId, int delta) {
        normalize();
        int index = indexOf(pieceId);
        if (index < 0 || (delta != -1 && delta != 1)) {
            return null;
        }
        SettlementPlacedPiece current = pieces.get(index);
        SettlementPlacedPiece rotated = current.withRotation((current.getRotation() + delta) & 0x3);
        pieces.set(index, rotated);
        return rotated;
    }

    public synchronized SettlementPlacedPiece duplicate(long pieceId, int plotX, int plotY, int plane) {
        normalize();
        int index = indexOf(pieceId);
        if (index < 0) {
            return null;
        }
        SettlementPlacedPiece current = pieces.get(index);
        SettlementBuildPiece definition = SettlementBuildPiece.forKey(current.getDefinitionKey());
        if (definition == null || !isValidPlotLocation(plotX, plotY, plane)
                || isOccupied(plotX, plotY, plane, definition.getObjectType(), -1L)) {
            return null;
        }
        SettlementPlacedPiece duplicate = new SettlementPlacedPiece(
                nextPieceId++, current.getDefinitionKey(), plotX, plotY, plane, current.getRotation());
        pieces.add(duplicate);
        return duplicate;
    }

    public synchronized SettlementPlacedPiece remove(long pieceId) {
        normalize();
        int index = indexOf(pieceId);
        return index < 0 ? null : pieces.remove(index);
    }

    public synchronized long getResourceAmount(SettlementResource resource) {
        normalize();
        if (resource == null) {
            return 0L;
        }
        Long amount = resources.get(resource.getKey());
        return amount == null ? 0L : amount.longValue();
    }

    public synchronized long getTotalStoredResources() {
        normalize();
        long total = 0L;
        for (Long amount : resources.values()) {
            if (amount != null && amount.longValue() > 0L) {
                total += amount.longValue();
            }
        }
        return total;
    }

    public synchronized int getStorageCapacity() {
        normalize();
        return storageCapacity;
    }

    public synchronized long getStorageRemaining() {
        normalize();
        return Math.max(0L, (long) storageCapacity - getTotalStoredResources());
    }

    public synchronized long addResource(SettlementResource resource, long amount) {
        normalize();
        if (resource == null || amount <= 0L) {
            return 0L;
        }
        long accepted = Math.min(amount, getStorageRemaining());
        if (accepted <= 0L) {
            return 0L;
        }
        long next = getResourceAmount(resource) + accepted;
        resources.put(resource.getKey(), Long.valueOf(next));
        return accepted;
    }

    public synchronized long removeResource(SettlementResource resource, long amount) {
        normalize();
        if (resource == null || amount <= 0L) {
            return 0L;
        }
        long current = getResourceAmount(resource);
        long removed = Math.min(current, amount);
        long next = current - removed;
        if (next <= 0L) {
            resources.remove(resource.getKey());
        } else {
            resources.put(resource.getKey(), Long.valueOf(next));
        }
        return removed;
    }

    public synchronized String getResourceSummary() {
        normalize();
        StringBuilder summary = new StringBuilder();
        for (SettlementResource resource : SettlementResource.values()) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(resource.getDisplayName()).append("=")
                    .append(getResourceAmount(resource));
        }
        summary.append(" | total=").append(getTotalStoredResources())
                .append("/").append(storageCapacity);
        return summary.toString();
    }

    private int indexOf(long pieceId) {
        for (int index = 0; index < pieces.size(); index++) {
            SettlementPlacedPiece piece = pieces.get(index);
            if (piece != null && piece.getPieceId() == pieceId) {
                return index;
            }
        }
        return -1;
    }

    public static boolean isValidPlotLocation(int plotX, int plotY, int plane) {
        return plane == PLOT_PLANE
                && plotX >= 0 && plotX < PLOT_TILES
                && plotY >= 0 && plotY < PLOT_TILES;
    }

    private boolean isOccupied(int plotX, int plotY, int plane, int objectType, long ignoredPieceId) {
        for (SettlementPlacedPiece piece : pieces) {
            if (piece == null || piece.getPieceId() == ignoredPieceId
                    || piece.getPlotX() != plotX || piece.getPlotY() != plotY
                    || piece.getPlane() != plane) {
                continue;
            }
            SettlementBuildPiece definition = SettlementBuildPiece.forKey(piece.getDefinitionKey());
            if (definition != null && definition.getObjectType() == objectType) {
                return true;
            }
        }
        return false;
    }
}
