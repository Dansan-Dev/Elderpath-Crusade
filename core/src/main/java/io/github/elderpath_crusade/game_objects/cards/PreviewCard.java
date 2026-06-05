package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.data_objects.ClickableEffectData;

import java.util.List;

/**
 * Non-interactive large card preview used by the hover panel.
 * Shows title and five stats inside the standard orbs.
 */
public class PreviewCard extends UnitCard {

    public PreviewCard(int x, int y, int width, int height, int z, String title,
                       int cost, int maxHealth, int damage, int speed, int actions) {
        super(x, y, width, height, z, cost, maxHealth, damage, speed, actions, title, null);
    }

    public void setDescription(String desc) {
        setDescriptionText(desc);
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        return null;
    }

    @Override
    protected String getCardName() {
        throw new UnsupportedOperationException("PreviewCard should not be asked for card name");
    }

    @Override
    protected List<String> getAbilityDescriptionsForCard() {
        throw new UnsupportedOperationException("PreviewCard should not be asked for card abilities");
    }
}
