package io.github.forest_of_dreams.data_objects;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Box {
    private int x;
    private int y;
    private int width;
    private int height;

    public Box(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
}
