package io.github.forest_of_dreams.rooms;

import com.badlogic.gdx.graphics.Color;
import io.github.forest_of_dreams.cards.WolfCard;
import io.github.forest_of_dreams.characters.pieces.WarpMage;
import io.github.forest_of_dreams.characters.pieces.Wolf;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.game_objects.cards.Card;
import io.github.forest_of_dreams.game_objects.cards.Deck;
import io.github.forest_of_dreams.game_objects.cards.Hand;
import io.github.forest_of_dreams.managers.InteractionManager;
import io.github.forest_of_dreams.tiles.MountainTile;
import io.github.forest_of_dreams.ui_objects.Button;
import io.github.forest_of_dreams.ui_objects.Text;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.board.Plot;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.supers.Room;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.utils.FontSize;
import io.github.forest_of_dreams.utils.Logger;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.enums.ClickableTargetType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class DemoRoom extends Room {
    private final Board board;
    private final Hand hand;
    private final Deck deck;
    private final UIRenderable pauseMenuHint;
    private final Supplier<int[]> pauseMenuPos = () -> new int[]{20, SettingsManager.screenSize.getScreenHeight() - 40};
    private final int plot_width = 40;
    private final int plot_height = 40;

    private DemoRoom() {
        super();

        board = new Board(0, 0, plot_width, plot_height, 7, 5);
        board.initializePlots();

//        board.setGamePiecePos(2, 0, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.ALLIED));
//        board.setGamePiecePos(3, 1, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.HOSTILE));
//        board.setGamePiecePos(1, 2, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.ALLIED));
//        board.setGamePiecePos(5, 3, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.NEUTRAL));
//        board.setGamePiecePos(4, 4, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.NEUTRAL));
        board.addGamePieceToPos(2, 0, new Wolf(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.ALLIED));
        board.addGamePieceToPos(4, 0, new Wolf(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.HOSTILE));
        board.addGamePieceToPos(1, 4, new Wolf(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.HOSTILE));
        board.addGamePieceToPos(3, 2, new WarpMage(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.ALLIED));
        board.addGamePieceToPos(4, 3, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.addGamePieceToPos(2, 1, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.addGamePieceToPos(5, 2, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.addGamePieceToPos(1, 2, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));

        addContent(board);

        int[] pauseMenuPos = this.pauseMenuPos.get();
        pauseMenuHint = new Text("ESC", FontType.SILKSCREEN, pauseMenuPos[0], pauseMenuPos[1], 1, Color.WHITE)
            .withFontSize(FontSize.BODY_MEDIUM);
        addUI(pauseMenuHint);

        // Test buttons for multi-click interactions (Step 6 verification)
        Text testMulti = new Text("Test: Multi (2 Plots)", FontType.SILKSCREEN, 20, pauseMenuPos[1] - 30, 1, Color.WHITE)
            .withFontSize(FontSize.CAPTION)
            .withOnClick((entities) -> {
                int count = entities.size() - 1; // exclude source at index 0
                Logger.log("DemoRoom", "MULTI_INTERACTION triggered with " + count + " targets: " + entities);
            }, ClickableEffectData.getMulti(ClickableTargetType.PLOT, 2));
        addUI(testMulti);

        Text testChoiceLimited = new Text("Test: Choice Limited (<=3 Pieces)", FontType.SILKSCREEN, 20, pauseMenuPos[1] - 50, 1, Color.WHITE)
            .withFontSize(FontSize.CAPTION)
            .withOnClick((entities) -> {
                // Translate selected plots to the occupying game pieces (if any)
                java.util.List<GamePiece> pieces = new java.util.ArrayList<>();
                for (int i = 1; i < entities.size(); i++) {
                    Object o = entities.get(i);
                    if (o instanceof Plot plot) {
                        GamePiece gp = board.getGamePieceAtPlot(plot);
                        if (gp != null) pieces.add(gp);
                    }
                }
                Logger.log("DemoRoom", "MULTI_CHOICE_LIMITED (Pieces via Plots) resolved with " + pieces.size() + " pieces: " + pieces);
            }, ClickableEffectData.getMultiChoiceLimited(ClickableTargetType.PLOT, 3));
        addUI(testChoiceLimited);

        Text testChoiceUnlimited = new Text("Test: Choice Unlimited (any)", FontType.SILKSCREEN, 20, pauseMenuPos[1] - 70, 1, Color.WHITE)
            .withFontSize(FontSize.CAPTION)
            .withOnClick((entities) -> {
                int count = entities.size() - 1;
                Logger.log("DemoRoom", "MULTI_CHOICE_UNLIMITED triggered with " + count + " targets: " + entities);
            }, ClickableEffectData.getMultiChoiceUnlimited(ClickableTargetType.NONE));
        addUI(testChoiceUnlimited);

        hand = new Hand(
            SettingsManager.screenSize.getScreenCenter()[0],
            -80,
            125,
            200,
            0
        );

        addContent(hand);

        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            if ( i % 2 == 0) {
                cards.add(new Card(0, 0, 125, 200, 0, null));
            } else {
                cards.add(new WolfCard(board, 0, 1, PieceAlignment.ALLIED, 0, 0, 125, 200, 0));
            }
        }

        deck = new Deck(
            cards,
            0, 0,
            125, 200,
            1,
            SpriteBoxPos.BOTTOM_LEFT
        );
        deck.setHand(hand);
        addContent(deck);

        System.out.println("InteractionManager.getClickables().size() = " + InteractionManager.getClickables().size());
        InteractionManager.getClickables().forEach((c) -> {
            if (c instanceof WolfCard wolfCard) {
                System.out.println("wolfCard = " + wolfCard);
            } else if (c instanceof Text text) {
                System.out.println("text.getText() = " + text.getText());
            } else if (c instanceof Button button) {
                System.out.println("button.getText() = " + button.getText());
            } else {
                System.out.println("c = " + c);
            }
        });

        int[] board_size = board.getPixelSize();
        layoutBoard(board_size[0], board_size[1]);
    }

    private void layoutBoard(int boardPixelWidth, int boardPixelHeight) {
        int[] screen_center = SettingsManager.screenSize.getScreenCenter();
        int boardCenteredX = screen_center[0] - boardPixelWidth / 2;
        int boardCenteredY = screen_center[1] - boardPixelHeight / 2;
        board.getBounds().setX(boardCenteredX);
        board.getBounds().setY(boardCenteredY);
        hand.setCenterX(screen_center[0]);
        hand.updateBounds();
    }

    @Override
    public void onScreenResize() {
        int boardPixelWidth = plot_width * 5;
        int boardPixelHeight = plot_height * 7;
        layoutBoard(boardPixelWidth, boardPixelHeight);

        int[] pauseMenuPos = this.pauseMenuPos.get();
        pauseMenuHint.getBounds().setX(pauseMenuPos[0]);
        pauseMenuHint.getBounds().setY(pauseMenuPos[1]);
    }

    public static DemoRoom get() {
        return new DemoRoom();
    }
}
