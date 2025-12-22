package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.managers.PlayerManager;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.managers.GameModeManager;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import io.github.elderpath_crusade.utils.Logger;
import java.util.Map;

import java.util.HashMap;

/**
 * Base class for summon-type cards. Handles multi-target click flow, mana cost,
 * summoning onto a Board plot, emitting events, and consuming the card.
 * Subclasses provide the concrete piece instantiation and stats/name.
 */
public abstract class SummonCard extends UnitCard implements TargetFilter {
    protected final Board board;
    protected final PieceAlignment alignment;

    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    protected SummonCard(
        Board board, PieceAlignment alignment,
        int x, int y,
        int width, int height,
        int z
    ) {
        super(x, y, width, height, z);
        this.board = board;
        this.alignment = alignment;
        initializeClickableEffect();
    }

    protected abstract GamePiece instantiatePiece(GamePieceStats stats);

    /**
     * Attempts to spend mana for this card based on its unified stats cost.
     * Returns true if the player had enough mana and the cost was deducted.
     */
    protected boolean trySpendMana() {
        PlayerManager.PlayerState playerState = PlayerManager.get(alignment);
        int cost = getStats().getCost();
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
     * Creates the piece instance and places it on the board at the given location.
     * Emits a generic CARD_PLAYED event upon success.
     */
    protected void performSummon(int row, int col) {
        GamePiece piece = instantiatePiece(getStats());
        if (piece == null) {
            Logger.error("SummonCard", "instantiatePiece(stats) returned null for " + getCardName() + "Card");
            return;
        }
        board.addGamePieceToPos(row, col, piece);

        EventBus.emit(
            GameEventType.CARD_PLAYED,
            Map.of(
                "card", getCardName(),
                "owner", alignment.name(),
                "row", row,
                "col", col,
                "pieceId", piece.getId().toString()
            )
        );
    }

    /**
     * Resolves the selected plot from the interaction entities into board coordinates.
     * Returns a two-element array {row, col} or null if the input is invalid.
     */
    private int[] resolveSelectedPlot(HashMap<Integer, CustomBox> entities) {
        if (board == null) return null;
        Object secondClicked = entities == null ? null : entities.get(1);
        if (!(secondClicked instanceof Plot plot)) {
            return null;
        }
        return plot.getIndices(); // may be null if plot isn't on this board
    }

    private void initializeClickableEffect() {
        setClickableEffect(
            (HashMap<Integer, CustomBox> entities) -> {
                int[] pos = resolveSelectedPlot(entities);
                if (pos == null) return;
                int row = pos[0];
                int col = pos[1];

                if (board.getGamePieceAtPos(row, col) != null) {
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
            && SettingsManager.debug.enableP2Bot
            && GameModeManager.getCurrent() != GameMode.LOCAL_MATCH
        ) {
            return null;
        }

        if (alignment != TurnManager.getCurrentPlayer()) return null;

        PlayerManager.PlayerState playerState = PlayerManager.get(alignment);
        int cost = getStats().getCost();
        if (playerState == null || playerState.mana < cost) return null;

        return clickableEffectData;
    }
}
