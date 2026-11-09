package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.FontSize;

/**
 * Simple victory/defeat screen. For every other mode: two actions, Play Again and Main Menu.
 * The screen text reflects whether the local perspective (P1 human) won or lost, but we show
 * both winner and loser labels to satisfy the 2-way trigger requirement.
 *
 * <p>For a roguelike run (ROUGELIKE_RUN): reports the outcome to RunManager first (the only
 * place this happens — nothing else observes a run's battles ending), then shows a single
 * action instead — "Continue" into {@link ElderPickRoom} (choosing a new Elder is the floor's
 * reward now, ELDER-005, replacing the old relic/new-card/stat-boost offer) while the run goes
 * on, or "Main Menu" once it has ended (permadeath, or the boss was cleared).
 */
public class VictoryRoom extends Room {
    private final Text title;
    private final Button playAgain;
    private final Button mainMenu;

    private VictoryRoom(PieceAlignment winner) {
        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

        GameMode mode = GameContext.get().getGameModeManager().getCurrent();
        boolean runEnded = true;
        if (mode == GameMode.ROUGELIKE_RUN) {
            RunManager runManager = GameContext.get().getRunManager();
            runManager.reportNodeOutcome(winner);
            runEnded = runManager.getRunState().isEnded();
        }

        // Title depends on game mode
        String titleText = switch (mode) {
            case LOCAL_MATCH, ONLINE_MATCH -> (winner == PieceAlignment.P1) ? "P1 Wins!" : "P2 Wins!";
            case DEMO, ROUGELIKE_RUN -> (winner == localPlayer()) ? "Victory!" : "Game Over!";
        };
        title = new Text(titleText, FontType.SILKSCREEN, 0, 0, 5, Color.WHITE)
            .withFontSize(FontSize.TITLE_LARGE);
        addUI(title);

        if (mode == GameMode.ROUGELIKE_RUN) {
            playAgain = null;
            mainMenu = runEnded ? mainMenuButton(screenW, screenH, "Main Menu", MainMenuRoom::get)
                    : mainMenuButton(screenW, screenH, "Continue", ElderPickRoom::get);
            addUI(mainMenu);
            layout();
            return;
        }

        // Buttons
        playAgain = Button.fromColor(
            Color.WHITE.cpy().mul(0.2f,0.2f,0.2f,1f),
            "Play Again",
            FontType.SILKSCREEN,
            FontSize.BODY_MEDIUM.getSize(),
            screenW/2 - 150,
            screenH/2 - 40,
            300,
            50,
            5
        ).withTextColors(Color.WHITE, Color.WHITE, Color.WHITE)
         .withOnClick((e) -> {
            // Navigate to DraftRoom - use Local Multiplayer if in LOCAL_MATCH mode
            if (mode == GameMode.LOCAL_MATCH) {
                GameContext.get().getRoomManager().gotoRoom(() -> DraftRoom.getForLocalMultiplayer(PieceAlignment.P1));
            } else {
                GameContext.get().getRoomManager().gotoRoom(DraftRoom::get);
            }
         }, ClickableEffectData.getImmediate());
        addUI(playAgain);

        mainMenu = mainMenuButton(screenW, screenH, "Main Menu", MainMenuRoom::get);
        addUI(mainMenu);

        layout();
    }

    private Button mainMenuButton(int screenW, int screenH, String label, java.util.function.Supplier<Room> destination) {
        return Button.fromColor(
            Color.WHITE.cpy().mul(0.2f,0.2f,0.2f,1f),
            label,
            FontType.SILKSCREEN,
            FontSize.BODY_MEDIUM.getSize(),
            screenW/2 - 150,
            screenH/2 - 100,
            300,
            50,
            5
        ).withTextColors(Color.WHITE, Color.WHITE, Color.WHITE)
         .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(destination), ClickableEffectData.getImmediate());
    }

    private void layout() {
        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        // Center title near top
        title.setBounds(new Box(
            (screenW - title.getWidth())/2,
            (int)(screenH * 0.65f),
            title.getWidth(),
            title.getHeight()
        ));
        // Buttons already positioned approximately around center
    }

    @Override
    public void onScreenResize() {
        layout();
        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        if (playAgain != null) {
            playAgain.getBounds().setX(screenW/2 - playAgain.getWidth()/2);
            playAgain.getBounds().setY(screenH/2 - 40);
        }
        if (mainMenu != null) {
            mainMenu.getBounds().setX(screenW/2 - mainMenu.getWidth()/2);
            mainMenu.getBounds().setY(screenH/2 - 100);
        }
    }

    public static VictoryRoom get(PieceAlignment winner) {
        return new VictoryRoom(winner);
    }

    /** The side this screen played as — the first (only, for solo) seat its session controlled. */
    private static PieceAlignment localPlayer() {
        ClientMatchSession session = GameContext.get().getClientMatchSession();
        if (session == null || session.getControlledSeats().isEmpty()) return PieceAlignment.P1;
        return session.getControlledSeats().get(0);
    }
}
