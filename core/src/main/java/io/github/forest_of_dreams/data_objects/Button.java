package io.github.forest_of_dreams.data_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.interfaces.CustomBox;
import io.github.forest_of_dreams.interfaces.OnClick;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.AbstractTexture;
import io.github.forest_of_dreams.utils.GraphicUtils;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;

/**
 * A simple Button component that renders a rectangular box (image or solid color),
 * centers text inside the box, and triggers an onClick when clicked.
 */
public class Button extends AbstractTexture implements Renderable, Clickable {
    @Getter @Setter private String text;
    @Getter @Setter private FontType fontType;
    @Getter @Setter private Color textColor = Color.WHITE;
    @Getter @Setter private int z;

    // Background options (only one should be used)
    private Texture backgroundTexture; // full texture scaled to bounds
    private Color backgroundColor;     // solid color fill

    // Optional hover / click visual tweaks (background)
    @Getter @Setter private Color hoverColor = null;      // background hover color
    @Getter @Setter private Color clickColor = null;      // background click color

    // Optional hover / click visual tweaks (text)
    @Getter @Setter private Color hoverTextColor = null;
    @Getter @Setter private Color clickTextColor = null;

    // Optional border colors
    @Getter @Setter private Color borderColor = null;
    @Getter @Setter private Color hoverBorderColor = null;
    @Getter @Setter private Color clickBorderColor = null;

    private Text textObj;

    // Clickable integration
    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    private Button(String text, FontType fontType, int fontSize, int x, int y, int width, int height, int z) {
        this.text = text;
        this.fontType = fontType;
        this.z = z;
        setBounds(new Box(x, y, width, height));
        updateText();
        textObj.withFontSize(fontSize);
    }

    // Factory: Color background
    public static Button fromColor(
        Color backgroundColor,
        String text,
        FontType fontType,
        int fontSize,
        int x, int y,
        int width, int height,
        int z
    ) {
        Button b = new Button(text, fontType, fontSize, x, y, width, height, z);
        b.backgroundColor = backgroundColor;
        return b;
    }

    // Factory: Image background (path to image)
    public static Button fromImage(
        String imagePath,
        String text,
        FontType fontType,
        int fontSize,
        int x, int y,
        int width, int height,
        int z
    ) {
        Button b = new Button(text, fontType, fontSize, x, y, width, height, z);
        b.backgroundTexture = new Texture(Gdx.files.internal(imagePath));
        return b;
    }

    public Button withOnClick(OnClick onClick, ClickableEffectData effectData) {
        setClickableEffect(onClick, effectData);
        return this;
    }

    public Button withTextColors(Color normal, Color hover, Color click) {
        this.textColor = normal;
        this.hoverTextColor = hover;
        this.clickTextColor = click;
        if (textObj != null && textObj.getLabel() != null) textObj.getLabel().setColor(textColor);
        return this;
    }

    private void updateText() {
        // Ensure Text object exists and is synced with current text/font
        if (textObj == null) {
            textObj = new Text(text, fontType, getBounds().getX(), getBounds().getY(), z, textColor);
        } else {
            textObj.setText(text);
            textObj.setFontType(fontType);
        }
        // Update to ensure label, size and style are current
        textObj.update();
        centerLabelInBounds();
    }

    private void centerLabelInBounds() {
        Box b = getBounds();
        if (b == null || textObj == null || textObj.getLabel() == null) return;
        // Ensure label has latest pref size
        Label lbl = textObj.getLabel();
        float labelWidth = lbl.getPrefWidth();
        float labelHeight = lbl.getPrefHeight();
        Box newBounds = new Box(
            (int) (b.getX() + (b.getWidth() - labelWidth) / 2f),
            (int) (b.getY() + (b.getHeight() - labelHeight) / 2f),
            (int) labelWidth,
            (int) labelHeight
        );
        textObj.setBounds(newBounds);
    }

    private boolean isHovered(int relX, int relY) {
        int x = getX() + relX;
        int y = getY() + relY;
        int width = getWidth();
        int height = getHeight();

        // Get the actual screen dimensions
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        // Get the configured dimensions
        int configuredWidth = SettingsManager.screenSize.getScreenConfiguredWidth();
        int configuredHeight = SettingsManager.screenSize.getScreenConfiguredHeight();

        // Calculate scale factors
        float scaleX = configuredWidth / screenWidth;
        float scaleY = configuredHeight / screenHeight;

        // Scale the mouse coordinates
        float mouseX = Gdx.input.getX() * scaleX;
        float mouseY = (Gdx.graphics.getHeight() - Gdx.input.getY()) * scaleY;

        boolean isInXRange = x <= mouseX && mouseX <= (x + width - 1);
        boolean isInYRange = y <= mouseY && mouseY <= (y + height - 1);

        return isInXRange && isInYRange;
    }

    @Override
    public List<Integer> getZs() {
        return List.of(z);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        if (isPaused) return;
        if (zLevel != z) return;
        drawBackground(batch);
        updateText();
        applyHoverClickTint(0, 0);
        if (textObj != null && textObj.getLabel() != null) {
            textObj.getLabel().draw(batch, 1);
            textObj.getLabel().setColor(textColor);
        }
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        if (isPaused) return;
        if (zLevel != z) return;
        // Temporarily offset bounds for drawing in higher-order containers
        Box original = getBounds();
        Box temp = new Box(x, y, original.getWidth(), original.getHeight());
        setBounds(temp);

        drawBackground(batch);
        updateText();
        applyHoverClickTint(x, y);
        if (textObj != null && textObj.getLabel() != null) {
            textObj.getLabel().draw(batch, 1);
            textObj.getLabel().setColor(textColor);
        }

        setBounds(original);
    }

    private void drawBackground(SpriteBatch batch) {
        Box b = getBounds();
        if (b == null) return;
        if (backgroundTexture != null) {
            batch.draw(backgroundTexture, b.getX(), b.getY(), b.getWidth(), b.getHeight());
        } else if (backgroundColor != null) {
            // Determine background color based on hover/click state (click overrides hover)
            Color bg = backgroundColor;
            boolean hovered = isHovered(0, 0);
            if (hovered && hoverColor != null) bg = hoverColor;
            if (hovered && Gdx.input.isTouched() && clickColor != null) bg = clickColor;
            Texture pixel = GraphicUtils.getPixelTexture(bg);
            batch.draw(pixel, b.getX(), b.getY(), b.getWidth(), b.getHeight());
        }

        // Optional border (drawn regardless of background type)
        Color activeBorder = null;
        if (borderColor != null) {
            activeBorder = borderColor;
            boolean hovered = isHovered(0, 0);
            if (hovered && hoverBorderColor != null) activeBorder = hoverBorderColor;
            if (hovered && Gdx.input.isTouched() && clickBorderColor != null) activeBorder = clickBorderColor;
        }
        if (activeBorder != null) {
            Texture px = GraphicUtils.getPixelTexture(activeBorder);
            int x = b.getX();
            int y = b.getY();
            int w = b.getWidth();
            int h = b.getHeight();
            int t = 1; // border thickness in pixels
            if (w > 0 && h > 0) {
                // Top
                batch.draw(px, x, y + h - t, w, t);
                // Bottom
                batch.draw(px, x, y, w, t);
                // Left
                batch.draw(px, x, y, t, h);
                // Right
                batch.draw(px, x + w - t, y, t, h);
            }
        }

        // else: transparent background (only text)
        centerLabelInBounds();
    }

    private void applyHoverClickTint(int relX, int relY) {
        if (!isHovered(relX, relY)) return;
        if (textObj == null || textObj.getLabel() == null) return;
        if (hoverTextColor != null) textObj.getLabel().setColor(hoverTextColor);
        // Visual click color change if provided
        if (clickTextColor != null && Gdx.input.isTouched()) textObj.getLabel().setColor(clickTextColor);
    }

    // Clickable implementation hooks
    @Override
    public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        this.onClick = onClick;
        this.clickableEffectData = effectData;
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        return clickableEffectData;
    }

    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (this.onClick == null) return;
        this.onClick.run(interactionEntities);
    }

    // Background hover color setter (hoverColor)
    public Button withHoverColor(Color hoverColor) {
        this.hoverColor = hoverColor;
        return this;
    }

    // Text hover color setter (hoverTextColor)
    public Button withHoverTextColor(Color hoverTextColor) {
        this.hoverTextColor = hoverTextColor;
        return this;
    }

    // Background click color setter (clickColor)
    public Button withClickColor(Color clickColor) {
        this.clickColor = clickColor;
        return this;
    }

    // Text click color setter (clickTextColor)
    public Button withClickTextColor(Color clickTextColor) {
        this.clickTextColor = clickTextColor;
        return this;
    }

    // Border base color setter
    public Button withBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        return this;
    }

    // Border hover color setter
    public Button withHoverBorderColor(Color hoverBorderColor) {
        this.hoverBorderColor = hoverBorderColor;
        return this;
    }

    // Border click color setter
    public Button withClickBorderColor(Color clickBorderColor) {
        this.clickBorderColor = clickBorderColor;
        return this;
    }
}
