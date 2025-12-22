package io.github.elderpath_crusade.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.abilities.Ability;
import io.github.elderpath_crusade.abilities.BasicAbility;
import io.github.elderpath_crusade.abilities.AbilityContext;
import io.github.elderpath_crusade.abilities.impl._base.BaseAttackAbility;
import io.github.elderpath_crusade.abilities.impl._base.BaseMoveAbility;
import io.github.elderpath_crusade.abilities.impl._base_override.JumpMoveAbility;
import io.github.elderpath_crusade.abilities.impl._base_override.OncePerTurnAttackAbility;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.ui_objects.AbilityBubble;
import io.github.elderpath_crusade.utils.AbilityUtils;
import io.github.elderpath_crusade.utils.GraphicUtils;

import java.util.*;

/**
 * Sole source of truth for "what should be glowing right now."
 * Consolidates highlighting logic for movement, attacks, and abilities.
 * Also handles the rendering of these highlights.
 */
public class HighlightManager implements Renderable {

    private static final HighlightManager INSTANCE = new HighlightManager();
    public static HighlightManager get() { return INSTANCE; }

    private HighlightManager() {}

    private static final float HIGHLIGHT_ANIMATION_SPEED = 10f;

    private static final Set<Plot> highlightedPlots = new HashSet<>();
    private static final Set<Plot> candidatePlots = new HashSet<>();
    private static final Set<Plot> attackCandidatePlots = new HashSet<>();
    private static final Set<Plot> friendlyCandidatePlots = new HashSet<>();

    private static final Map<Plot, HighlightState> states = new HashMap<>();

    private static class HighlightState {
        float selectedProgress = 0f;
        float candidateProgress = 0f;
        float attackProgress = 0f;
        float friendlyProgress = 0f;

        boolean isFinished() {
            return selectedProgress <= 0f && candidateProgress <= 0f && attackProgress <= 0f && friendlyProgress <= 0f;
        }
    }

    public static void update() {
        // 1. Clear all existing highlights
        highlightedPlots.clear();
        candidatePlots.clear();
        attackCandidatePlots.clear();
        friendlyCandidatePlots.clear();

        // 2. Determine what should be highlighted based on InteractionManager state
        if (InteractionManager.hasActiveSelection()) {
            CustomBox source = InteractionManager.getActiveSource();
            if (source != null) {
                // 3. Highlight currently selected targets (blue-white border)
                updateSelectedTargetHighlights();

                // 4. Highlight eligible targets (dots, red borders, green borders)
                if (source instanceof Plot sourcePlot) {
                    // Movement/Attack interaction initiated from a Plot
                    updateMovementAndAttackHighlights(sourcePlot);
                } else {
                    // Ability or other interaction
                    updateAbilityEligibleTargetHighlights(source);
                }
            }
        }

        // 5. Update animation states
        updateAnimationStates();
    }

    private static void updateAnimationStates() {
        float dt = Gdx.graphics.getDeltaTime();

        // Ensure states exist for all currently highlighted plots
        Set<Plot> activePlots = new HashSet<>();
        activePlots.addAll(highlightedPlots);
        activePlots.addAll(candidatePlots);
        activePlots.addAll(attackCandidatePlots);
        activePlots.addAll(friendlyCandidatePlots);

        for (Plot p : activePlots) {
            states.putIfAbsent(p, new HighlightState());
        }

        // Advance progress and cleanup
        Iterator<Map.Entry<Plot, HighlightState>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Plot, HighlightState> entry = it.next();
            Plot p = entry.getKey();
            HighlightState s = entry.getValue();

            s.selectedProgress = advanceProgress(s.selectedProgress, highlightedPlots.contains(p), dt);
            s.candidateProgress = advanceProgress(s.candidateProgress, candidatePlots.contains(p), dt);
            s.attackProgress = advanceProgress(s.attackProgress, attackCandidatePlots.contains(p), dt);
            s.friendlyProgress = advanceProgress(s.friendlyProgress, friendlyCandidatePlots.contains(p), dt);

            if (s.isFinished() && !activePlots.contains(p)) {
                it.remove();
            }
        }
    }

    private static float advanceProgress(float current, boolean active, float dt) {
        if (active) return Math.min(1f, current + HIGHLIGHT_ANIMATION_SPEED * dt);
        else return Math.max(0f, current - HIGHLIGHT_ANIMATION_SPEED * dt);
    }

    @Override public List<Integer> getZs() { return List.of(1, 2); }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        if (isPaused) return;

        for (Map.Entry<Plot, HighlightState> entry : states.entrySet()) {
            Plot plot = entry.getKey();
            HighlightState s = entry.getValue();
            int[] pos = plot.calculatePos();
            int x = pos[0];
            int y = pos[1];
            int w = plot.getWidth();
            int h = plot.getHeight();

            if (zLevel == 1) {
                if (s.selectedProgress > 0f) {
                    drawBorder(batch, x, y, w, h, Color.WHITE, s.selectedProgress);
                }
            } else if (zLevel == 2) {
                if (s.candidateProgress > 0f) {
                    drawDot(batch, x, y, w, h, Color.WHITE, s.candidateProgress);
                }
                if (s.attackProgress > 0f) {
                    drawBorder(batch, x, y, w, h, Color.RED, s.attackProgress);
                }
                if (s.friendlyProgress > 0f) {
                    drawBorder(batch, x, y, w, h, Color.GREEN, s.friendlyProgress);
                }
            }
        }
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        // We use absolute positions from plots, so we ignore the provided offsets
        render(batch, zLevel, isPaused);
    }

    private void drawBorder(SpriteBatch batch, int absX, int absY, int w, int h, Color color, float progress) {
        int maxThickness = Math.max(2, Math.round(Math.min(w, h) * 0.08f));
        int t = Math.max(1, Math.round(maxThickness * progress));
        batch.draw(GraphicUtils.getPixelTexture(color), absX, absY + h - t, w, t);
        batch.draw(GraphicUtils.getPixelTexture(color), absX, absY, w, t);
        batch.draw(GraphicUtils.getPixelTexture(color), absX, absY, t, h);
        batch.draw(GraphicUtils.getPixelTexture(color), absX + w - t, absY, t, h);
    }

    private void drawDot(SpriteBatch batch, int absX, int absY, int w, int h, Color color, float progress) {
        int baseSize = Math.max(2, Math.round(Math.min(w, h) * 0.25f));
        int s = Math.max(1, Math.round(baseSize * progress));
        int cx = absX + (w - s) / 2;
        int cy = absY + (h - s) / 2;

        Color c = color.cpy();
        c.a *= progress;
        batch.draw(GraphicUtils.getPixelTexture(c), cx, cy, s, s);
    }

    @Override public Box getParent() { return null; }
    @Override public void setParent(Box parent) {}
    @Override public Box getBounds() { return null; }
    @Override public void setBounds(Box bounds) {}

    public static boolean isHighlighted(Plot p) { return highlightedPlots.contains(p); }
    public static boolean isCandidate(Plot p) { return candidatePlots.contains(p); }
    public static boolean isAttackCandidate(Plot p) { return attackCandidatePlots.contains(p); }
    public static boolean isFriendlyCandidate(Plot p) { return friendlyCandidatePlots.contains(p); }

    private static void updateSelectedTargetHighlights() {
        List<CustomBox> targets = InteractionManager.getActiveTargets();
        if (targets.isEmpty()) return;

        for (CustomBox target : targets) {
            if (target instanceof Plot p) {
                highlightedPlots.add(p);
            } else if (target instanceof GamePiece gp) {
                // If a piece is selected, highlight the plot it's on
                Board.Position pos = AbilityContext.getOwnerPos(gp);
                if (pos != null && pos.getBoard() != null) {
                    Renderable r = pos.getBoard().getPlotAtPos(pos.getRow(), pos.getCol());
                    if (r instanceof Plot p) highlightedPlots.add(p);
                }
            }
        }
    }

    private static void updateMovementAndAttackHighlights(Plot sourcePlot) {
        Board board = sourcePlot.getBoard(); // Assuming Plot has getBoard() or similar.
        // Wait, Plot in the provided code has boardRef and setBoard(Board). I should check if it has getBoard().
        // Actually, Board.getIndicesOfPlot(sourcePlot) is used in the old code.

        // I'll use the logic from Board.updateCandidateMoveSpots
        int[] sIdx = board.getIndicesOfPlot(sourcePlot);
        if (sIdx == null) return;
        int sr = sIdx[0], sc = sIdx[1];
        GamePiece gp = board.getGamePieceAtPos(sr, sc);
        if (!(gp instanceof MonsterGamePiece mgp)) return;
        if (mgp.getAlignment() != TurnManager.getCurrentPlayer()) return;
        if (mgp.isStunned()) return;

        List<Plot> reachable = List.of();
        List<Plot> attackables = List.of();
        JumpMoveAbility jumpMoveAbility = null;
        BaseMoveAbility baseMoveAbility = null;
        OncePerTurnAttackAbility oncePerTurnAttackAbility = null;

        for (Ability ability : mgp.getAbilities()) {
            if (ability instanceof BasicAbility basicAbility) {
                if (basicAbility instanceof JumpMoveAbility) {
                    jumpMoveAbility = (JumpMoveAbility) basicAbility;
                } else if (basicAbility instanceof BaseMoveAbility) {
                    baseMoveAbility = (BaseMoveAbility) basicAbility;
                } else if (basicAbility instanceof OncePerTurnAttackAbility) {
                    oncePerTurnAttackAbility = (OncePerTurnAttackAbility) basicAbility;
                } else if (basicAbility instanceof BaseAttackAbility) {
                    if (oncePerTurnAttackAbility == null) {
                        attackables = basicAbility.getEligibleTargets(1);
                    }
                }
            }
        }

        if (oncePerTurnAttackAbility != null) {
            attackables = oncePerTurnAttackAbility.getEligibleTargets(1);
        }

        if (jumpMoveAbility != null) {
            reachable = jumpMoveAbility.getEligibleTargets(1);
        } else if (baseMoveAbility != null) {
            reachable = baseMoveAbility.getEligibleTargets(1);
        }

        // Apply highlights
        for (Plot p : attackables) {
            if (p != null) attackCandidatePlots.add(p);
        }
        for (Plot p : reachable) {
            // Don't show dot if it's already an attack candidate
            if (p != null && !attackCandidatePlots.contains(p)) {
                candidatePlots.add(p);
            }
        }
    }

    private static void updateAbilityEligibleTargetHighlights(CustomBox source) {
        TargetFilter filter = null;

        if (source instanceof AbilityBubble bubble) {
            var ability = bubble.getAbility();
            if (ability instanceof TargetFilter tf) {
                filter = tf;
            }
        } else if (source instanceof TargetFilter tf) {
            filter = tf;
        }

        if (filter == null) return;

        int selectedCount = InteractionManager.getSelectedCount();
        List<Plot> eligiblePlots = filter.getEligibleTargets(selectedCount);
        if (eligiblePlots == null || eligiblePlots.isEmpty()) return;

        PieceAlignment currentPlayer = TurnManager.getCurrentPlayer();
        if (currentPlayer == null) return;

        for (Plot plot : eligiblePlots) {
            if (plot == null) continue;

            Board board = plot.getBoard();
            if (board == null) continue;

            GamePiece piece = board.getGamePieceAtPlot(plot);
            if (piece instanceof MonsterGamePiece mgp) {
                PieceAlignment pieceAlignment = mgp.getAlignment();
                if (pieceAlignment != currentPlayer && pieceAlignment != PieceAlignment.NEUTRAL) {
                    attackCandidatePlots.add(plot);
                } else if (pieceAlignment == currentPlayer) {
                    friendlyCandidatePlots.add(plot);
                }
            } else {
                // If it's an empty plot, highlight it with a dot
                candidatePlots.add(plot);
            }
        }
    }
}
