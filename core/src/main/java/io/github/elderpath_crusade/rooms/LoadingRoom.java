package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.interfaces.Updatable;
import io.github.elderpath_crusade.path_loaders.ImagePathBackgroundAndUI;
import io.github.elderpath_crusade.path_loaders.ImagePathSpritesAndAnimations;
import io.github.elderpath_crusade.rendering.TextureManager;
import io.github.elderpath_crusade.supers.LowestOrderTexture;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.List;
import java.util.function.Supplier;

/**
 * Queues the game atlas and standalone textures via AssetService (async AssetManager)
 * instead of the synchronous load TextureManager otherwise does on first use, shows
 * progress, and transitions to the target room once loading finishes.
 */
public class LoadingRoom extends Room {
    private static final int HEADER_TOP_OFFSET = 120;

    private final Text label;

    private LoadingRoom(Supplier<Room> next) {
        super();

        queueAssets();

        label = new Text("Loading... 0%", FontType.SILKSCREEN, 0, 0, 0, Color.WHITE)
                .withFontSize(FontSize.BODY_LARGE);
        addContent(label);
        MenuLayout.centerHeader(label, HEADER_TOP_OFFSET);

        addContent(new LoadingPoller(next, label));
    }

    private void queueAssets() {
        GameContext.get().getAssets().loadAtlas(TextureManager.GAME_ATLAS_PATH);
        for (ImagePathSpritesAndAnimations image : ImagePathSpritesAndAnimations.values()) {
            GameContext.get().getAssets().loadTexture(image.getPath());
        }
        for (ImagePathBackgroundAndUI image : ImagePathBackgroundAndUI.values()) {
            GameContext.get().getAssets().loadTexture(image.getPath());
        }
    }

    @Override
    public void onScreenResize() {
        MenuLayout.centerHeader(label, HEADER_TOP_OFFSET);
    }

    public static Room get(Supplier<Room> next) {
        return new LoadingRoom(next);
    }

    /** Wrap a target room supplier so gotoRoom(...) shows this loading screen first. */
    public static Supplier<Room> before(Supplier<Room> next) {
        return () -> LoadingRoom.get(next);
    }

    /** Renders nothing — exists only to receive per-frame update() calls from GraphicsManager. */
    private static class LoadingPoller extends LowestOrderTexture implements Renderable, Updatable {
        private final Supplier<Room> next;
        private final Text label;
        private boolean transitioning = false;

        LoadingPoller(Supplier<Room> next, Text label) {
            this.next = next;
            this.label = label;
            setBounds(new Box(0, 0, 0, 0));
        }

        @Override
        public List<Integer> getZs() {
            return List.of();
        }

        @Override
        public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        }

        @Override
        public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        }

        @Override
        public void update(float delta) {
            if (transitioning) return;
            boolean finished = GameContext.get().getAssets().update();
            int percent = (int) (GameContext.get().getAssets().getProgress() * 100);
            label.setText("Loading... " + percent + "%");
            MenuLayout.centerHeader(label, HEADER_TOP_OFFSET);
            if (finished) {
                transitioning = true;
                GameContext.get().getRoomManager().gotoRoom(next);
            }
        }
    }
}
