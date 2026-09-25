package io.github.elderpath_crusade.game_objects.cards;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.ConditionEvaluator;
import io.github.elderpath_crusade.abilities.data.EffectExecutor;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.ExpressionContext;
import io.github.elderpath_crusade.abilities.data.TargetSelector;
import io.github.elderpath_crusade.abilities.data.TargetSelectorResolver;
import io.github.elderpath_crusade.data.SpellDefinition;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.multiplayer.net.NetworkCommand;
import io.github.elderpath_crusade.server.ActionDispatcher;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.CardRenderUtils;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven spell card. Handles targeting, mana cost, effect execution, and standard spell rendering.
 * Backed by a SpellDefinition loaded from spells.yaml (SpellRegistry) rather than hardcoded Java.
 */
public class SpellCard extends Card implements TargetFilter {

    protected final Board board;
    protected final PieceAlignment alignment;
    private final String spellName;
    private final String description;
    private final int manaCost;
    private final SpellDefinition definition;

    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    private Text manaText;
    private Text descText;

    public SpellCard(
            Board board, PieceAlignment alignment,
            int x, int y, int width, int height, int z,
            String spellName,
            SpellDefinition definition) {
        super(x, y, width, height, z, null);
        this.board = board;
        this.alignment = alignment;
        this.spellName = spellName;
        this.definition = definition;
        this.description = definition.description();
        this.manaCost = definition.manaCost();

        setTitle(spellName, FontType.SILKSCREEN);
        setTitleColor(Color.WHITE);
        initUi();
        initializeClickableEffect();
    }

    public String getSpellName() { return spellName; }
    public int getManaCost() { return manaCost; }

    private void initUi() {
        manaText = new Text(
                String.valueOf(manaCost),
                FontType.SILKSCREEN,
                0, 0,
                getZLayer(),
                Color.WHITE);

        if (description != null && !description.isEmpty()) {
            descText = new Text(
                    description,
                    FontType.SILKSCREEN,
                    0, 0,
                    getZLayer(),
                    ColorSettings.TEXT_DEFAULT.getColor());
        }
        updateUiSizes();
    }

    private void updateUiSizes() {
        int h = getBounds().getHeight();
        int big = Math.max(8, (int) (h * 0.08f));
        if (manaText != null)
            manaText.withFontSize(big);

        if (descText != null) {
            int w = getBounds().getWidth();
            int marginX = Math.round(w * CardRenderUtils.DESC_MARGIN_X_PCT);
            int wrapW = Math.max(1, w - marginX * 2);
            int wrapH = Math.max(1, Math.round(h * CardRenderUtils.DESC_HEIGHT_PCT));
            descText.withWrapBounds(wrapW, wrapH).withAlignment(Align.center);
        }
    }

    @Override
    public void setBounds(Box bounds) {
        super.setBounds(bounds);
        updateUiSizes();
    }

    private void initializeClickableEffect() {
        if (definition.targeting() == null) {
            setClickableEffect(
                    (HashMap<Integer, CustomBox> entities) -> ActionDispatcher.dispatch(
                            () -> new NetworkCommand.PlaySpellCard(alignment, handIndex(), null, null),
                            () -> executeSpell(null, null)
                    ),
                    ClickableEffectData.getImmediate());
            return;
        }

        setClickableEffect(
                (HashMap<Integer, CustomBox> entities) -> {
                    CustomBox target = entities.get(1);
                    if (target instanceof Plot plot) {
                        int row = plot.getRow();
                        int col = plot.getCol();
                        ActionDispatcher.dispatch(
                                () -> new NetworkCommand.PlaySpellCard(alignment, handIndex(), row, col),
                                () -> executeSpell(row, col)
                        );
                    }
                },
                ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1));
    }

    /** This card's position within its owner's hand, for a networked play request. */
    private int handIndex() {
        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(alignment);
        var hand = playerState == null ? null : playerState.hand;
        return hand == null ? -1 : hand.getCards().indexOf(this);
    }

    /**
     * Authoritative spell execution — validates turn/mana, resolves the target entity (if
     * this spell targets one) from board coordinates, runs effects, spends mana, and consumes
     * the card. targetRow/targetCol are null for an untargeted spell. Called from the local
     * click lambdas above and, identically, from GameServer for a networked play.
     */
    public boolean executeSpell(Integer targetRow, Integer targetCol) {
        if (alignment != GameContext.get().getTurnManager().getCurrentPlayer()) return false;

        Entity target = null;
        if (definition.targeting() != null) {
            if (targetRow == null || targetCol == null) return false;
            target = board.getEntityAtPos(targetRow, targetCol);
            if (target == null) return false;
        }

        if (!trySpendMana()) return false;
        runEffects(target);
        consume();
        return true;
    }

    /** Executes this spell's effects. chosen is the clicked target entity, or null for a no-target spell. */
    private void runEffects(Entity chosen) {
        ExpressionContext ctx = new ExpressionContext();
        ctx.set("$caster.alignment", alignment.name());
        Map<String, Object> state = new HashMap<>();
        for (EffectNode effectNode : definition.effects()) {
            List<Entity> targets = resolveTopLevelTarget(effectNode, chosen, ctx);
            EffectExecutor.execute(effectNode, targets, null, ctx, state);
        }
    }

    /**
     * Resolves a top-level effect's "target" param the same way
     * ActionableAbilityExecutor/AbilityResolverSystem do for abilities. Effects with no
     * "target" param (DrawCard, Branch, ForEach, Recast, ...) resolve their own operands
     * internally and ignore the returned list.
     */
    private List<Entity> resolveTopLevelTarget(EffectNode effect, Entity chosen, ExpressionContext context) {
        Object targetParam = effect.params().get("target");
        if (targetParam instanceof String s) {
            if ("$chosen".equals(s)) return chosen != null ? List.of(chosen) : List.of();
            return TargetSelectorResolver.resolve(new TargetSelector(s), null, context);
        }
        return List.of();
    }

    private boolean trySpendMana() {
        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(alignment);
        if (playerState == null || playerState.getMana() < manaCost) {
            Logger.log(
                    "SpellCard",
                    "Not enough mana. Need=" + manaCost + ", have=" + (playerState == null ? 0 : playerState.getMana()));
            return false;
        }
        playerState.addMana(-manaCost);
        return true;
    }

    @Override
    public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        this.onClick = onClick;
        this.clickableEffectData = effectData;
    }

    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (this.onClick == null)
            return;
        this.onClick.run(interactionEntities);
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        if (GameContext.get().getGameModeManager().getCurrent() == GameMode.ONLINE_MATCH) {
            PieceAlignment local = GameContext.get().getOnlineMatch().getLocalAlignment();
            if (local != null && alignment != local) return null;
        } else if (alignment == PieceAlignment.P2
                && GameContext.get().getSettingsManager().debug.enableP2Bot
                && GameContext.get().getGameModeManager().getCurrent() != GameMode.LOCAL_MATCH) {
            return null;
        }

        if (alignment != GameContext.get().getTurnManager().getCurrentPlayer())
            return null;

        PlayerManager.PlayerState playerState = GameContext.get().getPlayerManager().get(alignment);
        if (playerState == null || playerState.getMana() < manaCost)
            return null;

        return clickableEffectData;
    }

    @Override
    public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
        if (definition.targeting() == null) return false;
        if (!(box instanceof Plot plot)) return false;

        Entity candidate = board.getEntityAtPlot(plot);
        if (candidate == null) return false;

        List<io.github.elderpath_crusade.abilities.data.Condition> conditions = definition.targeting().conditions();
        if (conditions == null || conditions.isEmpty()) return true;

        ExpressionContext ctx = new ExpressionContext();
        ctx.set("$self.alignment", alignment.name());
        ctx.withTarget(Map.of(
                "health", EntityUtils.getCurrentHealth(candidate),
                "maxHealth", EntityUtils.getMaxHealth(candidate),
                "damage", EntityUtils.getDamage(candidate),
                "alignment", EntityUtils.getAlignment(candidate).name()
        ));

        for (var condition : conditions) {
            if (!ConditionEvaluator.evaluate(condition, ctx)) return false;
        }
        return true;
    }

    @Override
    protected void renderExtraOverlays(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        int w = getWidth();
        int h = getHeight();

        if (manaText != null) {
            int tx = x + Math.round(w * CardRenderUtils.MANA_CX) - manaText.getWidth() / 2;
            int ty = y + Math.round(h * CardRenderUtils.MANA_CY) - manaText.getHeight() / 2;
            manaText.render(batch, zLevel, false, tx, ty);
        }

        if (descText != null) {
            int marginX = Math.round(w * CardRenderUtils.DESC_MARGIN_X_PCT);
            int wrapW = Math.max(1, w - marginX * 2);
            int wrapH = Math.max(1, Math.round(h * CardRenderUtils.DESC_HEIGHT_PCT));

            descText.update();

            float textAreaBottomY = y + Math.round(h * CardRenderUtils.DESC_BOTTOM_Y_PCT);
            float textAreaCenterY = textAreaBottomY + wrapH / 2f;

            int tx = x + (w - descText.getWidth()) / 2;
            int ty = Math.round(textAreaCenterY - descText.getHeight() / 2f);

            descText.render(batch, zLevel, isPaused, tx, ty);
        }
    }
}
