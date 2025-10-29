package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl.PackHunterAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.Wolf;
import java.util.List;

/**
 * WolfCard now extends SummonCard, providing stats, name, and piece instantiation.
 * Title and border animation are handled by Card.
 */
public class WolfCard extends SummonCard {

    public WolfCard(
        Board board, PieceAlignment alignment,
        int x, int y, int width, int height, int z
    ) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected GamePieceStats buildStats() {
        // Define the card's stats (cost, hp, dmg, speed, actions), delivered to the piece upon summon
        return GamePieceStats.getMonsterStats(
            1, // cost
            1, // maxHealth
            1, // damage
            1, // speed
            1  // actions
        );
    }

    @Override
    protected String getCardName() { return "Wolf"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new Wolf(
            stats,
            0, 0,
            board.getPLOT_WIDTH(),
            board.getPLOT_HEIGHT(),
            alignment
        );
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() {
        return List.of(PackHunterAbility.getAbilityDescription());
    }
}
