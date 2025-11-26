package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl.passive.CrossbowmanRangeAbility;
import io.github.elderpath_crusade.abilities.impl.trigger.ExcessDamageCarryOverAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.Crossbowman;

import java.util.List;

/**
 * Crossbowman card. Ranged 2; Can only attack once a turn; Excess damage carries over behind target
 */
public class CrossbowmanCard extends SummonCard {
    public CrossbowmanCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected GamePieceStats buildStats() { return GamePieceStats.getMonsterStats(3, 1, 2, 1, 2); }

    @Override
    protected String getCardName() { return "Crossbowman"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new Crossbowman(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() {
        return List.of(
            CrossbowmanRangeAbility.getAbilityDescription(),
            "Can only attack\nonce per turn",
            ExcessDamageCarryOverAbility.getAbilityDescription()
        );
    }
}
