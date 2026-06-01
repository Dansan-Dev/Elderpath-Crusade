package io.github.elderpath_crusade.managers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.game_objects.pause.PauseScreen;
import io.github.elderpath_crusade.game_objects.sprites.SpriteObject;
import io.github.elderpath_crusade.interfaces.Clickable;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.interfaces.Updatable;
import io.github.elderpath_crusade.supers.HigherOrderTexture;
import io.github.elderpath_crusade.supers.HigherOrderUI;
import io.github.elderpath_crusade.utils.ClickableRegistryUtil;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GraphicsManager {
    @Getter private final List<Renderable> renderables = new ArrayList<>();
    @Getter private final List<UIRenderable> uiRenderables = new ArrayList<>();
    private SpriteBatch batch;

    // OPT-002: reusable to avoid per-frame allocation
    private final List<Renderable> updateSnapshot = new ArrayList<>();
    private final List<UIRenderable> uiUpdateSnapshot = new ArrayList<>();
    private final List<UIRenderable> uiRenderSnapshot = new ArrayList<>();

    public GraphicsManager() {}

    public SpriteBatch getBatch() {
        if (batch == null) {
            batch = new SpriteBatch();
        }
        return batch;
    }

    void pauseAnimations() {
        renderables.stream()
            .filter(r -> r instanceof SpriteObject)
            .forEach(r -> ((SpriteObject) r).pauseAnimation());
    }

    void unpauseAnimations() {
        renderables.stream()
            .filter(r -> r instanceof SpriteObject)
            .forEach(r -> ((SpriteObject) r).unpauseAnimation());
    }

    public void render(SpriteBatch batch) {
        renderGameGraphics(batch);
        renderUI(batch);
    }

    public void update(float delta) {
        if (GameContext.get().getGameManager().isPaused()) return;
        updateSnapshot.clear();
        updateSnapshot.addAll(renderables);
        for (Renderable r : updateSnapshot) {
            if (r instanceof Updatable u) {
                u.update(delta);
            }
        }
        uiUpdateSnapshot.clear();
        uiUpdateSnapshot.addAll(uiRenderables);
        for (UIRenderable r : uiUpdateSnapshot) {
            if (r instanceof Updatable u) {
                u.update(delta);
            }
        }
    }

    public void renderUI(SpriteBatch batch) {
        uiRenderSnapshot.clear();
        uiRenderSnapshot.addAll(uiRenderables);
        uiRenderSnapshot.forEach(r -> r.renderUI(batch, GameContext.get().getGameManager().isPaused()));
    }

    public void renderPauseUI(SpriteBatch batch) {
        if (!GameContext.get().getGameManager().isPaused()) return;
        PauseScreen.get().renderUI(batch, false);
    }

    private void renderGameGraphics(SpriteBatch batch) {
        boolean paused = GameContext.get().getGameManager().isPaused();
        for (Integer z : GameContext.get().getZIndexRegistry().getZLevels()) {
            Collection<Renderable> bucket = GameContext.get().getZIndexRegistry().getBucket(z);
            if (bucket == null) continue;
            for (Renderable r : bucket) {
                if (r instanceof HigherOrderTexture hot) {
                    r.render(batch, z, paused, hot.getX(), hot.getY());
                } else {
                    r.render(batch, z, paused);
                }
            }
        }
    }

    public void addRenderable(Renderable renderable) {
        renderables.add(renderable);
        GameContext.get().getZIndexRegistry().add(renderable);
        if (renderable instanceof Clickable clickable) {
            GameContext.get().getInteractionManager().addClickable(clickable);
        } else if (renderable instanceof HigherOrderTexture higherOrderTexture) {
            ClickableRegistryUtil.sendClickables(higherOrderTexture);
        }
    }

    public void addRenderables(List<Renderable> renderablesToAdd) {
        renderablesToAdd.forEach(this::addRenderable);
    }

    public void addUIRenderable(UIRenderable renderable) {
        uiRenderables.add(renderable);
        if (renderable instanceof Clickable clickable) {
            GameContext.get().getInteractionManager().addClickable(clickable);
        } else if (renderable instanceof HigherOrderUI higherOrderUI) {
            ClickableRegistryUtil.sendUIClickables(higherOrderUI);
        }
    }

    public void removeRenderable(Renderable renderable) {
        renderables.remove(renderable);
        GameContext.get().getZIndexRegistry().remove(renderable);
        if (renderable instanceof Clickable clickable) {
            GameContext.get().getInteractionManager().removeClickable(clickable);
        } else if (renderable instanceof HigherOrderTexture higherOrderTexture) {
            ClickableRegistryUtil.retractClickables(higherOrderTexture);
        }
    }

    public void removeRenderables(List<Renderable> renderablesToRemove) {
        renderablesToRemove.forEach(this::removeRenderable);
    }

    public void removeUIRenderable(UIRenderable renderable) {
        uiRenderables.remove(renderable);
        if (renderable instanceof Clickable clickable) {
            GameContext.get().getInteractionManager().removeClickable(clickable);
        } else if (renderable instanceof HigherOrderUI higherOrderUI) {
            ClickableRegistryUtil.retractUIClickables(higherOrderUI);
        }
    }

    public void removeUIRenderables(List<UIRenderable> renderablesToRemove) {
        renderablesToRemove.forEach(this::removeUIRenderable);
    }

    public void clearRenderables() {
        renderables.forEach(r -> {
            if (r instanceof Clickable clickable) {
                GameContext.get().getInteractionManager().removeClickable(clickable);
            } else if (r instanceof HigherOrderTexture higherOrderTexture) {
                ClickableRegistryUtil.retractClickables(higherOrderTexture);
            }
        });
        renderables.clear();
        GameContext.get().getZIndexRegistry().clear();
    }

    public void clearUIRenderables() {
        uiRenderables.forEach(r -> {
            if (r instanceof Clickable clickable) {
                GameContext.get().getInteractionManager().removeClickable(clickable);
            } else if (r instanceof HigherOrderUI higherOrderUI) {
                ClickableRegistryUtil.retractUIClickables(higherOrderUI);
            }
        });
        uiRenderables.clear();
    }

    public void draw(SpriteBatch batch) {
        GameContext.get().getRenderPipeline().draw(batch);
    }

    public void blurredDraw(SpriteBatch batch) {
        GameContext.get().getRenderPipeline().blurredDraw(batch);
    }

    public void drawPauseUI(SpriteBatch batch) {
        GameContext.get().getRenderPipeline().drawPauseUI(batch);
    }
}
