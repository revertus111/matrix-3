package com.rs.game.player.content.construction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Disposable confidence test for permanent worker skill progression.
 *
 * It never reads or mutates a Player save.
 */
public final class SettlementWorkerProgressionSelfTest {

    private SettlementWorkerProgressionSelfTest() {
    }

    public static String run() {
        String stage = "definitions";
        try {
            Set<String> keys = new HashSet<String>();
            for (SettlementWorkerSkill skill : SettlementWorkerSkill.values()) {
                require(keys.add(skill.getKey()), "duplicate skill key " + skill.getKey());
                require(SettlementWorkerSkill.forKey(skill.getKey()) == skill,
                        "skill lookup failed for " + skill.getKey());
            }
            for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
                require(job.getSkill() != null, "job has no worker skill " + job.getKey());
                require(job.getWorkerXp() > 0, "job has no worker xp " + job.getKey());
            }

            stage = "worker";
            SettlementWorkerDefinition definition = SettlementWorkerDefinition.STARTER_SETTLER;
            SettlementWorkerState worker = new SettlementWorkerState(
                    1L,
                    definition.getKey(),
                    definition.getDisplayName(),
                    definition.getArrivalPlotX(),
                    definition.getArrivalPlotY(),
                    definition.getArrivalPlane());
            for (SettlementWorkerSkill skill : SettlementWorkerSkill.values()) {
                require(worker.getSkillXp(skill) == 0L, "new skill xp is not zero " + skill.getKey());
                require(worker.getSkillLevel(skill) == 1, "new skill level is not one " + skill.getKey());
            }

            stage = "gain";
            require(worker.addSkillXp(SettlementWorkerSkill.WOODCUTTING, 83L) == 83L,
                    "woodcutting xp add mismatch");
            require(worker.getSkillXp(SettlementWorkerSkill.WOODCUTTING) == 83L,
                    "woodcutting xp total mismatch");
            require(worker.getSkillLevel(SettlementWorkerSkill.WOODCUTTING) == 2,
                    "RuneScape-style level curve mismatch");
            worker.addSkillXp(SettlementWorkerSkill.MINING, 24L);
            worker.addSkillXp(SettlementWorkerSkill.HAULING, 12L);

            stage = "serialize";
            byte[] encoded = serialize(worker);
            SettlementWorkerState restored = deserialize(encoded);
            restored.normalize(definition);
            require(restored.getSkillXp(SettlementWorkerSkill.WOODCUTTING) == 83L,
                    "woodcutting xp did not persist");
            require(restored.getSkillLevel(SettlementWorkerSkill.WOODCUTTING) == 2,
                    "woodcutting level changed after serialization");
            require(restored.getSkillXp(SettlementWorkerSkill.MINING) == 24L,
                    "mining xp did not persist");
            require(restored.getSkillsSummary().contains("Hauling L1 (12 xp)"),
                    "skills summary missing hauling");

            return "PASS: stable skills + job mapping + XP gain + level curve + serialization.";
        } catch (Throwable failure) {
            return "FAIL at " + stage + ": " + safeMessage(failure);
        }
    }

    private static byte[] serialize(SettlementWorkerState state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes);
        try {
            output.writeObject(state);
            output.flush();
            return bytes.toByteArray();
        } finally {
            output.close();
        }
    }

    private static SettlementWorkerState deserialize(byte[] encoded) throws Exception {
        ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(encoded));
        try {
            Object value = input.readObject();
            require(value instanceof SettlementWorkerState,
                    "deserialized value is not SettlementWorkerState");
            return (SettlementWorkerState) value;
        } finally {
            input.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.trim().isEmpty()
                ? failure.getClass().getSimpleName()
                : message;
    }
}
