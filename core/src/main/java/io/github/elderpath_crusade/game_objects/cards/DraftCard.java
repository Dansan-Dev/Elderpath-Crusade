package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;

import java.util.HashMap;

/**
 * Wrapper around SummonCard for use in drafting phase.
 * Bypasses mana checks and turn restrictions since drafting happens before the game starts.
 * Delegates all rendering to the wrapped card but overrides click behavior.
 */
public class DraftCard extends Card {
    private final SummonCard wrappedCard;
    private final OnClick draftOnClick;
    
    public DraftCard(SummonCard wrappedCard, OnClick draftOnClick) {
        // Create a Card with no effect (we'll override click behavior)
        super(wrappedCard.getBounds().getX(), wrappedCard.getBounds().getY(),
              wrappedCard.getWidth(), wrappedCard.getHeight(), 
              wrappedCard.getZLayer(), null);
        
        this.wrappedCard = wrappedCard;
        this.draftOnClick = draftOnClick;
        
        // Copy visual state from wrapped card
        if (wrappedCard.isFaceUp()) {
            showFront();
        } else {
            showBack();
        }
        
        // Copy title and styling
        setTitle(wrappedCard.getCardName(), io.github.elderpath_crusade.enums.FontType.SILKSCREEN);
        setTitleColor(com.badlogic.gdx.graphics.Color.WHITE);
        
        // Set bounds to match wrapped card
        setBounds(wrappedCard.getBounds());
    }
    
    @Override
    public ClickableEffectData getClickableEffectData() {
        // Always return clickable during drafting (no mana/turn checks)
        if (draftOnClick == null) {
            return null;
        }
        return ClickableEffectData.getImmediate();
    }
    
    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        // Use the draft-specific click handler
        if (draftOnClick != null) {
            draftOnClick.run(interactionEntities);
        }
    }
    
    @Override
    protected void renderExtraOverlays(com.badlogic.gdx.graphics.g2d.SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        // Delegate rendering of stat overlays to wrapped card
        wrappedCard.renderExtraOverlays(batch, zLevel, isPaused, x, y);
    }
    
    // Get the wrapped card for reference
    public SummonCard getWrappedCard() {
        return wrappedCard;
    }
}

