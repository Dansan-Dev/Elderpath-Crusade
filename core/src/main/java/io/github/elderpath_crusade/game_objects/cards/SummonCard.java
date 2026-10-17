package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.multiplayer.net.NetworkCommand;
import io.github.elderpath_crusade.server.ActionDispatcher;

import java.util.HashMap;

/**
 * Renderable view of a summon card in a hand. Offers the play (click → pick a plot) when
 * CardRules allows it and sends the play — by card instance id — to GameServer (or the host,
 * via ActionDispatcher). The rules themselves live in CardRules.
 */
public class SummonCard extends UnitCard implements TargetFilter {
    protected final PieceAlignment alignment;
    private final String cardName;
    private final String registryKey;

    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    public SummonCard(
        PieceAlignment alignment,
        int x, int y,
        int width, int height,
        int z,
        String cardName, String registryKey
    ) {
        super(x, y, width, height, z, registryKey, cardName);
        this.alignment = alignment;
        this.cardName = cardName;
        this.registryKey = registryKey;
        initializeClickableEffect();
    }

    @Override
    public String getCardName() { return cardName; }

    @Override
    public String getDisplayName() { return cardName; }

    @Override
    public String getRegistryKey() { return registryKey; }

    private void initializeClickableEffect() {
        setClickableEffect(
            (HashMap<Integer, CustomBox> entities) -> {
                Object secondClicked = entities == null ? null : entities.get(1);
                if (!(secondClicked instanceof Plot plot)) return;
                int row = plot.getRow();
                int col = plot.getCol();
                String instanceId = getInstanceId();
                PieceAlignment actor = GameContext.get().getLocalControl().actingSide();
                ActionDispatcher.dispatch(
                        () -> new NetworkCommand.PlaySummonCard(instanceId, row, col),
                        () -> GameContext.get().getGameServer().playSummonCard(actor, instanceId, row, col)
                );
            },
            ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1)
        );
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (!(box instanceof Plot plot)) return false;
        return GameContext.get().getCardRules().isValidSummonTarget(alignment, plot.getRow(), plot.getCol());
    }

    @Override
    public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        this.onClick = onClick;
        this.clickableEffectData = effectData;
    }

    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (this.onClick == null) return;
        this.onClick.run(interactionEntities);
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        if (!GameContext.get().getLocalControl().controls(alignment)) return null;
        if (!GameContext.get().getCardRules().canPlay(alignment, cardName)) return null;
        return clickableEffectData;
    }
}
