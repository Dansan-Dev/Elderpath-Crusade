package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;

public class SniperCard extends SummonCard {
    public SniperCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override protected String getRegistryKey() { return "Sniper"; }
    @Override protected String getCardName() { return "Sniper"; }
}
