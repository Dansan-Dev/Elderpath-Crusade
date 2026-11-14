package io.github.elderpath_crusade.ui_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.ClientMatchState;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.match.ActionResult;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import io.github.elderpath_crusade.supers.LowestOrderTexture;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.HashMap;
import java.util.Map;

/**
 * FEAT-015/B3: a rejected click (wrong turn, not enough mana, a piece that already acted, ...)
 * was completely silent before this — ClientMatchState.lastRejection was already tracked (used
 * only by tests, confirmed by checking: nothing in the real UI read it). Shows a short-lived
 * banner naming what went wrong for every new Rejected this screen's viewed seat receives.
 *
 * Audio feedback for the same gap (a rejection "denial" buzz) is covered by plans/sfx-plan.md —
 * deliberately not attempted here since no SFX asset/playback pipeline exists yet to pair it
 * with; this is the visual half only.
 */
public class RejectionToast extends LowestOrderTexture implements UIRenderable {
    private static final float DISPLAY_SECONDS = 2.5f;
    private static final int Z = 30;
    private static final int TOP_MARGIN = 80;

    private static final Map<ActionResult.Reason, String> MESSAGES = Map.ofEntries(
            Map.entry(ActionResult.Reason.NO_ACTIVE_MATCH, "No active match"),
            Map.entry(ActionResult.Reason.NOT_YOUR_TURN, "It's not your turn"),
            Map.entry(ActionResult.Reason.OUT_OF_BOUNDS, "That's off the board"),
            Map.entry(ActionResult.Reason.NO_PIECE, "No piece there"),
            Map.entry(ActionResult.Reason.NOT_YOUR_PIECE, "That's not your piece"),
            Map.entry(ActionResult.Reason.PIECE_STUNNED, "That piece is stunned"),
            Map.entry(ActionResult.Reason.PIECE_EXHAUSTED, "That piece has no actions left"),
            Map.entry(ActionResult.Reason.UNREACHABLE, "Can't reach that tile"),
            Map.entry(ActionResult.Reason.OUT_OF_RANGE, "Out of range"),
            Map.entry(ActionResult.Reason.INVALID_TARGET, "Invalid target"),
            Map.entry(ActionResult.Reason.ATTACK_NOT_ALLOWED, "Can't attack that"),
            Map.entry(ActionResult.Reason.CARD_NOT_IN_HAND, "That card isn't in your hand"),
            Map.entry(ActionResult.Reason.WRONG_CARD_TYPE, "Wrong card type for that"),
            Map.entry(ActionResult.Reason.UNKNOWN_CARD, "Unknown card"),
            Map.entry(ActionResult.Reason.INVALID_SUMMON_TARGET, "Can't summon there"),
            Map.entry(ActionResult.Reason.INVALID_SPELL_TARGET, "Can't target that with this spell"),
            Map.entry(ActionResult.Reason.NOT_ENOUGH_MANA, "Not enough mana"),
            Map.entry(ActionResult.Reason.CARDS_LOCKED, "Your cards are locked this turn"),
            Map.entry(ActionResult.Reason.NO_SUCH_ABILITY, "No such ability"),
            Map.entry(ActionResult.Reason.NO_VALID_TARGET, "No valid target for that"),
            Map.entry(ActionResult.Reason.PICK_PENDING, "Answer the pending choice first"),
            Map.entry(ActionResult.Reason.NO_PENDING_PICK, "Nothing to answer"),
            Map.entry(ActionResult.Reason.INVALID_PICK, "That's not a valid choice"),
            Map.entry(ActionResult.Reason.EXECUTION_FAILED, "Something went wrong"));

    private final Text text;
    // Each hotseat seat has its own independent command seq space (its own connection), so the
    // "already shown" seq must be tracked per seat — a single field would re-flash the OTHER
    // seat's stale old rejection the first time perspective switches to it.
    private final Map<PieceAlignment, Long> lastShownSeqBySeat = new HashMap<>();
    private float remaining = 0f;

    public RejectionToast() {
        this.text = new Text("", FontType.SILKSCREEN, 0, 0, Z, ColorSettings.TEXT_WARNING.getColor())
                .withFontSize(FontSize.BODY_MEDIUM);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        if (isPaused || GameContext.get().getGameManager().isPaused()) return;

        ClientMatchSession session = GameContext.get().getClientMatchSession();
        ClientMatchState state = session != null ? session.viewState() : null;
        ServerMessage.Rejected rejection = state != null ? state.getLastRejection() : null;

        if (rejection != null && state.getSeat() != null) {
            long lastShown = lastShownSeqBySeat.getOrDefault(state.getSeat(), -1L);
            if (rejection.seq() != lastShown) {
                lastShownSeqBySeat.put(state.getSeat(), rejection.seq());
                remaining = DISPLAY_SECONDS;
                text.setText(MESSAGES.getOrDefault(rejection.reason(), "That didn't work"));
                text.update();
            }
        }

        if (remaining <= 0f) return;
        remaining -= Gdx.graphics.getDeltaTime();

        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        int x = (screenW - text.getWidth()) / 2;
        int y = screenH - TOP_MARGIN;
        text.setBounds(new Box(x, y, text.getWidth(), text.getHeight()));
        text.render(batch, Z, isPaused);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        renderUI(batch, isPaused);
    }
}
