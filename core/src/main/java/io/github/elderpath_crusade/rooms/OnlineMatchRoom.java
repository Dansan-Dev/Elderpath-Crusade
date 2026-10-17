package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.interfaces.Updatable;
import io.github.elderpath_crusade.multiplayer.net.NetworkCommand;
import io.github.elderpath_crusade.server.ActionDispatcher;
import io.github.elderpath_crusade.server.ReplicaEventApplier;
import io.github.elderpath_crusade.supers.LowestOrderTexture;

import java.util.ArrayList;
import java.util.List;

/**
 * A tactical battle over the network. The host runs the real simulation exactly like local
 * play (see GameServer) — its own alignment is always P1. The guest is a thin client: its
 * clicks route to the host via ActionDispatcher/NetworkCommand, and its board/hand state is
 * mirrored from the host's relayed events via ReplicaEventApplier — its own alignment is
 * always P2. See ReplicaEventApplier's class doc for what is and isn't reconstructed.
 *
 * Skips the draft flow for this prototype — both sides start with the same fixed deck
 * (2x Wolf + 2x Wolf Cub, matching DraftRoom's starting deck).
 */
public class OnlineMatchRoom extends BattleRoom {
    private static final int STARTING_WOLVES = 2;
    private static final int STARTING_WOLF_CUBS = 2;

    private OnlineMatchRoom() {
        super(GameMode.ONLINE_MATCH);

        if (GameContext.get().getOnlineMatch().isGuest()) {
            ReplicaEventApplier applier = new ReplicaEventApplier(handSyncP1, handSyncP2);
            GameContext.get().getOnlineMatch().getClient().addListener(applier::apply);
            GameContext.get().getOnlineMatch().getClient().addSnapshotListener(applier::applySnapshot);
        }

        layoutBoard();
        addContent(new HandVisibilityEnforcer(handP1, handP2));
    }

    /**
     * Unlike hotseat (LocalMatchRoom), each screen has exactly one local human — so hand
     * visibility doesn't need to track whose turn it is, only whose screen this is: my own
     * hand is always visible, the opponent's is always hidden. This has to be enforced every
     * frame, not just once at construction — every newly created Card defaults to face-up
     * (Card's constructor sets faceUp = true), and new cards keep getting added to hands for
     * the rest of the match (via HandSync, from the host's own draws for both alignments, and
     * on the guest from relayed CardDrawnEvents) — a one-time pass at room
     * construction only ever covered the starting hand.
     */
    private static final class HandVisibilityEnforcer extends LowestOrderTexture implements Renderable, Updatable {
        private final Hand handP1;
        private final Hand handP2;

        HandVisibilityEnforcer(Hand handP1, Hand handP2) {
            this.handP1 = handP1;
            this.handP2 = handP2;
            setBounds(new Box(0, 0, 0, 0));
        }

        @Override
        public List<Integer> getZs() {
            return List.of();
        }

        @Override
        public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        }

        @Override
        public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        }

        @Override
        public void update(float delta) {
            PieceAlignment local = GameContext.get().getOnlineMatch().getLocalAlignment();
            if (local == null) return;
            applyTo(handP1, local == PieceAlignment.P1);
            applyTo(handP2, local == PieceAlignment.P2);
        }

        private void applyTo(Hand hand, boolean visible) {
            for (Card c : hand.getCards()) {
                if (visible && !c.isFaceUp()) c.showFront();
                else if (!visible && c.isFaceUp()) c.showBack();
            }
        }
    }

    /** Each online screen is oriented for its own side (the guest plays P2 from the bottom). */
    @Override
    protected PieceAlignment viewerAlignment() {
        PieceAlignment local = GameContext.get().getOnlineMatch().getLocalAlignment();
        return local != null ? local : PieceAlignment.P1;
    }

    @Override
    protected void onPassTurnClicked() {
        PieceAlignment local = GameContext.get().getOnlineMatch().getLocalAlignment();
        if (local == null) return;
        ActionDispatcher.dispatch(
                () -> new NetworkCommand.EndTurn(local),
                () -> {
                    GameContext.get().getTurnManager().endTurn();
                    return true;
                }
        );
    }

    @Override
    protected List<String> deckCardNames(PieceAlignment alignment) {
        List<String> names = new ArrayList<>();
        // Only the host's decks are real — the guest never draws locally; both hands are
        // mirrored from the host's relayed card events (see ReplicaEventApplier).
        if (GameContext.get().getOnlineMatch().isHost()) {
            for (int i = 0; i < STARTING_WOLVES; i++) names.add("Wolf");
            for (int i = 0; i < STARTING_WOLF_CUBS; i++) names.add("Wolf Cub");
        }
        return names;
    }

    @Override
    protected boolean mirrorsRemoteMatch() {
        return GameContext.get().getOnlineMatch().isGuest();
    }

    public static OnlineMatchRoom get() {
        return new OnlineMatchRoom();
    }
}
