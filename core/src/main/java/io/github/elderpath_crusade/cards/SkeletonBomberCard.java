package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl.actionable.BombActionAbility;
import io.github.elderpath_crusade.abilities.impl.passive.CannotAttackAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.SkeletonBomber;

import java.util.List;

/**
 * Skeleton Bomber card. Cannot Attack; BOMB ACTION: Deal damage equal to its attack to all units within 1 square
 */
public class SkeletonBomberCard extends SummonCard {
    public SkeletonBomberCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected String getRegistryKey() { return "SkeletonBomber"; }

    @Override
    protected String getCardName() { return "Skeleton Bomber"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new SkeletonBomber(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() {
        return List.of(
            CannotAttackAbility.getAbilityDescription(),
            BombActionAbility.getAbilityDescription()
        );
    }
}
