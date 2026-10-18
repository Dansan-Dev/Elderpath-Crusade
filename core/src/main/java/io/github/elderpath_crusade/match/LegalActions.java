package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.List;

/**
 * Everything one seat may do right now, as the server would accept it (see
 * LegalActionsCalculator) — what a client highlights and offers, with no rules of its own.
 * Anything not listed is rejected. While the seat has a pick pending, only answering it is
 * allowed: every list is empty and pendingPickId names the pick.
 *
 * Pieces and cards appear only if they have at least one option. Coordinates are row-major
 * sorted, pieces by id, cards in hand order — so two calculations of the same state are equal.
 */
public record LegalActions(
        PieceAlignment seat,
        /** It's this seat's turn (it may still have nothing left to do). */
        boolean yourTurn,
        Long pendingPickId,
        boolean canEndTurn,
        List<PieceActions> pieces,
        List<CardActions> cards
) {
    public LegalActions {
        pieces = List.copyOf(pieces);
        cards = List.copyOf(cards);
    }

    /** One own piece's options: empty destinations to move to, enemy squares to attack, abilities to activate. */
    public record PieceActions(String pieceId, Coord from, List<Coord> moves, List<Coord> attacks,
                               List<AbilityOption> abilities) {
        public PieceActions {
            moves = List.copyOf(moves);
            attacks = List.copyOf(attacks);
            abilities = List.copyOf(abilities);
        }
    }

    /**
     * An activatable ability: actionIndex as GameServer.activateAbility takes it (the bubble
     * number minus one). targeted = it asks for a pick once activated (Choose*); its
     * candidates arrive with that PickRequested.
     */
    public record AbilityOption(int actionIndex, String abilityId, boolean targeted) {}

    /**
     * A card in hand that can be played: onto/at one of targets (summon tiles, or the pieces a
     * spell may target), or — untargeted spell — with no target at all.
     */
    public record CardActions(String instanceId, boolean untargeted, List<Coord> targets) {
        public CardActions {
            targets = List.copyOf(targets);
        }
    }

    /** Nothing at all — not this seat's turn, match not running. */
    public static LegalActions none(PieceAlignment seat) {
        return new LegalActions(seat, false, null, false, List.of(), List.of());
    }

    /** This piece's options, or null if it has none. */
    public PieceActions forPiece(String pieceId) {
        for (PieceActions p : pieces) if (p.pieceId().equals(pieceId)) return p;
        return null;
    }

    /** This card's options, or null if it can't be played. */
    public CardActions forCard(String instanceId) {
        for (CardActions c : cards) if (c.instanceId().equals(instanceId)) return c;
        return null;
    }
}
