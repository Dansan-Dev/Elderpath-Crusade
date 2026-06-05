package io.github.elderpath_crusade.cards;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.game_objects.cards.SpellCard;
import io.github.elderpath_crusade.interfaces.CustomBox;

import java.util.HashMap;

public class HealingLight extends SpellCard {

    public HealingLight(Board board, PieceAlignment alignment, int x, int y, int width, int height, int z) {
        super(board, alignment, x, y, width, height, z);
    }

    @Override protected String getSpellName() { return "Healing Light"; }
    @Override protected String getSpellDescription() { return "Heal a friendly piece\nfor 2 HP."; }
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
                StatsComponent stats = entity.getComponent(StatsComponent.class);
                if (stats != null) {
                    stats.currentHealth = Math.min(stats.currentHealth + 2, EntityUtils.getMaxHealth(entity));
                }
            }
        }
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (box instanceof Plot plot) {
            Entity entity = board.getEntityAtPlot(plot);
            if (entity != null) {
                return EntityUtils.getAlignment(entity) == alignment
                        && EntityUtils.getCurrentHealth(entity) < EntityUtils.getMaxHealth(entity);
            }
        }
        return false;
    }
}
