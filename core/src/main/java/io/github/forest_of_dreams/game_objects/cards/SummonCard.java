package io.github.forest_of_dreams.game_objects.cards;

import com.badlogic.gdx.graphics.Color;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.enums.ClickableTargetType;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.board.GamePieceStats;
import io.github.forest_of_dreams.game_objects.board.Plot;
import io.github.forest_of_dreams.interfaces.CustomBox;
import io.github.forest_of_dreams.interfaces.OnClick;
import io.github.forest_of_dreams.interfaces.TargetFilter;
import io.github.forest_of_dreams.managers.PlayerManager;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.managers.TurnManager;
import io.github.forest_of_dreams.multiplayer.EventBus;
import io.github.forest_of_dreams.multiplayer.GameEventType;
import io.github.forest_of_dreams.utils.Logger;

import java.util.HashMap;

/**
 * Base class for summon-type cards. Handles multi-target click flow, mana cost,
 * summoning onto a Board plot, emitting events, and consuming the card.
 * Subclasses provide the concrete piece instantiation and stats/name.
 */
public abstract class SummonCard extends Card implements TargetFilter {
    protected final Board board;
    protected final PieceAlignment alignment;
    protected final GamePieceStats stats; // unified stats used by both card and resulting piece

    // Clickable plumbing for InteractionManager
    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    protected SummonCard(
        Board board, PieceAlignment alignment,
        int x, int y,
        int width, int height,
        int z
    ) {
        super(x, y, width, height, z, null);
        this.board = board;
        this.alignment = alignment;
        this.stats = buildStats();
        setTitle(getCardName(), FontType.SILKSCREEN);
        setTitleColor(Color.WHITE);
        initializeClickableEffect();
    }

    // Subclass hooks
    protected abstract GamePieceStats buildStats();
    protected abstract String getCardName();
    protected abstract GamePiece instantiatePiece(GamePieceStats stats);

    private void initializeClickableEffect() {
        setClickableEffect(
            (HashMap<Integer, CustomBox> entities) -> {
                if (board == null) return;
                Object secondClicked = entities.get(1);
                if (!(secondClicked instanceof Plot plot)) return;
                int[] plotPos = board.getIndicesOfPlot(plot);
                if (plotPos == null) return;

                // Reject if occupied
                if (board.getGamePieceAtPos(plotPos[0], plotPos[1]) != null) {
                    Logger.log("SummonCard", "Summon aborted: occupied (" + plotPos[0] + "," + plotPos[1] + ")");
                    return;
                }

                // Mana check and spend using unified stats cost
                PlayerManager.PlayerState playerState = PlayerManager.get(alignment);
                int cost = stats.getCost();
                if (playerState == null || playerState.mana < cost) {
                    Logger.log("SummonCard", "Not enough mana. Need=" + cost + ", have=" + (playerState == null ? 0 : playerState.mana));
                    return;
                }
                playerState.mana -= cost;

                GamePiece piece = instantiatePiece(stats);
                if (piece == null) {
                    Logger.error("SummonCard", "instantiatePiece(stats) returned null for " + getCardName() + "Card");
                    return;
                }
                board.addGamePieceToPos(plotPos[0], plotPos[1], piece);

                // Emit generic CARD_PLAYED
                EventBus.emit(
                    GameEventType.CARD_PLAYED,
                    java.util.Map.of(
                        "card", getCardName(),
                        "owner", alignment.name(),
                        "row", plotPos[0],
                        "col", plotPos[1],
                        "pieceId", piece.getId().toString()
                    )
                );

                // Move the card from hand to discard
                consume();
            },
            ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1)
        );
    }

    // TargetFilter for InteractionManager validation
    @Override
    public boolean isValidTargetForEffect(CustomBox box) {
        if (board == null) return false;
        if (!(box instanceof Plot plot)) return false;
        return board.isValidSummonTarget(plot, alignment);
    }

    // Clickable integration — stored here because Card is generic
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
        // Only allow playing on owner turn. If P2 bot is enabled, block human clicks on P2 cards.
        if (alignment == PieceAlignment.P2 && SettingsManager.debug.enableP2Bot) return null;
        return (alignment == TurnManager.getCurrentPlayer()) ? clickableEffectData : null;
    }
}
