package io.github.elderpath_crusade.cards;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.StunComponent;
import io.github.elderpath_crusade.ecs.systems.CombatSystem;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.game_objects.cards.SpellCard;
import io.github.elderpath_crusade.interfaces.CustomBox;

import java.util.HashMap;

public class Frostbolt extends SpellCard {

    public Frostbolt(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override protected String getSpellName() { return "Frostbolt"; }
    @Override protected String getSpellDescription() { return "Deal 1 damage to an enemy\nand freeze it (0 actions)."; }
    @Override protected int getManaCost() { return 2; }

    @Override
    protected ClickableEffectData getSpellEffectData() {
        return ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1);
    }

    @Override
    protected void applySpellEffect(HashMap<Integer, CustomBox> entities) {
        CustomBox target = entities.get(1);
        if (target instanceof Plot plot) {
            Entity entity = board.getEntityAtPlot(plot);
            if (entity != null) {
                GameContext.get().getEcsEngine().getSystem(CombatSystem.class).applyDamage(entity, 1);
                StatsComponent stats = entity.getComponent(StatsComponent.class);
                if (stats != null && stats.currentHealth > 0) {
                    stats.remainingActions = 0;
                }
            }
        }
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (box instanceof Plot plot) {
            Entity entity = board.getEntityAtPlot(plot);
            if (entity != null) {
                PieceAlignment target = EntityUtils.getAlignment(entity);
                return target != alignment && target != PieceAlignment.NEUTRAL;
            }
        }
        return false;
    }
}
