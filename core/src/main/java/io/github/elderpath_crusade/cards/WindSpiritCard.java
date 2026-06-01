package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl.actionable.BoostActionAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.WindSpirit;

import java.util.List;

/**
 * Wind Spirit card. Cannot Attack. BOOST ACTION: Give an adjacent friendly unit +1 action this turn.
 */
public class WindSpiritCard extends SummonCard {
    public WindSpiritCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected String getRegistryKey() { return "WindSpirit"; }

    @Override
    protected String getCardName() { return "Wind Spirit"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new WindSpirit(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() { return List.of(BoostActionAbility.getAbilityDescription()); }
}
