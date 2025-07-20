package io.github.forest_of_dreams.interfaces;

import java.util.HashMap;

@FunctionalInterface
public interface OnClick {
    void run(HashMap<Integer, CustomBox> interactionEntities);
}
