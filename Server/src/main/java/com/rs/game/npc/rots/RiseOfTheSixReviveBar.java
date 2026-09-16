package com.rs.game.npc.rots;

import com.rs.game.HitBar;

/**
 * RoTS incapacitation/revival bar.
 *
 * Type 5 is the donor-backed client hitbar used for the Rise of the Six
 * incapacitation timer. The encounter owner re-sends the current 0-255 progress
 * each Matrix3 tick so every subdued brother shares the same resettable timer.
 */
public final class RiseOfTheSixReviveBar extends HitBar {

	private static final int TYPE = 5;
	private static final int MAX_PERCENTAGE = 255;

	private final int percentage;

	public RiseOfTheSixReviveBar(int percentage) {
		this.percentage = Math.max(0, Math.min(MAX_PERCENTAGE, percentage));
	}

	@Override
	public int getType() {
		return TYPE;
	}

	@Override
	public int getPercentage() {
		return percentage;
	}
}
