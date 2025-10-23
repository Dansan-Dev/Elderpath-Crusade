package io.github.forest_of_dreams.managers;

import io.github.forest_of_dreams.cards.WolfCard;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;
import io.github.forest_of_dreams.game_objects.board.Plot;
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
 * Policy (prototype):
 * - Try to play one WolfCard if affordable, to the last row on the first available plot.
 * - Then attempt one adjacent attack per P2 piece (cardinal adjacency only), if actions remain.
 * - End turn immediately after performing available actions.
 */
public final class BotManager {
    private static boolean initialized = false;

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
            // Safety: don't act if paused or if a multi-selection is active
            if (GraphicsManager.isPaused()) return;
            if (InteractionManager.hasActiveSelection()) return;
            try {
                runBotTurn();
            } catch (Exception ex) {
                Logger.error("BotManager", "Exception during bot turn: " + ex.getMessage());
            }
        };
        EventBus.register(GameEventType.TURN_STARTED, onTurn);
    }

    private static void runBotTurn() {
        // Collect boards currently rendered
        List<Board> boards = getActiveBoards();
        if (boards.isEmpty()) {
            // Nothing to do; end turn to avoid getting stuck
            TurnManager.endTurn();
            return;
        }

        // 1) Try to play one WolfCard if possible
        tryPlayOneWolfCard(boards);

        // 2) Attempt adjacent attacks (one per piece)
        tryAdjacentAttacks(boards);

        // 3) End turn
        TurnManager.endTurn();
    }

    private static List<Board> getActiveBoards() {
        List<Board> out = new ArrayList<>();
        for (Renderable r : GraphicsManager.getRenderables()) {
            if (r instanceof Board b) out.add(b);
        }
        return out;
    }

    private static void tryPlayOneWolfCard(List<Board> boards) {
        var ps = PlayerManager.get(PieceAlignment.P2);
        if (ps == null || ps.hand == null) return;
        // Find a WolfCard in hand
        WolfCard targetCard = null;
        for (var c : ps.hand.getCards()) {
            if (c instanceof WolfCard wc) { targetCard = wc; break; }
        }
        if (targetCard == null) return;
        // Find a valid target plot on any board's last row
        for (Board b : boards) {
            int lastRow = b.getROWS() - 1;
            for (int col = 0; col < b.getCOLS(); col++) {
                Renderable r = b.getPlotAtPos(lastRow, col);
                if (r instanceof Plot p) {
                    if (b.isValidSummonTarget(p, PieceAlignment.P2)) {
                        // Emulate the card's multi-interaction resolve: source=card, target=plot
                        HashMap<Integer, CustomBox> entities = new HashMap<>();
                        entities.put(0, targetCard);
                        entities.put(1, p);
                        targetCard.triggerClickEffect(entities);
                        // WolfCard consumes itself on success; either way, stop after one attempt
                        return;
                    }
                }
            }
        }
    }

    private static void tryAdjacentAttacks(List<Board> boards) {
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
                                HashMap<Integer, CustomBox> entities = new HashMap<>();
                                entities.put(0, srcPlot);
                                entities.put(1, dstPlot);
                                // Call plot's trigger (wired to Board.handlePlotMove)
                                srcPlot.triggerClickEffect(entities);
                            }
                        }
                    }
                }
            }
        }
    }
}
