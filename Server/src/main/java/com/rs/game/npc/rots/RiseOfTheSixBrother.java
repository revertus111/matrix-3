package com.rs.game.npc.rots;

import com.rs.game.Animation;
import com.rs.game.Entity;
import com.rs.game.WorldTile;
import com.rs.game.map.bossInstance.impl.RiseOfTheSixInstance;
import com.rs.game.npc.NPC;
import com.rs.game.npc.combat.NPCCombatDefinitions;

/**
 * Empowered Barrows brother used only by the Rise of the Six encounter.
 *
 * Standard BarrowsBrother is intentionally not reused because RoTS brothers are
 * incapacitated at zero HP and can be restored by the shared shadow bond.
 */
public final class RiseOfTheSixBrother extends NPC {

	private static final long serialVersionUID = 1L;

	public static enum Brother {
		AHRIM("Ahrim the Blighted", 18538, 18539),
		DHAROK("Dharok the Wretched", 18540, -1),
		GUTHAN("Guthan the Infested", 18541, 18542),
		KARIL("Karil the Tainted", 18543, -1),
		TORAG("Torag the Corrupted", 18544, -1),
		VERAC("Verac the Defiled", 18545, -1);

		private final String displayName;
		private final int npcId;
		private final int alternateNpcId;

		private Brother(String displayName, int npcId, int alternateNpcId) {
			this.displayName = displayName;
			this.npcId = npcId;
			this.alternateNpcId = alternateNpcId;
		}

		public String getDisplayName() {
			return displayName;
		}

		public int getNpcId() {
			return npcId;
		}

		public int getAlternateNpcId() {
			return alternateNpcId;
		}

		public static Brother forNpcId(int npcId) {
			for (Brother brother : values()) {
				if (brother.npcId == npcId || brother.alternateNpcId == npcId)
					return brother;
			}
			return null;
		}
	}

	private final Brother brother;
	private final transient RiseOfTheSixInstance instance;
	private boolean subdued;

	public RiseOfTheSixBrother(Brother brother, WorldTile tile, RiseOfTheSixInstance instance) {
		super(brother.getNpcId(), tile, -1, true, true);
		this.brother = brother;
		this.instance = instance;
		setBossInstance(instance);
		setForceAgressive(true);
	}

	public Brother getBrother() {
		return brother;
	}

	public boolean isSubdued() {
		return subdued;
	}

	@Override
	public void sendDeath(Entity source) {
		if (subdued || hasFinished() || instance == null || instance.isFightComplete())
			return;

		subdued = true;
		NPCCombatDefinitions defs = getCombatDefinitions();
		resetWalkSteps();
		getCombat().removeTarget();
		if (!isDead())
			setHitpoints(0);
		setCantInteract(true);
		setNextAnimation(new Animation(defs.getDeathEmote()));
		giveXP();
		instance.onBrotherSubdued(this);
	}

	public void revive(int hitpoints) {
		if (!subdued || hasFinished())
			return;
		reset();
		setHitpoints(Math.min(getMaxHitpoints(), Math.max(1, hitpoints)));
		setCantInteract(false);
		subdued = false;
	}
}
