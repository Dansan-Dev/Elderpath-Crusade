package io.github.elderpath_crusade.abilities.impl.trigger;
import io.github.elderpath_crusade.ecs.components.StunComponent;

import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

/**
 * Sniper ability: ON ATTACK: Get stunned for 2 turns.
 * When the owner attacks, sets STUN_TURNS_REMAINING to 2.
 * On turn start, if stunned, sets remainingActions to 0 and decrements stun turns.
 */
public class StunSelfOnAttackAbility implements TriggeredAbility {
    private MonsterGamePiece owner;

    @Override
    public String getName() { return "Self-Stun"; }

    @Override
    public String getDescription() { return getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "ON ATTACK: This gets stunned for 2 turns";
    }

    @Override
    public void onAttach(MonsterGamePiece owner) {
        this.owner = owner;
    }

    @Override
    public void onDetach() {
        this.owner = null;
    }

    @Override
    public void onOwnerAttack(MonsterGamePiece owner, MonsterGamePiece target, int damage) {
        if (this.owner == null || owner != this.owner) return;
        // Set stun to 3 turns (will be decremented at the start of each of the owner's turns)
        // This ensures stun lasts for 2 full turns after the attack:
        // - Attack turn: stun=3 (shows stun tint)
        // - Turn 1 After: stun=3->2 (shows stun tint)
        // - Turn 2 After: stun=2->1 (shows stun tint)
        // - Turn 3 After: stun=1->0 (stun cleared, may show exhaust if no actions)
        if (owner.getEntity() != null) {
            StunComponent stun = owner.getEntity().getComponent(StunComponent.class);
            if (stun == null) {
                stun = new StunComponent();
                owner.getEntity().add(stun);
            }
            stun.turnsRemaining = 3;
        }
        owner.updateData(GamePieceData.STUN_TURNS_REMAINING, 3);
    }

    @Override
    public void onTurnStarted(PieceAlignment currentPlayer) {
        if (this.owner == null) return;
        if (owner.getAlignment() != currentPlayer) return;

        if (owner.getEntity() != null) {
            StunComponent stun = owner.getEntity().getComponent(StunComponent.class);
            if (stun != null && stun.turnsRemaining > 0) {
                stun.decrement();
                owner.updateData(GamePieceData.STUN_TURNS_REMAINING, stun.turnsRemaining > 0 ? stun.turnsRemaining : null);
                return;
            }
        }

        // Fallback for non-entity pieces
        Object stunObj = owner.getData(GamePieceData.STUN_TURNS_REMAINING);
        int stunTurns = 0;
        if (stunObj instanceof Integer) {
            stunTurns = (Integer) stunObj;
        }
        if (stunTurns > 0) {
            stunTurns--;
            owner.updateData(GamePieceData.STUN_TURNS_REMAINING, stunTurns > 0 ? stunTurns : null);
        }
    }
}

