package io.github.elderpath_crusade.abilities.impl.trigger;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.multiplayer.GameEvent;
import io.github.elderpath_crusade.multiplayer.GameEventType;

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
        GameEventType type = event.getType();

        // Check PIECE_ATTACKED events to verify the owner attacked the tracked target
        if (type == GameEventType.PIECE_ATTACKED) {
            String attackerId = (String) event.getData().get("attackerId");
            String defenderId = (String) event.getData().get("defenderId");

            // If this attack is from the owner and targets our tracked target, mark it
            if (attackerId != null && attackerId.equals(owner.getId().toString())) {
                if (trackedTargetId != null && trackedTargetId.equals(defenderId)) {
                    trackedTargetAttackedByOwner = true;
                } else {
                    // Owner attacked a different target - clear tracking (attack sequence ended)
                    clearTracking();
                }
            }
        }

        // Check PIECE_DIED events to see if our tracked target died
        if (type == GameEventType.PIECE_DIED) {
            String deadPieceId = (String) event.getData().get("pieceId");

            // Only process if:
            // 1. We have a tracked target
            // 2. The dead piece matches our tracked target
            // 3. We confirmed the owner attacked this target (verified via PIECE_ATTACKED event)
            if (trackedTargetId != null
                && deadPieceId != null
                && trackedTargetId.equals(deadPieceId)
                && trackedTargetAttackedByOwner) {

                // Apply growth: permanently increase damage by 1
                growthModifier.addDamage += 1;

                // Remove and re-add the modifier to ensure it's in the accumulator
                // (it may not have been added initially since it was a no-op)
                owner.getStatsAccumulator().remove(growthModifier);
                owner.getStatsAccumulator().add(growthModifier);

                // Heal 1 HP (capped at max health)
                owner.heal(1);
            }

            // Clear tracking after processing death (whether it was our kill or not)
            // This ensures we only track immediate kills from the current attack
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

