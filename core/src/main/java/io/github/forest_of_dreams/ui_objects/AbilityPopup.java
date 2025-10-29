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
import io.github.forest_of_dreams.managers.InteractionManager;
import io.github.forest_of_dreams.supers.HigherOrderUI;

import java.util.*;

/**
 * Renders ability bubbles (icon or index) near pieces with actionable abilities.
 * New behavior:
 * - Bubbles appear only after hovering the piece's plot (activation).
 * - While active, a larger extended region keeps bubbles visible (prevents blinking).
 * - Bubbles are arranged in a centered horizontal row above the plot.
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

    // Cache created bubbles per ability
    private final Map<AbilityKey, AbilityBubble> bubbles = new HashMap<>();

    // Visual constants
    private static final int BUBBLE_Z = 3;     // UI layer z
    private static final int OFFSET_Y = 4;     // vertical offset above the plot (per feedback)
    private static final int SPACING = 6;      // horizontal spacing between bubbles (per feedback)

    // Sticky activation state
    private UUID focusedPieceId = null;
    private boolean stickyActive = false;

    public AbilityPopup() {
        super();
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        if (isPaused) return;
        // Clear on active selection (hide during interactions)
        if (InteractionManager.hasActiveSelection()) {
            clearAllBubbles();
            focusedPieceId = null;
            stickyActive = false;
            return;
        }
        // Determine which piece's bubbles (if any) should be visible now
        MonsterGamePiece piece = resolveFocusedPiece();
        Set<AbilityKey> desired = (piece == null) ? Collections.emptySet() : getActionableAbilityKeys(piece);
        // Remove stale bubbles
        Iterator<Map.Entry<AbilityKey, AbilityBubble>> it = bubbles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<AbilityKey, AbilityBubble> e = it.next();
            if (!desired.contains(e.getKey())) {
                GraphicsManager.removeUIRenderable(e.getValue());
                getRenderableUIs().remove(e.getValue());
                it.remove();
            }
        }
        // Position and (re)wire bubbles
        positionAndWireBubbles(piece, desired);
    }

    private void clearAllBubbles() {
        for (AbilityBubble b : new ArrayList<>(bubbles.values())) {
            GraphicsManager.removeUIRenderable(b);
            getRenderableUIs().remove(b);
        }
        bubbles.clear();
    }

    // Compute bubble size as 70% of plot size, clamped to reasonable bounds
    private static int computeBubbleSize(Board board) {
        if (board == null) return 24;
        int plot = Math.min(board.getPLOT_WIDTH(), board.getPLOT_HEIGHT());
        int sz = Math.round(plot * 0.70f);
        // Ensure smaller than plot and not tiny/huge
        sz = Math.min(sz, plot - 2);
        sz = Math.max(16, sz);
        return sz;
    }

    // Resolve which piece is in focus according to the activation rules
    private MonsterGamePiece resolveFocusedPiece() {
        PieceAlignment current = TurnManager.getCurrentPlayer();
        int mouseX = Gdx.input.getX();
        int mouseY = SettingsManager.screenSize.getScreenHeight() - Gdx.input.getY();

        // If we have an active focus, retain it while within its extended region and eligibility holds
        if (stickyActive && focusedPieceId != null) {
            MonsterGamePiece focused = findPieceById(focusedPieceId);
            if (focused != null && isPieceEligible(focused, current) && isInExtendedRegion(focused, mouseX, mouseY)) {
                return focused;
            }
            // Otherwise, deactivate
            stickyActive = false;
            focusedPieceId = null;
        }

        // No active focus: activation requires hovering the piece's plot
        MonsterGamePiece hoveredPlotPiece = findHoveredPieceOnPlot(mouseX, mouseY, current);
        if (hoveredPlotPiece != null) {
            stickyActive = true;
            focusedPieceId = hoveredPlotPiece.getId();
            return hoveredPlotPiece;
        }
        return null;
    }

    private MonsterGamePiece findHoveredPieceOnPlot(int mouseX, int mouseY, PieceAlignment current) {
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
            if (gp instanceof MonsterGamePiece mgp && isPieceEligible(mgp, current)) return mgp;
        }
        return null;
    }

    private boolean isInExtendedRegion(MonsterGamePiece mgp, int mouseX, int mouseY) {
        if (mgp == null) return false;
        for (Renderable r : GraphicsManager.getRenderables()) {
            if (!(r instanceof Board board)) continue;
            // Find this piece's cell
            for (int row = 0; row < board.getROWS(); row++) {
                for (int col = 0; col < board.getCOLS(); col++) {
                    GamePiece gp = board.getGamePieceAtPos(row, col);
                    if (gp == mgp) {
                        int plotAbsX = board.getX() + col * board.getPLOT_WIDTH();
                        int plotAbsY = board.getY() + row * board.getPLOT_HEIGHT();
                        int plotRight = plotAbsX + board.getPLOT_WIDTH();
                        int plotTop = plotAbsY + board.getPLOT_HEIGHT();
                        // Compute bubble row rect horizontally centered
                        List<Ability> acts = mgp.getAbilities();
                        int count = 0; for (Ability a : acts) if (a instanceof ActionableAbility) count++;
                        if (count <= 0) return false;
                        int bubbleSize = computeBubbleSize(board);
                        int totalW = count * bubbleSize + (count - 1) * SPACING;
                        int centerX = plotAbsX + board.getPLOT_WIDTH() / 2;
                        int rowLeft = centerX - totalW / 2;
                        int rowRight = rowLeft + totalW;
                        int rowBottom = plotTop + OFFSET_Y;
                        int rowTop = rowBottom + bubbleSize; // row height equals bubble size
                        // Extended region: from plot rectangle up through bubble row rectangle
                        int extLeft = Math.min(plotAbsX, rowLeft);
                        int extRight = Math.max(plotRight, rowRight);
                        int extBottom = plotAbsY; // include whole plot (activation origin)
                        int extTop = rowTop;
                        return mouseX >= extLeft && mouseX <= extRight && mouseY >= extBottom && mouseY <= extTop;
                    }
                }
            }
        }
        return false;
    }

    // --- Helpers ---
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
        Set<AbilityKey> keys = new LinkedHashSet<>();
        for (Ability a : mgp.getAbilities()) {
            if (a instanceof ActionableAbility) keys.add(new AbilityKey(mgp.getId(), a.getName()));
        }
        return keys;
    }

    private void positionAndWireBubbles(MonsterGamePiece piece, Set<AbilityKey> desired) {
        if (piece == null || desired.isEmpty()) return;
        // Find board cell for the piece and compute centered row position
        for (Renderable r : GraphicsManager.getRenderables()) {
            if (!(r instanceof Board board)) continue;
            for (int row = 0; row < board.getROWS(); row++) {
                for (int col = 0; col < board.getCOLS(); col++) {
                    GamePiece gp = board.getGamePieceAtPos(row, col);
                    if (gp != piece) continue;
                    int plotAbsX = board.getX() + col * board.getPLOT_WIDTH();
                    int plotAbsY = board.getY() + row * board.getPLOT_HEIGHT();
                    int centerX = plotAbsX + board.getPLOT_WIDTH() / 2;
                    // Count actionable abilities in attachment order
                    List<Ability> abilities = piece.getAbilities();
                    List<ActionableAbility> acts = new ArrayList<>();
                    for (Ability a : abilities) if (a instanceof ActionableAbility act) acts.add(act);
                    int n = acts.size();
                    int bubbleSize = computeBubbleSize(board);
                    int totalW = n * bubbleSize + (n - 1) * SPACING;
                    int startX = centerX - totalW / 2;
                    int y = plotAbsY + board.getPLOT_HEIGHT() + OFFSET_Y;
                    // Place left-to-right
                    int x = startX;
                    int index = 1;
                    for (ActionableAbility act : acts) {
                        AbilityKey key = new AbilityKey(piece.getId(), act.getName());
                        if (!desired.contains(key)) { index++; continue; }
                        AbilityBubble bubble = bubbles.get(key);
                        if (bubble == null) {
                            String iconPath = act.getIconPath();
                            bubble = new AbilityBubble(0, 0, bubbleSize, BUBBLE_Z);
                            if (iconPath != null && !iconPath.isBlank()) {
                                bubble.withIcon(iconPath);
                            } else {
                                bubble.withIndexLabel(index, Color.WHITE);
                            }
                            bubbles.put(key, bubble);
                            getRenderableUIs().add(bubble);
                            GraphicsManager.addUIRenderable(bubble);
                        }
                        // Position
                        bubble.getBounds().setX(x);
                        bubble.getBounds().setY(y);
                        bubble.getBounds().setWidth(bubbleSize);
                        bubble.getBounds().setHeight(bubbleSize);
                        // Wire click to start and execute ability
                        ClickableEffectData ced = act.getClickableEffectData();
                        bubble.withOnClick(entities -> {
                            act.execute(entities);
                        }, ced);
                        // advance
                        x += bubbleSize + SPACING;
                        index++;
                    }
                    return; // done for this board
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
