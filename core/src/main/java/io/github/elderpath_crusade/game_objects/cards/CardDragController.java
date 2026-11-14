package io.github.elderpath_crusade.game_objects.cards;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.input.InteractionManager;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.supers.LowestOrderTexture;

/**
 * FEAT-015/C1: lets a card in hand be dragged onto its target instead of click-click (click the
 * card, then click a target plot) — the two coexist by design (the user's call, not every
 * player wants to drag). Deliberately adds no new play path: a press on a card already starts
 * the exact same selection the click-click flow uses (InteractionManager.processLeftClick runs
 * in the Input phase, before this UIRenderable's renderUI() in the same frame), so this class
 * only (1) shows the card following the cursor once the drag crosses a small movement
 * threshold — a plain click/release in place never crosses it, so click-click is untouched —
 * and (2), on release, replays processLeftClick at the drop position (exactly what a second
 * click there would do) and, only if that didn't land on a valid target, cancels the selection
 * rather than leaving it stuck pending a future click the player no longer intends to make.
 */
public class CardDragController extends LowestOrderTexture implements UIRenderable {
    private static final int DRAG_THRESHOLD_PX = 12;

    private Card armedCard;
    private int pressX, pressY;
    private boolean dragging;

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        if (isPaused || GameContext.get().getGameManager().isPaused()) return;
        if (GameContext.get().getGameManager().isInteractionsLocked()) {
            // Mirrors Main.render()'s own input gate — a lock (e.g. a win transition) doesn't
            // itself clear a still-active selection, so a mid-drag release must not act on it.
            endDrag();
            return;
        }

        InteractionManager interactionManager = GameContext.get().getInteractionManager();
        int mx = Gdx.input.getX();
        int my = GameContext.get().getSettingsManager().screenSize.getScreenHeight() - Gdx.input.getY();
        boolean leftDown = Gdx.input.isButtonPressed(Input.Buttons.LEFT);

        if (armedCard == null) {
            // A press this exact frame already ran through the Input phase before this
            // renderUI() call — if it started a card selection, that's our drag candidate.
            // Also require the press itself to land on the card: a selection can already be
            // active with a card as its source when the press is really a (possibly rejected)
            // second click on a target elsewhere, and that must never be mistaken for picking
            // the card back up from wherever that second click happened.
            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && interactionManager.hasActiveSelection()
                    && interactionManager.getActiveSource() instanceof Card card && card.inRange(mx, my)) {
                armedCard = card;
                pressX = mx;
                pressY = my;
                dragging = false;
            }
            return;
        }

        // The selection this drag belongs to ended for some other reason (turn changed, match
        // ended, right-click/ESC cancel) — never leave the card invisible with nothing to clear it.
        if (!interactionManager.hasActiveSelection() || interactionManager.getActiveSource() != armedCard) {
            endDrag();
            return;
        }

        if (!leftDown) {
            if (dragging) {
                interactionManager.processLeftClick(mx, my, false);
                // Still pending (the drop wasn't a valid target) — a real drag gesture snaps
                // back rather than leaving a stale selection waiting for an unrelated later click.
                if (interactionManager.hasActiveSelection() && interactionManager.getActiveSource() == armedCard) {
                    interactionManager.cancelSelection();
                }
            }
            endDrag();
            return;
        }

        if (!dragging) {
            int dx = mx - pressX, dy = my - pressY;
            if (dx * dx + dy * dy >= DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX) {
                dragging = true;
                armedCard.setSuppressNormalRender(true);
            }
        }

        if (dragging) {
            int x = mx - armedCard.getWidth() / 2;
            int y = my - armedCard.getHeight() / 2;
            armedCard.render(batch, armedCard.getZLayer(), false, x, y);
        }
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        renderUI(batch, isPaused);
    }

    private void endDrag() {
        if (armedCard != null) armedCard.setSuppressNormalRender(false);
        armedCard = null;
        dragging = false;
    }
}
