package io.github.elderpath_crusade.managers;

import com.badlogic.gdx.Gdx;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.Ability;
import io.github.elderpath_crusade.abilities.AbilityContext;
import io.github.elderpath_crusade.abilities.BasicAbility;
import io.github.elderpath_crusade.abilities.impl._base.BaseAttackAbility;
import io.github.elderpath_crusade.abilities.impl._base.BaseMoveAbility;
import io.github.elderpath_crusade.abilities.impl._base_override.JumpMoveAbility;
import io.github.elderpath_crusade.abilities.impl._base_override.OncePerTurnAttackAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.interfaces.TargetFilter;

import java.util.*;

/**
 * Owns all highlight state: which plots are highlighted/candidate and their animation progress.
 */
class HighlightState {

    private static final float HIGHLIGHT_ANIMATION_SPEED = 10f;

    private final Set<Plot> highlightedPlots = new HashSet<>();
    private final Set<Plot> candidatePlots = new HashSet<>();
    private final Set<Plot> attackCandidatePlots = new HashSet<>();
    private final Set<Plot> friendlyCandidatePlots = new HashSet<>();

    private final Map<Plot, AnimState> states = new HashMap<>();

    static class AnimState {
        float selectedProgress = 0f;
        float candidateProgress = 0f;
        float attackProgress = 0f;
        float friendlyProgress = 0f;

        boolean isFinished() {
            return selectedProgress <= 0f && candidateProgress <= 0f && attackProgress <= 0f && friendlyProgress <= 0f;
        }
    }

    Map<Plot, AnimState> getStates() {
        return states;
    }

    void update() {
        highlightedPlots.clear();
        candidatePlots.clear();
        attackCandidatePlots.clear();
        friendlyCandidatePlots.clear();

        if (GameContext.get().getInteractionManager().hasActiveSelection()) {
            CustomBox source = GameContext.get().getInteractionManager().getActiveSource();
            if (source != null) {
                updateSelectedTargetHighlights();

                if (source instanceof Plot sourcePlot) {
                    updateMovementAndAttackHighlights(sourcePlot);
                } else {
                    updateAbilityEligibleTargetHighlights(source);
                }
            }
        }

        updateAnimationStates();
    }

    boolean isHighlighted(Plot p) { return highlightedPlots.contains(p); }
    boolean isCandidate(Plot p) { return candidatePlots.contains(p); }
    boolean isAttackCandidate(Plot p) { return attackCandidatePlots.contains(p); }
    boolean isFriendlyCandidate(Plot p) { return friendlyCandidatePlots.contains(p); }

    private void updateAnimationStates() {
        float dt = Gdx.graphics.getDeltaTime();

        Set<Plot> activePlots = new HashSet<>();
        activePlots.addAll(highlightedPlots);
        activePlots.addAll(candidatePlots);
        activePlots.addAll(attackCandidatePlots);
        activePlots.addAll(friendlyCandidatePlots);

        for (Plot p : activePlots) {
            states.putIfAbsent(p, new AnimState());
        }

        Iterator<Map.Entry<Plot, AnimState>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Plot, AnimState> entry = it.next();
            Plot p = entry.getKey();
            AnimState s = entry.getValue();

            s.selectedProgress = advanceProgress(s.selectedProgress, highlightedPlots.contains(p), dt);
            s.candidateProgress = advanceProgress(s.candidateProgress, candidatePlots.contains(p), dt);
            s.attackProgress = advanceProgress(s.attackProgress, attackCandidatePlots.contains(p), dt);
            s.friendlyProgress = advanceProgress(s.friendlyProgress, friendlyCandidatePlots.contains(p), dt);

            if (s.isFinished() && !activePlots.contains(p)) {
                it.remove();
            }
        }
    }

    private float advanceProgress(float current, boolean active, float dt) {
        if (active) return Math.min(1f, current + HIGHLIGHT_ANIMATION_SPEED * dt);
        else return Math.max(0f, current - HIGHLIGHT_ANIMATION_SPEED * dt);
    }

    private void updateSelectedTargetHighlights() {
        List<CustomBox> targets = GameContext.get().getInteractionManager().getActiveTargets();
        if (targets.isEmpty()) return;

        for (CustomBox target : targets) {
            if (target instanceof Plot p) {
                highlightedPlots.add(p);
            } else if (target instanceof GamePiece gp) {
                Board.Position pos = AbilityContext.getOwnerPos(gp);
                if (pos != null && pos.getBoard() != null) {
                    Renderable r = pos.getBoard().getPlotAtPos(pos.getRow(), pos.getCol());
                    if (r instanceof Plot p) highlightedPlots.add(p);
                }
            }
        }
    }

    private void updateMovementAndAttackHighlights(Plot sourcePlot) {
        Board board = sourcePlot.getBoard();
        int[] sIdx = sourcePlot.getIndices();
        if (sIdx == null) return;
        int sr = sIdx[0], sc = sIdx[1];
        GamePiece gp = board.getGamePieceAtPos(sr, sc);
        if (!(gp instanceof MonsterGamePiece mgp)) return;
        if (mgp.getAlignment() != GameContext.get().getTurnManager().getCurrentPlayer()) return;
        if (mgp.isStunned()) return;

        List<Plot> reachable = List.of();
        List<Plot> attackables = List.of();
        JumpMoveAbility jumpMoveAbility = null;
        BaseMoveAbility baseMoveAbility = null;
        OncePerTurnAttackAbility oncePerTurnAttackAbility = null;

        for (Ability ability : mgp.getAbilities()) {
            if (ability instanceof BasicAbility basicAbility) {
                if (basicAbility instanceof JumpMoveAbility jma) {
                    jumpMoveAbility = jma;
                } else if (basicAbility instanceof BaseMoveAbility bma) {
                    baseMoveAbility = bma;
                } else if (basicAbility instanceof OncePerTurnAttackAbility opta) {
                    oncePerTurnAttackAbility = opta;
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

        for (Plot p : attackables) {
            if (p != null) attackCandidatePlots.add(p);
        }
        for (Plot p : reachable) {
            if (p != null && !attackCandidatePlots.contains(p)) {
                candidatePlots.add(p);
            }
        }
    }

    private void updateAbilityEligibleTargetHighlights(CustomBox source) {
        if (!(source instanceof TargetFilter filter)) return;

        int selectedCount = GameContext.get().getInteractionManager().getSelectedCount();
        List<Plot> eligiblePlots = filter.getEligibleTargets(selectedCount);
        if (eligiblePlots == null || eligiblePlots.isEmpty()) return;

        PieceAlignment currentPlayer = GameContext.get().getTurnManager().getCurrentPlayer();
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
                candidatePlots.add(plot);
            }
        }
    }
}
