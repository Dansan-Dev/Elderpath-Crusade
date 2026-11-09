package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.match.ActionResult.Reason;
import io.github.elderpath_crusade.server.GameServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ELDER-002's card-lock mechanic through the real MatchSetup/GameServer/PlayerManager pipeline —
 * a side with {@code MatchConfig.cardLockTurns()} > 0 gains no mana and can't play any card for
 * that many of its own turns, then plays normally from the turn after; card draw is unaffected
 * throughout. Headless, same bar as RelicCarrierTest/MatchSetupCardModificationTest.
 */
class CardLockTest {
    private static final PieceDefinition WOLF = new PieceDefinition("Wolf", 1, 3, 1, 1, 1, List.of());

    private GameServer server;

    @BeforeEach
    void setUp() {
        GameContext.createHeadless();
        PieceRegistry.register(WOLF);
        CardCatalog.clear();
        CardCatalog.registerSummon("Wolf", "Wolf");
        server = GameContext.get().getGameServer();
    }

    @AfterEach
    void tearDown() {
        CardCatalog.clear();
    }

    private void prepare(int p1LockTurns) {
        MatchConfig config = MatchConfig.standard(GameMode.ROUGELIKE_RUN,
                        List.of("Wolf", "Wolf", "Wolf", "Wolf"), List.of("Wolf", "Wolf", "Wolf", "Wolf"), 3L, Map.of())
                .withCardLockTurns(Map.of(PieceAlignment.P1, p1LockTurns));
        MatchSetup.prepare(config);
        MatchSetup.begin();
    }

    private PlayerManager.PlayerState p1() {
        return GameContext.get().getPlayerManager().get(PieceAlignment.P1);
    }

    private PlayerManager.PlayerState p2() {
        return GameContext.get().getPlayerManager().get(PieceAlignment.P2);
    }

    @Test
    void lockedSide_gainsNoManaOnTurnStart() {
        prepare(2);
        assertTrue(p1().isCardLocked());
        assertEquals(0, p1().getMana(), "turn 1 is locked (1 <= 2) — no mana gain");
    }

    @Test
    void lockedSide_stillDrawsAFullHand() {
        prepare(2);
        assertEquals(3, p1().hand.size(), "drawing continues during the lock — only mana/play are gated");
    }

    @Test
    void unlockedSide_gainsManaNormallyFromTurnOne() {
        prepare(0);
        assertFalse(p1().isCardLocked());
        assertEquals(1, p1().getMana());
    }

    @Test
    void lockedSide_cannotPlayACardEvenWithMana() {
        prepare(2);
        p1().setMana(10); // isolate: the lock blocks play regardless of mana on hand

        ActionResult result = server.playSummonCard(PieceAlignment.P1, firstHandCardId(p1()), 0, 2);

        assertEquals(Reason.CARDS_LOCKED, result.reason());
    }

    @Test
    void canPlay_isFalseWhileLockedEvenWithEnoughMana() {
        prepare(2);
        p1().setMana(10);
        assertFalse(GameContext.get().getCardRules().canPlay(PieceAlignment.P1, "Wolf"));
    }

    @Test
    void theOtherSidesOwnLockIsIndependent() {
        prepare(2); // P1 locked; P2 (unspecified) defaults to 0 — never locked
        assertFalse(p2().isCardLocked());

        server.endTurn(PieceAlignment.P1); // P2's own turn 1 now starts
        assertFalse(p2().isCardLocked());
        assertEquals(1, p2().getMana(), "P2 was never locked — gains mana on its own first turn");
    }

    @Test
    void sideUnlocksOnceItsOwnTurnCountExceedsTheLock() {
        prepare(2);
        // P1 turn1 (locked, turnsTaken=1) -> P2 turn -> P1 turn2 (locked, turnsTaken=2) -> P2 turn
        // -> P1 turn3 (turnsTaken=3 > 2, unlocked)
        server.endTurn(PieceAlignment.P1);
        server.endTurn(PieceAlignment.P2);
        assertTrue(p1().isCardLocked(), "P1's 2nd turn (turnsTaken=2) is still locked");
        assertEquals(0, p1().getMana());

        server.endTurn(PieceAlignment.P1);
        server.endTurn(PieceAlignment.P2);
        assertFalse(p1().isCardLocked(), "P1's 3rd turn (turnsTaken=3) is unlocked");
        assertEquals(1, p1().getMana(), "mana gain resumed on the unlocking turn");

        ActionResult result = server.playSummonCard(PieceAlignment.P1, firstHandCardId(p1()), 0, 2);
        assertTrue(result.isOk(), "expected ok, got " + result);
    }

    private static String firstHandCardId(PlayerManager.PlayerState player) {
        return player.hand.getCards().get(0).instanceId();
    }
}
