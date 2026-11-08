package io.github.elderpath_crusade.client;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.interaction.LocalUiPickPrompt;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.LegalActions;
import io.github.elderpath_crusade.match.PickRequest;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Turns a replica's state + {@link LegalActions} into view gating, and clicks into commands —
 * the pure logic between {@link ClientMatchState}/{@link ClientMatchSession} and the
 * renderables. Owns no rules of its own: what's clickable comes from {@code LegalActions},
 * what a click does is a direct {@link ClientCommands} call the server will re-validate.
 *
 * <p>Also owns the two things nothing else translates from server messages yet: a newly
 * arrived {@link PickRequest} becomes a pick prompt, and the match ending becomes the victory
 * screen. Both side effects are behind small callbacks so this class stays testable without a
 * live session, a board, or a GameContext — see {@link #update()}.
 */
public final class ClientController {

    /** Shows/clears a pick prompt. Production wiring: {@link #localUiPickPrompter()}. */
    public interface PickPrompter {
        /** Returns false if it couldn't be shown (e.g. no board yet) — the pick is cancelled instead. */
        boolean show(PickRequest request, Consumer<Coord> onChosen, Runnable onCancelled);

        void clear();
    }

    private final Supplier<ClientMatchState> stateSupplier;
    private final ClientCommands commands;
    private final PickPrompter pickPrompter;
    private final Consumer<PieceAlignment> victoryPresenter;

    private Long lastSeenPickId;
    private boolean victoryShown;
    private boolean handoffGated;

    public ClientController(Supplier<ClientMatchState> stateSupplier, ClientCommands commands,
            PickPrompter pickPrompter, Consumer<PieceAlignment> victoryPresenter) {
        this.stateSupplier = Objects.requireNonNull(stateSupplier);
        this.commands = Objects.requireNonNull(commands);
        this.pickPrompter = Objects.requireNonNull(pickPrompter);
        this.victoryPresenter = Objects.requireNonNull(victoryPresenter);
    }

    /** The real pick prompt, backed by {@code GameContext.get().getActiveBoard()}. */
    public static PickPrompter localUiPickPrompter() {
        return new PickPrompter() {
            @Override
            public boolean show(PickRequest request, Consumer<Coord> onChosen, Runnable onCancelled) {
                return LocalUiPickPrompt.show(request, onChosen, onCancelled);
            }

            @Override
            public void clear() {
                LocalUiPickPrompt.clear();
            }
        };
    }

    /**
     * Call once per poll, after the session/state has been refreshed for this frame. Detects a
     * newly requested pick (prompts for it) or one that's gone away without our answer (clears
     * any showing prompt), and detects the match ending (shows the victory screen once).
     */
    public void update() {
        ClientMatchState state = stateSupplier.get();
        if (state == null) return;

        PickRequest pick = state.getPendingPick();
        Long pickId = pick == null ? null : pick.pickId();
        if (!Objects.equals(pickId, lastSeenPickId)) {
            pickPrompter.clear();
            lastSeenPickId = pickId;
            if (pick != null) {
                boolean shown = pickPrompter.show(pick, commands::answerPick, commands::cancelPick);
                if (!shown) commands.cancelPick();
            }
        }

        if (state.isEnded()) {
            if (!victoryShown) {
                victoryShown = true;
                victoryPresenter.accept(state.getWinner());
            }
        } else {
            victoryShown = false;
        }
    }

    /**
     * Blocks every gate that reads {@link #legalActions()} (pieces, cards, abilities, move/
     * attack highlights) without touching {@link #canEndTurn()} — set by a hotseat room while
     * the next player's hand is hidden behind a "Start Turn" click, so the turn that's already
     * genuinely active server-side can't be played blind through the previous player's screen.
     */
    public void setHandoffGated(boolean gated) {
        this.handoffGated = gated;
    }

    /** What this screen's acting seat may currently do, or null before the first update from the server, or while {@link #setHandoffGated} is up. */
    public LegalActions legalActions() {
        return handoffGated ? null : rawLegalActions();
    }

    public boolean canEndTurn() {
        LegalActions actions = rawLegalActions();
        return actions != null && actions.canEndTurn();
    }

    /** True while this screen's acting seat has a pick outstanding — nothing else should be clickable. */
    public boolean hasPendingPick() {
        return lastSeenPickId != null;
    }

    private LegalActions rawLegalActions() {
        ClientMatchState state = stateSupplier.get();
        return state == null ? null : state.getLegalActions();
    }

    public long moveOrAttack(int srcRow, int srcCol, int dstRow, int dstCol) {
        return commands.moveOrAttack(srcRow, srcCol, dstRow, dstCol);
    }

    public long playSummon(String instanceId, int row, int col) {
        return commands.playSummon(instanceId, row, col);
    }

    public long playSpell(String instanceId, Integer targetRow, Integer targetCol) {
        return commands.playSpell(instanceId, targetRow, targetCol);
    }

    public long activateAbility(String pieceId, int actionIndex) {
        return commands.activateAbility(pieceId, actionIndex);
    }

    public long endTurn() {
        return commands.endTurn();
    }
}
