package io.github.elderpath_crusade.rendering.highlight;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
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
            }
        }
    }

    private void updateMovementAndAttackHighlights(Plot sourcePlot) {
        Board board = sourcePlot.getBoard();
        if (board == null) return;
        int sr = sourcePlot.getRow(), sc = sourcePlot.getCol();
        Entity entity = board.getEntityAtPos(sr, sc);
        if (entity == null) return;
        if (EntityUtils.getAlignment(entity) != GameContext.get().getTurnManager().getCurrentPlayer()) return;
        if (EntityUtils.isStunned(entity)) return;

        int speed = EntityUtils.getSpeed(entity);
        List<Plot> reachable = board.getReachablePlots(sr, sc, speed);
        List<Plot> attackable = board.getAttackableEnemyPlots(sr, sc, EntityUtils.getAlignment(entity));

        for (Plot p : attackable) {
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

            Entity entity = board.getEntityAtPlot(plot);
            if (entity != null) {
                PieceAlignment pieceAlignment = EntityUtils.getAlignment(entity);
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
