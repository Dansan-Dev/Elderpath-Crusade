package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.ActionableAbilityExecutor;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game.PlayerManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes a seat's LegalActions from the authoritative rules themselves — nothing here
 * decides legality on its own:
 * - moves/attacks: candidates from BoardGrid.getReachable / getAttackableEnemies (the only
 *   destinations MoveRules.validate can accept), each kept only if MoveRules.validate is OK
 * - abilities: AbilityRules.validate (turn, piece, stun, actions, costs, a valid target)
 * - cards: CardRules.validateSummon / validateSpell for every tile (or no target)
 * - end turn: TurnRules.validateEndTurn
 * - a pending pick blocks everything else, as GameServer's PICK_PENDING does
 * So a listed action is exactly one GameServer would accept, and nothing else is.
 */
public final class LegalActionsCalculator {
    private static final Family PIECES = Family.all(IdentityComponent.class, PositionComponent.class,
            AlignmentComponent.class, StatsComponent.class).get();

    private LegalActionsCalculator() {}

    public static LegalActions compute(PieceAlignment seat) {
        GameContext ctx = GameContext.get();
        BoardGrid grid = ctx.getActiveGrid();
        if (grid == null || seat == null) return LegalActions.none(seat);
        if (ctx.getPickRouter().hasPending(seat)) {
            PickRequest pending = ctx.getPickRouter().activeRequest(seat);
            return new LegalActions(seat, isCurrent(seat), pending != null ? pending.pickId() : null,
                    false, List.of(), List.of());
        }
        // Every rule rejects a seat whose turn it isn't — skip computing what can only be empty.
        if (!isCurrent(seat)) return LegalActions.none(seat);

        return new LegalActions(seat, true, null, TurnRules.validateEndTurn(seat).isOk(),
                pieceActions(grid, seat), cardActions(grid, seat));
    }

    private static boolean isCurrent(PieceAlignment seat) {
        return seat == GameContext.get().getTurnManager().getCurrentPlayer();
    }

    private static List<LegalActions.PieceActions> pieceActions(BoardGrid grid, PieceAlignment seat) {
        ImmutableArray<Entity> entities = GameContext.get().getEcsEngine().getEntitiesFor(PIECES);
        List<LegalActions.PieceActions> out = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            Entity piece = entities.get(i);
            if (EntityUtils.getAlignment(piece) != seat) continue;
            int row = EntityUtils.getRow(piece);
            int col = EntityUtils.getCol(piece);

            List<Coord> moves = new ArrayList<>();
            List<Coord> attacks = new ArrayList<>();
            // MoveRules acts on whatever the grid holds at the source, so only an on-board piece can move.
            Set<Coord> candidates = new LinkedHashSet<>();
            if (grid.isInBounds(row, col) && grid.getEntityAt(row, col) == piece) {
                candidates.addAll(grid.getReachable(piece, row, col, EntityUtils.getSpeed(piece)));
                candidates.addAll(grid.getAttackableEnemies(row, col, seat));
            }
            for (Coord dst : candidates) {
                if (!MoveRules.validate(seat, row, col, dst.row(), dst.col()).isOk()) continue;
                (grid.isOccupied(dst.row(), dst.col()) ? attacks : moves).add(dst);
            }
            moves.sort(ROW_MAJOR);
            attacks.sort(ROW_MAJOR);

            List<LegalActions.AbilityOption> abilities = new ArrayList<>();
            String pieceId = EntityUtils.getId(piece);
            List<AbilityDefinition> actionable = AbilityRules.actionableAbilities(piece);
            for (int index = 0; index < actionable.size(); index++) {
                if (!AbilityRules.validate(seat, pieceId, index).isOk()) continue;
                AbilityDefinition def = actionable.get(index);
                boolean targeted = ActionableAbilityExecutor.firstPickCandidates(piece, def.actions().get(0)) != null;
                abilities.add(new LegalActions.AbilityOption(index, def.id(), targeted));
            }

            if (!moves.isEmpty() || !attacks.isEmpty() || !abilities.isEmpty()) {
                out.add(new LegalActions.PieceActions(pieceId, new Coord(row, col), moves, attacks, abilities));
            }
        }
        out.sort(Comparator.comparing(LegalActions.PieceActions::pieceId));
        return out;
    }

    private static List<LegalActions.CardActions> cardActions(BoardGrid grid, PieceAlignment seat) {
        PlayerManager.PlayerState player = GameContext.get().getPlayerManager().get(seat);
        if (player == null || player.hand == null) return List.of();
        CardRules rules = GameContext.get().getCardRules();
        List<LegalActions.CardActions> out = new ArrayList<>();
        for (CardInstance card : player.hand.getCards()) {
            String id = card.instanceId();
            CardCatalog.Entry entry = CardCatalog.get(card.cardName());
            boolean untargeted = entry instanceof CardCatalog.Spell spell && spell.definition().targeting() == null
                    && rules.validateSpell(seat, id, null, null).isOk();
            List<Coord> targets = new ArrayList<>();
            if (!untargeted) {
                for (int r = 0; r < grid.getRows(); r++) {
                    for (int c = 0; c < grid.getCols(); c++) {
                        boolean ok = entry instanceof CardCatalog.Summon
                                ? rules.validateSummon(seat, id, r, c).isOk()
                                : rules.validateSpell(seat, id, r, c).isOk();
                        if (ok) targets.add(new Coord(r, c));
                    }
                }
            }
            if (untargeted || !targets.isEmpty()) out.add(new LegalActions.CardActions(id, untargeted, targets));
        }
        return out;
    }

    private static final Comparator<Coord> ROW_MAJOR = Comparator.comparingInt(Coord::row).thenComparingInt(Coord::col);
}
