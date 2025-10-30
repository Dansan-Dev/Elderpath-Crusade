package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.Charger;

import java.util.List;

/**
 * Charger card. TODO rules: On attack: push target 1 back and move 1 towards; if collision, all involved take 1 damage
 */
public class ChargerCard extends SummonCard {
    public ChargerCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected GamePieceStats buildStats() { return GamePieceStats.getMonsterStats(2, 3, 1, 1, 1); }

    @Override
    protected String getCardName() { return "Charger"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new Charger(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() { return List.of(); }
}
