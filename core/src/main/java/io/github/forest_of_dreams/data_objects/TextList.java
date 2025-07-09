package io.github.forest_of_dreams.data_objects;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import io.github.forest_of_dreams.supers.HigherOrderTexture;

import java.util.List;
import java.util.stream.IntStream;

public class TextList extends HigherOrderTexture {

    public TextList() {}

    public TextList(List<Text> texts) {
        getRenderables().addAll(texts);
    }

    public void addText(Text text) {
        getRenderables().add(text);
    }

    public void alignTextAcrossXAxis(int offset, int centerX, int centerY) {
        int startX = centerX - (getRenderables().size() - 1) * (offset/2);
        IntStream.range(0, getRenderables().size())
            .forEach(i -> {
                Text text = (Text) getRenderables().get(i);
                Label label = text.getLabel();
                label.setPosition(
                    startX + i*offset,
                    centerY - (label.getHeight()/2)
                );
            });
    }

    public void alignTextAcrossYAxis(int offset, int centerX, int centerY) {
        int startY = centerY + (getRenderables().size()-1) * (offset/2);
        IntStream.range(0, getRenderables().size())
            .forEach(i -> {
                Text text = (Text) getRenderables().get(i);
                Label label = text.getLabel();
                label.setPosition(
                    centerX - (label.getWidth()/2),
                    startY - i*offset
                );
            });
    }
}
