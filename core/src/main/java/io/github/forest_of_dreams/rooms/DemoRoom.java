package io.github.forest_of_dreams.rooms;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Sprite;
import io.github.forest_of_dreams.characters.pieces.monster.WarpMage;
import io.github.forest_of_dreams.characters.pieces.monster.Wolf;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.game_objects.cards.Card;
import io.github.forest_of_dreams.game_objects.cards.Deck;
import io.github.forest_of_dreams.game_objects.cards.Hand;
import io.github.forest_of_dreams.game_objects.sprites.SpriteObject;
import io.github.forest_of_dreams.tiles.MountainTile;
import io.github.forest_of_dreams.ui_objects.Text;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.supers.Room;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.utils.SpriteCreator;

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
            .withFontSize(io.github.forest_of_dreams.utils.FontSize.BODY_MEDIUM);
        addUI(pauseMenuHint);

        // Preload frames once to avoid reloading textures on every card creation
        List<Sprite> frontBaseFrames = List.of(
            SpriteCreator.makeSprite(
                "assets/images/card_front.png",
                0,
                0,
                1024,
                1536,
                125,
                200
            )
        );
        List<Sprite> backBaseFrames = List.of(
            SpriteCreator.makeSprite(
                "assets/images/card_back.png",
                0,
                0,
                1024,
                1536,
                125,
                200
            )
        );

        Supplier<Card> cardSupplier = () -> {
            // Clone sprites so each card owns its sprites without reloading textures
            List<Sprite> frontFrames = new java.util.ArrayList<>(frontBaseFrames.size());
            for (Sprite s : frontBaseFrames) frontFrames.add(new Sprite(s));
            List<Sprite> backFrames = new java.util.ArrayList<>(backBaseFrames.size());
            for (Sprite s : backBaseFrames) backFrames.add(new Sprite(s));

            SpriteObject cardSprite1 = new SpriteObject(0, 0,125, 200, 1, SpriteBoxPos.BOTTOM_LEFT);
            cardSprite1.addAnimation("general", frontFrames, 0);

            SpriteObject cardSprite2 = new SpriteObject(0, 0,125, 200, 1, SpriteBoxPos.BOTTOM_LEFT);
            cardSprite2.addAnimation("general", backFrames, 0);

            return new Card(
                0,
                0,
                cardSprite1,
                cardSprite2,
                null
            );
        };

        hand = new Hand(
            SettingsManager.screenSize.getScreenCenter()[0],
            -80,
            125,
            200
        );
        for (int i = 0; i < 6; i++) {
            hand.addCard(cardSupplier.get());
        }

        addContent(hand);

        deck = new Deck(
            List.of(cardSupplier.get()),
            0, 0,
            125, 200,
            1,
            SpriteBoxPos.BOTTOM_LEFT
        );

        addContent(deck);

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
