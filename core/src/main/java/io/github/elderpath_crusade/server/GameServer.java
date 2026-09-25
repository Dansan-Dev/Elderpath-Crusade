package io.github.elderpath_crusade.server;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.game_objects.cards.SpellCard;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;

import java.util.List;

/**
 * Single authoritative entry point for game actions (move/attack, card play), taking
 * plain data (board coordinates, hand indices) rather than live click objects, so it can
 * be called identically from a local click handler (in-process, same JVM) or a network
 * command handler (received from a remote client) — see elderpath-multiplayer-events.md.
 *
 * Every mode is meant to route action execution through this class. It does not replace
 * click detection/hit-testing (still an inherently per-machine rendering concern) — only
 * the "now actually do it" step.
 */
public class GameServer {

    /** Moves or attacks with the piece at (srcRow, srcCol) toward (dstRow, dstCol). Returns true on success. */
    public boolean movePlot(int srcRow, int srcCol, int dstRow, int dstCol) {
        Board board = GameContext.get().getActiveBoard();
        if (board == null) return false;
        return board.movePiece(srcRow, srcCol, dstRow, dstCol);
    }

    /** Plays the summon card at hand index handIndex onto (row, col). Returns true on success. */
    public boolean playSummonCard(PieceAlignment alignment, int handIndex, int row, int col) {
        SummonCard card = resolveHandCard(alignment, handIndex, SummonCard.class);
        if (card == null) return false;
        return card.executeSummon(row, col);
    }

    /** Plays the spell card at hand index handIndex, targeting (targetRow, targetCol) — null/null for an untargeted spell. */
    public boolean playSpellCard(PieceAlignment alignment, int handIndex, Integer targetRow, Integer targetCol) {
        SpellCard card = resolveHandCard(alignment, handIndex, SpellCard.class);
        if (card == null) return false;
        return card.executeSpell(targetRow, targetCol);
    }

    private <T extends Card> T resolveHandCard(PieceAlignment alignment, int handIndex, Class<T> type) {
        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(alignment);
        Hand hand = playerState == null ? null : playerState.hand;
        if (hand == null) return null;
        List<Card> cards = hand.getCards();
        if (handIndex < 0 || handIndex >= cards.size()) return null;
        Card card = cards.get(handIndex);
        return type.isInstance(card) ? type.cast(card) : null;
    }
}
