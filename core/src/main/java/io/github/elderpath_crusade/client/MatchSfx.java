package io.github.elderpath_crusade.client;

import io.github.elderpath_crusade.audio.SoundEffect;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.CardDrawnEvent;
import io.github.elderpath_crusade.events.CardPlayedEvent;
import io.github.elderpath_crusade.events.CardShuffledEvent;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.events.PieceAttackedEvent;
import io.github.elderpath_crusade.events.PieceDamagedEvent;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.events.PieceHealedEvent;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.match.RelicCarrierIds;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * FEAT-017: turns the match events a client receives into sound effects. Registered on exactly
 * one seat's {@link ClientMatchState} per screen ({@code BattleRoom}) — hotseat has two
 * connections that both receive every public event, and listening on both would double every
 * sound. "Ours" (draws, shuffles, turn start) is decided by {@code controls}, i.e. any seat this
 * screen plays, so hotseat hears both players' turns. Deliberately silent: mana/action
 * bookkeeping, discards, turn end, opponent draws — frequent and low-payoff.
 *
 * <p>Pure (no Gdx) — plays through a {@code Consumer<SoundEffect>}, tested with a recorder.
 * Victory/defeat isn't here: {@link ClientController} already detects the end exactly once, and
 * {@link #endOfMatch} gives it the right effect.
 */
public final class MatchSfx {
    private final Consumer<SoundEffect> player;
    private final Predicate<PieceAlignment> controls;

    public MatchSfx(Consumer<SoundEffect> player, Predicate<PieceAlignment> controls) {
        this.player = Objects.requireNonNull(player);
        this.controls = Objects.requireNonNull(controls);
    }

    public void onEvent(GameEvent event) {
        SoundEffect effect = effectFor(event);
        if (effect != null) player.accept(effect);
    }

    SoundEffect effectFor(GameEvent event) {
        if (event instanceof PieceMovedEvent e) {
            return e.movementType() == PieceMovedEvent.MovementType.FORCED ? SoundEffect.FORCED_MOVE : SoundEffect.MOVE;
        }
        if (event instanceof PieceAttackedEvent) return SoundEffect.ATTACK;
        if (event instanceof PieceDamagedEvent) return SoundEffect.DAMAGE;
        if (event instanceof PieceHealedEvent) return SoundEffect.HEAL;
        if (event instanceof PieceDiedEvent) return SoundEffect.DEATH;
        if (event instanceof PieceSpawnedEvent e) {
            return RelicCarrierIds.isRelicCarrier(e.pieceId()) ? null : SoundEffect.SUMMON;
        }
        // A summon card already sounds through its PieceSpawnedEvent — only spells get their own.
        if (event instanceof CardPlayedEvent e) return e.pieceId() == null ? SoundEffect.SPELL_CAST : null;
        if (event instanceof CardDrawnEvent e) return controls.test(e.owner()) ? SoundEffect.CARD_DRAW : null;
        if (event instanceof CardShuffledEvent e) return controls.test(e.owner()) ? SoundEffect.SHUFFLE : null;
        if (event instanceof TurnStartedEvent e) return controls.test(e.player()) ? SoundEffect.YOUR_TURN : null;
        return null;
    }

    /**
     * The end-of-match sound for {@code winner}: victory if this screen plays the winning side —
     * or plays no side at all (bot vs bot, nothing to lose) — otherwise defeat. A draw (null
     * winner) is a defeat sting only if this screen played a side.
     */
    public static SoundEffect endOfMatch(PieceAlignment winner, Predicate<PieceAlignment> controls, boolean controlsAnySeat) {
        if (!controlsAnySeat) return SoundEffect.VICTORY;
        return winner != null && controls.test(winner) ? SoundEffect.VICTORY : SoundEffect.DEFEAT;
    }
}
