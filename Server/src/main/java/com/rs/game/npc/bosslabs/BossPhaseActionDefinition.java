package com.rs.game.npc.bosslabs;

/**
 * One ordered BossLabs phase entry/exit action.
 *
 * Matrix3 remains authoritative for animation, graphics, health, NPC spawning,
 * combat, and lifecycle. This definition only describes the requested
 * phase-side effect.
 */
public final class BossPhaseActionDefinition {

	public static final int PLAY_ANIMATION = 0;
	public static final int PLAY_GRAPHIC = 1;
	public static final int HEAL_BOSS = 2;
	public static final int SPAWN_MINIONS = 3;
	public static final int MAX_HEAL_AMOUNT = 1000000;
	public static final int MAX_MINION_COUNT = 8;
	public static final int MAX_MINION_RADIUS = 8;

	private final int type;
	private final int value;
	private final int quantity;
	private final int radius;

	public BossPhaseActionDefinition(int type, int value) {
		this(type, value, 1, 1);
	}

	public BossPhaseActionDefinition(int type, int value, int quantity, int radius) {
		if (type != PLAY_ANIMATION && type != PLAY_GRAPHIC && type != HEAL_BOSS && type != SPAWN_MINIONS)
			throw new IllegalArgumentException("Unsupported BossLabs phase action type: " + type);
		if ((type == PLAY_ANIMATION || type == PLAY_GRAPHIC) && value < 0)
			throw new IllegalArgumentException("Phase animation/graphic id must be zero or greater.");
		if (type == HEAL_BOSS && (value < 1 || value > MAX_HEAL_AMOUNT))
			throw new IllegalArgumentException("Phase heal amount must be between 1 and " + MAX_HEAL_AMOUNT + ".");
		if (type == SPAWN_MINIONS) {
			if (value < 0)
				throw new IllegalArgumentException("Phase minion NPC id must be zero or greater.");
			if (quantity < 1 || quantity > MAX_MINION_COUNT)
				throw new IllegalArgumentException("Phase minion amount must be between 1 and " + MAX_MINION_COUNT + ".");
			if (radius < 1 || radius > MAX_MINION_RADIUS)
				throw new IllegalArgumentException("Phase minion radius must be between 1 and " + MAX_MINION_RADIUS + ".");
		} else if (quantity != 1 || radius != 1) {
			throw new IllegalArgumentException("Non-minion phase actions must use default quantity/radius values.");
		}
		this.type = type;
		this.value = value;
		this.quantity = quantity;
		this.radius = radius;
	}

	public int getType() {
		return type;
	}

	public int getValue() {
		return value;
	}

	public int getQuantity() {
		return quantity;
	}

	public int getRadius() {
		return radius;
	}
}
