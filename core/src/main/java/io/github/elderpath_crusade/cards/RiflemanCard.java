package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl.passive.RiflemanRangeAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.Rifleman;

import java.util.List;

/**
 * Rifleman card. Ranged 2 via passive.
 */
public class RiflemanCard extends SummonCard {
    public RiflemanCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected GamePieceStats buildStats() {
        return GamePieceStats.getMonsterStats(2, 1, 1, 1, 1);
    }

    @Override
    protected String getCardName() { return "Rifleman"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new Rifleman(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() { return List.of(RiflemanRangeAbility.getAbilityDescription()); }
}
