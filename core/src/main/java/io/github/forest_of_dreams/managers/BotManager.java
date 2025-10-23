package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.utils.Timer;
import io.github.forest_of_dreams.cards.WolfCard;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;
import io.github.forest_of_dreams.game_objects.board.Plot;
import io.github.forest_of_dreams.enums.GamePieceData;
import io.github.forest_of_dreams.interfaces.CustomBox;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.multiplayer.EventBus;
import io.github.forest_of_dreams.multiplayer.GameEvent;
import io.github.forest_of_dreams.multiplayer.GameEventType;
import io.github.forest_of_dreams.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal P2 bot that plays on TURN_STARTED for P2.
 * Priority: ATTACK > MOVE > SUMMON. Iterate until no actions remain, then end turn.
 */
public final class BotManager {
    private static boolean initialized = false;
    // Small delays to make bot actions feel more natural and to avoid race conditions with UI
    private static final float DELAY_BEFORE_ACT = 0.4f;
    private static final float DELAY_BEFORE_END = 0.4f;
    private static final float DELAY_BETWEEN_ACTIONS = 0.35f;
    private static final int MAX_ACTIONS_PER_TURN = 50; // safety guard

    private BotManager() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        Consumer<GameEvent> onTurn = (evt) -> {
            if (evt.getType() != GameEventType.TURN_STARTED) return;
            // Feature flag: bot enabled?
            if (!SettingsManager.debug.enableP2Bot) return;
            Object p = evt.getData().get("player");
            if (p == null) return;
            if (!PieceAlignment.P2.name().equals(p.toString())) return;
            // Safety: don't act if paused
            if (GraphicsManager.isPaused()) return;
            // Clear any lingering multi-selection from the human player before bot acts
            if (InteractionManager.hasActiveSelection()) {
                InteractionManager.cancelSelection();
            }
            // Schedule a small delay before the bot acts (visual breathing room)
            Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    try {
                        runBotTurn();
                    } catch (Exception ex) {
                        Logger.error("BotManager", "Exception during bot turn: " + ex.getMessage());
                    }
                }
            }, DELAY_BEFORE_ACT);
        };
        EventBus.register(GameEventType.TURN_STARTED, onTurn);
    }

    private static void runBotTurn() {
        chainNextAction(0);
    }

    private static void chainNextAction(int actionsDone) {
        // Stop if paused or it's no longer P2's turn (e.g., human ended early)
        if (GraphicsManager.isPaused() || TurnManager.getCurrentPlayer() != PieceAlignment.P2) {
            return;
        }
        if (actionsDone >= MAX_ACTIONS_PER_TURN) {
            Logger.log("BotManager", "Reached max actions safety cap; ending turn");
            scheduleEndTurn();
            return;
        }

        // Gather boards fresh each iteration
        List<Board> boards = getActiveBoards();
        if (boards.isEmpty()) {
            Logger.log("BotManager", "No boards; ending P2 turn");
            scheduleEndTurn();
            return;
        }

        // Try one action in priority order
        if (tryOneAdjacentAttack(boards)) {
            Logger.log("BotManager", "P2 bot: ATTACK executed (#" + (actionsDone+1) + ")");
            scheduleNextAction(actionsDone + 1);
            return;
        }
        if (tryOneMovementTowardEnemy(boards)) {
            Logger.log("BotManager", "P2 bot: MOVE executed (#" + (actionsDone+1) + ")");
            scheduleNextAction(actionsDone + 1);
            return;
        }
        if (tryPlayOneWolfCard(boards)) {
            Logger.log("BotManager", "P2 bot: SUMMON executed (#" + (actionsDone+1) + ")");
            scheduleNextAction(actionsDone + 1);
            return;
        }

        // Nothing left to do -> end turn
        Logger.log("BotManager", "P2 bot: NO MORE ACTIONS; ending turn");
        scheduleEndTurn();
    }

    private static void scheduleNextAction(final int actionsDone) {
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                chainNextAction(actionsDone);
            }
        }, DELAY_BETWEEN_ACTIONS);
    }

    private static List<Board> getActiveBoards() {
        List<Board> out = new ArrayList<>();
        for (Renderable r : GraphicsManager.getRenderables()) {
            if (r instanceof Board b) out.add(b);
        }
        return out;
    }

    private static boolean tryPlayOneWolfCard(List<Board> boards) {
        var ps = PlayerManager.get(PieceAlignment.P2);
        if (ps == null || ps.hand == null) return false;
        // Find a WolfCard in hand
        WolfCard targetCard = null;
        for (var c : ps.hand.getCards()) {
            if (c instanceof WolfCard wc) { targetCard = wc; break; }
        }
        if (targetCard == null) return false;
        // Find a valid target plot on any board's last row
        for (Board b : boards) {
            int lastRow = b.getROWS() - 1;
            for (int col = 0; col < b.getCOLS(); col++) {
                Renderable r = b.getPlotAtPos(lastRow, col);
                if (r instanceof Plot p) {
                    if (b.isValidSummonTarget(p, PieceAlignment.P2)) {
                        // Emulate the card's multi-interaction resolve: source=card, target=plot
                        int beforeHandSize = ps.hand.getCards().size();
                        int beforeMana = ps.mana;
                        HashMap<Integer, CustomBox> entities = new HashMap<>();
                        entities.put(0, targetCard);
                        entities.put(1, p);
                        targetCard.triggerClickEffect(entities);
                        // Success criteria: card consumed (no longer in hand) OR mana decreased
                        boolean consumed = !ps.hand.getCards().contains(targetCard) || ps.hand.getCards().size() < beforeHandSize;
                        boolean spentMana = ps.mana < beforeMana;
                        if (consumed || spentMana) {
                            return true;
                        } else {
                            // Try next valid plot or next board
                            continue;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean tryOneAdjacentAttack(List<Board> boards) {
        for (Board b : boards) {
            int rows = b.getROWS();
            int cols = b.getCOLS();
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    GamePiece gp = b.getGamePieceAtPos(r, c);
                    if (gp instanceof MonsterGamePiece mgp && mgp.getAlignment() == PieceAlignment.P2) {
                        // Build a list of adjacent hostile plots
                        List<Plot> hostile = b.getAdjacentHostilePlots(r, c, PieceAlignment.P2);
                        if (!hostile.isEmpty()) {
                            // Resolve attack by invoking the Plot's onClick (Board.handlePlotMove)
                            Renderable srcR = b.getPlotAtPos(r, c);
                            if (srcR instanceof Plot srcPlot) {
                                Plot dstPlot = hostile.get(0);
                                int[] dIdx = b.getIndicesOfPlot(dstPlot);
                                if (dIdx == null) continue;
                                // Snapshot defender state and attacker remaining actions before triggering
                                GamePiece defenderBefore = b.getGamePieceAtPos(dIdx[0], dIdx[1]);
                                int defenderHpBefore = -1;
                                if (defenderBefore instanceof MonsterGamePiece defM) {
                                    defenderHpBefore = defM.getStats().getCurrentHealth();
                                }
                                int actionsBefore;
                                Object aVal = mgp.getData(GamePieceData.ACTIONS_REMAINING);
                                if (aVal instanceof Integer n) actionsBefore = n; else actionsBefore = mgp.getStats().getActions();

                                HashMap<Integer, CustomBox> entities = new HashMap<>();
                                entities.put(0, srcPlot);
                                entities.put(1, dstPlot);
                                // Call plot's trigger (wired to Board.handlePlotMove)
                                srcPlot.triggerClickEffect(entities);

                                // Verify success: defender damaged/dead OR attacker actions decreased
                                GamePiece defenderAfter = b.getGamePieceAtPos(dIdx[0], dIdx[1]);
                                boolean defenderDied = (defenderBefore instanceof MonsterGamePiece) && (defenderAfter == null || defenderAfter != defenderBefore);
                                boolean defenderDamaged = false;
                                if (defenderBefore instanceof MonsterGamePiece defM2 && defenderAfter instanceof MonsterGamePiece defM2After && defenderBefore == defenderAfter) {
                                    defenderDamaged = defM2After.getStats().getCurrentHealth() < defenderHpBefore;
                                }
                                int actionsAfter;
                                Object aValAfter = mgp.getData(GamePieceData.ACTIONS_REMAINING);
                                if (aValAfter instanceof Integer n2) actionsAfter = n2; else actionsAfter = mgp.getStats().getActions();
                                boolean spentAction = actionsAfter < actionsBefore;

                                if (defenderDied || defenderDamaged || spentAction) {
                                    return true;
                                } else {
                                    // Attack didn’t resolve (no state change) — keep searching
                                    continue;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean tryOneMovementTowardEnemy(List<Board> boards) {
        for (Board b : boards) {
            int rows = b.getROWS();
            int cols = b.getCOLS();
            // Precompute all enemy locations for quick Manhattan distance checks
            List<int[]> enemies = new ArrayList<>();
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    GamePiece gp = b.getGamePieceAtPos(r, c);
                    if (gp instanceof MonsterGamePiece em && em.getAlignment() == PieceAlignment.P1) {
                        enemies.add(new int[]{r, c});
                    }
                }
            }
            if (enemies.isEmpty()) continue;

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    GamePiece gp = b.getGamePieceAtPos(r, c);
                    if (!(gp instanceof MonsterGamePiece mgp) || mgp.getAlignment() != PieceAlignment.P2) continue;
                    // Source and current distance to nearest enemy
                    int currentDist = nearestEnemyManhattan(r, c, enemies);
                    if (currentDist <= 1) continue; // already adjacent; attack path would have handled
                    int speed = mgp.getStats().getSpeed();
                    List<Plot> reachable = b.getReachablePlots(r, c, speed);
                    if (reachable.isEmpty()) continue;
                    // Choose any reachable plot that strictly reduces distance
                    Plot best = null;
                    int bestDist = currentDist;
                    for (Plot p : reachable) {
                        int[] idx = b.getIndicesOfPlot(p);
                        if (idx == null) continue;
                        int d = nearestEnemyManhattan(idx[0], idx[1], enemies);
                        if (d < bestDist) {
                            bestDist = d;
                            best = p;
                        }
                    }
                    if (best != null) {
                        // Invoke move via plot trigger so Board handles rules + events
                        Renderable srcR = b.getPlotAtPos(r, c);
                        if (srcR instanceof Plot srcPlot) {
                            // Snapshot destination indices and source piece for verification after trigger
                            int[] bestIdx = b.getIndicesOfPlot(best);
                            if (bestIdx == null) continue;
                            GamePiece before = b.getGamePieceAtPos(r, c);
                            HashMap<Integer, CustomBox> entities = new HashMap<>();
                            entities.put(0, srcPlot);
                            entities.put(1, best);
                            srcPlot.triggerClickEffect(entities);
                            // Verify that move actually occurred (piece arrived at destination)
                            GamePiece afterAtDest = b.getGamePieceAtPos(bestIdx[0], bestIdx[1]);
                            if (afterAtDest == before) {
                                return true; // movement succeeded
                            } else {
                                // Movement did not happen (blocked or invalid); keep searching
                                continue;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static int nearestEnemyManhattan(int r, int c, List<int[]> enemies) {
        int best = Integer.MAX_VALUE;
        for (int[] e : enemies) {
            int d = Math.abs(e[0] - r) + Math.abs(e[1] - c);
            if (d < best) best = d;
        }
        return best;
    }

    private static void scheduleEndTurn() {
        // Schedule end turn with a small delay to ensure UI and events settle
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                // Avoid ending turn while paused
                if (!GraphicsManager.isPaused()) {
                    TurnManager.endTurn();
                }
            }
        }, DELAY_BEFORE_END);
    }
}
