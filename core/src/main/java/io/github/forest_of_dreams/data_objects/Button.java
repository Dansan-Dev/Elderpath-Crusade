package io.github.forest_of_dreams.data_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
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

    // Optional hover / click visual tweaks
    @Getter @Setter private Color hoverTextColor = null;
    @Getter @Setter private Color clickTextColor = null;

    private static final Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
    private LabelStyle style;
    @Getter private Label label;

    // Clickable integration
    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    private Button(String text, FontType fontType, int x, int y, int width, int height, int z) {
        this.text = text;
        this.fontType = fontType;
        this.z = z;
        setBounds(new Box(x, y, width, height));
        updateLabel();
    }

    // Factory: Color background
    public static Button fromColor(Color backgroundColor, String text, FontType fontType,
                                   int x, int y, int width, int height, int z) {
        Button b = new Button(text, fontType, x, y, width, height, z);
        b.backgroundColor = backgroundColor;
        return b;
    }

    // Factory: Image background (path to image)
    public static Button fromImage(String imagePath, String text, FontType fontType,
                                   int x, int y, int width, int height, int z) {
        Button b = new Button(text, fontType, x, y, width, height, z);
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
        if (label != null) label.setColor(textColor);
        return this;
    }

    private void updateLabel() {
        style = skin.get(fontType.getFontName(), Label.LabelStyle.class);
        if (label == null) {
            label = new Label(text, style);
        } else {
            label.setStyle(style);
            label.setText(text);
        }
        label.setColor(textColor);
        centerLabelInBounds();
    }

    private void centerLabelInBounds() {
        Box b = getBounds();
        if (b == null || label == null) return;
        // Ensure label has latest pref size
        float labelWidth = label.getPrefWidth();
        float labelHeight = label.getPrefHeight();
        // Center inside box
        float lx = b.getX() + (b.getWidth() - labelWidth) / 2f;
        float ly = b.getY() + (b.getHeight() - labelHeight) / 2f;
        label.setPosition(lx, ly);
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
        updateLabel();
        applyHoverClickTint(0, 0);
        label.draw(batch, 1);
        label.setColor(textColor);
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
        updateLabel();
        applyHoverClickTint(x, y);
        label.draw(batch, 1);
        label.setColor(textColor);

        setBounds(original);
    }

    private void drawBackground(SpriteBatch batch) {
        Box b = getBounds();
        if (b == null) return;
        if (backgroundTexture != null) {
            batch.draw(backgroundTexture, b.getX(), b.getY(), b.getWidth(), b.getHeight());
        } else if (backgroundColor != null) {
            Texture pixel = GraphicUtils.getPixelTexture(backgroundColor);
            batch.draw(pixel, b.getX(), b.getY(), b.getWidth(), b.getHeight());
        }
        // else: transparent background (only text)
        centerLabelInBounds();
    }

    private void applyHoverClickTint(int relX, int relY) {
        if (!isHovered(relX, relY)) return;
        if (hoverTextColor != null) label.setColor(hoverTextColor);
        // Visual click color change if provided
        if (clickTextColor != null && Gdx.input.isTouched()) label.setColor(clickTextColor);
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
}
