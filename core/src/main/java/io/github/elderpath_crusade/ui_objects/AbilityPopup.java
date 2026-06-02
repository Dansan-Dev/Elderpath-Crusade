package io.github.elderpath_crusade.ui_objects;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.abilities.Ability;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.utils.AbilityUtils;
import io.github.elderpath_crusade.abilities.ActionableAbility;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.managers.GraphicsManager;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.managers.InteractionManager;
import io.github.elderpath_crusade.supers.HigherOrderUI;

import java.util.*;

/**
 * Renders ability bubbles (icon or index) near pieces with actionable
 * abilities.
 * New behavior:
 * - Bubbles appear only after hovering the piece's plot (activation).
 * - While active, a larger extended region keeps bubbles visible (prevents
 * blinking).
 * - Bubbles are arranged in a centered horizontal row above the plot.
 */
public class AbilityPopup extends HigherOrderUI {

    private record AbilityKey(UUID pieceId, String abilityName) {
    }

    private record BubbleLayout(int startX, int y, int bubbleSize, int totalW) {
    }

    // Cache created bubbles per ability
    private final Map<AbilityKey, AbilityBubble> bubbles = new HashMap<>();

    // Visual constants
    private static final int BUBBLE_Z = 3; // UI layer z
    private static final int OFFSET_Y = 4; // vertical offset above the plot (per feedback)
    private static final int SPACING = 6; // horizontal spacing between bubbles (per feedback)

    // Sticky activation state
    private UUID focusedPieceId = null;
    private boolean stickyActive = false;

    // Cached piece lookup to avoid full-board scan per frame
    private MonsterGamePiece cachedFocusedPiece = null;
    private UUID cachedFocusedPieceId = null;

    public AbilityPopup() {
        super();
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        if (isPaused) return;
        if (GameContext.get().getInteractionManager().hasActiveSelection()) {
            clearAllBubbles();
            focusedPieceId = null;
            stickyActive = false;
            return;
        }

        MonsterGamePiece piece = resolveFocusedPiece();
        Set<AbilityKey> desired = (piece == null)
            ? Collections.emptySet()
            : getActionableAbilityKeys(piece);

        Iterator<Map.Entry<AbilityKey, AbilityBubble>> it = bubbles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<AbilityKey, AbilityBubble> e = it.next();
            if (!desired.contains(e.getKey())) {
                GameContext.get().getGraphicsManager().removeUIRenderable(e.getValue());
                getRenderableUIs().remove(e.getValue());
                it.remove();
            }
        }

        positionAndWireBubbles(piece, desired);
    }

    private void clearAllBubbles() {
        for (AbilityBubble b : new ArrayList<>(bubbles.values())) {
            GameContext.get().getGraphicsManager().removeUIRenderable(b);
            getRenderableUIs().remove(b);
        }
        bubbles.clear();
        cachedFocusedPiece = null;
        cachedFocusedPieceId = null;
    }

    private int computeBubbleSize() {
        Board board = getBoard();
        if (board == null) return 24;
        int plot = Math.min(board.getPLOT_WIDTH(), board.getPLOT_HEIGHT());
        int bubbleSize = Math.round(plot * 0.70f);
        bubbleSize = Math.min(bubbleSize, plot - 2);
        bubbleSize = Math.max(16, bubbleSize);
        return bubbleSize;
    }

    private MonsterGamePiece resolveFocusedPiece() {
        PieceAlignment current = GameContext.get().getTurnManager().getCurrentPlayer();
        int mouseX = Gdx.input.getX();
        int mouseY = GameContext.get().getSettingsManager().screenSize.getScreenHeight() - Gdx.input.getY();

        if (stickyActive && focusedPieceId != null) {
            MonsterGamePiece focused = findPieceById(focusedPieceId);
            if (isPieceEligible(focused, current) && isInExtendedRegion(focused, mouseX, mouseY)) {
                return focused;
            }
            stickyActive = false;
            focusedPieceId = null;
        }

        MonsterGamePiece hoveredPlotPiece = findHoveredPieceOnPlot(mouseX, mouseY, current);
        if (hoveredPlotPiece != null) {
            stickyActive = true;
            focusedPieceId = hoveredPlotPiece.getId();
            return hoveredPlotPiece;
        }
        return null;
    }

    private MonsterGamePiece findHoveredPieceOnPlot(int mouseX, int mouseY, PieceAlignment current) {
        Board board = getBoard();
        if (board == null) return null;

        int boardX = board.getX();
        int boardY = board.getY();
        int boardW = board.getPLOT_WIDTH() * board.getCOLS();
        int boardH = board.getPLOT_HEIGHT() * board.getROWS();

        if (mouseX < boardX || mouseX >= boardX + boardW) return null;
        if (mouseY < boardY || mouseY >= boardY + boardH) return null;

        int localX = mouseX - boardX;
        int localY = mouseY - boardY;
        int col = localX / board.getPLOT_WIDTH();
        int row = localY / board.getPLOT_HEIGHT();

        if (
            row < 0
            || row >= board.getROWS()
            || col < 0
            || col >= board.getCOLS()
        ) {
            return null;
        }

        GamePiece gp = board.getGamePieceAtPos(row, col);
        if (gp instanceof MonsterGamePiece mgp && isPieceEligible(mgp, current)) {
            return mgp;
        }

        return null;
    }

    private boolean isInExtendedRegion(MonsterGamePiece mgp, int mouseX, int mouseY) {
        if (mgp == null) return false;
        Board board = getBoard();
        if (board == null) return false;

        List<ActionableAbility> acts = getActionableAbilities(mgp);
        if (acts.isEmpty()) return false;

        BubbleLayout layout = calculateBubbleLayout(mgp, board, acts);
        if (layout == null) return false;

        Object posObj = mgp.getData(GamePieceData.POSITION);
        if (!(posObj instanceof Board.Position pos)) return false;

        int row = pos.getRow();
        int col = pos.getCol();

        int plotAbsX = board.getX() + col * board.getPLOT_WIDTH();
        int plotAbsY = board.getY() + row * board.getPLOT_HEIGHT();
        int plotRight = plotAbsX + board.getPLOT_WIDTH();

        int rowLeft = layout.startX;
        int rowRight = rowLeft + layout.totalW;
        int rowBottom = layout.y;
        int rowTop = rowBottom + layout.bubbleSize;

        int extLeft = Math.min(plotAbsX, rowLeft);
        int extRight = Math.max(plotRight, rowRight);
        int extBottom = plotAbsY;
        int extTop = rowTop;

        return mouseX >= extLeft
            && mouseX <= extRight
            && mouseY >= extBottom
            && mouseY <= extTop;
    }

    private MonsterGamePiece findPieceById(UUID id) {
        if (id == null) return null;
        if (id.equals(cachedFocusedPieceId) && cachedFocusedPiece != null) {
            return cachedFocusedPiece;
        }
        Board board = getBoard();
        if (board == null) return null;
        int rows = board.getROWS();
        int cols = board.getCOLS();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                GamePiece gp = board.getGamePieceAtPos(r, c);
                if (gp instanceof MonsterGamePiece mgp && id.equals(mgp.getId())) {
                    cachedFocusedPiece = mgp;
                    cachedFocusedPieceId = id;
                    return mgp;
                }
            }
        }
        cachedFocusedPiece = null;
        cachedFocusedPieceId = null;
        return null;
    }

    private boolean isPieceEligible(MonsterGamePiece mgp, PieceAlignment current) {
        if (mgp == null) return false;
        if (mgp.getAlignment() != current) return false;
        if (mgp.isStunned()) return false;
        if (AbilityUtils.getRemainingActions(mgp) <= 0) return false;
        return !getActionableAbilities(mgp).isEmpty();
    }

    private Set<AbilityKey> getActionableAbilityKeys(MonsterGamePiece mgp) {
        Set<AbilityKey> keys = new LinkedHashSet<>();
        for (ActionableAbility act : getActionableAbilities(mgp)) {
            keys.add(new AbilityKey(mgp.getId(), act.getName()));
        }
        return keys;
    }

    private void positionAndWireBubbles(MonsterGamePiece piece, Set<AbilityKey> desired) {
        if (piece == null || desired.isEmpty()) return;
        Board board = getBoard();
        if (board == null) return;

        List<ActionableAbility> acts = getActionableAbilities(piece);
        if (acts.isEmpty()) return;

        BubbleLayout layout = calculateBubbleLayout(piece, board, acts);
        if (layout == null) return;

        int x = layout.startX;
        int y = layout.y;
        int bubbleSize = layout.bubbleSize;
        int index = 1;

        for (ActionableAbility act : acts) {
            AbilityKey key = new AbilityKey(piece.getId(), act.getName());
            if (!desired.contains(key)) {
                index++;
                continue;
            }
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
                GameContext.get().getGraphicsManager().addUIRenderable(bubble);
            }
            bubble.getBounds().setX(x);
            bubble.getBounds().setY(y);
            bubble.getBounds().setWidth(bubbleSize);
            bubble.getBounds().setHeight(bubbleSize);
            ClickableEffectData ced = act.getClickableEffectData();
            bubble.withAbility(act);
            bubble.withOnClick(entities -> {
                AbilityUtils.execute(act, entities);
            }, ced);
            x += bubbleSize + SPACING;
            index++;
        }
    }

    private Board getBoard() {
        return GameContext.get().getActiveBoard();
    }

    private List<ActionableAbility> getActionableAbilities(MonsterGamePiece piece) {
        List<ActionableAbility> acts = new ArrayList<>();
        if (piece == null) return acts;
        for (Ability a : piece.getAbilities()) {
            if (a instanceof ActionableAbility act) {
                acts.add(act);
            }
        }
        return acts;
    }

    private BubbleLayout calculateBubbleLayout(MonsterGamePiece piece, Board board, List<ActionableAbility> acts) {
        Object posObj = piece.getData(GamePieceData.POSITION);
        if (!(posObj instanceof Board.Position pos)) return null;

        int row = pos.getRow();
        int col = pos.getCol();

        int plotAbsX = board.getX() + col * board.getPLOT_WIDTH();
        int plotAbsY = board.getY() + row * board.getPLOT_HEIGHT();
        int centerX = plotAbsX + board.getPLOT_WIDTH() / 2;

        int n = acts.size();
        int bubbleSize = computeBubbleSize();
        int totalW = n * bubbleSize + (n - 1) * SPACING;
        int startX = centerX - totalW / 2;
        int y = plotAbsY + board.getPLOT_HEIGHT() + OFFSET_Y;

        return new BubbleLayout(startX, y, bubbleSize, totalW);
    }
}
