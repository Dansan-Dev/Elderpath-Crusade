package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl.trigger.CleaveAttackAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.Barbarian;

import java.util.List;

/**
 * Barbarian card. ON ATTACK: Deal damage to all adjacent squares (cleave).
 */
public class BarbarianCard extends SummonCard {
    public BarbarianCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected String getCardName() { return "Barbarian"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new Barbarian(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() {
        return List.of(CleaveAttackAbility.getAbilityDescription());
    }
}
