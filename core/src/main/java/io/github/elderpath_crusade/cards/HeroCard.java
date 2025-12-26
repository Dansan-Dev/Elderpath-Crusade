package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl.trigger.GrowthOnKillAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePiece;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.Hero;

import java.util.List;

/**
 * Hero card. ON KILL: gain 1 attack and heal 1.
 */
public class HeroCard extends SummonCard {
    public HeroCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected GamePieceStats buildStats() { return GamePieceStats.getMonsterStats(2, 2, 1, 1, 1); }

    @Override
    protected String getCardName() { return "Hero"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new Hero(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() { return List.of(GrowthOnKillAbility.getAbilityDescription()); }
}
