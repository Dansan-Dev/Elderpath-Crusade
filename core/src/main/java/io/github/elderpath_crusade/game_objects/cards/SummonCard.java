package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.client.ClientController;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.LegalActions;

import java.util.HashMap;

/**
 * Renderable view of a summon card in a hand. Whether it's playable, and which tiles are valid
 * summon targets, come straight from this screen's LegalActions.CardActions entry for this
 * card's instance id (absent — not playable — while it's not this seat's turn, this card isn't
 * affordable, or a pick is pending). Playing it sends the card's instance id to the match
 * server via ClientController; the server re-validates and is the sole judge of legality.
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
                GameContext.get().getClientController().playSummon(getInstanceId(), plot.getRow(), plot.getCol());
            },
            ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1)
        );
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (!(box instanceof Plot plot)) return false;
        LegalActions.CardActions legal = legalCardActions();
        return legal != null && legal.targets().contains(new Coord(plot.getRow(), plot.getCol()));
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
        return legalCardActions() != null ? clickableEffectData : null;
    }

    private LegalActions.CardActions legalCardActions() {
        ClientController controller = GameContext.get().getClientController();
        LegalActions actions = controller != null ? controller.legalActions() : null;
        return actions != null ? actions.forCard(getInstanceId()) : null;
    }
}
