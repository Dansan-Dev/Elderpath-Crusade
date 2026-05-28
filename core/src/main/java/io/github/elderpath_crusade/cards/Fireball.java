package io.github.elderpath_crusade.cards;

import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.game_objects.cards.SpellCard;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.utils.AbilityUtils;

import java.util.HashMap;

/**
 * A basic spell card that deals 2 damage to a target piece.
 */
public class Fireball extends SpellCard {

    public Fireball(
            Board board, PieceAlignment alignment,
            int x, int y,
            int width, int height,
            int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override
    protected String getSpellName() {
        return "Fireball";
    }

    @Override
    protected String getSpellDescription() {
        return "Deal 2 damage to a target piece.";
    }

    @Override
    protected int getManaCost() {
        return 3;
    }

    @Override
    protected ClickableEffectData getSpellEffectData() {
        return ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1);
    }

    @Override
    protected void applySpellEffect(HashMap<Integer, CustomBox> entities) {
        CustomBox firstClicked = entities.get(1);
        if (firstClicked instanceof Plot plot) {
            GamePiece gp = board.getGamePieceAtPlot(plot);
            if (gp instanceof MonsterGamePiece target) {
                AbilityUtils.dealDamage(target, 2, null, true);
            }
        }
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (box instanceof Plot plot) {
            GamePiece gp = board.getGamePieceAtPlot(plot);
            return gp instanceof MonsterGamePiece;
        }
        return false;
    }
}
