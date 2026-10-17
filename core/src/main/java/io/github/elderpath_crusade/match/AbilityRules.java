package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.ActionableAbilityExecutor;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.Cost;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.AbilityInstanceComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.game.TurnManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Authoritative validation for activating a piece's actionable ability — the same gates the
 * ability bubbles show (own piece, own turn, not stunned, actions left), plus affordable
 * costs — before handing it to ActionableAbilityExecutor.
 */
public final class AbilityRules {
    private static final Family IDENTIFIED = Family.all(IdentityComponent.class).get();

    private AbilityRules() {}

    public static ActionResult activate(PieceAlignment actor, String pieceId, int actionIndex) {
        if (GameContext.get().getActiveGrid() == null) return rejected(ActionResult.Reason.NO_ACTIVE_MATCH);
        TurnManager turns = GameContext.get().getTurnManager();
        if (actor == null || actor != turns.getCurrentPlayer() || turns.isWaitingForNextPlayer()) {
            return rejected(ActionResult.Reason.NOT_YOUR_TURN);
        }
        Entity piece = findPiece(pieceId);
        if (piece == null) return rejected(ActionResult.Reason.NO_PIECE);
        if (EntityUtils.getAlignment(piece) != actor) return rejected(ActionResult.Reason.NOT_YOUR_PIECE);
        if (EntityUtils.isStunned(piece)) return rejected(ActionResult.Reason.PIECE_STUNNED);
        StatsComponent stats = piece.getComponent(StatsComponent.class);
        if (stats == null || stats.remainingActions <= 0) return rejected(ActionResult.Reason.PIECE_EXHAUSTED);

        List<AbilityDefinition> actionable = actionableAbilities(piece);
        if (actionIndex < 0 || actionIndex >= actionable.size()) return rejected(ActionResult.Reason.NO_SUCH_ABILITY);
        AbilityDefinition def = actionable.get(actionIndex);
        var action = def.actions().get(0);

        ActionResult affordable = checkCosts(piece, stats, actor, action.costs());
        if (!affordable.isOk()) return affordable;

        return ActionableAbilityExecutor.execute(piece, def, action);
    }

    /** The piece's abilities that have a player-activated action, in attachment order (bubble order). */
    public static List<AbilityDefinition> actionableAbilities(Entity piece) {
        AbilityInstanceComponent aic = piece.getComponent(AbilityInstanceComponent.class);
        List<AbilityDefinition> out = new ArrayList<>();
        if (aic == null) return out;
        for (AbilityDefinition def : aic.definitions) {
            if (def.actions() != null && !def.actions().isEmpty()) out.add(def);
        }
        return out;
    }

    private static ActionResult checkCosts(Entity piece, StatsComponent stats, PieceAlignment actor, List<Cost> costs) {
        if (costs == null) return ActionResult.ok();
        for (Cost cost : costs) {
            if ("Action".equals(cost.type()) && stats.remainingActions < cost.amount()) {
                return rejected(ActionResult.Reason.PIECE_EXHAUSTED);
            }
            if ("Mana".equals(cost.type())) {
                PlayerManager.PlayerState player = GameContext.get().getPlayerManager().get(actor);
                if (player == null || player.getMana() < cost.amount()) return rejected(ActionResult.Reason.NOT_ENOUGH_MANA);
            }
        }
        return ActionResult.ok();
    }

    private static Entity findPiece(String pieceId) {
        if (pieceId == null) return null;
        ImmutableArray<Entity> all = GameContext.get().getEcsEngine().getEntitiesFor(IDENTIFIED);
        for (int i = 0; i < all.size(); i++) {
            IdentityComponent id = all.get(i).getComponent(IdentityComponent.class);
            if (pieceId.equals(id.id)) return all.get(i);
        }
        return null;
    }

    private static ActionResult rejected(ActionResult.Reason reason) {
        return ActionResult.rejected(reason);
    }
}
