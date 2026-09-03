package com.rs.game.npc.rots;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.rs.game.Entity;
import com.rs.game.WorldTile;
import com.rs.game.map.bossInstance.impl.RiseOfTheSixInstance;
import com.rs.game.map.bossInstance.impl.RiseOfTheSixInstance.ArenaSide;
import com.rs.game.npc.NPC;
import com.rs.game.npc.rots.RiseOfTheSixBrother.Brother;
import com.rs.game.player.Player;
import com.rs.game.tasks.WorldTask;
import com.rs.game.tasks.WorldTasksManager;

/**
 * Development-only flight recorder for Rise of the Six.
 *
 * Game/world threads only build strings and offer them to a bounded queue. A
 * daemon writer owns disk I/O so RoTS combat never waits on log writes.
 */
public final class RiseOfTheSixDebugLog {

	private static final File LOG_DIRECTORY = new File("data/logs/rots");
	private static final int QUEUE_CAPACITY = 32768;
	private static final Map<RiseOfTheSixInstance, RiseOfTheSixDebugLog> ACTIVE =
			Collections.synchronizedMap(new IdentityHashMap<RiseOfTheSixInstance, RiseOfTheSixDebugLog>());
	private static final BrotherProbe BROTHER_PROBE = new BrotherProbe();

	private final RiseOfTheSixInstance instance;
	private final File file;
	private final String relativePath;
	private final long startMillis;
	private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>(QUEUE_CAPACITY);
	private final AtomicInteger droppedLines = new AtomicInteger();
	private final EnumMap<Brother, String> previousBrotherControl = new EnumMap<Brother, String>(Brother.class);
	private final EnumMap<Brother, Integer> previousBrotherHp = new EnumMap<Brother, Integer>(Brother.class);
	private final Map<String, Integer> previousPlayerHp = new HashMap<String, Integer>();
	private final Map<String, ArenaSide> previousPlayerSide = new HashMap<String, ArenaSide>();
	private final Set<String> previousPlayers = new HashSet<String>();
	private final Set<String> announcedPlayers = new HashSet<String>();

	private volatile boolean closing;
	private volatile boolean enabled;
	private volatile int tick;
	private Thread writerThread;
	private String previousInstanceControl;

	private RiseOfTheSixDebugLog(RiseOfTheSixInstance instance) {
		this.instance = instance;
		startMillis = System.currentTimeMillis();
		String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS").format(new Date(startMillis));
		String fileName = "rots_" + timestamp + "_" + Integer.toHexString(System.identityHashCode(instance)) + ".log";
		file = new File(LOG_DIRECTORY, fileName);
		relativePath = "data/logs/rots/" + fileName;
		enabled = prepareFile();
		if (!enabled)
			return;
		startWriter();
		log("RECORDER", "START absolutePath=" + file.getAbsolutePath());
		startSnapshotTask();
	}

	public static RiseOfTheSixDebugLog attach(RiseOfTheSixInstance instance) {
		if (instance == null)
			return null;
		synchronized (ACTIVE) {
			RiseOfTheSixDebugLog existing = ACTIVE.get(instance);
			if (existing != null && !existing.closing)
				return existing;
			/*
			 * Delayed special/world tasks can outlive instance cleanup by a tick. Once
			 * BossInstance is finished, never create a second file for that dead fight.
			 */
			if (instance.isFinished())
				return null;
			RiseOfTheSixDebugLog created = new RiseOfTheSixDebugLog(instance);
			ACTIVE.put(instance, created);
			return created;
		}
	}

	public static RiseOfTheSixDebugLog attach(RiseOfTheSixBrother brother) {
		if (brother == null || !(brother.getBossInstance() instanceof RiseOfTheSixInstance))
			return null;
		return attach((RiseOfTheSixInstance) brother.getBossInstance());
	}

	public static void event(RiseOfTheSixInstance instance, String category, String detail) {
		RiseOfTheSixDebugLog log = attach(instance);
		if (log != null)
			log.log(category, detail);
	}

	public static void event(RiseOfTheSixBrother brother, String category, String detail) {
		RiseOfTheSixDebugLog log = attach(brother);
		if (log != null)
			log.log(category, "brother=" + brother.getBrother() + " " + detail);
	}

	public static void close(RiseOfTheSixInstance instance, String reason) {
		RiseOfTheSixDebugLog log;
		synchronized (ACTIVE) {
			log = ACTIVE.remove(instance);
		}
		if (log != null)
			log.close(reason);
	}

	public static void logCombatWait(RiseOfTheSixBrother brother, Entity target) {
		event(brother, "COMBAT_WAIT", "target=" + entityLabel(target) + " specialActive=true state=" + BROTHER_PROBE.controlState(brother));
	}

	public static void logSpecialDispatch(RiseOfTheSixBrother brother, Entity target) {
		event(brother, "SPECIAL_DISPATCH", "target=" + entityLabel(target) + " stateAfter=" + BROTHER_PROBE.controlState(brother));
	}

	public static void logNormalAttack(RiseOfTheSixBrother brother, Entity target, String style,
			int animation, int projectile, int gfx, int maxHit, int rolledDamage,
			int hitDelay, int returnDelay, String extra) {
		StringBuilder detail = new StringBuilder();
		detail.append("target=").append(entityLabel(target));
		detail.append(" style=").append(style);
		detail.append(" animation=").append(animation);
		detail.append(" projectile=").append(projectile);
		detail.append(" gfx=").append(gfx);
		detail.append(" maxHit=").append(maxHit);
		detail.append(" rolledDamage=").append(rolledDamage);
		detail.append(" hitDelay=").append(hitDelay);
		detail.append(" returnDelay=").append(returnDelay);
		if (extra != null && extra.length() != 0)
			detail.append(' ').append(extra);
		event(brother, "NORMAL_ATTACK", detail.toString());
	}

	public String getRelativePath() {
		return relativePath;
	}

	private boolean prepareFile() {
		try {
			if (!LOG_DIRECTORY.exists() && !LOG_DIRECTORY.mkdirs())
				throw new IOException("Could not create " + LOG_DIRECTORY.getAbsolutePath());
			if (!file.exists() && !file.createNewFile())
				throw new IOException("Could not create " + file.getAbsolutePath());
			return true;
		} catch (IOException ex) {
			System.err.println("[RoTS Fight Recorder] " + ex.getMessage());
			return false;
		}
	}

	private void startWriter() {
		writerThread = new Thread(new Runnable() {
			@Override
			public void run() {
				writeLoop();
			}
		}, "RoTS-Fight-Recorder-" + Integer.toHexString(System.identityHashCode(instance)));
		writerThread.setDaemon(true);
		writerThread.start();
	}

	private void writeLoop() {
		int bufferedLines = 0;
		long lastFlush = System.currentTimeMillis();
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false), 65536)) {
			while (!closing || !queue.isEmpty()) {
				String line = null;
				try {
					line = queue.poll(1L, TimeUnit.SECONDS);
				} catch (InterruptedException ex) {
					// Closing interrupts the poll so queued lines can drain immediately.
				}
				if (line != null) {
					writer.write(line);
					writer.newLine();
					bufferedLines++;
				}
				long now = System.currentTimeMillis();
				if (bufferedLines >= 64 || now - lastFlush >= 1000L) {
					writer.flush();
					bufferedLines = 0;
					lastFlush = now;
				}
			}
			int dropped = droppedLines.get();
			if (dropped > 0) {
				writer.write(formatLine(tick, "RECORDER", "DROPPED_LINES count=" + dropped));
				writer.newLine();
			}
			writer.flush();
		} catch (IOException ex) {
			System.err.println("[RoTS Fight Recorder] writer failed for " + file.getAbsolutePath() + ": " + ex);
		} finally {
			enabled = false;
		}
	}

	private void startSnapshotTask() {
		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				if (closing || !enabled) {
					stop();
					return;
				}
				if (instance.isFinished()) {
					log("INSTANCE", "FINISHED detected by recorder");
					RiseOfTheSixDebugLog.close(instance, "instance-finished");
					stop();
					return;
				}
				tick++;
				snapshot();
			}
		}, 0, 0);
	}

	private void snapshot() {
		String instanceControl = "rotation=" + instance.getActiveRotationNumber()
				+ " fightComplete=" + instance.isFightComplete()
				+ " subdued=" + instance.getSubduedCount()
				+ " sideHopPending=" + instance.isSideHopPending()
				+ " sideHopComplete=" + instance.isSideHopComplete()
				+ " sideHopFrom=" + instance.getSideHopFrom()
				+ " sideHopTo=" + instance.getSideHopTo();
		log("SNAPSHOT_INSTANCE", instanceControl);
		if (previousInstanceControl == null)
			log("INSTANCE_STATE_INIT", instanceControl);
		else if (!previousInstanceControl.equals(instanceControl))
			log("INSTANCE_STATE_CHANGE", "from={" + previousInstanceControl + "} to={" + instanceControl + "}");
		previousInstanceControl = instanceControl;

		Set<String> currentPlayers = new HashSet<String>();
		for (Player player : instance.getPlayers()) {
			if (player == null)
				continue;
			String name = safe(player.getDisplayName());
			currentPlayers.add(name);
			ArenaSide side = instance.getPlayerSide(player);
			log("SNAPSHOT_PLAYER", "name=" + name
					+ " hp=" + player.getHitpoints() + "/" + player.getMaxHitpoints()
					+ " tile=" + tile(player)
					+ " side=" + side
					+ " dead=" + player.isDead()
					+ " finished=" + player.hasFinished()
					+ " running=" + player.isRunning());

			if (!previousPlayers.contains(name))
				log("PLAYER_ENTER", "name=" + name + " side=" + side + " tile=" + tile(player));
			Integer oldHp = previousPlayerHp.put(name, Integer.valueOf(player.getHitpoints()));
			if (oldHp != null && oldHp.intValue() != player.getHitpoints())
				log("PLAYER_HP_CHANGE", "name=" + name + " from=" + oldHp + " to=" + player.getHitpoints()
						+ " delta=" + (player.getHitpoints() - oldHp.intValue()));
			ArenaSide oldSide = previousPlayerSide.put(name, side);
			if (oldSide != side)
				log("PLAYER_SIDE_CHANGE", "name=" + name + " from=" + oldSide + " to=" + side);
			announcePath(player, name);
		}
		for (String oldPlayer : previousPlayers) {
			if (!currentPlayers.contains(oldPlayer)) {
				log("PLAYER_LEAVE", "name=" + oldPlayer);
				previousPlayerHp.remove(oldPlayer);
				previousPlayerSide.remove(oldPlayer);
			}
		}
		previousPlayers.clear();
		previousPlayers.addAll(currentPlayers);

		if (instance.getBrothers() == null)
			return;
		for (RiseOfTheSixBrother brother : instance.getBrothers()) {
			if (brother == null)
				continue;
			Brother key = brother.getBrother();
			String control = BROTHER_PROBE.controlState(brother);
			log("SNAPSHOT_BROTHER", "brother=" + key
					+ " id=" + brother.getId()
					+ " hp=" + brother.getHitpoints() + "/" + brother.getMaxHitpoints()
					+ " tile=" + tile(brother)
					+ " side=" + instance.getBrotherSide(brother)
					+ " target=" + entityLabel(brother.getCombat().getTarget())
					+ " " + control);

			String oldControl = previousBrotherControl.put(key, control);
			if (oldControl == null)
				log("BROTHER_STATE_INIT", "brother=" + key + " " + control);
			else if (!oldControl.equals(control))
				log("BROTHER_STATE_CHANGE", "brother=" + key + " from={" + oldControl + "} to={" + control + "}");
			Integer oldHp = previousBrotherHp.put(key, Integer.valueOf(brother.getHitpoints()));
			if (oldHp != null && oldHp.intValue() != brother.getHitpoints())
				log("BROTHER_HP_CHANGE", "brother=" + key + " from=" + oldHp + " to=" + brother.getHitpoints()
						+ " delta=" + (brother.getHitpoints() - oldHp.intValue()));
		}
	}

	private void announcePath(Player player, String name) {
		if (player == null || player.hasFinished() || announcedPlayers.contains(name))
			return;
		announcedPlayers.add(name);
		player.getPackets().sendGameMessage("RoTS Fight Recorder: " + relativePath);
	}

	private void log(String category, String detail) {
		if (!enabled || closing)
			return;
		String line = formatLine(tick, category, detail == null ? "" : detail);
		if (!queue.offer(line))
			droppedLines.incrementAndGet();
	}

	private String formatLine(int lineTick, String category, String detail) {
		long elapsed = Math.max(0L, System.currentTimeMillis() - startMillis);
		return String.format("[+%07dms][tick=%05d][%s] %s", Long.valueOf(elapsed),
				Integer.valueOf(lineTick), category, detail);
	}

	private void close(String reason) {
		if (closing)
			return;
		log("RECORDER", "END reason=" + safe(reason) + " queued=" + queue.size() + " dropped=" + droppedLines.get());
		closing = true;
		if (writerThread != null)
			writerThread.interrupt();
	}

	private static String entityLabel(Entity entity) {
		if (entity == null)
			return "null";
		if (entity instanceof Player)
			return "player:" + safe(((Player) entity).getDisplayName()) + "@" + tile(entity);
		if (entity instanceof RiseOfTheSixBrother) {
			RiseOfTheSixBrother brother = (RiseOfTheSixBrother) entity;
			return "brother:" + brother.getBrother() + "#" + brother.getId() + "@" + tile(entity);
		}
		if (entity instanceof NPC) {
			NPC npc = (NPC) entity;
			return "npc:" + safe(npc.getName()) + "#" + npc.getId() + "@" + tile(entity);
		}
		return entity.getClass().getSimpleName() + "@" + tile(entity);
	}

	private static String tile(Entity entity) {
		return entity == null ? "null" : entity.getX() + "," + entity.getY() + "," + entity.getPlane();
	}

	private static String tile(WorldTile tile) {
		return tile == null ? "null" : tile.getX() + "," + tile.getY() + "," + tile.getPlane();
	}

	private static String safe(String text) {
		if (text == null)
			return "null";
		return text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
	}

	/**
	 * Read-only access to RoTS-owned private state for the recorder. These are our
	 * own descriptive fields, not Matrix3 core internals; failures are rendered as
	 * unknown values rather than affecting gameplay.
	 */
	private static final class BrotherProbe {
		private final Field dharokStoredDamage = field("dharokStoredDamage");
		private final Field toragReleaseDamage = field("toragReleaseDamage");
		private final Field toragVictim = field("toragVictim");
		private final Field hurricaneTarget = field("hurricaneTarget");
		private final Field wallSlamCapturedTile = field("wallSlamCapturedTile");
		private final Field guthanImpaleLaunching = field("guthanImpaleLaunching");
		private final Field guthanRetrievingSpear = field("guthanRetrievingSpear");
		private final Field guthanImpaleVictim = field("guthanImpaleVictim");
		private final Field guthanPrimaryTarget = field("guthanPrimaryTarget");
		private final Field forceReviveHurricane = field("forceReviveHurricane");
		private final Field meleeAutosUntilSpecial = field("meleeAutosUntilSpecial");
		private final Field meleeSpecialIndex = field("meleeSpecialIndex");

		private String controlState(RiseOfTheSixBrother brother) {
			return "subdued=" + brother.isSubdued()
					+ " special=" + specialName(brother)
					+ " dharokCharging=" + brother.isDharokCharging()
					+ " dharokStoredDamage=" + intValue(dharokStoredDamage, brother)
					+ " toragWhacking=" + brother.isToragWhacking()
					+ " toragReleaseDamage=" + intValue(toragReleaseDamage, brother)
					+ " toragVictim=" + entityLabel((Entity) objectValue(toragVictim, brother))
					+ " hurricaning=" + brother.isHurricaning()
					+ " hurricaneTarget=" + entityLabel((Entity) objectValue(hurricaneTarget, brother))
					+ " wallSlamming=" + brother.isWallSlamming()
					+ " wallCapturedTile=" + tile((WorldTile) objectValue(wallSlamCapturedTile, brother))
					+ " guthanSpearAway=" + brother.isGuthanSpearAway()
					+ " guthanLaunching=" + booleanValue(guthanImpaleLaunching, brother)
					+ " guthanRetrieving=" + booleanValue(guthanRetrievingSpear, brother)
					+ " guthanVictim=" + entityLabel((Entity) objectValue(guthanImpaleVictim, brother))
					+ " guthanPrimary=" + entityLabel((Entity) objectValue(guthanPrimaryTarget, brother))
					+ " forceReviveHurricane=" + booleanValue(forceReviveHurricane, brother)
					+ " autosUntilSpecial=" + intValue(meleeAutosUntilSpecial, brother)
					+ " specialIndex=" + intValue(meleeSpecialIndex, brother);
		}

		private String specialName(RiseOfTheSixBrother brother) {
			if (brother.isDharokCharging())
				return "GREATEST_AXE_CHARGE";
			if (brother.isToragWhacking())
				return "TORAG_WHACK";
			if (brother.isHurricaning())
				return "HURRICANE";
			if (brother.isWallSlamming())
				return "WALL_SLAM";
			if (booleanValue(guthanImpaleLaunching, brother))
				return "GUTHAN_IMPALE_LAUNCH";
			if (booleanValue(guthanRetrievingSpear, brother))
				return "GUTHAN_IMPALE_RETRIEVE";
			if (brother.isGuthanSpearAway())
				return "GUTHAN_SPEAR_AWAY";
			return "NONE";
		}

		private static Field field(String name) {
			try {
				Field field = RiseOfTheSixBrother.class.getDeclaredField(name);
				field.setAccessible(true);
				return field;
			} catch (Exception ex) {
				System.err.println("[RoTS Fight Recorder] unavailable field " + name + ": " + ex);
				return null;
			}
		}

		private static int intValue(Field field, RiseOfTheSixBrother brother) {
			if (field == null)
				return Integer.MIN_VALUE;
			try {
				return field.getInt(brother);
			} catch (Exception ex) {
				return Integer.MIN_VALUE;
			}
		}

		private static boolean booleanValue(Field field, RiseOfTheSixBrother brother) {
			if (field == null)
				return false;
			try {
				return field.getBoolean(brother);
			} catch (Exception ex) {
				return false;
			}
		}

		private static Object objectValue(Field field, RiseOfTheSixBrother brother) {
			if (field == null)
				return null;
			try {
				return field.get(brother);
			} catch (Exception ex) {
				return null;
			}
		}
	}
}
