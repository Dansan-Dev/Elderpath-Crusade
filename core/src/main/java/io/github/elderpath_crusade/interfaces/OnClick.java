package io.github.elderpath_crusade.interfaces;

import java.util.HashMap;

@FunctionalInterface
public interface OnClick {
    void run(HashMap<Integer, CustomBox> interactionEntities);
}
