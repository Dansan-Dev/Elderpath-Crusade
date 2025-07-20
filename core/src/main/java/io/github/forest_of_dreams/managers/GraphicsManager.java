package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.game_objects.pause.PauseScreen;
import io.github.forest_of_dreams.game_objects.SpriteObject;
import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import io.github.forest_of_dreams.supers.HigherOrderUI;
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
//        if (isPaused) PauseScreen.render(batch, 10, isPaused);
    }

    public static void renderUI(SpriteBatch batch) {
        uiRenderables.forEach(r -> r.renderUI(batch, isPaused));
    }

    public static void renderPauseUI(SpriteBatch batch) {
        if (!isPaused) return;
        PauseScreen.get().renderUI(batch, false);
    }

    private static void renderGameGraphics(SpriteBatch batch) {
        for(int i = minZ; i <= maxZ; i++) {
            for(Renderable r : renderables) {
                if (r instanceof HigherOrderTexture) r.render(batch, i, isPaused, ((HigherOrderTexture) r).getX(), ((HigherOrderTexture) r).getY());
                else r.render(batch, i, isPaused);
            }
        }
    }

    public static void addRenderable(Renderable renderable) {
        tryMinMaxZBoundary(renderable);
        renderables.add(renderable);
        if (renderable instanceof Clickable clickable) {
            InteractionManager.addClickable(clickable);
        } else if (renderable instanceof HigherOrderTexture higherOrderTexture) {
            sendClickables(higherOrderTexture);
        }
    }

    public static void addRenderables(List<Renderable> renderables) {
        renderables.forEach(GraphicsManager::addRenderable);
    }

    public static void addUIRenderable(UIRenderable renderable) {
        uiRenderables.add(renderable);
        if (renderable instanceof Clickable clickable) {
            InteractionManager.addClickable(clickable);
        } else if (renderable instanceof HigherOrderUI higherOrderUI) {
            sendUIClickables(higherOrderUI);
        }
    }

    public static void removeRenderable(Renderable renderable) {
        renderables.remove(renderable);
        if (renderable instanceof Clickable clickable)
            InteractionManager.removeClickable(clickable);
    }

    public static void removeRenderables(List<Renderable> renderables) {
        renderables.forEach(GraphicsManager::removeRenderable);
    }

    public static void removeUIRenderable(UIRenderable renderable) {
        uiRenderables.remove(renderable);
        if (renderable instanceof Clickable clickable)
            InteractionManager.removeClickable(clickable);
    }

    public static void removeUIRenderables(List<UIRenderable> renderables) {
        renderables.forEach(GraphicsManager::removeUIRenderable);
    }

    public static void clearRenderables() {
        renderables.forEach(r -> {
            if (r instanceof Clickable clickable)
                InteractionManager.removeClickable(clickable);
        });
        renderables.clear();
    }

    public static void  clearUIRenderables() {
        renderables.forEach(r -> {
            if (r instanceof Clickable clickable)
                InteractionManager.removeClickable(clickable);
        });
        uiRenderables.clear();
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

    public static void sendClickables(HigherOrderTexture texture) {
        texture.getRenderables().forEach(r -> {
            if (r instanceof Clickable clickable) {
                InteractionManager.addClickable(clickable);
            }
            else if (r instanceof HigherOrderTexture higherOrderTexture) sendClickables(higherOrderTexture);
        });
    }

    public static void retractClickables(HigherOrderTexture texture) {
        texture.getRenderables().forEach(r -> {
            if (r instanceof Clickable clickable) InteractionManager.removeClickable(clickable);
            else if (r instanceof HigherOrderTexture higherOrderTexture) retractClickables(higherOrderTexture);
        });
    }

    public static void sendUIClickables(HigherOrderUI ui) {
        ui.getRenderableUIs().forEach(r -> {
            if (r instanceof Clickable clickable) InteractionManager.addClickable(clickable);
            else if (r instanceof HigherOrderUI higherOrderUI) sendUIClickables(higherOrderUI);
        });
    }

    public static void retractUIClickables(HigherOrderUI ui) {
        ui.getRenderableUIs().forEach(r -> {
            if (r instanceof Clickable clickable) InteractionManager.removeClickable(clickable);
            else if (r instanceof HigherOrderUI higherOrderUI) retractUIClickables(higherOrderUI);
        });
    }
}
