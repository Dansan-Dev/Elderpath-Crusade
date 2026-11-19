package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.MatchResultText;
import io.github.elderpath_crusade.utils.DisplayName;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.run.RunNodeType;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.FontSize;

/**
 * Simple victory/defeat screen. For every other mode except an online match: two actions, Play Again and Main Menu
 * (an online match offers only Main Menu).
 * The screen text reflects whether the local perspective (P1 human) won or lost, but we show
 * both winner and loser labels to satisfy the 2-way trigger requirement.
 *
 * <p>For a roguelike run (ROUGELIKE_RUN): reports the outcome to RunManager first (the only
 * place this happens — nothing else observes a run's battles ending). A run that just ended in
 * a win also gets its deck/Elders/relics snapshotted into {@code CollectionManager} right here
 * (Sprint 64) — the only place the win is known before the next room is picked. Then shows a single
 * action — "Main Menu" once the run has ended (permadeath, or the *last* floor's boss was
 * cleared), otherwise "Continue" into whichever reward (if any) this node type grants (Phase
 * 3.6): a Boss win (not the last floor) continues into {@link ElderPickRoom} (which also
 * advances to the next floor once a pick is made); an Elite win continues into {@link
 * EliteRewardRoom} (a relic or a card modification, randomly); an ordinary Battle win grants
 * nothing and continues straight back to {@link RunRoom}.
 */
public class VictoryRoom extends Room {
    private static final int MAX_NAME_CHARS = 20;
    private final Text title;
    /** Second line under the title (online loss: "You lose"); null when there is none. */
    private Text subtitle;
    private final Button playAgain;
    private final Button mainMenu;

    private VictoryRoom(PieceAlignment winner) {
        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

        GameMode mode = GameContext.get().getGameModeManager().getCurrent();
        boolean runEnded = true;
        RunNodeType wonNodeType = null;
        if (mode == GameMode.ROUGELIKE_RUN) {
            RunManager runManager = GameContext.get().getRunManager();
            wonNodeType = runManager.getRunState().currentNode().type();
            runManager.reportNodeOutcome(winner);
            runEnded = runManager.getRunState().isEnded();
            // Sprint 64: a won run's deck/Elders/relics go into the player's persistent collection here —
            // the one place a run's outcome is known (see the class doc).
            if (runEnded && runManager.getRunState().isWon()) {
                GameContext.get().getCollectionManager().recordWin(runManager.getRunState(), mode);
            }
        }

        // Title depends on game mode. An online match names the players: "<winner> wins!", and for the loser a
        // second line "You lose" (names come from the lobby service, later verified Steam profile names).
        String subtitleText = null;
        String titleText;
        if (mode == GameMode.ONLINE_MATCH) {
            MatchResultText result = onlineResult(winner);
            titleText = result.headline();
            subtitleText = result.subline();
        } else {
            titleText = switch (mode) {
                case LOCAL_MATCH, ONLINE_MATCH -> (winner == PieceAlignment.P1) ? "P1 Wins!" : "P2 Wins!";
                case DEMO, ROUGELIKE_RUN -> (winner == localPlayer()) ? "Victory!" : "Game Over!";
            };
        }
        title = new Text(titleText, FontType.SILKSCREEN, 0, 0, 5, Color.WHITE)
            .withFontSize(FontSize.TITLE_LARGE);
        addUI(title);
        if (subtitleText != null) {
            subtitle = new Text(subtitleText, FontType.SILKSCREEN, 0, 0, 5, Color.WHITE)
                .withFontSize(FontSize.TITLE_MEDIUM);
            addUI(subtitle);
        }

        if (mode == GameMode.ROUGELIKE_RUN) {
            playAgain = null;
            if (runEnded) {
                mainMenu = mainMenuButton(screenW, screenH, "Main Menu", MainMenuRoom::get);
            } else {
                java.util.function.Supplier<Room> next = switch (wonNodeType) {
                    case BOSS -> () -> ElderPickRoom.get(true);
                    case ELITE -> EliteRewardRoom::get;
                    default -> () -> UpgradeOfferRoom.get(RunNodeType.BATTLE); // default is always BATTLE — see RunRoom.onNodeClicked
                };
                mainMenu = mainMenuButton(screenW, screenH, "Continue", next);
            }
            addUI(mainMenu);
            layout();
            return;
        }

        // An online match has no "Play Again": a rematch would need the same two players and a new lobby
        // round, and the old button sent the player into a local Draft instead (UI-017).
        if (mode == GameMode.ONLINE_MATCH) {
            playAgain = null;
            mainMenu = mainMenuButton(screenW, screenH, "Main Menu", MainMenuRoom::get);
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
            // Navigate to DraftRoom - use Local Multiplayer's deck-source flow if in LOCAL_MATCH mode
            if (mode == GameMode.LOCAL_MATCH) {
                GameContext.get().getRoomManager().gotoRoom(DeckSourceSelectRoom::startLocalMultiplayer);
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
        if (subtitle != null) {
            subtitle.setBounds(new Box(
                (screenW - subtitle.getWidth()) / 2,
                title.getBounds().getY() - subtitle.getHeight() - 16,
                subtitle.getWidth(),
                subtitle.getHeight()
            ));
        }
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

    /** The names the result screen uses: own side's and the other side's, cleaned for the pixel font. */
    private static MatchResultText onlineResult(PieceAlignment winner) {
        ClientMatchSession session = GameContext.get().getClientMatchSession();
        PieceAlignment local = localPlayer();
        PieceAlignment other = local == PieceAlignment.P1 ? PieceAlignment.P2 : PieceAlignment.P1;
        String own = session == null ? null : session.displayNameOf(local);
        String opponent = session == null ? null : session.displayNameOf(other);
        return MatchResultText.online(
                own == null ? null : DisplayName.forScreen(own, MAX_NAME_CHARS),
                opponent == null ? null : DisplayName.forScreen(opponent, MAX_NAME_CHARS),
                winner == local);
    }

    /** The side this screen played as — the first (only, for solo) seat its session controlled. */
    private static PieceAlignment localPlayer() {
        ClientMatchSession session = GameContext.get().getClientMatchSession();
        if (session == null || session.getControlledSeats().isEmpty()) return PieceAlignment.P1;
        return session.getControlledSeats().get(0);
    }
}
