package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl._multi.aura.KingEnemyAuraAbility;
import io.github.elderpath_crusade.abilities.impl._multi.aura.KingFriendlyAuraAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePiece;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.King;

import java.util.List;

/**
 * King card. Enemies within 1 range have +1 action; Other friendly units have +1 health.
 */
public class KingCard extends SummonCard {
    public KingCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected GamePieceStats buildStats() { return GamePieceStats.getMonsterStats(3, 2, 0, 1, 1); }

    @Override
    protected String getCardName() { return "King"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new King(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() {
        return List.of(
            KingEnemyAuraAbility.getAbilityDescription(),
            KingFriendlyAuraAbility.getAbilityDescription()
        );
    }
}
