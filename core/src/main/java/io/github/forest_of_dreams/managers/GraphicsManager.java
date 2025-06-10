package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.interfaces.Renderable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphicsManager {
    private Map<Integer, List<Renderable>> renderables;
    private int maxZ = 0;
    private int minZ = 0;

    public GraphicsManager() {
        renderables = new HashMap<>();
    }

    public void render(SpriteBatch batch) {
        for(int i = minZ; i <= maxZ; i++) {
            for(Renderable r : renderables.getOrDefault(i, new ArrayList<>())) {
                r.render(batch);
            }
        }
    }

    public void addRenderable(Renderable renderable) {
        int z = renderable.getZ();
        if(z > maxZ) maxZ = z;
        if(z < minZ) minZ = z;
        if(!renderables.containsKey(z)) {
            renderables.put(z, new ArrayList<>());
        }
        renderables.get(z).add(renderable);
    }

    public void addRenderables(List<Renderable> renderables) {
        renderables.forEach(this::addRenderable);
    }

    public void removeRenderable(Renderable renderable) {
        renderables.forEach((key, value) -> value.remove(renderable));
    }

    public void removeRenderables(List<Renderable> renderables) {
        renderables.forEach(this::removeRenderable);
    }
}
