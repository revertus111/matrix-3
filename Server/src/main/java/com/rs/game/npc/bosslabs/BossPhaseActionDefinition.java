package com.rs.game.npc.bosslabs;

/**
 * One ordered BossLabs phase entry/exit action.
 *
 * Matrix3 remains authoritative for animation, graphics, health, and NPC
 * lifecycle. This definition only describes the requested phase-side effect.
 */
public final class BossPhaseActionDefinition {

	public static final int PLAY_ANIMATION = 0;
	public static final int PLAY_GRAPHIC = 1;
	public static final int HEAL_BOSS = 2;
	public static final int MAX_HEAL_AMOUNT = 1000000;

	private final int type;
	private final int value;

	public BossPhaseActionDefinition(int type, int value) {
		if (type != PLAY_ANIMATION && type != PLAY_GRAPHIC && type != HEAL_BOSS)
			throw new IllegalArgumentException("Unsupported BossLabs phase action type: " + type);
		if ((type == PLAY_ANIMATION || type == PLAY_GRAPHIC) && value < 0)
			throw new IllegalArgumentException("Phase animation/graphic id must be zero or greater.");
		if (type == HEAL_BOSS && (value < 1 || value > MAX_HEAL_AMOUNT))
			throw new IllegalArgumentException("Phase heal amount must be between 1 and " + MAX_HEAL_AMOUNT + ".");
		this.type = type;
		this.value = value;
	}

	public int getType() {
		return type;
	}

	public int getValue() {
		return value;
	}
}
