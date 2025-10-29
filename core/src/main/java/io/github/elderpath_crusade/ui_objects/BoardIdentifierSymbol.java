package io.github.elderpath_crusade.ui_objects;

import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.GRID_DIRECTION;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.utils.ColorSettings;

public class BoardIdentifierSymbol extends Text implements UIRenderable {
    private static final Color ROW_COLOR = ColorSettings.BOARD_IDENTIFIER_SYMBOL_ROW.getColor();
    private static final Color COLUMN_COLOR = ColorSettings.BOARD_IDENTIFIER_SYMBOL_COL.getColor();

    public BoardIdentifierSymbol(String text, int x, int y, GRID_DIRECTION direction, boolean isCentered) {
        super(
            text,
            FontType.DEFAULT,
            x,
            y,
            0,
            direction==GRID_DIRECTION.ROW
                ? ROW_COLOR
                : COLUMN_COLOR
        );
        if (isCentered) {
            setBounds(new Box(
                x - getWidth()/2,
                y - getHeight()/2,
                getWidth(),
                getHeight()
            ));
        }
    }

}
