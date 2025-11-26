package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl.actionable.DisplaceAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.WarpMage;

import java.util.List;

/**
 * Warp Mage card.
 * Note: The actionable Displace ability is implemented on the piece itself (DisplaceAbility).
 * Card text is intentionally empty to rely on ability descriptions, per project guidelines.
 */
public class WarpMageCard extends SummonCard {
    public WarpMageCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected GamePieceStats buildStats() { return GamePieceStats.getMonsterStats(0, 1, 0, 1, 2); }

    @Override
    protected String getCardName() { return "Warp Mage"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new WarpMage(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() { return List.of(DisplaceAbility.getAbilityDescription()); }
}
