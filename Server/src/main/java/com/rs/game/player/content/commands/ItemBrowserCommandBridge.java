package com.rs.game.player.content.commands;

import com.rs.cache.loaders.ItemDefinitions;
import com.rs.cache.loaders.NPCDefinitions;
import com.rs.cache.loaders.ObjectDefinitions;
import com.rs.game.World;
import com.rs.game.WorldObject;
import com.rs.game.WorldTile;
import com.rs.game.item.Item;
import com.rs.game.npc.NPC;
import com.rs.game.player.CombatDefinitions;
import com.rs.game.player.Player;
import com.rs.game.player.Skills;
import com.rs.game.player.content.construction.SettlementBuildPiece;
import com.rs.game.player.content.construction.SettlementBundle12FinalCheck;
import com.rs.game.player.content.construction.SettlementBundle13FinalCheck;
import com.rs.game.player.content.construction.SettlementBundle14FinalGate;
import com.rs.game.player.content.construction.SettlementBundle15FinalCheck;
import com.rs.game.player.content.construction.SettlementBundle22FinalGate;
import com.rs.game.player.content.construction.SettlementInstance;
import com.rs.game.player.content.construction.SettlementHousingCheck;
import com.rs.game.player.content.construction.SettlementObjectCatalog;
import com.rs.game.player.content.construction.SettlementObjectProbe;
import com.rs.game.player.content.construction.SettlementPlacedPiece;
import com.rs.game.player.content.construction.SettlementPopulationCheck;
import com.rs.game.player.content.construction.SettlementProcessingRecipe;
import com.rs.game.player.content.construction.SettlementProcessingSelfTest;
import com.rs.game.player.content.construction.SettlementProcessingTransaction;
import com.rs.game.player.content.construction.SettlementResource;
import com.rs.game.player.content.construction.SettlementResourceNode;
import com.rs.game.player.content.construction.SettlementResourceSelfTest;
import com.rs.game.player.content.construction.SettlementShelterSelfTest;
import com.rs.game.player.content.construction.SettlementStateAudit;
import com.rs.game.player.content.construction.SettlementStateSelfTest;
import com.rs.game.player.content.construction.SettlementWorkerArrivalCheck;
import com.rs.game.player.content.construction.SettlementWorkerJob;
import com.rs.game.player.content.construction.SettlementWorkerJobsSelfTest;
import com.rs.game.player.content.construction.SettlementWorkerNeed;
import com.rs.game.player.content.construction.SettlementWorkerNeedsSelfTest;
import com.rs.game.player.content.construction.SettlementWorkerProgressionSelfTest;
import com.rs.game.player.content.construction.SettlementWorkerRolePreset;
import com.rs.game.player.content.construction.SettlementWorkerSelfTest;
import com.rs.game.player.content.construction.SettlementWorkerState;

/**
 * Owner-only server authority bridge for Client Console Item Browser,
 * development settings, and Dev Mode live placement/manipulation actions.
 */
public final class ItemBrowserCommandBridge {

    private ItemBrowserCommandBridge() {
    }

    public static boolean process(Player player, String[] cmd) {
        if (player == null) {
            return false;
        }
        if (!player.hasStarted() || !player.isRunning() || player.hasFinished()) {
            return true;
        }
        if (player.getRights() < 2) {
            player.getPackets().sendGameMessage("Admin+ only!");
            return true;
        }
        if (cmd != null && cmd.length >= 3 && "backpack".equalsIgnoreCase(cmd[1])
                && "open".equalsIgnoreCase(cmd[2])) {
            player.getInventory().getBackpack().open();
            return true;
        }
        if (cmd != null && cmd.length >= 2 && "settings".equalsIgnoreCase(cmd[1])) {
            return processSettings(player, cmd);
        }
        if (cmd != null && cmd.length >= 2 && "constructioncamera".equalsIgnoreCase(cmd[1])) {
            return processConstructionCamera(player, cmd);
        }
        if (cmd != null && cmd.length >= 2 && "settlement".equalsIgnoreCase(cmd[1])) {
            return processSettlement(player, cmd);
        }
        if (cmd != null && cmd.length >= 2 && "objectprobe".equalsIgnoreCase(cmd[1])) {
            return processObjectProbe(player, cmd);
        }
        if (cmd != null && cmd.length >= 2 && "objectlab".equalsIgnoreCase(cmd[1])) {
            return processObjectLab(player, cmd);
        }
        if (cmd != null && cmd.length >= 2 && "devspawn".equalsIgnoreCase(cmd[1])) {
            return processDevSpawn(player, cmd);
        }
        if (cmd != null && cmd.length >= 2 && "devedit".equalsIgnoreCase(cmd[1])) {
            return processDevEdit(player, cmd);
        }
        if (cmd == null || cmd.length < 4) {
            player.getPackets().sendGameMessage(
                    "Use: ::itembrowser <inventory|bank> <itemId> <amount> or ::itembrowser backpack open");
            return true;
        }

        final boolean bank;
        if ("inventory".equalsIgnoreCase(cmd[1])) {
            bank = false;
        } else if ("bank".equalsIgnoreCase(cmd[1])) {
            bank = true;
        } else {
            player.getPackets().sendGameMessage(
                    "Use: ::itembrowser <inventory|bank> <itemId> <amount> or ::itembrowser backpack open");
            return true;
        }

        final int itemId;
        final int amount;
        try {
            itemId = Integer.parseInt(cmd[2]);
            amount = Integer.parseInt(cmd[3]);
        } catch (NumberFormatException ex) {
            player.getPackets().sendGameMessage("Item id and amount must be whole numbers.");
            return true;
        }

        if (itemId < 0 || amount <= 0) {
            player.getPackets().sendGameMessage("Item id must be valid and amount must be greater than zero.");
            return true;
        }

        ItemDefinitions definition = ItemDefinitions.getItemDefinitions(itemId);
        if (definition == null || !definition.isLoaded() || definition.name == null
                || definition.name.trim().length() == 0 || "null".equalsIgnoreCase(definition.name.trim())) {
            player.getPackets().sendGameMessage("Unable to spawn unknown item id " + itemId + ".");
            return true;
        }

        boolean added = bank
                ? player.getBank().addItem(itemId, amount, true)
                : player.getInventory().addItem(itemId, amount);
        if (added) {
            player.getPackets().sendGameMessage("Spawned " + amount + " x " + definition.name
                    + (bank ? " to your bank." : " to your inventory."));
        } else {
            player.getPackets().sendGameMessage(bank
                    ? "Unable to add that item to your bank (bank may be full)."
                    : "Unable to add that item to your inventory (inventory may be full or restricted)." );
        }
        return true;
    }

    private static boolean processObjectLab(Player player, String[] cmd) {
        if (cmd == null || cmd.length < 3) {
            player.getPackets().sendGameMessage("Use: ::itembrowser objectlab <save|list> ...");
            return true;
        }
        String operation = cmd[2].toLowerCase();
        if ("list".equals(operation)) {
            for (String line : SettlementObjectCatalog.listRecent(12))
                player.getPackets().sendGameMessage(line);
            return true;
        }
        if ("save".equals(operation)) {
            if (cmd.length < 11) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser objectlab save <id> <type> <rotation> <tag> <x> <y> <plane> <notes64>");
                return true;
            }
            final int id, type, rotation, x, y, plane;
            try {
                id = Integer.parseInt(cmd[3]);
                type = Integer.parseInt(cmd[4]);
                rotation = Integer.parseInt(cmd[5]);
                x = Integer.parseInt(cmd[7]);
                y = Integer.parseInt(cmd[8]);
                plane = Integer.parseInt(cmd[9]);
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage(
                        "Object Lab id/type/rotation/source tile values must be whole numbers.");
                return true;
            }
            String notes = "";
            if (!"-".equals(cmd[10])) {
                try {
                    notes = new String(java.util.Base64.getUrlDecoder().decode(cmd[10]),
                            java.nio.charset.StandardCharsets.UTF_8);
                } catch (IllegalArgumentException ex) {
                    player.getPackets().sendGameMessage("Object Lab notes payload is invalid.");
                    return true;
                }
            }
            player.getPackets().sendGameMessage(
                    SettlementObjectCatalog.save(id, type, rotation, cmd[6], x, y, plane, notes));
            return true;
        }
        player.getPackets().sendGameMessage("Use: ::itembrowser objectlab <save|list> ...");
        return true;
    }

    private static boolean processObjectProbe(Player player, String[] cmd) {
        if (cmd == null || cmd.length < 3) {
            player.getPackets().sendGameMessage(
                    "Use: ::itembrowser objectprobe <tile|nearby|logtile|lognearby>");
            return true;
        }

        String operation = cmd[2].toLowerCase();
        int radius;
        boolean log;
        if ("tile".equals(operation)) {
            radius = 0;
            log = false;
        } else if ("nearby".equals(operation)) {
            radius = 1;
            log = false;
        } else if ("logtile".equals(operation)) {
            radius = 0;
            log = true;
        } else if ("lognearby".equals(operation)) {
            radius = 1;
            log = true;
        } else {
            player.getPackets().sendGameMessage(
                    "Use: ::itembrowser objectprobe <tile|nearby|logtile|lognearby>");
            return true;
        }

        WorldTile center = new WorldTile(player.getX(), player.getY(), player.getPlane());
        if (log) {
            player.getPackets().sendGameMessage(
                    SettlementObjectProbe.appendToCatalog(center, radius));
            return true;
        }

        for (String line : SettlementObjectProbe.scan(center, radius)) {
            player.getPackets().sendGameMessage(line);
        }
        return true;
    }

    private static boolean processConstructionCamera(Player player, String[] cmd) {
        if (cmd != null && cmd.length >= 4 && "debug".equalsIgnoreCase(cmd[2])) {
            StringBuilder state = new StringBuilder();
            for (int index = 3; index < cmd.length; index++) {
                if (state.length() > 0) {
                    state.append(' ');
                }
                state.append(cmd[index]);
            }
            System.out.println("[ConstructionBuildCamera] " + state.toString().replace('_', ' '));
            return true;
        }
        player.getPackets().sendGameMessage(
                "Construction camera is client-owned. Legacy Orb camera commands remain disabled.");
        return true;
    }

    private static boolean processSettlement(Player player, String[] cmd) {
        if (cmd == null || cmd.length < 3) {
            player.getPackets().sendGameMessage(
                    "Use: ::itembrowser settlement <enter|exit|status|list|resources|storagereset|storageset|resourceselftest|processing|process|processingselftest|shelter|shelterselftest|bundle13check|workers|workerselftest|workercheck|workerjobs|workerjob|workerjobsall|workerjobselftest|workerai|workerneeds|workerneed|workerneedsreset|workerneedselftest|workerprogress|workerprogressselftest|bundle14gatebaseline|bundle14gatecheck|bundle15selftest|bundle15baseline|bundle15check|bundle22selftest|bundle22baseline|bundle22check|workerallstatus|population|populationrecruit|populationselftest|populationcheck|audit|selftest|finalcheck>");
            return true;
        }

        String operation = cmd[2].toLowerCase();
        if ("enter".equals(operation)) {
            player.getPackets().sendGameMessage(SettlementInstance.enter(player));
            return true;
        }

        SettlementInstance active = SettlementInstance.getActive(player);
        if ("exit".equals(operation)) {
            if (active == null) {
                player.getPackets().sendGameMessage("You are not inside an active settlement.");
            } else {
                active.leaveToReturn();
                player.getPackets().sendGameMessage("You leave your Construction settlement.");
            }
            return true;
        }

        if ("erasetile".equals(operation)) {
            if (active == null) {
                player.getPackets().sendGameMessage("You are not inside an active settlement.");
                return true;
            }
            if (cmd.length < 6) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement erasetile <worldX> <worldY> <plane>");
                return true;
            }
            final int worldX, worldY, plane;
            try {
                worldX = Integer.parseInt(cmd[3]);
                worldY = Integer.parseInt(cmd[4]);
                plane = Integer.parseInt(cmd[5]);
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage("Erase tile coordinates must be whole numbers.");
                return true;
            }
            player.getPackets().sendGameMessage(
                    active.eraseDevelopmentTile(new WorldTile(worldX, worldY, plane)));
            return true;
        }

        if ("undo".equals(operation)) {
            player.getPackets().sendGameMessage(active == null
                    ? "You are not inside an active settlement."
                    : active.undoLastBuild());
            return true;
        }

        if ("clearbuilds".equals(operation)) {
            player.getPackets().sendGameMessage(active == null
                    ? "You are not inside an active settlement."
                    : active.clearPlayerBuilds());
            return true;
        }

        if ("status".equals(operation)) {
            player.getPackets().sendGameMessage(
                    "Settlement: " + player.getSettlementState().size() + " saved piece(s), runtime "
                            + (active == null ? "inactive." : (active.isLoaded() ? "loaded." : "loading.")));
            return true;
        }

        if ("list".equals(operation)) {
            java.util.List<SettlementPlacedPiece> pieces = player.getSettlementState().snapshotPieces();
            player.getPackets().sendGameMessage(
                    "Settlement saved pieces: " + pieces.size() + ".");
            int shown = 0;
            for (SettlementPlacedPiece piece : pieces) {
                if (piece == null) {
                    continue;
                }
                if (shown >= 20) {
                    player.getPackets().sendGameMessage(
                            "... " + (pieces.size() - shown) + " more piece(s) not shown.");
                    break;
                }
                SettlementBuildPiece definition = SettlementBuildPiece.forKey(piece.getDefinitionKey());
                String name = definition == null ? piece.getDefinitionKey() : definition.getDisplayName();
                player.getPackets().sendGameMessage(
                        "#" + piece.getPieceId() + " " + name
                                + " plot=" + piece.getPlotX() + "," + piece.getPlotY()
                                + "," + piece.getPlane() + " rot=" + piece.getRotation());
                shown++;
            }
            return true;
        }

        if ("resources".equals(operation)) {
            player.getPackets().sendGameMessage(
                    "Settlement storage: " + player.getSettlementState().getResourceSummary());
            return true;
        }

        if ("storagereset".equals(operation)) {
            for (SettlementResource resource : SettlementResource.values()) {
                long current = player.getSettlementState().getResourceAmount(resource);
                if (current > 0L) {
                    player.getSettlementState().removeResource(resource, current);
                }
            }
            player.getPackets().sendGameMessage(
                    "Settlement storage reset: " + player.getSettlementState().getResourceSummary());
            return true;
        }

        if ("storageset".equals(operation)) {
            if (cmd.length < 5) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement storageset <resourceKey> <amount>");
                return true;
            }
            SettlementResource resource = SettlementResource.forKey(cmd[3].toLowerCase());
            if (resource == null) {
                player.getPackets().sendGameMessage(
                        "Unknown settlement resource: " + cmd[3] + ".");
                return true;
            }
            final long targetAmount;
            try {
                targetAmount = Long.parseLong(cmd[4]);
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage("Storage amount must be a whole number.");
                return true;
            }
            int capacity = player.getSettlementState().getStorageCapacity(resource);
            if (targetAmount < 0L || targetAmount > capacity) {
                player.getPackets().sendGameMessage(
                        resource.getDisplayName() + " storage amount must be between 0 and "
                                + capacity + ".");
                return true;
            }

            long current = player.getSettlementState().getResourceAmount(resource);
            if (current > targetAmount) {
                player.getSettlementState().removeResource(resource, current - targetAmount);
            } else if (current < targetAmount) {
                player.getSettlementState().addResource(resource, targetAmount - current);
            }

            long actual = player.getSettlementState().getResourceAmount(resource);
            player.getPackets().sendGameMessage(
                    "Settlement storage set: " + resource.getDisplayName() + "="
                            + actual + "/" + capacity + " | "
                            + player.getSettlementState().getResourceSummary());
            return true;
        }

        if ("resourceselftest".equals(operation)) {
            String result = SettlementResourceSelfTest.run();
            System.out.println("[SettlementResourceSelfTest] " + result);
            player.getPackets().sendGameMessage("Bundle 1.3 resource self-test: " + result);
            return true;
        }

        if ("processing".equals(operation)) {
            player.getPackets().sendGameMessage(
                    "Settlement processing | storage="
                            + player.getSettlementState().getResourceSummary());
            for (SettlementProcessingRecipe recipe : SettlementProcessingRecipe.values()) {
                player.getPackets().sendGameMessage(
                        recipe.getKey() + " | " + recipe.getSummary());
            }
            return true;
        }

        if ("process".equals(operation)) {
            if (cmd.length < 4) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement process <recipeKey> [cycles]");
                return true;
            }
            SettlementProcessingRecipe recipe =
                    SettlementProcessingRecipe.forKey(cmd[3].toLowerCase());
            if (recipe == null) {
                player.getPackets().sendGameMessage(
                        "Unknown settlement processing recipe: " + cmd[3] + ".");
                return true;
            }
            int cycles = 1;
            if (cmd.length >= 5) {
                try {
                    cycles = Integer.parseInt(cmd[4]);
                } catch (NumberFormatException ex) {
                    player.getPackets().sendGameMessage(
                            "Processing cycles must be a whole number.");
                    return true;
                }
            }
            SettlementProcessingTransaction.Result result =
                    SettlementProcessingTransaction.apply(
                            player.getSettlementState(), recipe, cycles);
            player.getPackets().sendGameMessage(
                    (result.isSuccess() ? "Processing complete: " : "Processing blocked: ")
                            + result.getSummary());
            if (result.isSuccess()) {
                player.getPackets().sendGameMessage(
                        "Settlement storage: "
                                + player.getSettlementState().getResourceSummary());
            }
            return true;
        }

        if ("processingselftest".equals(operation)) {
            String result = SettlementProcessingSelfTest.run();
            System.out.println("[SettlementProcessingSelfTest] " + result);
            player.getPackets().sendGameMessage(
                    "Phase 3 processing self-test: " + result);
            return true;
        }

        if ("shelter".equals(operation)) {
            boolean newlyCompleted = player.getSettlementState().tryCompleteStarterShelterMilestone();
            if (newlyCompleted) {
                player.getPackets().sendGameMessage(
                        "<col=3CB371>Starter shelter milestone complete!</col>");
            }
            player.getPackets().sendGameMessage(
                    "Starter shelter: " + player.getSettlementState().getStarterShelterStatus());
            return true;
        }

        if ("shelterselftest".equals(operation)) {
            String result = SettlementShelterSelfTest.run();
            System.out.println("[SettlementShelterSelfTest] " + result);
            player.getPackets().sendGameMessage("Starter shelter self-test: " + result);
            return true;
        }

        if ("bundle13check".equals(operation)) {
            String result = SettlementBundle13FinalCheck.run(player);
            System.out.println("[SettlementBundle13FinalCheck] " + result);
            player.getPackets().sendGameMessage("Bundle 1.3 final check: " + result);
            return true;
        }

        if ("workers".equals(operation)) {
            java.util.List<SettlementWorkerState> workers =
                    player.getSettlementState().snapshotWorkers();
            SettlementInstance live = SettlementInstance.getActive(player);
            player.getPackets().sendGameMessage(
                    "Settlement workers: saved=" + workers.size()
                            + ", runtime=" + (live == null ? 0 : live.getActiveWorkerCount()) + ".");
            for (SettlementWorkerState worker : workers) {
                if (worker == null) {
                    continue;
                }
                player.getPackets().sendGameMessage(
                        "Worker #" + worker.getWorkerId() + " " + worker.getName()
                                + " [" + worker.getDefinitionKey() + "] home="
                                + worker.getHomePlotX() + "," + worker.getHomePlotY()
                                + "," + worker.getHomePlane());
                player.getPackets().sendGameMessage(
                        "Paused=" + (worker.isPaused() ? "YES" : "NO")
                                + " | Allowed Jobs: " + worker.getAllowedJobsSummary());
                player.getPackets().sendGameMessage(
                        "Needs: " + worker.getNeedsSummary());
                player.getPackets().sendGameMessage(
                        "Skills: " + worker.getSkillsSummary());
            }
            return true;
        }

        if ("workerallstatus".equals(operation)) {
            java.util.List<SettlementWorkerState> workers =
                    player.getSettlementState().snapshotWorkers();
            player.getPackets().sendGameMessage(
                    "All Workers: saved=" + workers.size()
                            + ", runtime=" + (active == null ? 0 : active.getActiveWorkerCount())
                            + " | storage=" + player.getSettlementState().getResourceSummary());
            for (SettlementWorkerState worker : workers) {
                if (worker == null) {
                    continue;
                }
                SettlementWorkerRolePreset matchingPreset =
                        SettlementWorkerRolePreset.findMatching(worker);
                player.getPackets().sendGameMessage(
                        "Worker #" + worker.getWorkerId() + " " + worker.getName()
                                + " | Paused=" + (worker.isPaused() ? "YES" : "NO")
                                + " | Preset="
                                + (matchingPreset == null ? "Custom" : matchingPreset.getDisplayName())
                                + " | Jobs: " + worker.getAllowedJobsSummary());
                player.getPackets().sendGameMessage(
                        "Needs: " + worker.getNeedsSummary()
                                + " | Skills: " + worker.getSkillsSummary());
                if (active != null && active.isLoaded()) {
                    player.getPackets().sendGameMessage(
                            active.getWorkerAiSummary(worker.getWorkerId()));
                }
            }
            return true;
        }

        if ("workerselftest".equals(operation)) {
            String result = SettlementWorkerSelfTest.run();
            System.out.println("[SettlementWorkerSelfTest] " + result);
            player.getPackets().sendGameMessage("Worker self-test: " + result);
            return true;
        }

        if ("population".equals(operation)) {
            player.getPackets().sendGameMessage(
                    "Settlement population: "
                            + player.getSettlementState().getPopulationSummary() + ".");
            return true;
        }

        if ("populationrecruit".equals(operation)) {
            if (active == null || !active.isLoaded()) {
                player.getPackets().sendGameMessage(
                        "Enter the loaded settlement before recruiting another worker.");
            } else {
                player.getPackets().sendGameMessage(active.recruitAdditionalWorker());
            }
            return true;
        }

        if ("housing".equals(operation)) {
            player.getPackets().sendGameMessage(
                    "Settlement housing: " + player.getSettlementState().getHousingSummary() + ".");
            return true;
        }

        if ("housingbedadd".equals(operation)) {
            boolean added = player.getSettlementState().addHousingBed();
            player.getPackets().sendGameMessage(
                    (added ? "Added one persistent housing bed capacity unit. "
                            : "Unable to add housing bed capacity. ")
                            + player.getSettlementState().getHousingSummary() + ".");
            return true;
        }

        if ("housingbedremove".equals(operation)) {
            boolean removed = player.getSettlementState().removeHousingBed();
            player.getPackets().sendGameMessage(
                    (removed ? "Removed one persistent housing bed capacity unit. "
                            : "Unable to remove housing bed capacity (it may be occupied). ")
                            + player.getSettlementState().getHousingSummary() + ".");
            return true;
        }

        if ("housing23selftest".equals(operation)) {
            String result = SettlementHousingCheck.runSelfTest();
            System.out.println("[SettlementHousingCheck] " + result);
            player.getPackets().sendGameMessage("Bundle 2.3 housing self-test: " + result);
            return true;
        }

        if ("housing23check".equals(operation)) {
            String result = SettlementHousingCheck.run(player);
            System.out.println("[SettlementHousingCheck] " + result);
            player.getPackets().sendGameMessage("Bundle 2.3 housing check: " + result);
            return true;
        }

        if ("housing23baseline".equals(operation)) {
            String result = SettlementHousingCheck.capture(player);
            System.out.println("[SettlementHousingCheck] " + result);
            player.getPackets().sendGameMessage("Bundle 2.3 housing gate: " + result);
            return true;
        }

        if ("housing23baselinecheck".equals(operation)) {
            String result = SettlementHousingCheck.check(player);
            System.out.println("[SettlementHousingCheck] " + result);
            player.getPackets().sendGameMessage("Bundle 2.3 housing gate: " + result);
            return true;
        }

        if ("populationselftest".equals(operation)) {
            String result = SettlementPopulationCheck.runSelfTest();
            System.out.println("[SettlementPopulationCheck] " + result);
            player.getPackets().sendGameMessage("Population self-test: " + result);
            return true;
        }

        if ("populationcheck".equals(operation)) {
            String result = SettlementPopulationCheck.run(player);
            System.out.println("[SettlementPopulationCheck] " + result);
            player.getPackets().sendGameMessage("Population check: " + result);
            return true;
        }

        if ("workercheck".equals(operation)) {
            String result = SettlementWorkerArrivalCheck.run(player);
            System.out.println("[SettlementWorkerArrivalCheck] " + result);
            player.getPackets().sendGameMessage("Worker arrival check: " + result);
            return true;
        }

        if ("workerselectionset".equals(operation)) {
            if (cmd.length < 4) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerselectionset <runtimeNpcIndexCsv|none> [self|noself]");
                return true;
            }
            if (active == null || !active.isLoaded()) {
                player.getPackets().sendGameMessage(
                        "Enter the loaded settlement before committing a radial selection.");
                return true;
            }
            boolean playerSelected = cmd.length >= 5 && "self".equalsIgnoreCase(cmd[4]);
            int[] npcIndexes;
            if ("none".equalsIgnoreCase(cmd[3])) {
                npcIndexes = new int[0];
            } else {
                npcIndexes = parseRuntimeNpcIndexes(cmd[3]);
                if (npcIndexes == null) {
                    player.getPackets().sendGameMessage(
                            "Radial worker selection must be a comma-separated list of valid runtime NPC indexes.");
                    return true;
                }
            }
            player.getPackets().sendGameMessage(
                    active.setRuntimeSelection(npcIndexes, playerSelected));
            return true;
        }

        if ("workerselectionclear".equals(operation)) {
            if (active != null) {
                active.clearRuntimeWorkerSelection();
            }
            player.getPackets().sendGameMessage("Radial worker selection cleared.");
            return true;
        }

        if ("workerselectionmove".equals(operation)) {
            if (cmd.length < 6) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerselectionmove <worldX> <worldY> <plane>");
                return true;
            }
            if (active == null || !active.isLoaded()) {
                player.getPackets().sendGameMessage(
                        "Enter the loaded settlement before using RTS move orders.");
                return true;
            }
            final int worldX;
            final int worldY;
            final int plane;
            try {
                worldX = Integer.parseInt(cmd[3]);
                worldY = Integer.parseInt(cmd[4]);
                plane = Integer.parseInt(cmd[5]);
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage("RTS move target coordinates are invalid.");
                return true;
            }
            player.getPackets().sendGameMessage(
                    active.orderRuntimeSelectionMove(new WorldTile(worldX, worldY, plane)));
            return true;
        }

        if ("workerselectiongather".equals(operation)) {
            if (cmd.length < 7) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerselectiongather [object|npc] <id> <worldX> <worldY> <plane>");
                return true;
            }
            if (active == null || !active.isLoaded()) {
                player.getPackets().sendGameMessage(
                        "Enter the loaded settlement before using RTS gather orders.");
                return true;
            }

            SettlementResourceNode.SourceKind sourceKind = SettlementResourceNode.SourceKind.OBJECT;
            int valueIndex = 3;
            if ("object".equalsIgnoreCase(cmd[3]) || "npc".equalsIgnoreCase(cmd[3])) {
                sourceKind = "npc".equalsIgnoreCase(cmd[3])
                        ? SettlementResourceNode.SourceKind.NPC
                        : SettlementResourceNode.SourceKind.OBJECT;
                valueIndex = 4;
            }
            if (cmd.length <= valueIndex + 3) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerselectiongather [object|npc] <id> <worldX> <worldY> <plane>");
                return true;
            }

            final int runtimeId;
            final int worldX;
            final int worldY;
            final int plane;
            try {
                runtimeId = Integer.parseInt(cmd[valueIndex]);
                worldX = Integer.parseInt(cmd[valueIndex + 1]);
                worldY = Integer.parseInt(cmd[valueIndex + 2]);
                plane = Integer.parseInt(cmd[valueIndex + 3]);
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage("RTS gather target is invalid.");
                return true;
            }
            player.getPackets().sendGameMessage(
                    active.orderRuntimeSelectionGather(
                            sourceKind, runtimeId, worldX, worldY, plane));
            return true;
        }

        if ("workerselectionprocess".equals(operation)) {
            if (cmd.length < 7) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerselectionprocess <objectId> <worldX> <worldY> <plane>");
                return true;
            }
            if (active == null || !active.isLoaded()) {
                player.getPackets().sendGameMessage(
                        "Enter the loaded settlement before using RTS processing orders.");
                return true;
            }
            try {
                int objectId = Integer.parseInt(cmd[3]);
                int worldX = Integer.parseInt(cmd[4]);
                int worldY = Integer.parseInt(cmd[5]);
                int plane = Integer.parseInt(cmd[6]);
                player.getPackets().sendGameMessage(
                        active.orderRuntimeSelectionProcessWood(objectId, worldX, worldY, plane));
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage("RTS processing target is invalid.");
            }
            return true;
        }

        if ("workerselectionstatus".equals(operation)) {
            if (active == null || !active.isLoaded()) {
                player.getPackets().sendGameMessage(
                        "Enter the loaded settlement before using radial worker commands.");
                return true;
            }
            java.util.List<SettlementWorkerState> selected =
                    active.snapshotRuntimeWorkerSelection();
            boolean selfSelected = active.isRuntimePlayerSelected();
            if (selected.isEmpty() && !selfSelected) {
                player.getPackets().sendGameMessage(
                        "No server-owned radial selection is active. Drag-select units again.");
                return true;
            }
            player.getPackets().sendGameMessage(
                    "Radial selection: "
                            + (selected.isEmpty() ? "" : formatWorkerIds(selected))
                            + (selfSelected ? (selected.isEmpty() ? "self" : " + self") : "")
                            + ".");
            for (SettlementWorkerState worker : selected) {
                SettlementWorkerRolePreset matchingPreset =
                        SettlementWorkerRolePreset.findMatching(worker);
                player.getPackets().sendGameMessage(
                        "Worker #" + worker.getWorkerId()
                                + " Preset="
                                + (matchingPreset == null ? "Custom" : matchingPreset.getDisplayName())
                                + " | Paused=" + (worker.isPaused() ? "YES" : "NO")
                                + " | Jobs: " + worker.getAllowedJobsSummary());
            }
            return true;
        }

        if ("workerselectionpause".equals(operation)) {
            if (cmd.length < 4) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerselectionpause <on|off>");
                return true;
            }
            if (active == null || !active.isLoaded()) {
                player.getPackets().sendGameMessage(
                        "Enter the loaded settlement before using radial worker commands.");
                return true;
            }
            String state = cmd[3].toLowerCase();
            if (!"on".equals(state) && !"off".equals(state)) {
                player.getPackets().sendGameMessage(
                        "Selected worker pause state must be on or off.");
                return true;
            }
            java.util.List<SettlementWorkerState> selected =
                    active.snapshotRuntimeWorkerSelection();
            if (selected.isEmpty()) {
                player.getPackets().sendGameMessage(
                        "No server-owned radial worker selection is active. Drag-select workers again.");
                return true;
            }
            boolean paused = "on".equals(state);
            for (SettlementWorkerState worker : selected) {
                worker.setPaused(paused);
            }
            player.getPackets().sendGameMessage(
                    "Radial selection " + formatWorkerIds(selected)
                            + " Paused=" + (paused ? "YES" : "NO")
                            + ". Allowed Jobs unchanged.");
            return true;
        }

        if ("workerselectionpreset".equals(operation)) {
            if (cmd.length < 4) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerselectionpreset "
                                + "<lumberjack|forager|stone-miner|ore-miner|hauler-only|idle>");
                return true;
            }
            if (active == null || !active.isLoaded()) {
                player.getPackets().sendGameMessage(
                        "Enter the loaded settlement before using radial worker commands.");
                return true;
            }
            SettlementWorkerRolePreset preset =
                    SettlementWorkerRolePreset.forKey(cmd[3]);
            if (preset == null) {
                player.getPackets().sendGameMessage(
                        "Unknown worker preset: " + cmd[3] + ".");
                return true;
            }
            java.util.List<SettlementWorkerState> selected =
                    active.snapshotRuntimeWorkerSelection();
            if (selected.isEmpty()) {
                player.getPackets().sendGameMessage(
                        "No server-owned radial worker selection is active. Drag-select workers again.");
                return true;
            }
            for (SettlementWorkerState worker : selected) {
                preset.applyTo(worker);
            }
            player.getPackets().sendGameMessage(
                    "Applied " + preset.getDisplayName() + " to radial selection "
                            + formatWorkerIds(selected)
                            + ". Pause/Needs/Progression unchanged.");
            return true;
        }

        if ("workerselectionjob".equals(operation)) {
            if (cmd.length < 5) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerselectionjob <job-key> <on|off>");
                return true;
            }
            if (active == null || !active.isLoaded()) {
                player.getPackets().sendGameMessage(
                        "Enter the loaded settlement before using radial worker commands.");
                return true;
            }
            SettlementWorkerJob job = SettlementWorkerJob.forKey(cmd[3].toLowerCase());
            if (job == null) {
                player.getPackets().sendGameMessage(
                        "Unknown worker job key: " + cmd[3] + ".");
                return true;
            }
            String state = cmd[4].toLowerCase();
            if (!"on".equals(state) && !"off".equals(state)) {
                player.getPackets().sendGameMessage(
                        "Selected worker job state must be on or off.");
                return true;
            }
            java.util.List<SettlementWorkerState> selected =
                    active.snapshotRuntimeWorkerSelection();
            if (selected.isEmpty()) {
                player.getPackets().sendGameMessage(
                        "No server-owned radial worker selection is active. Drag-select workers again.");
                return true;
            }
            boolean allowed = "on".equals(state);
            for (SettlementWorkerState worker : selected) {
                worker.setJobAllowed(job, allowed);
            }
            player.getPackets().sendGameMessage(
                    "Radial selection " + formatWorkerIds(selected) + " "
                            + job.getDisplayName() + "=" + (allowed ? "ON" : "OFF") + ".");
            return true;
        }

        if ("workerselectionjobsall".equals(operation)) {
            if (cmd.length < 4) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerselectionjobsall <on|off>");
                return true;
            }
            if (active == null || !active.isLoaded()) {
                player.getPackets().sendGameMessage(
                        "Enter the loaded settlement before using radial worker commands.");
                return true;
            }
            String state = cmd[3].toLowerCase();
            if (!"on".equals(state) && !"off".equals(state)) {
                player.getPackets().sendGameMessage(
                        "Selected worker jobs state must be on or off.");
                return true;
            }
            java.util.List<SettlementWorkerState> selected =
                    active.snapshotRuntimeWorkerSelection();
            if (selected.isEmpty()) {
                player.getPackets().sendGameMessage(
                        "No server-owned radial worker selection is active. Drag-select workers again.");
                return true;
            }
            boolean allowed = "on".equals(state);
            for (SettlementWorkerState worker : selected) {
                for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
                    worker.setJobAllowed(job, allowed);
                }
            }
            player.getPackets().sendGameMessage(
                    "Radial selection " + formatWorkerIds(selected)
                            + " Allowed Jobs=" + (allowed ? "ALL ON" : "ALL OFF") + ".");
            return true;
        }

        if ("workerselectionjobsreplace".equals(operation)) {
            if (cmd.length < 4) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerselectionjobsreplace <job-key-csv|none>");
                return true;
            }
            if (active == null || !active.isLoaded()) {
                player.getPackets().sendGameMessage(
                        "Enter the loaded settlement before changing selected worker jobs.");
                return true;
            }
            java.util.Set<SettlementWorkerJob> jobs = parseWorkerJobSet(cmd[3]);
            if (jobs == null) {
                player.getPackets().sendGameMessage("Allowed Jobs list contains an unknown job key.");
                return true;
            }
            player.getPackets().sendGameMessage(
                    active.replaceRuntimeSelectionAllowedJobs(jobs));
            return true;
        }

        if ("workernpcjobsreplace".equals(operation)) {
            if (cmd.length < 5) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workernpcjobsreplace <runtimeNpcIndex> <job-key-csv|none>");
                return true;
            }
            if (active == null || !active.isLoaded()) {
                player.getPackets().sendGameMessage(
                        "Enter the loaded settlement before changing worker jobs.");
                return true;
            }
            try {
                int runtimeNpcIndex = Integer.parseInt(cmd[3]);
                java.util.Set<SettlementWorkerJob> jobs = parseWorkerJobSet(cmd[4]);
                if (jobs == null) {
                    player.getPackets().sendGameMessage("Allowed Jobs list contains an unknown job key.");
                    return true;
                }
                player.getPackets().sendGameMessage(
                        active.replaceRuntimeNpcAllowedJobs(runtimeNpcIndex, jobs));
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage("Runtime worker NPC index is invalid.");
            }
            return true;
        }

        if ("workerpause".equals(operation)) {
            boolean targeted = hasWorkerIdArgument(cmd, 3);
            int stateIndex = targeted ? 4 : 3;
            if (cmd.length <= stateIndex) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerpause [workerId] <on|off>");
                return true;
            }
            SettlementWorkerState worker = resolveSettlementWorker(player, cmd, 3);
            if (worker == null) {
                return true;
            }
            String state = cmd[stateIndex].toLowerCase();
            if (!"on".equals(state) && !"off".equals(state)) {
                player.getPackets().sendGameMessage(
                        "Worker pause state must be on or off.");
                return true;
            }
            boolean paused = "on".equals(state);
            worker.setPaused(paused);
            player.getPackets().sendGameMessage(
                    "Worker #" + worker.getWorkerId() + " Paused="
                            + (paused ? "YES" : "NO")
                            + " | Allowed Jobs unchanged: "
                            + worker.getAllowedJobsSummary());
            return true;
        }

        if ("workerpreset".equals(operation)) {
            boolean targeted = hasWorkerIdArgument(cmd, 3);
            int presetIndex = targeted ? 4 : 3;
            if (cmd.length <= presetIndex) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerpreset [workerId] "
                                + "<lumberjack|forager|stone-miner|ore-miner|hauler-only|idle>");
                return true;
            }
            SettlementWorkerState worker = resolveSettlementWorker(player, cmd, 3);
            if (worker == null) {
                return true;
            }
            SettlementWorkerRolePreset preset =
                    SettlementWorkerRolePreset.forKey(cmd[presetIndex]);
            if (preset == null) {
                player.getPackets().sendGameMessage(
                        "Unknown worker preset: " + cmd[presetIndex] + ".");
                return true;
            }
            preset.applyTo(worker);
            player.getPackets().sendGameMessage(
                    "Worker #" + worker.getWorkerId() + " Preset="
                            + preset.getDisplayName()
                            + " | Allowed Jobs: " + worker.getAllowedJobsSummary());
            return true;
        }

        if ("workerjobs".equals(operation)) {
            SettlementWorkerState worker = resolveSettlementWorker(player, cmd, 3);
            if (worker != null) {
                SettlementWorkerRolePreset matchingPreset =
                        SettlementWorkerRolePreset.findMatching(worker);
                player.getPackets().sendGameMessage(
                        "Worker #" + worker.getWorkerId() + " Preset="
                                + (matchingPreset == null ? "Custom" : matchingPreset.getDisplayName())
                                + " | Allowed Jobs: " + worker.getAllowedJobsSummary());
            }
            return true;
        }

        if ("workerjobselftest".equals(operation)) {
            String result = SettlementWorkerJobsSelfTest.run();
            System.out.println("[SettlementWorkerJobsSelfTest] " + result);
            player.getPackets().sendGameMessage("Allowed Jobs self-test: " + result);
            return true;
        }

        if ("workerai".equals(operation)) {
            SettlementWorkerState worker = resolveSettlementWorker(player, cmd, 3);
            if (worker == null) {
                return true;
            }
            if (active == null || !active.isLoaded()) {
                player.getPackets().sendGameMessage(
                        "Worker #" + worker.getWorkerId() + " runtime AI is inactive.");
                return true;
            }
            player.getPackets().sendGameMessage(active.getWorkerAiSummary(worker.getWorkerId()));
            player.getPackets().sendGameMessage(
                    "Settlement storage: " + player.getSettlementState().getResourceSummary());
            return true;
        }

        if ("workerneeds".equals(operation)) {
            SettlementWorkerState worker = resolveSettlementWorker(player, cmd, 3);
            if (worker != null) {
                player.getPackets().sendGameMessage(
                        "Worker #" + worker.getWorkerId() + " Needs: "
                                + worker.getNeedsSummary());
            }
            return true;
        }

        if ("workerneedselftest".equals(operation)) {
            String result = SettlementWorkerNeedsSelfTest.run();
            System.out.println("[SettlementWorkerNeedsSelfTest] " + result);
            player.getPackets().sendGameMessage("Worker Needs self-test: " + result);
            return true;
        }

        if ("workerneedsreset".equals(operation)) {
            SettlementWorkerState worker = resolveSettlementWorker(player, cmd, 3);
            if (worker != null) {
                worker.resetNeeds();
                player.getPackets().sendGameMessage(
                        "Worker #" + worker.getWorkerId() + " Needs reset: "
                                + worker.getNeedsSummary());
            }
            return true;
        }

        if ("workerneed".equals(operation)) {
            boolean targeted = hasWorkerIdArgument(cmd, 3);
            int needIndex = targeted ? 4 : 3;
            int valueIndex = targeted ? 5 : 4;
            if (cmd.length <= valueIndex) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerneed [workerId] <hunger|thirst|energy> <0-100>");
                return true;
            }
            SettlementWorkerState worker = resolveSettlementWorker(player, cmd, 3);
            if (worker == null) {
                return true;
            }
            SettlementWorkerNeed need = SettlementWorkerNeed.forKey(cmd[needIndex]);
            if (need == null) {
                player.getPackets().sendGameMessage("Unknown worker need: " + cmd[needIndex] + ".");
                return true;
            }
            final int value;
            try {
                value = Integer.parseInt(cmd[valueIndex]);
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage("Worker need value must be 0-100.");
                return true;
            }
            if (value < 0 || value > SettlementWorkerState.MAX_NEED) {
                player.getPackets().sendGameMessage("Worker need value must be 0-100.");
                return true;
            }
            worker.setNeed(need, value);
            player.getPackets().sendGameMessage(
                    "Worker #" + worker.getWorkerId() + " "
                            + need.getDisplayName() + "=" + value + ". "
                            + worker.getNeedsSummary());
            return true;
        }

        if ("workerprogress".equals(operation)) {
            SettlementWorkerState worker = resolveSettlementWorker(player, cmd, 3);
            if (worker != null) {
                player.getPackets().sendGameMessage(
                        "Worker #" + worker.getWorkerId() + " Skills: "
                                + worker.getSkillsSummary());
                player.getPackets().sendGameMessage(
                        "Player Construction XP: "
                                + (long) player.getSkills().getXp(Skills.CONSTRUCTION) + ".");
            }
            return true;
        }

        if ("workerprogressselftest".equals(operation)) {
            String result = SettlementWorkerProgressionSelfTest.run();
            System.out.println("[SettlementWorkerProgressionSelfTest] " + result);
            player.getPackets().sendGameMessage("Worker Progression self-test: " + result);
            return true;
        }

        if ("bundle14gatebaseline".equals(operation)) {
            String result = SettlementBundle14FinalGate.capture(player);
            System.out.println("[SettlementBundle14FinalGate] " + result);
            player.getPackets().sendGameMessage("Bundle 1.4 gate: " + result);
            return true;
        }

        if ("bundle14gatecheck".equals(operation)) {
            String result = SettlementBundle14FinalGate.check(player);
            System.out.println("[SettlementBundle14FinalGate] " + result);
            player.getPackets().sendGameMessage("Bundle 1.4 gate: " + result);
            return true;
        }

        if ("bundle15selftest".equals(operation)) {
            String result = SettlementBundle15FinalCheck.runSelfTest();
            System.out.println("[SettlementBundle15FinalCheck] " + result);
            player.getPackets().sendGameMessage("Bundle 1.5 self-test: " + result);
            return true;
        }

        if ("bundle15baseline".equals(operation)) {
            String result = SettlementBundle15FinalCheck.capture(player);
            System.out.println("[SettlementBundle15FinalCheck] " + result);
            player.getPackets().sendGameMessage("Bundle 1.5 gate: " + result);
            return true;
        }

        if ("bundle15check".equals(operation)) {
            String result = SettlementBundle15FinalCheck.check(player);
            System.out.println("[SettlementBundle15FinalCheck] " + result);
            player.getPackets().sendGameMessage("Bundle 1.5 gate: " + result);
            return true;
        }

        if ("bundle22selftest".equals(operation)) {
            String result = SettlementBundle22FinalGate.runSelfTest();
            System.out.println("[SettlementBundle22FinalGate] " + result);
            player.getPackets().sendGameMessage("Bundle 2.2 self-test: " + result);
            return true;
        }

        if ("bundle22preparebaseline".equals(operation)) {
            String result = SettlementBundle22FinalGate.prepareAndCapture(player);
            System.out.println("[SettlementBundle22FinalGate] " + result);
            player.getPackets().sendGameMessage("Bundle 2.2 gate: " + result);
            return true;
        }

        if ("bundle22baseline".equals(operation)) {
            String result = SettlementBundle22FinalGate.capture(player);
            System.out.println("[SettlementBundle22FinalGate] " + result);
            player.getPackets().sendGameMessage("Bundle 2.2 gate: " + result);
            return true;
        }

        if ("bundle22check".equals(operation)) {
            String result = SettlementBundle22FinalGate.check(player);
            System.out.println("[SettlementBundle22FinalGate] " + result);
            player.getPackets().sendGameMessage("Bundle 2.2 gate: " + result);
            return true;
        }

        if ("workerjob".equals(operation)) {
            boolean targeted = hasWorkerIdArgument(cmd, 3);
            int jobIndex = targeted ? 4 : 3;
            int stateIndex = targeted ? 5 : 4;
            if (cmd.length <= stateIndex) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerjob [workerId] <job-key> <on|off>");
                return true;
            }
            SettlementWorkerState worker = resolveSettlementWorker(player, cmd, 3);
            if (worker == null) {
                return true;
            }
            SettlementWorkerJob job = SettlementWorkerJob.forKey(cmd[jobIndex].toLowerCase());
            if (job == null) {
                player.getPackets().sendGameMessage(
                        "Unknown worker job key: " + cmd[jobIndex] + ".");
                return true;
            }
            String state = cmd[stateIndex].toLowerCase();
            if (!"on".equals(state) && !"off".equals(state)) {
                player.getPackets().sendGameMessage(
                        "Worker job state must be on or off.");
                return true;
            }
            boolean allowed = "on".equals(state);
            worker.setJobAllowed(job, allowed);
            player.getPackets().sendGameMessage(
                    "Worker #" + worker.getWorkerId() + " "
                            + job.getDisplayName() + "=" + (allowed ? "ON" : "OFF") + ".");
            return true;
        }

        if ("workerjobsall".equals(operation)) {
            boolean targeted = hasWorkerIdArgument(cmd, 3);
            int stateIndex = targeted ? 4 : 3;
            if (cmd.length <= stateIndex) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser settlement workerjobsall [workerId] <on|off>");
                return true;
            }
            SettlementWorkerState worker = resolveSettlementWorker(player, cmd, 3);
            if (worker == null) {
                return true;
            }
            String state = cmd[stateIndex].toLowerCase();
            if (!"on".equals(state) && !"off".equals(state)) {
                player.getPackets().sendGameMessage(
                        "Worker jobs state must be on or off.");
                return true;
            }
            boolean allowed = "on".equals(state);
            for (SettlementWorkerJob job : SettlementWorkerJob.values()) {
                worker.setJobAllowed(job, allowed);
            }
            player.getPackets().sendGameMessage(
                    "Worker #" + worker.getWorkerId() + " Allowed Jobs: "
                            + worker.getAllowedJobsSummary());
            return true;
        }

        if ("audit".equals(operation)) {
            String result = SettlementStateAudit.run(player.getSettlementState());
            System.out.println("[SettlementStateAudit] " + result);
            player.getPackets().sendGameMessage("Settlement saved-state audit: " + result);
            return true;
        }

        if ("finalcheck".equals(operation)) {
            String result = SettlementBundle12FinalCheck.run(player);
            System.out.println("[SettlementBundle12FinalCheck] " + result);
            player.getPackets().sendGameMessage("Bundle 1.2 final auto check: " + result);
            return true;
        }

        if ("selftest".equals(operation)) {
            String result = SettlementStateSelfTest.run();
            System.out.println("[SettlementStateSelfTest] " + result);
            player.getPackets().sendGameMessage("Settlement state self-test: " + result);
            return true;
        }

        player.getPackets().sendGameMessage(
                "Use: ::itembrowser settlement <enter|exit|status|list|resources|storagereset|storageset|resourceselftest|shelter|shelterselftest|bundle13check|workers|workerallstatus|workerselftest|workercheck|workerpause|workerpreset|workerselectionset|workerselectionclear|workerselectionstatus|workerselectionpause|workerselectionpreset|workerselectionjob|workerselectionjobsall|workerjobs|workerjob|workerjobsall|workerjobselftest|workerai|workerneeds|workerneed|workerneedsreset|workerneedselftest|workerprogress|workerprogressselftest|bundle14gatebaseline|bundle14gatecheck|bundle15selftest|bundle15baseline|bundle15check|bundle22selftest|bundle22baseline|bundle22check|population|populationrecruit|populationselftest|populationcheck|audit|selftest|finalcheck>");
        return true;
    }

    private static int[] parseRuntimeNpcIndexes(String csv) {
        if (csv == null || csv.trim().isEmpty() || "none".equalsIgnoreCase(csv.trim())) {
            return null;
        }
        String[] parts = csv.split(",");
        if (parts.length == 0 || parts.length > 64) {
            return null;
        }

        int[] parsed = new int[parts.length];
        java.util.HashSet<Integer> seen = new java.util.HashSet<Integer>();
        int count = 0;
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                return null;
            }
            final int npcIndex;
            try {
                npcIndex = Integer.parseInt(part.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
            if (npcIndex < 0 || npcIndex > 32767) {
                return null;
            }
            if (seen.add(Integer.valueOf(npcIndex))) {
                parsed[count++] = npcIndex;
            }
        }
        if (count == 0) {
            return null;
        }
        return count == parsed.length
                ? parsed : java.util.Arrays.copyOf(parsed, count);
    }

    private static String formatWorkerIds(
            java.util.List<SettlementWorkerState> workers) {
        if (workers == null || workers.isEmpty()) {
            return "none";
        }
        StringBuilder result = new StringBuilder();
        for (SettlementWorkerState worker : workers) {
            if (worker == null) {
                continue;
            }
            if (result.length() > 0) {
                result.append(',');
            }
            result.append('#').append(worker.getWorkerId());
        }
        return result.length() == 0 ? "none" : result.toString();
    }

    private static SettlementWorkerState resolveSettlementWorker(
            Player player, String[] cmd, int workerIdIndex) {
        if (player == null) {
            return null;
        }
        if (hasWorkerIdArgument(cmd, workerIdIndex)) {
            final long workerId;
            try {
                workerId = Long.parseLong(cmd[workerIdIndex]);
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage("Worker id must be a whole number.");
                return null;
            }
            SettlementWorkerState worker = player.getSettlementState().findWorker(workerId);
            if (worker == null) {
                player.getPackets().sendGameMessage("Worker #" + workerId + " was not found.");
            }
            return worker;
        }

        SettlementWorkerState worker = player.getSettlementState().getStarterWorker();
        if (worker == null) {
            player.getPackets().sendGameMessage("No starter worker exists yet.");
        }
        return worker;
    }

    private static java.util.Set<SettlementWorkerJob> parseWorkerJobSet(String value) {
        java.util.Set<SettlementWorkerJob> jobs =
                new java.util.HashSet<SettlementWorkerJob>();
        if (value == null || value.trim().length() == 0
                || "none".equalsIgnoreCase(value.trim())) {
            return jobs;
        }
        String[] keys = value.split(",");
        for (String key : keys) {
            SettlementWorkerJob job =
                    SettlementWorkerJob.forKey(key == null ? null : key.trim().toLowerCase());
            if (job == null) {
                return null;
            }
            jobs.add(job);
        }
        return jobs;
    }

    private static boolean hasWorkerIdArgument(String[] cmd, int index) {
        if (cmd == null || index < 0 || cmd.length <= index) {
            return false;
        }
        try {
            Long.parseLong(cmd[index]);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean processDevSpawn(Player player, String[] cmd) {
        if (cmd.length < 7) {
            player.getPackets().sendGameMessage(
                    "Use: ::itembrowser devspawn <npc|object|item> <id> <x> <y> <plane> [type rotation|amount]");
            return true;
        }

        final String kind = cmd[2].toLowerCase();
        final int id;
        final int x;
        final int y;
        final int plane;
        try {
            id = Integer.parseInt(cmd[3]);
            x = Integer.parseInt(cmd[4]);
            y = Integer.parseInt(cmd[5]);
            plane = Integer.parseInt(cmd[6]);
        } catch (NumberFormatException ex) {
            player.getPackets().sendGameMessage("Dev spawn id and tile coordinates must be whole numbers.");
            return true;
        }

        if (!validTarget(id, x, y, plane)) {
            player.getPackets().sendGameMessage("Dev spawn id/tile is outside the supported Matrix3 world range.");
            return true;
        }

        WorldTile tile = new WorldTile(x, y, plane);

        if ("npc".equals(kind)) {
            NPCDefinitions definition = NPCDefinitions.getNPCDefinitions(id);
            if (definition == null || (definition.modelIds.length == 0 && !hasName(definition.name))) {
                player.getPackets().sendGameMessage("Unable to spawn unknown NPC id " + id + ".");
                return true;
            }
            NPC npc = World.spawnNPC(id, tile, -1, true, true);
            if (npc == null) {
                player.getPackets().sendGameMessage("Matrix3 could not spawn NPC id " + id + " on that tile.");
                return true;
            }
            DevModeRuntimeManager.trackNpc(player, npc);
            player.getPackets().sendGameMessage("Dev Mode spawned NPC " + displayName(definition.name, id)
                    + " at " + x + ", " + y + ", " + plane + ".");
            return true;
        }

        if ("object".equals(kind)) {
            if (cmd.length < 9) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser devspawn object <id> <x> <y> <plane> <type> <rotation>");
                return true;
            }
            final int type;
            final int rotation;
            try {
                type = Integer.parseInt(cmd[7]);
                rotation = Integer.parseInt(cmd[8]);
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage("Object type and rotation must be whole numbers.");
                return true;
            }
            if (type < 0 || type > 22 || rotation < 0 || rotation > 3) {
                player.getPackets().sendGameMessage("Object type must be 0-22 and rotation must be 0-3.");
                return true;
            }
            ObjectDefinitions definition = ObjectDefinitions.getObjectDefinitions(id);
            if (definition == null || (definition.modelIds == null && !hasName(definition.name))) {
                player.getPackets().sendGameMessage("Unable to spawn unknown object id " + id + ".");
                return true;
            }

            SettlementInstance settlement = SettlementInstance.getActive(player);
            if (settlement != null) {
                player.getPackets().sendGameMessage(
                        settlement.placeDevelopmentPiece(id, type, rotation, tile));
                return true;
            }

            WorldObject object = new WorldObject(id, type, rotation, tile);
            World.spawnObject(object);
            DevModeRuntimeManager.trackObject(player, object);
            player.getPackets().sendGameMessage("Dev Mode spawned object " + displayName(definition.name, id)
                    + " at " + x + ", " + y + ", " + plane + ".");
            return true;
        }

        if ("item".equals(kind)) {
            if (cmd.length < 8) {
                player.getPackets().sendGameMessage(
                        "Use: ::itembrowser devspawn item <id> <x> <y> <plane> <amount>");
                return true;
            }
            final int amount;
            try {
                amount = Integer.parseInt(cmd[7]);
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage("Ground item amount must be a whole number.");
                return true;
            }
            if (amount <= 0) {
                player.getPackets().sendGameMessage("Ground item amount must be greater than zero.");
                return true;
            }
            ItemDefinitions definition = ItemDefinitions.getItemDefinitions(id);
            if (definition == null || !definition.isLoaded() || !hasName(definition.name)) {
                player.getPackets().sendGameMessage("Unable to spawn unknown item id " + id + ".");
                return true;
            }
            World.addGroundItem(new Item(id, amount), tile, player, true, 180);
            player.getPackets().sendGameMessage("Dev Mode spawned " + amount + " x " + definition.name
                    + " at " + x + ", " + y + ", " + plane + ".");
            return true;
        }

        player.getPackets().sendGameMessage("Dev spawn type must be npc, object, or item.");
        return true;
    }

    private static boolean processDevEdit(Player player, String[] cmd) {
        if (cmd.length < 9) {
            player.getPackets().sendGameMessage(
                    "Use: ::itembrowser devedit <move|duplicate|rotate|delete> <npc|object> <id> <x> <y> <plane> <runtimeRef> [...]");
            return true;
        }

        String operation = cmd[2].toLowerCase();
        String kind = cmd[3].toLowerCase();
        final int id;
        final int sourceX;
        final int sourceY;
        final int sourcePlane;
        final int runtimeRef;
        try {
            id = Integer.parseInt(cmd[4]);
            sourceX = Integer.parseInt(cmd[5]);
            sourceY = Integer.parseInt(cmd[6]);
            sourcePlane = Integer.parseInt(cmd[7]);
            runtimeRef = Integer.parseInt(cmd[8]);
        } catch (NumberFormatException ex) {
            player.getPackets().sendGameMessage("Dev edit target values must be whole numbers.");
            return true;
        }

        if (!validTarget(id, sourceX, sourceY, sourcePlane)) {
            player.getPackets().sendGameMessage("Dev edit source target is outside the supported Matrix3 world range.");
            return true;
        }
        if (!"npc".equals(kind) && !"object".equals(kind)) {
            player.getPackets().sendGameMessage("Dev edit type must be npc or object.");
            return true;
        }

        WorldTile source = new WorldTile(sourceX, sourceY, sourcePlane);

        if ("move".equals(operation) || "duplicate".equals(operation)) {
            if (cmd.length < 12) {
                player.getPackets().sendGameMessage("Dev move/duplicate requires destination x y plane.");
                return true;
            }
            final int destinationX;
            final int destinationY;
            final int destinationPlane;
            try {
                destinationX = Integer.parseInt(cmd[9]);
                destinationY = Integer.parseInt(cmd[10]);
                destinationPlane = Integer.parseInt(cmd[11]);
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage("Dev edit destination values must be whole numbers.");
                return true;
            }
            if (!validTile(destinationX, destinationY, destinationPlane)) {
                player.getPackets().sendGameMessage("Dev edit destination is outside the supported Matrix3 world range.");
                return true;
            }
            WorldTile destination = new WorldTile(destinationX, destinationY, destinationPlane);

            SettlementInstance settlement = SettlementInstance.getActive(player);
            if ("object".equals(kind) && settlement != null && settlement.containsWorldTile(source)) {
                String result = "move".equals(operation)
                        ? settlement.moveDevelopmentPiece(id, source, destination)
                        : settlement.duplicateDevelopmentPiece(id, source, destination);
                player.getPackets().sendGameMessage(result);
                return true;
            }

            if ("npc".equals(kind)) {
                if ("move".equals(operation)) {
                    DevModeRuntimeManager.moveNpc(player, runtimeRef, id, source, destination);
                } else {
                    DevModeRuntimeManager.duplicateNpc(player, runtimeRef, id, source, destination);
                }
            } else if ("move".equals(operation)) {
                DevModeRuntimeManager.moveObject(player, id, source, destination);
            } else {
                DevModeRuntimeManager.duplicateObject(player, id, source, destination);
            }
            return true;
        }

        if ("rotate".equals(operation)) {
            if (!"object".equals(kind)) {
                player.getPackets().sendGameMessage("Only objects can be rotated by this Dev Mode bundle.");
                return true;
            }
            if (cmd.length < 10) {
                player.getPackets().sendGameMessage("Dev rotate requires a -1 or 1 direction.");
                return true;
            }
            final int delta;
            try {
                delta = Integer.parseInt(cmd[9]);
            } catch (NumberFormatException ex) {
                player.getPackets().sendGameMessage("Dev rotate direction must be -1 or 1.");
                return true;
            }
            if (delta != -1 && delta != 1) {
                player.getPackets().sendGameMessage("Dev rotate direction must be -1 or 1.");
                return true;
            }

            SettlementInstance settlement = SettlementInstance.getActive(player);
            if (settlement != null && settlement.containsWorldTile(source)) {
                player.getPackets().sendGameMessage(
                        settlement.rotateDevelopmentPiece(id, source, delta));
                return true;
            }

            DevModeRuntimeManager.rotateObject(player, id, source, delta);
            return true;
        }

        if ("delete".equals(operation)) {
            if ("npc".equals(kind)) {
                DevModeRuntimeManager.deleteNpc(player, runtimeRef, id, source);
            } else {
                SettlementInstance settlement = SettlementInstance.getActive(player);
                if (settlement != null && settlement.containsWorldTile(source)) {
                    player.getPackets().sendGameMessage(
                            settlement.deleteDevelopmentPiece(id, source));
                    return true;
                }
                DevModeRuntimeManager.deleteObject(player, id, source);
            }
            return true;
        }

        player.getPackets().sendGameMessage("Dev edit operation must be move, duplicate, rotate, or delete.");
        return true;
    }

    private static boolean validTarget(int id, int x, int y, int plane) {
        return id >= 0 && validTile(x, y, plane);
    }

    private static boolean validTile(int x, int y, int plane) {
        return x >= 0 && x <= 16383 && y >= 0 && y <= 16383 && plane >= 0 && plane <= 3;
    }

    private static boolean hasName(String name) {
        return name != null && name.trim().length() > 0 && !"null".equalsIgnoreCase(name.trim());
    }

    private static String displayName(String name, int id) {
        return hasName(name) ? name + " (" + id + ")" : "id " + id;
    }

    private static boolean processSettings(Player player, String[] cmd) {
        if (cmd.length < 4) {
            player.getPackets().sendGameMessage(
                    "Use: ::itembrowser settings <combat|interface> <legacy|eoc|nis>");
            return true;
        }

        ensureIndependentModes(player);

        if ("combat".equalsIgnoreCase(cmd[2])) {
            if ("legacy".equalsIgnoreCase(cmd[3])) {
                setCombatMode(player, CombatDefinitions.LEGACY_COMBAT_MODE);
                player.getPackets().sendGameMessage("Client Console combat mode: Legacy combat.");
                return true;
            }
            if ("eoc".equalsIgnoreCase(cmd[3]) || "manual".equalsIgnoreCase(cmd[3])) {
                setCombatMode(player, CombatDefinitions.MANUAL_COMBAT_MODE);
                player.getPackets().sendGameMessage("Client Console combat mode: EoC manual combat.");
                return true;
            }
            player.getPackets().sendGameMessage("Use: ::itembrowser settings combat <legacy|eoc>");
            return true;
        }

        if ("interface".equalsIgnoreCase(cmd[2])) {
            if ("legacy".equalsIgnoreCase(cmd[3])) {
                applyLegacyInterface(player);
                player.getPackets().sendGameMessage(
                        "Client Console interface mode: Legacy interface. Combat mode was left unchanged.");
                return true;
            }
            if ("nis".equalsIgnoreCase(cmd[3]) || "eoc".equalsIgnoreCase(cmd[3])) {
                player.refreshInterfaceVars();
                player.getPackets().sendGameMessage(
                        "Client Console interface mode: NIS. Combat mode was left unchanged.");
                return true;
            }
            player.getPackets().sendGameMessage("Use: ::itembrowser settings interface <legacy|nis>");
            return true;
        }

        player.getPackets().sendGameMessage(
                "Use: ::itembrowser settings <combat|interface> <legacy|eoc|nis>");
        return true;
    }

    /**
     * Matrix3's original legacyMode flag couples combat and interface state. The
     * Client Console split controls normalize that master flag off while preserving
     * the currently effective combat mode, then drive combat and interface state
     * independently.
     */
    private static void ensureIndependentModes(Player player) {
        if (!player.isLegacyMode()) {
            return;
        }
        int effectiveCombatMode = player.getCombatDefinitions().getCombatMode();
        player.switchLegacyMode();
        setCombatMode(player, effectiveCombatMode);
    }

    private static void setCombatMode(Player player, int mode) {
        CombatDefinitions definitions = player.getCombatDefinitions();
        definitions.setCombatMode(mode);

        boolean legacyCombat = mode == CombatDefinitions.LEGACY_COMBAT_MODE;
        definitions.setMagicAbilityMenu(legacyCombat ? 0 : 1);
        if (legacyCombat) {
            // These two CombatDefinitions refreshers normally key off the original
            // coupled Player.legacyMode flag. Reproduce their legacy-combat state
            // explicitly while that master flag is intentionally false for NIS.
            player.getVarsManager().sendVarBit(21686, 0);
            player.getVarsManager().sendVarBit(21684, 1);
        } else {
            definitions.refreshShowCombatModeIcon();
            definitions.refreshAllowAbilityQueueing();
        }
    }

    /**
     * Applies only the legacy-interface var state while keeping Player.legacyMode
     * false. This preserves the selected CombatDefinitions mode instead of using
     * Matrix3's original all-in-one legacy switch.
     */
    private static void applyLegacyInterface(Player player) {
        player.refreshInterfaceVars();
        player.getVarsManager().sendVarBit(22874, 1); // map icons
        player.getVarsManager().sendVarBit(19924, 1); // slim headers forced in legacy UI
        player.getVarsManager().sendVarBit(19925, 1); // interface customization locked
        player.getVarsManager().sendVarBit(20188, 1); // click-through chatboxes
        player.getVarsManager().sendVarBit(19928, 1); // hide title bars when locked
        player.getVarsManager().sendVarBit(19929, 1); // target reticules unavailable
        player.getVarsManager().sendVarBit(19927, 0); // target information legacy state
        player.getVarsManager().sendVarBit(22310, 1); // always-on chat legacy state
        player.getVarsManager().sendVarBit(22875, 1); // legacy gameframe
        player.getVarsManager().sendVarBit(22872, 1); // legacy interface mode
    }
}
