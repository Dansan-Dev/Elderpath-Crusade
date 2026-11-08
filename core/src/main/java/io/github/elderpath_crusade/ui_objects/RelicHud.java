package io.github.elderpath_crusade.ui_objects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.client.ClientController;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.input.InteractionManager;
import io.github.elderpath_crusade.match.LegalActions;
import io.github.elderpath_crusade.match.RelicCarrierIds;
import io.github.elderpath_crusade.supers.HigherOrderUI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A small, always-visible row of clickable icons for the roguelike run's active relics
 * (UPG-001) — the relic carrier piece is invisible and off-board, so AbilityPopup explicitly
 * skips its abilities rather than anchoring a bubble to a plot it doesn't have; they surface
 * here instead, reading the same LegalActions.pieces() the board bubbles do. Only ever
 * non-empty during a roguelike run with at least one active-ability relic — added to
 * RunBattleRoom only, not the shared BattleRoom every other mode uses.
 */
public class RelicHud extends HigherOrderUI {
    private static final int ICON_SIZE = 50;
    private static final int SPACING = 10;
    private static final int BOTTOM_MARGIN = 20;
    private static final int LEFT_MARGIN = 20;
    private static final int Z = 20;

    private final Map<String, AbilityBubble> activeIcons = new LinkedHashMap<>();

    public RelicHud() {
        super();
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        GameContext ctx = GameContext.get();
        if (ctx == null) return;
        InteractionManager interactionManager = ctx.getInteractionManager();
        ClientController controller = ctx.getClientController();
        LegalActions actions = controller != null ? controller.legalActions() : null;

        List<String> desired = new ArrayList<>();
        if (actions != null) {
            for (LegalActions.PieceActions pa : actions.pieces()) {
                if (!RelicCarrierIds.isRelicCarrier(pa.pieceId())) continue;
                for (LegalActions.AbilityOption option : pa.abilities()) {
                    desired.add(pa.pieceId() + "_" + option.actionIndex());
                }
            }
        }

        Set<String> stale = new HashSet<>(activeIcons.keySet());
        stale.removeAll(desired);
        for (String key : stale) {
            interactionManager.removeClickable(activeIcons.get(key));
            activeIcons.remove(key);
        }

        int index = 0;
        for (String key : desired) {
            if (!activeIcons.containsKey(key)) {
                int last = key.lastIndexOf('_');
                String pieceId = key.substring(0, last);
                int actionIndex = Integer.parseInt(key.substring(last + 1));
                int x = LEFT_MARGIN + index * (ICON_SIZE + SPACING);
                AbilityBubble bubble = new AbilityBubble(x, BOTTOM_MARGIN, ICON_SIZE, Z)
                        .withIndexLabel(actionIndex + 1, Color.WHITE)
                        .withOnClick((e) -> activate(pieceId, actionIndex), ClickableEffectData.getImmediate());
                activeIcons.put(key, bubble);
                interactionManager.addClickable(bubble);
            }
            index++;
        }

        for (AbilityBubble bubble : activeIcons.values()) {
            bubble.renderUI(batch, isPaused);
        }
    }

    private static void activate(String pieceId, int actionIndex) {
        GameContext.get().getClientController().activateAbility(pieceId, actionIndex);
    }
}
