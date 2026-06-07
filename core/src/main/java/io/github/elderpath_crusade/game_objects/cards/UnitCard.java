package io.github.elderpath_crusade.game_objects.cards;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.CardRenderUtils;
import io.github.elderpath_crusade.utils.ColorSettings;

import java.util.List;

/**
 * Shared base for unit-like cards that display five core stats and optional rules text.
 */
public abstract class UnitCard extends Card {
    private int cost;
    private int maxHealth;
    private int damage;
    private int speed;
    private int actions;

    private Text manaText;
    private Text hpText;
    private Text spdText;
    private Text actText;
    private Text atkText;

    private Text descText;

    protected UnitCard(int x, int y, int width, int height, int z) {
        this(x, y, width, height, z, null);
    }

    protected UnitCard(int x, int y, int width, int height, int z, String registryKeyOverride) {
        super(x, y, width, height, z, null);
        String key = registryKeyOverride != null ? registryKeyOverride : getRegistryKey();
        loadStats(key);
        setTitle(getCardName(), FontType.SILKSCREEN);
        setTitleColor(Color.WHITE);

        initStatTexts();
        List<String> descs = getAbilityDescriptionsForCard(key);
        if (descs != null && !descs.isEmpty()) {
            String desc = String.join("\n\n", descs);
            descText = new Text(desc, FontType.SILKSCREEN, 0, 0, getZLayer(), ColorSettings.TEXT_DEFAULT.getColor());
        }
    }

    /**
     * Alternate constructor for cases where the subclass already has concrete stats/name/descriptions
     * available at construction time (e.g., non-interactive PreviewCard).
     */
    protected UnitCard(int x, int y, int width, int height, int z,
                       int cost, int maxHealth, int damage, int speed, int actions,
                       String name, List<String> descs) {
        super(x, y, width, height, z, null);
        this.cost = cost;
        this.maxHealth = maxHealth;
        this.damage = damage;
        this.speed = speed;
        this.actions = actions;
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
    private void loadStats(String key) {
        PieceDefinition def = PieceRegistry.get(key);
        if (def == null) throw new IllegalArgumentException("No piece definition for: " + key);
        this.cost = def.cost();
        this.maxHealth = def.health();
        this.damage = def.damage();
        this.speed = def.speed();
        this.actions = def.actions();
    }
    protected abstract String getCardName();

    protected List<String> getAbilityDescriptionsForCard() {
        return getAbilityDescriptionsForCard(getRegistryKey());
    }

    private List<String> getAbilityDescriptionsForCard(String key) {
        PieceDefinition def = PieceRegistry.get(key);
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

    /** Read-only access to cost. */
    public int getStatsCost() { return cost; }
    public int getStatsMaxHealth() { return maxHealth; }
    public int getStatsDamage() { return damage; }
    public int getStatsSpeed() { return speed; }
    public int getStatsActions() { return actions; }

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

    private void initStatTexts() {
        manaText = makeStatText(cost);
        hpText = makeStatText(maxHealth);
        spdText = makeStatText(speed);
        actText = makeStatText(actions);
        atkText = makeStatText(damage);
        updateStatTextSizes();
    }

    private Text makeStatText(int value) { return makeStatText(String.valueOf(value)); }
    private Text makeStatText(String text) {
        return new Text(text, FontType.SILKSCREEN, 0, 0, getZLayer(), Color.WHITE);
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
