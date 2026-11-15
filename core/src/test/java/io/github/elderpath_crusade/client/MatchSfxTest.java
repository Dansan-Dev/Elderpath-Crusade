package io.github.elderpath_crusade.client;

import io.github.elderpath_crusade.audio.SoundEffect;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.match.RelicCarrierIds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static io.github.elderpath_crusade.enums.PieceAlignment.P1;
import static io.github.elderpath_crusade.enums.PieceAlignment.P2;
import static org.junit.jupiter.api.Assertions.*;

/** FEAT-017: which client-side match events make which sound. Pure, no Gdx. */
class MatchSfxTest {
    private final List<SoundEffect> played = new ArrayList<>();
    private final Predicate<PieceAlignment> controlsP1 = side -> side == P1;
    private final MatchSfx sfx = new MatchSfx(played::add, controlsP1);

    @Test
    void boardEvents_mapToTheirSounds() {
        sfx.onEvent(new PieceMovedEvent("a", P1, 0, 0, 1, 0, PieceMovedEvent.MovementType.ACTIVE, null));
        sfx.onEvent(new PieceMovedEvent("a", P2, 1, 0, 2, 0, PieceMovedEvent.MovementType.FORCED, "Push"));
        sfx.onEvent(new PieceAttackedEvent("a", P1, 0, 0, "b", 1, 0, 2, List.of(), null));
        sfx.onEvent(new PieceDamagedEvent("b", P2, 1, 0, 1));
        sfx.onEvent(new PieceHealedEvent("b", P2, 1, 0, 1));
        sfx.onEvent(new PieceDiedEvent("b", 1, 0));
        sfx.onEvent(new PieceSpawnedEvent("c", P2, 6, 2, "Wolf"));

        assertEquals(List.of(SoundEffect.MOVE, SoundEffect.FORCED_MOVE, SoundEffect.ATTACK, SoundEffect.DAMAGE,
                SoundEffect.HEAL, SoundEffect.DEATH, SoundEffect.SUMMON), played);
    }

    @Test
    void onlySpellsSoundOnCardPlayed_summonsSoundThroughTheirSpawn() {
        sfx.onEvent(new CardPlayedEvent("Wolf", P1, 0, 2, "piece-1", "P1-0"));
        sfx.onEvent(new CardPlayedEvent("Fireball", P1, 3, 2, null, "P1-1"));
        assertEquals(List.of(SoundEffect.SPELL_CAST), played);
    }

    @Test
    void drawsShufflesAndTurnStarts_onlyForSeatsThisScreenPlays() {
        sfx.onEvent(new CardDrawnEvent(P2, "hidden-1", null, 3));
        sfx.onEvent(new CardShuffledEvent(P2, 5));
        sfx.onEvent(new TurnStartedEvent(P2));
        assertTrue(played.isEmpty(), "the opponent's draws/shuffles/turns are silent");

        sfx.onEvent(new CardDrawnEvent(P1, "P1-3", "Wolf", 4));
        sfx.onEvent(new CardShuffledEvent(P1, 5));
        sfx.onEvent(new TurnStartedEvent(P1));
        assertEquals(List.of(SoundEffect.CARD_DRAW, SoundEffect.SHUFFLE, SoundEffect.YOUR_TURN), played);
    }

    @Test
    void relicCarrierSpawn_andBookkeepingEvents_areSilent() {
        sfx.onEvent(new PieceSpawnedEvent(RelicCarrierIds.idFor(P1), P1, -1, 0, "RelicCarrier"));
        sfx.onEvent(new ManaChangedEvent(P1, 3));
        sfx.onEvent(new ActionsResetEvent(P1));
        sfx.onEvent(new TurnEndedEvent(P1));
        assertTrue(played.isEmpty());
    }

    @Test
    void endOfMatch_victoryForOurWinOrNoSeat_defeatOtherwise() {
        assertEquals(SoundEffect.VICTORY, MatchSfx.endOfMatch(P1, controlsP1, true));
        assertEquals(SoundEffect.DEFEAT, MatchSfx.endOfMatch(P2, controlsP1, true));
        assertEquals(SoundEffect.DEFEAT, MatchSfx.endOfMatch(null, controlsP1, true), "a draw is no win");
        assertEquals(SoundEffect.VICTORY, MatchSfx.endOfMatch(P2, side -> false, false), "bot vs bot: nothing lost");
    }
}
