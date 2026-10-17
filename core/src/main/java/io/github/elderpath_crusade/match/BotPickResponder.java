package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * Answers a bot slot's picks immediately on the server, so a bot-owned pick never waits for a
 * click. Deterministic: PIECE → the candidate closest to dying (most likely to secure a kill
 * or finish a heal target — the old ChooseTarget bot rule); ROW → the row holding the most
 * enemy pieces; TILE → the candidate nearest the board's centre column (ties: first listed).
 */
final class BotPickResponder implements PickResponder {

    @Override
    public void onPickRequested(PickRequest request, PickRouter router) {
        Coord choice = switch (request.kind()) {
            case PIECE -> weakestPiece(request);
            case ROW -> busiestEnemyRow(request);
            case TILE -> mostCentral(request);
        };
        router.answer(request.owner(), PickResponse.chose(request.pickId(), choice));
    }

    private static Coord weakestPiece(PickRequest request) {
        BoardGrid grid = GameContext.get().getActiveGrid();
        Coord best = request.candidates().get(0);
        int bestHealth = Integer.MAX_VALUE;
        for (Coord c : request.candidates()) {
            Entity e = grid == null ? null : grid.getEntityAt(c.row(), c.col());
            int health = e == null ? Integer.MAX_VALUE : EntityUtils.getCurrentHealth(e);
            if (health < bestHealth) {
                best = c;
                bestHealth = health;
            }
        }
        return best;
    }

    private static Coord busiestEnemyRow(PickRequest request) {
        BoardGrid grid = GameContext.get().getActiveGrid();
        Coord best = request.candidates().get(0);
        int bestCount = -1;
        for (Coord c : request.candidates()) {
            int count = 0;
            for (int col = 0; grid != null && col < grid.getCols(); col++) {
                Entity e = grid.getEntityAt(c.row(), col);
                PieceAlignment a = e == null ? null : EntityUtils.getAlignment(e);
                if (a != null && a != PieceAlignment.NEUTRAL && a != request.owner()) count++;
            }
            if (count > bestCount) {
                best = c;
                bestCount = count;
            }
        }
        return best;
    }

    private static Coord mostCentral(PickRequest request) {
        BoardGrid grid = GameContext.get().getActiveGrid();
        int center = grid == null ? 0 : grid.getCols() / 2;
        Coord best = request.candidates().get(0);
        for (Coord c : request.candidates()) {
            if (Math.abs(c.col() - center) < Math.abs(best.col() - center)) best = c;
        }
        return best;
    }
}
