package io.github.forest_of_dreams.cards;

import io.github.forest_of_dreams.abilities.impl.OnSummonShockAbility;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.board.GamePieceStats;
import io.github.forest_of_dreams.game_objects.cards.SummonCard;
import io.github.forest_of_dreams.characters.pieces.Shockling;

import java.util.List;

public class ShocklingCard extends SummonCard {

    public ShocklingCard(
        Board board, PieceAlignment alignment,
        int x, int y, int width, int height, int z
    ) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected GamePieceStats buildStats() {
        // Shockling baseline
        return GamePieceStats.getMonsterStats(
            1, // cost
            1, // hp
            0, // dmg
            1, // speed
            1  // actions
        );
    }

    @Override
    protected String getCardName() { return "Shockling"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new Shockling(
            stats,
            0, 0,
            board.getPLOT_WIDTH(),
            board.getPLOT_HEIGHT(),
            alignment
        );
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() {
        return List.of(OnSummonShockAbility.getAbilityDescription());
    }
}
