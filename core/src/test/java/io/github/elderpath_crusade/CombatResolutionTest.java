package io.github.elderpath_crusade;

import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.game_piece.MonsterGamePiece;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEvent;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import io.github.elderpath_crusade.utils.AbilityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CombatResolutionTest {

    private Board board;
    private MonsterGamePiece attacker;
    private MonsterGamePiece defender;

    @BeforeEach
    void setUp() {
        board = mock(Board.class);

        // Attacker: 3 damage, 5 hp
        attacker = new MonsterGamePiece(
            GamePieceStats.getMonsterStats(1, 5, 3, 2, 1),
            GamePieceType.MONSTER, PieceAlignment.P1, UUID.randomUUID(), null
        );

        // Defender: 2 damage, 4 hp
        defender = new MonsterGamePiece(
            GamePieceStats.getMonsterStats(1, 4, 2, 2, 1),
            GamePieceType.MONSTER, PieceAlignment.P2, UUID.randomUUID(), null
        );

        // Give defender a position so performAttack can resolve it
        Board.Position defPos = new Board.Position(board, 1, 0);
        defender.updateData(GamePieceData.POSITION, defPos);
    }

    @Test
    void attackDealsDamageToDefender() {
        boolean result = AbilityUtils.performAttack(board, attacker, defender, 0, 0, 1, 0);

        assertTrue(result);
        assertEquals(1, defender.getStats().getCurrentHealth()); // 4 - 3 = 1
    }

    @Test
    void attackKillsDefenderWhenHealthReachesZero() {
        // Defender has 4 hp, attacker deals 3. Hit twice via dealDamage first.
        defender.getStats().dealDamage(1); // now 3 hp

        AbilityUtils.performAttack(board, attacker, defender, 0, 0, 1, 0);

        assertTrue(defender.getStats().isDead());
        verify(board).removeGamePieceAtPos(1, 0);
    }

    @Test
    void attackEmitsPieceAttackedEvent() {
        List<GameEvent> captured = new ArrayList<>();
        Consumer<GameEvent> listener = captured::add;
        EventBus.register(GameEventType.PIECE_ATTACKED, listener);

        try {
            AbilityUtils.performAttack(board, attacker, defender, 0, 0, 1, 0);

            assertEquals(1, captured.size());
            GameEvent evt = captured.get(0);
            assertEquals(attacker.getId().toString(), evt.getData().get("attackerId"));
            assertEquals(defender.getId().toString(), evt.getData().get("defenderId"));
            assertEquals(3, evt.getData().get("damage"));
        } finally {
            EventBus.unregister(GameEventType.PIECE_ATTACKED, listener);
        }
    }

    @Test
    void attackEmitsPieceDiedEventOnKill() {
        // Set defender to 2 hp so 3 damage kills
        defender.getStats().dealDamage(2);

        List<GameEvent> captured = new ArrayList<>();
        Consumer<GameEvent> listener = captured::add;
        EventBus.register(GameEventType.PIECE_DIED, listener);

        try {
            AbilityUtils.performAttack(board, attacker, defender, 0, 0, 1, 0);

            assertEquals(1, captured.size());
            assertEquals(defender.getId().toString(), captured.get(0).getData().get("pieceId"));
        } finally {
            EventBus.unregister(GameEventType.PIECE_DIED, listener);
        }
    }

    @Test
    void attackDoesNotEmitDiedEventWhenDefenderSurvives() {
        List<GameEvent> captured = new ArrayList<>();
        Consumer<GameEvent> listener = captured::add;
        EventBus.register(GameEventType.PIECE_DIED, listener);

        try {
            AbilityUtils.performAttack(board, attacker, defender, 0, 0, 1, 0);

            assertTrue(captured.isEmpty());
        } finally {
            EventBus.unregister(GameEventType.PIECE_DIED, listener);
        }
    }

    @Test
    void attackReturnsFalseWhenAttackerIsDefender() {
        boolean result = AbilityUtils.performAttack(board, attacker, attacker, 0, 0, 0, 0);

        assertFalse(result);
        assertEquals(5, attacker.getStats().getCurrentHealth()); // no damage dealt
    }

    @Test
    void attackReturnsFalseWithNullArguments() {
        assertFalse(AbilityUtils.performAttack(null, attacker, defender, 0, 0, 1, 0));
        assertFalse(AbilityUtils.performAttack(board, null, defender, 0, 0, 1, 0));
        assertFalse(AbilityUtils.performAttack(board, attacker, null, 0, 0, 1, 0));
    }

    @Test
    void dealDamageReturnsTrueWhenTargetSurvives() {
        boolean alive = AbilityUtils.dealDamage(defender, 1, attacker, true);

        assertTrue(alive);
        assertEquals(3, defender.getStats().getCurrentHealth());
    }

    @Test
    void dealDamageReturnsFalseAndEmitsDiedWhenTargetDies() {
        List<GameEvent> captured = new ArrayList<>();
        Consumer<GameEvent> listener = captured::add;
        EventBus.register(GameEventType.PIECE_DIED, listener);

        try {
            boolean alive = AbilityUtils.dealDamage(defender, 10, attacker, true);

            assertFalse(alive);
            assertEquals(1, captured.size());
        } finally {
            EventBus.unregister(GameEventType.PIECE_DIED, listener);
        }
    }

    @Test
    void spendActionDecrementsAndEmitsEvent() {
        attacker.getStats().setRemainingActions(2);

        List<GameEvent> captured = new ArrayList<>();
        Consumer<GameEvent> listener = captured::add;
        EventBus.register(GameEventType.ACTION_SPENT, listener);

        try {
            AbilityUtils.spendAction(attacker);

            assertEquals(1, attacker.getStats().getRemainingActions());
            assertEquals(1, captured.size());
            assertEquals(1, captured.get(0).getData().get("remaining"));
        } finally {
            EventBus.unregister(GameEventType.ACTION_SPENT, listener);
        }
    }

    @Test
    void spendActionNeverGoesBelowZero() {
        attacker.getStats().setRemainingActions(0);

        AbilityUtils.spendAction(attacker);

        assertEquals(0, attacker.getStats().getRemainingActions());
    }
}
