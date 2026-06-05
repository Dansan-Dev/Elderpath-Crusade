package io.github.elderpath_crusade.game_objects.cards;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.factory.PieceFactory;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.config.SettingsManager;
import io.github.elderpath_crusade.game.TurnManager;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.events.CardPlayedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.utils.Logger;

import java.util.HashMap;

/**
 * Data-driven summon card. Handles multi-target click flow, mana cost,
 * summoning onto a Board plot, emitting events, and consuming the card.
 */
public class SummonCard extends UnitCard implements TargetFilter {
    protected final Board board;
    protected final PieceAlignment alignment;
    private final String cardName;
    private final String registryKey;

    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    public SummonCard(
        Board board, PieceAlignment alignment,
        int x, int y,
        int width, int height,
        int z,
        String cardName, String registryKey
    ) {
        super(x, y, width, height, z);
        this.board = board;
        this.alignment = alignment;
        this.cardName = cardName;
        this.registryKey = registryKey;
        initializeClickableEffect();
    }

    @Override
    public String getCardName() { return cardName; }

    @Override
    protected String getRegistryKey() { return registryKey; }

    /**
     * Attempts to spend mana for this card based on its unified stats cost.
     * Returns true if the player had enough mana and the cost was deducted.
     */
    protected boolean trySpendMana() {
        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(alignment);
        int cost = getStatsCost();
        if (playerState == null || playerState.mana < cost) {
            Logger.log(
                "SummonCard",
                "Not enough mana. Need=" + cost + ", have=" + (playerState == null ? 0 : playerState.mana)
            );
            return false;
        }
        playerState.mana -= cost;
        return true;
    }

    /**
     * Creates the entity and places it on the board at the given location.
     * Emits CARD_PLAYED and PIECE_SPAWNED events upon success.
     */
    protected void performSummon(int row, int col) {
        String key = getRegistryKey();
        PieceDefinition def = PieceRegistry.get(key);
        if (def == null) {
            Logger.error("SummonCard", "No PieceDefinition for " + key);
            return;
        }
        Entity entity = PieceFactory.createPiece(def, 0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(),
                alignment, row, col);
        String pieceId = EntityUtils.getId(entity);
        board.addEntityToPos(row, col, entity, pieceId);

        TypedEventBus.get().emit(new PieceSpawnedEvent(pieceId, alignment, row, col));
        TypedEventBus.get().emit(new CardPlayedEvent(getCardName(), alignment, row, col, pieceId));
    }

    /**
     * Resolves the selected plot from the interaction entities into board coordinates.
     */
    private int[] resolveSelectedPlot(HashMap<Integer, CustomBox> entities) {
        if (board == null) return null;
        Object secondClicked = entities == null ? null : entities.get(1);
        if (!(secondClicked instanceof Plot plot)) {
            return null;
        }
        return plot.getIndices();
    }

    private void initializeClickableEffect() {
        setClickableEffect(
            (HashMap<Integer, CustomBox> entities) -> {
                int[] pos = resolveSelectedPlot(entities);
                if (pos == null) return;
                int row = pos[0];
                int col = pos[1];

                if (board.getEntityAtPos(row, col) != null) {
                    Logger.log("SummonCard", "Summon aborted: occupied (" + row + "," + col + ")");
                    return;
                }

                if (!trySpendMana()) return;
                performSummon(row, col);
                consume();
            },
            ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1)
        );
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (board == null) return false;
        if (!(box instanceof Plot plot)) return false;
        return board.isValidSummonTarget(plot, alignment);
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
        if (
            alignment == PieceAlignment.P2
            && GameContext.get().getSettingsManager().debug.enableP2Bot
            && GameContext.get().getGameModeManager().getCurrent() != GameMode.LOCAL_MATCH
        ) {
            return null;
        }

        if (alignment != GameContext.get().getTurnManager().getCurrentPlayer()) return null;

        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(alignment);
        int cost = getStatsCost();
        if (playerState == null || playerState.mana < cost) return null;

        return clickableEffectData;
    }
}
