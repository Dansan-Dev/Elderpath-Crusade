package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePieceStats;

import java.util.List;

/**
 * Non-interactive large card preview used by the hover panel.
 * Shows title and five stats inside the standard orbs.
 */
public class PreviewCard extends UnitCard {

    public PreviewCard(int x, int y, int width, int height, int z, String title, GamePieceStats stats) {
        super(x, y, width, height, z, stats, title, null);
    }

    public void setDescription(String desc) {
        setDescriptionText(desc);
    }

    // Non-interactive: never returns a click effect
    @Override
    public ClickableEffectData getClickableEffectData() {
        return null;
    }

    @Override
    protected GamePieceStats buildStats() {
        throw new UnsupportedOperationException("PreviewCard should be constructed with ready stats");
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
