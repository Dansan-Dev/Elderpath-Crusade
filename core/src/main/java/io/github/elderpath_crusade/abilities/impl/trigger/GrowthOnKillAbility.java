package io.github.elderpath_crusade.abilities.impl.trigger;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.events.PieceAttackedEvent;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

/**
 * Hero ability: ON KILL: gain 1 attack and heal 1.
 * When the owner kills an enemy, permanently increase damage by 1 and heal 1 HP.
 * Tracks attack targets and verifies kills via PIECE_DIED events.
 */
public class GrowthOnKillAbility implements TriggeredAbility {
    private final StatsModifier growthModifier;
    private MonsterGamePiece owner;
    // Track the target of the current attack sequence (cleared after attack completes)
    private String trackedTargetId;
    // Flag to indicate if the current tracked target was attacked by this owner
    private boolean trackedTargetAttackedByOwner;

    public GrowthOnKillAbility() {
        this.growthModifier = new StatsModifier();
        this.growthModifier.source = this;
        this.growthModifier.addDamage = 0; // Will be incremented on each kill
    }

    @Override
    public String getName() { return "Growth"; }

    @Override
    public String getDescription() { return GrowthOnKillAbility.getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "ON KILL: gain 1 attack and heal 1";
    }

    @Override
    public void onAttach(MonsterGamePiece owner) {
        this.owner = owner;
        // Don't add modifier yet - it's a no-op initially (addDamage = 0)
        // It will be added to the accumulator when we first increment addDamage
    }

    @Override
    public void onDetach() {
        if (owner != null) {
            owner.getStatsAccumulator().remove(growthModifier);
        }
        this.owner = null;
        clearTracking();
    }

    @Override
    public void onOwnerAttack(MonsterGamePiece owner, MonsterGamePiece target, int damage) {
        if (this.owner == null || owner != this.owner) return;
        if (target == null) return;

        // Clear any previous tracking (new attack sequence starting)
        clearTracking();

        // Start tracking this target for the current attack sequence
        trackedTargetId = target.getId().toString();
        trackedTargetAttackedByOwner = false; // Will be set to true when we see PIECE_ATTACKED event
    }

    @Override
    public void onGameEvent(GameEvent event) {
        if (owner == null) return;

        if (event instanceof PieceAttackedEvent attacked) {
            String attackerId = attacked.attackerId();
            String defenderId = attacked.defenderId();

            if (attackerId != null && attackerId.equals(owner.getId().toString())) {
                if (trackedTargetId != null && trackedTargetId.equals(defenderId)) {
                    trackedTargetAttackedByOwner = true;
                } else {
                    clearTracking();
                }
            }
        }

        if (event instanceof PieceDiedEvent died) {
            String deadPieceId = died.pieceId();

            if (trackedTargetId != null
                && deadPieceId != null
                && trackedTargetId.equals(deadPieceId)
                && trackedTargetAttackedByOwner) {

                growthModifier.addDamage += 1;
                owner.getStatsAccumulator().remove(growthModifier);
                owner.getStatsAccumulator().add(growthModifier);
                owner.heal(1);
            }

            clearTracking();
        }
    }

    /**
     * Clear the tracked target. Called after attack sequence completes.
     */
    private void clearTracking() {
        trackedTargetId = null;
        trackedTargetAttackedByOwner = false;
    }
}

