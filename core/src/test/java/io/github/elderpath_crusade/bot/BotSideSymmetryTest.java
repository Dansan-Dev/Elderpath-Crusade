package io.github.elderpath_crusade.bot;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.factory.PieceFactory;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.match.MatchSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * BUG-023 regression: bots play either side identically. Each bot plays one turn from a
 * position as P1, then from the mirror-image position (rows flipped, sides swapped) as P2 —
 * its actions must be the mirror image too. The map is symmetric (mountains mirrored), so
 * any difference would be a leftover side assumption in the bot.
 *
 * (SmartBot vs SmartBot being lopsided on the standard map is the map, not the bot: its two
 * mountains sit on row 2, closer to P1's home row. On symmetric maps the first player wins
 * most mirror matches instead — see the ARCH-024 report.)
 */
class BotSideSymmetryTest {
    private static final int ROWS = 7;
    private static final List<Coord> SYMMETRIC_TERRAIN =
            List.of(new Coord(2, 1), new Coord(2, 3), new Coord(4, 1), new Coord(4, 3));
    private static final PieceDefinition RUNNER = new PieceDefinition("Runner", 1, 3, 1, 2, 1, List.of());

    /** A piece placed before the bot's turn, as seen from P1's side ("own" = the bot's). */
    private record Placed(boolean own, int row, int col) {}

    private static final List<List<Placed>> POSITIONS = List.of(
            List.of(), // empty board: just a summon
            List.of(new Placed(true, 1, 0), new Placed(false, 5, 4)), // far apart
            List.of(new Placed(true, 2, 2), new Placed(false, 4, 2)), // facing, two apart (BUG-023's turning point)
            List.of(new Placed(true, 3, 2), new Placed(false, 4, 2)), // adjacent: attack
            List.of(new Placed(true, 4, 0), new Placed(true, 1, 4), new Placed(false, 5, 0), new Placed(false, 3, 3)), // mixed
            List.of(new Placed(true, 5, 2), new Placed(false, 6, 2))  // blocked one step from the goal row
    );

    @AfterEach
    void tearDown() {
        CardCatalog.clear();
        TypedEventBus.get().clear();
    }

    @Test
    void smartBot_playsTheMirrorPositionAsTheMirrorMove() {
        assertSymmetric(BotKind.SMART);
    }

    @Test
    void basicBot_playsTheMirrorPositionAsTheMirrorMove() {
        assertSymmetric(BotKind.BASIC);
    }

    private static void assertSymmetric(BotKind kind) {
        for (int i = 0; i < POSITIONS.size(); i++) {
            List<String> asP1 = playOneTurn(kind, POSITIONS.get(i), PieceAlignment.P1);
            List<String> asP2 = playOneTurn(kind, POSITIONS.get(i), PieceAlignment.P2);
            assertFalse(asP1.isEmpty(), kind + " position " + i + ": the bot did something");
            assertEquals(asP1, asP2, kind + " position " + i + ": P2's turn (mirrored back) differs from P1's");
        }
    }

    /**
     * Plays the bot's first turn as side from the position (mirrored when side is P2) and
     * returns its actions in P1's frame (rows mirrored back for P2), so the two can be compared.
     */
    private static List<String> playOneTurn(BotKind kind, List<Placed> position, PieceAlignment side) {
        TypedEventBus.get().clear();
        GameContext.createHeadless();
        PieceRegistry.register(RUNNER);
        CardCatalog.clear();
        CardCatalog.registerSummon("Wolf", "Runner"); // BasicBot only ever summons a card named "Wolf"

        PieceAlignment other = side == PieceAlignment.P1 ? PieceAlignment.P2 : PieceAlignment.P1;
        List<String> deck = new ArrayList<>(Collections.nCopies(12, "Wolf"));
        BoardGrid grid = MatchSetup.prepare(new MatchConfig(ROWS, 5, SYMMETRIC_TERRAIN,
                Map.of(PieceAlignment.P1, deck, PieceAlignment.P2, new ArrayList<>(deck)), 1L, GameMode.DEMO,
                Map.of(side, kind)));
        for (Placed p : position) {
            int row = side == PieceAlignment.P1 ? p.row() : ROWS - 1 - p.row();
            Entity e = PieceFactory.createPiece(RUNNER, p.own() ? side : other, row, p.col());
            grid.addEntity(row, p.col(), e);
        }

        List<String> actions = new ArrayList<>();
        boolean[] done = new boolean[1];
        TypedEventBus bus = TypedEventBus.get();
        bus.register(PieceMovedEvent.class, e -> {
            if (e.owner() == side) actions.add("move " + frame(side, e.fromRow()) + "," + e.fromCol() + "->" + frame(side, e.toRow()) + "," + e.toCol());
        });
        bus.register(PieceAttackedEvent.class, e -> {
            if (e.attackerOwner() == side) actions.add("attack " + frame(side, e.defenderRow()) + "," + e.defenderCol());
        });
        bus.register(PieceSpawnedEvent.class, e -> {
            if (e.owner() == side) actions.add("summon " + frame(side, e.row()) + "," + e.col());
        });
        bus.register(TurnEndedEvent.class, e -> {
            if (e.player() == side) done[0] = true;
        });

        MatchSetup.begin();
        if (side == PieceAlignment.P2) GameContext.get().getGameServer().endTurn(PieceAlignment.P1);
        for (int i = 0; i < 10_000 && !done[0]; i++) MatchSetup.tick(0.05f);
        return actions;
    }

    private static int frame(PieceAlignment side, int row) {
        return side == PieceAlignment.P1 ? row : ROWS - 1 - row;
    }
}
