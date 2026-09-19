package com.rs.game.player.content.construction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Disposable confidence test for persistent worker needs.
 *
 * It never reads or mutates a Player save.
 */
public final class SettlementWorkerNeedsSelfTest {

    private SettlementWorkerNeedsSelfTest() {
    }

    public static String run() {
        String stage = "definitions";
        try {
            Set<String> keys = new HashSet<String>();
            for (SettlementWorkerNeed need : SettlementWorkerNeed.values()) {
                require(keys.add(need.getKey()), "duplicate need key " + need.getKey());
                require(SettlementWorkerNeed.forKey(need.getKey()) == need,
                        "need lookup failed for " + need.getKey());
            }

            stage = "defaults";
            SettlementWorkerDefinition definition = SettlementWorkerDefinition.STARTER_SETTLER;
            SettlementWorkerState worker = new SettlementWorkerState(
                    1L,
                    definition.getKey(),
                    definition.getDisplayName(),
                    definition.getArrivalPlotX(),
                    definition.getArrivalPlotY(),
                    definition.getArrivalPlane());
            require(worker.getNeed(SettlementWorkerNeed.HUNGER) == 0,
                    "new worker hunger is not satisfied");
            require(worker.getNeed(SettlementWorkerNeed.THIRST) == 0,
                    "new worker thirst is not satisfied");
            require(worker.getNeed(SettlementWorkerNeed.ENERGY) == 100,
                    "new worker energy is not full");

            stage = "work-cost";
            worker.applyWorkCycleCost();
            require(worker.getNeed(SettlementWorkerNeed.HUNGER) == 4,
                    "work hunger cost mismatch");
            require(worker.getNeed(SettlementWorkerNeed.THIRST) == 5,
                    "work thirst cost mismatch");
            require(worker.getNeed(SettlementWorkerNeed.ENERGY) == 94,
                    "work energy cost mismatch");

            stage = "critical-recovery";
            worker.setNeed(SettlementWorkerNeed.HUNGER, SettlementWorkerState.CRITICAL_HUNGER);
            worker.setNeed(SettlementWorkerNeed.THIRST, SettlementWorkerState.CRITICAL_THIRST);
            worker.setNeed(SettlementWorkerNeed.ENERGY, SettlementWorkerState.CRITICAL_ENERGY);
            require(worker.needsFood(), "critical hunger not detected");
            require(worker.needsWater(), "critical thirst not detected");
            require(worker.needsRest(), "critical energy not detected");
            worker.recoverFromMeal();
            worker.recoverFromDrink();
            worker.recoverFromRest();
            require(!worker.needsFood(), "meal did not recover hunger");
            require(!worker.needsWater(), "drink did not recover thirst");
            require(!worker.needsRest(), "rest did not recover energy");

            stage = "clamp";
            worker.setNeed(SettlementWorkerNeed.HUNGER, 999);
            worker.setNeed(SettlementWorkerNeed.ENERGY, -10);
            require(worker.getNeed(SettlementWorkerNeed.HUNGER) == 100,
                    "upper clamp failed");
            require(worker.getNeed(SettlementWorkerNeed.ENERGY) == 0,
                    "lower clamp failed");

            stage = "serialize";
            byte[] encoded = serialize(worker);
            SettlementWorkerState restored = deserialize(encoded);
            restored.normalize(definition);
            require(restored.getNeed(SettlementWorkerNeed.HUNGER) == 100,
                    "hunger did not persist");
            require(restored.getNeed(SettlementWorkerNeed.ENERGY) == 0,
                    "energy did not persist");
            require(restored.getNeedsSummary().contains("Thirst="),
                    "needs summary missing thirst");

            return "PASS: defaults + work cost + critical recovery + clamp + serialization.";
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
