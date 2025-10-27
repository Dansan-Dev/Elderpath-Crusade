package io.github.forest_of_dreams.ui_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.abilities.Ability;
import io.github.forest_of_dreams.abilities.ActionableAbility;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.enums.GamePieceData;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.managers.GraphicsManager;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.managers.TurnManager;
import io.github.forest_of_dreams.supers.HigherOrderUI;

import java.util.*;

/**
 * Renders small UI bubbles (buttons) near pieces that have actionable abilities (e.g., WarpMage).
 * Clicking a bubble starts that ability's multi-selection and executes it on completion.
 *
 * Contextual behavior: bubbles only appear when hovering a current player's piece that
 * has actionable abilities and at least 1 remaining action. Hidden when paused.
 */
public class AbilityPopup extends HigherOrderUI {

    private static class AbilityKey {
        final UUID pieceId;
        final String abilityName;
        AbilityKey(UUID pieceId, String abilityName) { this.pieceId = pieceId; this.abilityName = abilityName; }
        @Override public boolean equals(Object o) {
            if (this == o) return true; if (o == null || getClass() != o.getClass()) return false;
            AbilityKey k = (AbilityKey) o; return Objects.equals(pieceId, k.pieceId) && Objects.equals(abilityName, k.abilityName);
        }
        @Override public int hashCode() { return Objects.hash(pieceId, abilityName); }
    }

    private final Map<AbilityKey, Button> buttons = new HashMap<>();

    // Visual constants for the small bubbles
    private static final int BTN_W = 72;
    private static final int BTN_H = 20;
    private static final int BTN_Z = 3; // UI layer z
    private static final int OFFSET_Y = 6; // above the plot

    public AbilityPopup() {
        super();
        // No fixed bounds; this is a UI overlay that positions children absolutely
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        // Hide entire overlay while paused
        if (isPaused) {
            return;
        }
        // Reconcile current desired buttons with existing cache
        Set<AbilityKey> desired = computeDesiredButtons();
        // Remove stale buttons
        Iterator<Map.Entry<AbilityKey, Button>> it = buttons.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<AbilityKey, Button> e = it.next();
            if (!desired.contains(e.getKey())) {
                // Retract from GraphicsManager and container
                GraphicsManager.removeUIRenderable(e.getValue());
                getRenderableUIs().remove(e.getValue());
                it.remove();
            }
        }
        // Add or update desired buttons
        for (AbilityKey key : desired) {
            Button b = buttons.get(key);
            if (b == null) {
                // Create button
                Button nb = Button.fromColor(
                    Color.WHITE.cpy().mul(0.15f, 0.15f, 0.35f, 0.95f),
                    key.abilityName,
                    io.github.forest_of_dreams.enums.FontType.SILKSCREEN,
                    12,
                    0, 0,
                    BTN_W, BTN_H,
                    BTN_Z
                );
                nb.withTextColors(Color.WHITE, Color.WHITE, Color.WHITE);
                // Click behavior set when positioning (we need the ability reference)
                buttons.put(key, nb);
                getRenderableUIs().add(nb);
                GraphicsManager.addUIRenderable(nb);
            }
        }
        // Position and (re)wire buttons based on current board state
        positionAndWireButtons();
    }

    // Return bubbles for the currently focused piece (eligible), where focus is:
    // 1) Hovering an existing ability button (sticky)
    // 2) Hovering the piece's plot
    // 3) Hovering the vertical corridor between the plot and its bubble
    private Set<AbilityKey> computeDesiredButtons() {
        Set<AbilityKey> out = new HashSet<>();
        if (GraphicsManager.isPaused()) return out;
        PieceAlignment current = TurnManager.getCurrentPlayer();
        // Mouse position in window coordinates
        int mouseX = Gdx.input.getX();
        int mouseY = SettingsManager.screenSize.getScreenHeight() - Gdx.input.getY();

        // 1) If hovering any existing ability button, keep popup for that button's owner
        AbilityKey hoveredKey = getHoveredButtonKey(mouseX, mouseY);
        if (hoveredKey != null) {
            MonsterGamePiece owner = findPieceById(hoveredKey.pieceId);
            if (owner != null && isPieceEligible(owner, current)) {
                return getActionableAbilityKeys(owner);
            }
        }

        // 2) If hovering a piece's plot, show its abilities
        for (Renderable r : GraphicsManager.getRenderables()) {
            if (!(r instanceof Board board)) continue;
            int boardX = board.getX();
            int boardY = board.getY();
            int boardW = board.getPLOT_WIDTH() * board.getCOLS();
            int boardH = board.getPLOT_HEIGHT() * board.getROWS();
            if (mouseX < boardX || mouseX >= boardX + boardW) continue;
            if (mouseY < boardY || mouseY >= boardY + boardH) continue;
            int localX = mouseX - boardX;
            int localY = mouseY - boardY;
            int col = localX / board.getPLOT_WIDTH();
            int row = localY / board.getPLOT_HEIGHT();
            if (row < 0 || row >= board.getROWS() || col < 0 || col >= board.getCOLS()) continue;
            GamePiece gp = board.getGamePieceAtPos(row, col);
            if (gp instanceof MonsterGamePiece mgp && isPieceEligible(mgp, current)) {
                return getActionableAbilityKeys(mgp);
            }
        }

        // 3) Sticky hover corridor: if the mouse is between a piece's plot top and its bubble,
        //    and within the bubble's horizontal span, keep the popup visible for that piece.
        MonsterGamePiece corridorPiece = getPieceUnderHoverCorridor(mouseX, mouseY, current);
        if (corridorPiece != null) {
            return getActionableAbilityKeys(corridorPiece);
        }

        return out;
    }

    // --- Helpers ---
    private AbilityKey getHoveredButtonKey(int mouseX, int mouseY) {
        for (Map.Entry<AbilityKey, Button> e : buttons.entrySet()) {
            Button b = e.getValue();
            int bx = b.getX();
            int by = b.getY();
            int bw = b.getWidth();
            int bh = b.getHeight();
            if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                return e.getKey();
            }
        }
        return null;
    }

    private MonsterGamePiece findPieceById(UUID id) {
        if (id == null) return null;
        for (Renderable r : GraphicsManager.getRenderables()) {
            if (!(r instanceof Board board)) continue;
            for (int row = 0; row < board.getROWS(); row++) {
                for (int col = 0; col < board.getCOLS(); col++) {
                    GamePiece gp = board.getGamePieceAtPos(row, col);
                    if (gp instanceof MonsterGamePiece mgp) {
                        if (id.equals(mgp.getId())) return mgp;
                    }
                }
            }
        }
        return null;
    }

    private boolean isPieceEligible(MonsterGamePiece mgp, PieceAlignment current) {
        if (mgp == null) return false;
        if (mgp.getAlignment() != current) return false;
        if (getRemainingActions(mgp) <= 0) return false;
        List<Ability> abilities = mgp.getAbilities();
        if (abilities == null || abilities.isEmpty()) return false;
        for (Ability a : abilities) {
            if (a instanceof ActionableAbility) return true;
        }
        return false;
    }

    private Set<AbilityKey> getActionableAbilityKeys(MonsterGamePiece mgp) {
        Set<AbilityKey> keys = new HashSet<>();
        for (Ability a : mgp.getAbilities()) {
            if (a instanceof ActionableAbility) {
                keys.add(new AbilityKey(mgp.getId(), a.getName()));
            }
        }
        return keys;
    }

    private MonsterGamePiece getPieceUnderHoverCorridor(int mouseX, int mouseY, PieceAlignment current) {
        for (Renderable r : GraphicsManager.getRenderables()) {
            if (!(r instanceof Board board)) continue;
            for (int row = 0; row < board.getROWS(); row++) {
                for (int col = 0; col < board.getCOLS(); col++) {
                    GamePiece gp = board.getGamePieceAtPos(row, col);
                    if (!(gp instanceof MonsterGamePiece mgp)) continue;
                    if (!isPieceEligible(mgp, current)) continue;
                    int plotAbsX = board.getX() + col * board.getPLOT_WIDTH();
                    int plotAbsY = board.getY() + row * board.getPLOT_HEIGHT();
                    int btnX = plotAbsX + (board.getPLOT_WIDTH() - BTN_W) / 2;
                    int btnY = plotAbsY + board.getPLOT_HEIGHT() + OFFSET_Y;
                    int corridorLeft = btnX;
                    int corridorRight = btnX + BTN_W;
                    int corridorBottom = plotAbsY + board.getPLOT_HEIGHT();
                    int corridorTop = btnY + BTN_H; // extend through the bubble
                    if (mouseX >= corridorLeft && mouseX <= corridorRight && mouseY >= corridorBottom && mouseY <= corridorTop) {
                        return mgp;
                    }
                }
            }
        }
        return null;
    }

    private void positionAndWireButtons() {
        PieceAlignment current = TurnManager.getCurrentPlayer();
        for (Renderable r : GraphicsManager.getRenderables()) {
            if (!(r instanceof Board board)) continue;
            for (int row = 0; row < board.getROWS(); row++) {
                for (int col = 0; col < board.getCOLS(); col++) {
                    GamePiece gp = board.getGamePieceAtPos(row, col);
                    if (!(gp instanceof MonsterGamePiece mgp)) continue;
                    if (mgp.getAlignment() != current) continue;
                    Object posObj = mgp.getData(GamePieceData.POSITION);
                    if (!(posObj instanceof Board.Position)) continue;
                    // Compute absolute position of the plot within the window
                    int plotAbsX = board.getX() + col * board.getPLOT_WIDTH();
                    int plotAbsY = board.getY() + row * board.getPLOT_HEIGHT();
                    int btnX = plotAbsX + (board.getPLOT_WIDTH() - BTN_W) / 2;
                    int btnY = plotAbsY + board.getPLOT_HEIGHT() + OFFSET_Y;
                    // Iterate actionable abilities for this piece to set positions (stack vertically if many)
                    int offsetY = 0;
                    for (Ability a : mgp.getAbilities()) {
                        if (!(a instanceof ActionableAbility act)) continue;
                        AbilityKey key = new AbilityKey(mgp.getId(), a.getName());
                        Button b = buttons.get(key);
                        if (b == null) continue; // will be created next frame by computeDesiredButtons
                        // Set button position
                        b.getBounds().setX(btnX);
                        b.getBounds().setY(btnY + offsetY);
                        offsetY += BTN_H + 4;
                        // Ensure the button starts the ability selection and executes on completion
                        // Set onClick each frame to bind latest ability instance
                        ClickableEffectData ced = act.getClickableEffectData();
                        b.withOnClick(entities -> {
                            // Delegate to the ability; selection entities come from InteractionManager
                            act.execute(entities);
                        }, ced);
                    }
                }
            }
        }
    }

    private static int getRemainingActions(MonsterGamePiece mgp) {
        Object v = mgp.getData(GamePieceData.ACTIONS_REMAINING);
        if (v instanceof Integer n) return n;
        return mgp.getStats().getActions();
    }
}
