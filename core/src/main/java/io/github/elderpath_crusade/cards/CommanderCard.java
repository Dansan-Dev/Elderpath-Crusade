package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.abilities.impl._multi.aura.CommanderAuraAbility;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.characters.pieces.Commander;

import java.util.List;

/**
 * Commander card. Adjacent friendly units gain +1 attack.
 */
public class CommanderCard extends SummonCard {
    public CommanderCard(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected GamePieceStats buildStats() { return GamePieceStats.getMonsterStats(1, 1, 0, 1, 2); }

    @Override
    protected String getCardName() { return "Commander"; }

    @Override
    protected GamePiece instantiatePiece(GamePieceStats stats) {
        return new Commander(stats, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), alignment);
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() { return List.of(CommanderAuraAbility.getAbilityDescription()); }
}
