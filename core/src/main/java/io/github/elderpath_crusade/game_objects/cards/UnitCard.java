package io.github.elderpath_crusade.game_objects.cards;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.CardRenderUtils;
import io.github.elderpath_crusade.utils.ColorSettings;

import java.util.List;

/**
 * Shared base for unit-like cards that display five core stats and optional rules text.
 * Extracted from SummonCard so both SummonCard and other card types can reuse the same
 * title/stats/description rendering without duplicating logic.
 */
public abstract class UnitCard extends Card {
    private final GamePieceStats stats;

    private Text manaText;
    private Text hpText;
    private Text spdText;
    private Text actText;
    private Text atkText;

    private Text descText;

    protected UnitCard(int x, int y, int width, int height, int z) {
        super(x, y, width, height, z, null);
        this.stats = buildStats();
        setTitle(getCardName(), FontType.SILKSCREEN);
        setTitleColor(Color.WHITE);

        initStatTexts();
        List<String> descs = getAbilityDescriptionsForCard();
        if (descs != null && !descs.isEmpty()) {
            String desc = String.join("\n\n", descs);
            descText = new Text(desc, FontType.SILKSCREEN, 0, 0, getZLayer(), ColorSettings.TEXT_DEFAULT.getColor());
        }
    }

    /**
     * Alternate constructor for cases where the subclass already has concrete stats/name/descriptions
     * available at construction time (e.g., non-interactive PreviewCard). This avoids calling the
     * abstract hooks in the default constructor.
     */
    protected UnitCard(int x, int y, int width, int height, int z,
                       GamePieceStats readyStats, String name, List<String> descs) {
        super(x, y, width, height, z, null);
        if (readyStats == null) {
            throw new IllegalArgumentException("readyStats must not be null for this UnitCard constructor");
        }
        this.stats = readyStats.copy();
        setTitle(name == null ? "" : name, FontType.SILKSCREEN);
        setTitleColor(Color.WHITE);

        initStatTexts();
        if (descs != null && !descs.isEmpty()) {
            String desc = String.join("\n\n", descs);
            descText = new Text(desc, FontType.SILKSCREEN, 0, 0, getZLayer(), ColorSettings.TEXT_DEFAULT.getColor());
        }
    }

    // Subclass hooks
    protected String getRegistryKey() { return PieceRegistry.toRegistryKey(getCardName()); }
    protected GamePieceStats buildStats() {
        String key = getRegistryKey();
        PieceDefinition def = PieceRegistry.get(key);
        if (def == null) throw new IllegalArgumentException("No piece definition for: " + key);
        return GamePieceStats.getMonsterStats(def.cost(), def.health(), def.damage(), def.speed(), def.actions());
    }
    protected abstract String getCardName();
    /**
     * Returns ability descriptions for card display.
     * Default implementation looks up descriptions from AbilityRegistry via PieceRegistry.
     * Subclasses may override for custom text.
     */
    protected List<String> getAbilityDescriptionsForCard() {
        String key = getRegistryKey();
        io.github.elderpath_crusade.data.PieceDefinition def = PieceRegistry.get(key);
        if (def == null || def.abilities().isEmpty()) return List.of();
        List<String> descs = new java.util.ArrayList<>();
        for (String abilityName : def.abilities()) {
            io.github.elderpath_crusade.abilities.data.AbilityDefinition abDef =
                io.github.elderpath_crusade.data.AbilityRegistry.get(abilityName);
            if (abDef != null && abDef.description() != null && !abDef.description().isEmpty()) {
                descs.add(abDef.description());
            }
        }
        return descs;
    }

    /**
     * Read-only access to the base stats represented by this card.
     * Returns a copy to prevent external mutation of the card's internal state.
     */
    public GamePieceStats getStats() { return stats.copy(); }

    /**
     * Allow subclasses to update the description text dynamically (e.g., PreviewCard hover panel).
     */
    protected void setDescriptionText(String desc) {
        if (desc == null || desc.isEmpty()) {
            this.descText = null;
            return;
        }
        if (this.descText == null) {
            this.descText = new Text(
                desc,
                FontType.SILKSCREEN,
                0, 0,
                getZLayer(),
                ColorSettings.TEXT_DEFAULT.getColor()
            );
        } else {
            this.descText.setText(desc);
            this.descText.update();
        }
    }

    // --- Stat overlay initialization and sizing ---
    private void initStatTexts() {
        manaText = makeStatText(stats.getCost());
        hpText = makeStatText(stats.getMaxHealth());
        spdText = makeStatText(stats.getSpeed());
        actText = makeStatText(stats.getActions());
        atkText = makeStatText(stats.getDamage());
        updateStatTextSizes();
    }

    // Small helper to avoid repeated constructor boilerplate for stat texts
    private Text makeStatText(int value) { return makeStatText(String.valueOf(value)); }
    private Text makeStatText(String text) {
        return new Text(
            text,
            FontType.SILKSCREEN,
            0,
            0,
            getZLayer(),
            Color.WHITE
        );
    }

    private void updateStatTextSizes() {
        int h = getBounds().getHeight();
        int big = Math.max(8, (int) (h * 0.08f));
        int small = Math.max(8, (int) (h * 0.06f));
        if (manaText != null) manaText.withFontSize(big);
        if (hpText != null) hpText.withFontSize(big);
        if (atkText != null) atkText.withFontSize(big);
        if (spdText != null) spdText.withFontSize(small);
        if (actText != null) actText.withFontSize(small);
    }

    @Override
    public void setBounds(Box bounds) {
        super.setBounds(bounds);
        updateStatTextSizes();
        if (descText != null) {
            int w = getBounds().getWidth();
            int h = getBounds().getHeight();
            int marginX = Math.round(w * CardRenderUtils.DESC_MARGIN_X_PCT);
            int wrapW = Math.max(1, w - marginX * 2);
            int wrapH = Math.max(1, Math.round(h * CardRenderUtils.DESC_HEIGHT_PCT));
            descText.withWrapBounds(wrapW, wrapH).withAlignment(Align.center);
        }
    }

    @Override
    protected void renderExtraOverlays(SpriteBatch batch, int zLevel, boolean isPaused, int baseX, int baseY) {
        CardRenderUtils.renderUnitCardOverlays(
                batch, zLevel, isPaused,
                baseX, baseY, getWidth(), getHeight(),
                manaText, hpText, spdText, actText, atkText,
                descText);
    }
}
