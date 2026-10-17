package io.github.elderpath_crusade.server;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.CardDiscardedEvent;
import io.github.elderpath_crusade.events.CardDrawnEvent;
import io.github.elderpath_crusade.events.CardPlayedEvent;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.match.CardInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What one viewer may learn about the opponent's cards (BUG-021). The rules:
 * - The viewer's own cards are sent as they are (instance id + name).
 * - An opponent's card in hand is a fresh opaque handle ("hidden-n") with no name. The
 *   handle is new per draw, so it reveals nothing: the simulation's instance ids follow
 *   deck-build order and survive reshuffles, so sending them would tell a viewer who
 *   knows the decklist which card was drawn.
 * - Playing a card is public: CardPlayedEvent keeps its name, under the card's handle.
 * - Deck contents and order are never sent (CardShuffledEvent only carries a count).
 * - Everything else (pieces, mana, turns, combat) is public.
 * Picks go only to their owner (MatchServer's seat responders), so they aren't filtered here.
 */
final class HiddenInfoFilter {
    private final PieceAlignment viewer;
    private final Map<String, String> handles = new HashMap<>();
    private long nextHandle = 1;

    HiddenInfoFilter(PieceAlignment viewer) {
        this.viewer = viewer;
    }

    /** The event as viewer may see it. */
    GameEvent filter(GameEvent event) {
        if (event instanceof CardDrawnEvent e && e.owner() != viewer) {
            return new CardDrawnEvent(e.owner(), handleFor(e.instanceId()), null, e.handSize());
        }
        if (event instanceof CardPlayedEvent e && e.owner() != viewer) {
            return new CardPlayedEvent(e.cardName(), e.owner(), e.row(), e.col(), e.pieceId(), release(e.instanceId()));
        }
        if (event instanceof CardDiscardedEvent e && e.player() != viewer) {
            List<String> ids = new ArrayList<>();
            for (String id : e.instanceIds()) ids.add(release(id));
            return new CardDiscardedEvent(e.player(), e.count(), ids);
        }
        return event;
    }

    /** The opponent's hand as handles, in order — for a snapshot. */
    List<String> opponentHand(List<CardInstance> hand) {
        List<String> out = new ArrayList<>();
        for (CardInstance card : hand) out.add(handleFor(card.instanceId()));
        return out;
    }

    private String handleFor(String instanceId) {
        return handles.computeIfAbsent(instanceId, id -> "hidden-" + nextHandle++);
    }

    /** The card's handle, forgotten now that it left the hand (it gets a new one if drawn again). */
    private String release(String instanceId) {
        String handle = handles.remove(instanceId);
        return handle != null ? handle : "hidden-" + nextHandle++;
    }
}
