package com.rs.game.npc.bosslabs;

/**
 * One encounter-relative tile offset used by a BossLabs attack pattern.
 */
public final class BossTileOffset {

	public static final int MAX_ABSOLUTE_OFFSET = 16;

	private final int x;
	private final int y;

	public BossTileOffset(int x, int y) {
		if (Math.abs(x) > MAX_ABSOLUTE_OFFSET || Math.abs(y) > MAX_ABSOLUTE_OFFSET)
			throw new IllegalArgumentException("Boss tile offsets must stay within +/-" + MAX_ABSOLUTE_OFFSET + ".");
		this.x = x;
		this.y = y;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	@Override
	public int hashCode() {
		return 31 * x + y;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object)
			return true;
		if (!(object instanceof BossTileOffset))
			return false;
		BossTileOffset other = (BossTileOffset) object;
		return x == other.x && y == other.y;
	}
}
