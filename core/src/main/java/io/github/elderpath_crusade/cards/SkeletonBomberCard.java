package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;

public class SkeletonBomberCard extends SummonCard {
    public SkeletonBomberCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override protected String getRegistryKey() { return "SkeletonBomber"; }
    @Override protected String getCardName() { return "Skeleton Bomber"; }
}
