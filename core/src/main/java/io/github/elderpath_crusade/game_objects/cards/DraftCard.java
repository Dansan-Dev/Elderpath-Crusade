package io.github.elderpath_crusade.game_objects.cards;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.utils.GraphicUtils;
import io.github.elderpath_crusade.utils.HoverUtils;

import java.util.HashMap;

/**
 * Wrapper around SummonCard for use in drafting phase.
 * Bypasses mana checks and turn restrictions since drafting happens before the game starts.
 * Delegates all rendering to the wrapped card but overrides click behavior.
 * Overrides hover behavior to show border highlight instead of elevation.
 */
public class DraftCard extends Card {
    private final SummonCard wrappedCard;
    private final OnClick draftOnClick;
    
    // Hover border animation state
    private float hoverBorderProgress = 0f; // 0..1
    private static final float HOVER_BORDER_SPEED = 8f; // Animation speed
    private static final int HOVER_BORDER_THICKNESS = 3; // Border thickness when fully hovered
    
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
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        // Override to disable hover lift (always render at base position)
        int[] abs = calculatePos();
        render(batch, zLevel, isPaused, abs[0], abs[1]);
    }
    
    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        // Override to disable hover lift - render card elements directly without offset
        // Render card front/back at base position (no hover offset)
        Renderable side = isFaceUp() ? getFront() : getBack();
        side.render(batch, zLevel, isPaused, x, y);
        
        // Render overlays when face-up and at card z layer
        if (!isPaused && isFaceUp() && zLevel == getZLayer()) {
            // Render title (now that renderTitle is protected, we can call it)
            renderTitle(batch, zLevel, x, y);
            // Use wrapped card to render stat overlays (it has all the stat rendering logic)
            renderExtraOverlays(batch, zLevel, isPaused, x, y);
            // Render hover border on top (our custom hover effect)
            renderHoverBorder(batch, zLevel, x, y, isPaused);
        }
    }
    
    /**
     * Render hover border highlight instead of elevation.
     * Shows an animated border when the card is hovered.
     */
    private void renderHoverBorder(SpriteBatch batch, int zLevel, int x, int y, boolean isPaused) {
        // Check if card is hovered
        boolean hovered = HoverUtils.isHovered(x, y, getWidth(), getHeight());
        
        // Animate border progress
        float dt = Gdx.graphics.getDeltaTime();
        if (!isPaused && hovered && isFaceUp()) {
            hoverBorderProgress = Math.min(1f, hoverBorderProgress + HOVER_BORDER_SPEED * dt);
        } else {
            hoverBorderProgress = Math.max(0f, hoverBorderProgress - HOVER_BORDER_SPEED * dt);
        }
        
        // Draw border if hovered
        if (hoverBorderProgress > 0f) {
            int width = getWidth();
            int height = getHeight();
            int thickness = Math.max(1, Math.round(HOVER_BORDER_THICKNESS * hoverBorderProgress));
            Color borderColor = new Color(1f, 1f, 1f, 0.8f * hoverBorderProgress); // White with alpha
            
            // Draw border on all four sides
            // Top
            batch.draw(GraphicUtils.getPixelTexture(borderColor), x, y + height - thickness, width, thickness);
            // Bottom
            batch.draw(GraphicUtils.getPixelTexture(borderColor), x, y, width, thickness);
            // Left
            batch.draw(GraphicUtils.getPixelTexture(borderColor), x, y, thickness, height);
            // Right
            batch.draw(GraphicUtils.getPixelTexture(borderColor), x + width - thickness, y, thickness, height);
        }
    }
    
    @Override
    protected void renderExtraOverlays(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        // Delegate rendering of stat overlays to wrapped card
        wrappedCard.renderExtraOverlays(batch, zLevel, isPaused, x, y);
    }
    
    // Get the wrapped card for reference
    public SummonCard getWrappedCard() {
        return wrappedCard;
    }
}

