package com.rs.game.player.content.construction;

/**
 * Deliberate bridge from legacy settlement resource identities to real
 * RuneScape item IDs as each production chain migrates to physical logistics.
 *
 * Only mappings verified against Matrix3 source belong here. Unmigrated
 * resources intentionally have no mapping and remain on compatibility storage.
 */
public enum SettlementFactoryItem {

    // VERIFIED-static: Matrix3 Woodcutting normal Logs mapping.
    NORMAL_LOGS(SettlementResource.WOOD, 1511),
    // VERIFIED-static: HouseConstants.PLANK = 960.
    PLANKS(SettlementResource.PLANKS, 960);

    private final SettlementResource resource;
    private final int itemId;

    SettlementFactoryItem(SettlementResource resource, int itemId) {
        this.resource = resource;
        this.itemId = itemId;
    }

    public SettlementResource getResource() {
        return resource;
    }

    public int getItemId() {
        return itemId;
    }

    public static SettlementFactoryItem forResource(SettlementResource resource) {
        if (resource == null) {
            return null;
        }
        for (SettlementFactoryItem mapping : values()) {
            if (mapping.resource == resource) {
                return mapping;
            }
        }
        return null;
    }

    public static SettlementFactoryItem forItemId(int itemId) {
        for (SettlementFactoryItem mapping : values()) {
            if (mapping.itemId == itemId) {
                return mapping;
            }
        }
        return null;
    }

    public String getDisplayName() {
        return resource.getDisplayName();
    }
}
