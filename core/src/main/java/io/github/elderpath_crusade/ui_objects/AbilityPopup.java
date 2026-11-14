package io.github.elderpath_crusade.ui_objects;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.client.ClientController;
import io.github.elderpath_crusade.data.AbilityRef;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.input.InteractionManager;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.LegalActions;
import io.github.elderpath_crusade.match.RelicCarrierIds;
import io.github.elderpath_crusade.supers.HigherOrderUI;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shows an activation bubble for each usable ability of this screen's hovered piece. Which
 * abilities are usable (and their actionIndex) comes straight from LegalActions.PieceActions —
 * the server already excludes pieces with no remaining action, a stunned piece, pieces that
 * aren't this seat's, and a seat with a pending pick (LegalActions is all-empty then), so this
 * class owns no eligibility logic of its own.
 *
 * FEAT-015/C2: hovering a specific bubble (not just the piece) shows that ability's rules-text
 * description — the same text CardPreviewPanel already shows for a whole piece, just resolved
 * for one ability. Reduces the "guess what the number means" friction a bubble with no icon
 * (numeric fallback) has for a new player, before they commit to clicking it.
 */
public class AbilityPopup extends HigherOrderUI {

    private static final int BUBBLE_Z = 20;
    private static final int BUBBLE_Y_OFFSET = 8;
    private static final int TOOLTIP_Y_OFFSET = 6;

    private final Map<String, AbilityBubble> activeBubbles = new HashMap<>();
    // Narrow corridor between each bubble and its piece's plot — keeps bubble alive while mouse travels upward
    private final Map<String, int[]> corridors = new HashMap<>(); // [x, y, w, h]
    private final Map<String, BubbleSpec> activeSpecs = new HashMap<>();
    private final Text tooltipText;

    public AbilityPopup() {
        super();
        this.tooltipText = new Text("", FontType.SILKSCREEN, 0, 0, BUBBLE_Z + 1, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.CAPTION);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        GameContext ctx = GameContext.get();
        if (ctx == null) return;
        Board board = ctx.getActiveBoard();
        if (board == null) return;

        InteractionManager interactionManager = ctx.getInteractionManager();

        if (interactionManager.hasActiveSelection()) {
            removeAllBubbles(interactionManager);
            return;
        }

        ClientController controller = ctx.getClientController();
        LegalActions actions = controller != null ? controller.legalActions() : null;
        if (actions == null) {
            removeAllBubbles(interactionManager);
            return;
        }

        int boardX = board.getBounds().getX();
        int boardY = board.getBounds().getY();

        List<BubbleSpec> specs = new ArrayList<>();
        for (LegalActions.PieceActions pa : actions.pieces()) {
            // The relic carrier (UPG-001) is off-board and has no plot to anchor a bubble to —
            // its actionable abilities surface through RelicHud instead.
            if (RelicCarrierIds.isRelicCarrier(pa.pieceId())) continue;
            if (pa.abilities().isEmpty()) continue;
            for (LegalActions.AbilityOption option : pa.abilities()) {
                String key = pa.pieceId() + "_" + option.actionIndex();
                specs.add(new BubbleSpec(key, pa.pieceId(), option.actionIndex(), pa.from(), option.abilityId()));
            }
        }

        // Determine hovered piece using LibGDX-flipped mouse coords
        int mx = Gdx.input.getX();
        int my = Gdx.graphics.getHeight() - Gdx.input.getY();

        String hoveredPieceId = null;

        // Sticky corridor: if hovering an existing bubble or the corridor leading up to it, keep that piece's bubbles visible
        for (Map.Entry<String, AbilityBubble> entry : activeBubbles.entrySet()) {
            String key = entry.getKey();
            boolean hit = entry.getValue().inRange(mx, my);
            if (!hit) {
                int[] c = corridors.get(key);
                if (c != null) hit = mx >= c[0] && mx < c[0] + c[2] && my >= c[1] && my < c[1] + c[3];
            }
            if (hit) {
                int last = key.lastIndexOf('_');
                hoveredPieceId = last >= 0 ? key.substring(0, last) : key;
                break;
            }
        }
        // Otherwise check if hovering a plot that belongs to an eligible piece
        if (hoveredPieceId == null) {
            for (BubbleSpec spec : specs) {
                int plotX = boardX + spec.from.col() * board.getPLOT_WIDTH();
                int plotY = boardY + board.toDisplayRow(spec.from.row()) * board.getPLOT_HEIGHT();
                if (mx >= plotX && mx < plotX + board.getPLOT_WIDTH()
                        && my >= plotY && my < plotY + board.getPLOT_HEIGHT()) {
                    hoveredPieceId = spec.pieceId;
                    break;
                }
            }
        }

        // Only show bubbles for the currently hovered piece
        Set<String> desiredKeys = new HashSet<>();
        if (hoveredPieceId != null) {
            for (BubbleSpec spec : specs) {
                if (spec.pieceId.equals(hoveredPieceId)) desiredKeys.add(spec.key);
            }
        }

        Set<String> staleKeys = new HashSet<>(activeBubbles.keySet());
        staleKeys.removeAll(desiredKeys);
        for (String key : staleKeys) {
            interactionManager.removeClickable(activeBubbles.get(key));
            activeBubbles.remove(key);
            corridors.remove(key);
            activeSpecs.remove(key);
        }

        int bubbleSize = (int) (board.getPLOT_WIDTH() * 0.7f);
        for (BubbleSpec spec : specs) {
            if (!desiredKeys.contains(spec.key)) continue;
            if (!activeBubbles.containsKey(spec.key)) {
                int plotX = boardX + spec.from.col() * board.getPLOT_WIDTH();
                int plotY = boardY + board.toDisplayRow(spec.from.row()) * board.getPLOT_HEIGHT();
                int bubX = plotX + board.getPLOT_WIDTH() / 2 - bubbleSize / 2;
                int bubY = plotY + board.getPLOT_HEIGHT() + BUBBLE_Y_OFFSET;

                String pieceId = spec.pieceId;
                int actionIndex = spec.actionIndex;

                AbilityBubble bubble = new AbilityBubble(bubX, bubY, bubbleSize, BUBBLE_Z)
                        .withIndexLabel(spec.actionIndex + 1, Color.WHITE)
                        .withOnClick(
                                (e) -> activate(pieceId, actionIndex),
                                ClickableEffectData.getImmediate()
                        );

                // Corridor: narrow strip from plot top to bubble bottom, same width as bubble
                corridors.put(spec.key, new int[]{bubX, plotY + board.getPLOT_HEIGHT(), bubbleSize, BUBBLE_Y_OFFSET});
                activeBubbles.put(spec.key, bubble);
                activeSpecs.put(spec.key, spec);
                interactionManager.addClickable(bubble);
            }
        }

        for (AbilityBubble bubble : activeBubbles.values()) {
            bubble.renderUI(batch, isPaused);
        }

        renderHoveredTooltip(batch, isPaused, board, mx, my);
    }

    /** C2: hovering a specific bubble shows that ability's description above it. */
    private void renderHoveredTooltip(SpriteBatch batch, boolean isPaused, Board board, int mx, int my) {
        for (Map.Entry<String, AbilityBubble> entry : activeBubbles.entrySet()) {
            AbilityBubble bubble = entry.getValue();
            if (!bubble.inRange(mx, my)) continue;

            BubbleSpec spec = activeSpecs.get(entry.getKey());
            String description = spec != null ? resolveDescription(spec, board) : null;
            if (description == null || description.isEmpty()) return;

            tooltipText.setText(description);
            tooltipText.update();
            int tx = bubble.getX() + bubble.getWidth() / 2 - tooltipText.getWidth() / 2;
            int ty = bubble.getY() + bubble.getHeight() + TOOLTIP_Y_OFFSET;
            tooltipText.setBounds(new Box(tx, ty, tooltipText.getWidth(), tooltipText.getHeight()));
            tooltipText.render(batch, BUBBLE_Z + 1, isPaused);
            return;
        }
    }

    /** Same resolution CardPreviewPanel uses for a whole piece, narrowed to this one ability. */
    private String resolveDescription(BubbleSpec spec, Board board) {
        Entity entity = board.getEntityAtPos(spec.from.row(), spec.from.col());
        if (entity == null) return null;
        PieceDefinition pieceDef = PieceRegistry.get(EntityUtils.getName(entity));
        if (pieceDef == null) return null;
        for (AbilityRef ref : pieceDef.abilities()) {
            if (!ref.name().equals(spec.abilityId)) continue;
            AbilityDefinition abilityDef = ref.params().isEmpty()
                    ? AbilityRegistry.get(ref.name())
                    : AbilityRegistry.specialize(ref.name(), ref.params());
            return abilityDef != null ? abilityDef.description() : null;
        }
        return null;
    }

    /** Asks the match server to activate the ability — it re-validates and is the sole judge of legality. */
    private static void activate(String pieceId, int actionIndex) {
        GameContext.get().getClientController().activateAbility(pieceId, actionIndex);
    }

    private void removeAllBubbles(InteractionManager interactionManager) {
        for (AbilityBubble bubble : activeBubbles.values()) {
            interactionManager.removeClickable(bubble);
        }
        activeBubbles.clear();
        corridors.clear();
        activeSpecs.clear();
    }

    private record BubbleSpec(String key, String pieceId, int actionIndex, Coord from, String abilityId) {}
}
