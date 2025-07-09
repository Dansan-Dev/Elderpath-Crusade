package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.game_objects.pause.PauseScreen;
import io.github.forest_of_dreams.game_objects.SpriteObject;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class GraphicsManager {
    @Getter private static final List<Renderable> renderables = new ArrayList<>();;
    @Getter private static final List<UIRenderable> uiRenderables = new ArrayList<>();;
    private static int maxZ = 0;
    private static int minZ = 0;
    @Getter private static boolean isPaused = false;

    public static void pause() {
        isPaused = true;
        renderables.stream()
            .filter(r -> r instanceof SpriteObject)
            .forEach(
                r -> ((SpriteObject) r).pauseAnimation()
            );
    }

    public static void unpause() {
        isPaused = false;
        renderables.stream()
            .filter(r -> r instanceof SpriteObject)
            .forEach(
                r -> ((SpriteObject) r).unpauseAnimation()
            );
    }

    public static void render(SpriteBatch batch) {
        renderGameGraphics(batch);
        renderUI(batch);
        if (isPaused) PauseScreen.render(batch, 10, isPaused);
    }

    public static void renderUI(SpriteBatch batch) {
        uiRenderables.forEach(r -> r.renderUI(batch, isPaused));
    }

    public static void renderPauseUI(SpriteBatch batch) {
        if (!isPaused) return;
        PauseScreen.renderPauseUI(batch);
    }

    private static void renderGameGraphics(SpriteBatch batch) {
        for(int i = minZ; i <= maxZ; i++) {
            for(Renderable r : renderables) {
                if (r instanceof HigherOrderTexture) r.render(batch, i, isPaused, ((HigherOrderTexture) r).getX(), ((HigherOrderTexture) r).getY());
                else r.render(batch, i, isPaused);
            }
        }
    }

    public static void updateMinMaxZ() {
        minZ = 0;
        maxZ = 0;
        for (Renderable r : renderables) {
            tryMinMaxZBoundary(r);
        }
    }

    public static void addRenderable(Renderable renderable) {
        tryMinMaxZBoundary(renderable);
        renderables.add(renderable);
    }

    public static void addRenderables(List<Renderable> renderables) {
        renderables.forEach(GraphicsManager::addRenderable);
    }

    public static void addUIRenderable(UIRenderable renderable) {
        uiRenderables.add(renderable);
    }

    public static void removeRenderable(Renderable renderable) {
        renderables.remove(renderable);
    }

    public static void removeRenderables(List<Renderable> renderables) {
        renderables.forEach(GraphicsManager::removeRenderable);
    }

    private static void tryMinMaxZBoundary(Renderable r) {
        List<Integer> renderableZs = r.getZs();
        int renderableMaxZ = renderableZs.stream()
            .max(Integer::compareTo)
            .orElse(0);
        int renderableMinZ = renderableZs.stream()
            .min(Integer::compareTo)
            .orElse(0);
        if(renderableMaxZ > maxZ) maxZ = renderableMaxZ;
        if(renderableMinZ < minZ) minZ = renderableMinZ;
    }
}
