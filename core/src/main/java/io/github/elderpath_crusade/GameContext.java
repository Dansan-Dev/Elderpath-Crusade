package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import io.github.elderpath_crusade.assets.AssetService;
import io.github.elderpath_crusade.ecs.systems.PieceSyncSystem;
import io.github.elderpath_crusade.ecs.systems.TurnSystem;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.managers.BotManager;
import io.github.elderpath_crusade.managers.DeckManager;
import io.github.elderpath_crusade.managers.GameManager;
import io.github.elderpath_crusade.managers.GameModeManager;
import io.github.elderpath_crusade.managers.PlayerManager;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.managers.VictoryHandler;
import io.github.elderpath_crusade.managers.WinConditionManager;
import io.github.elderpath_crusade.session.GameSession;
import io.github.elderpath_crusade.state.GameStateMachine;
import lombok.Getter;

/**
 * Central service locator for the game. Holds references to all core services.
 * Provides a static accessor during migration; future phases will pass it via constructors.
 */
public class GameContext {
    private static GameContext instance;

    @Getter private final TypedEventBus eventBus;
    @Getter private final GameStateMachine stateMachine;
    @Getter private final Engine ecsEngine;
    @Getter private final AssetService assets;
    @Getter private final TurnManager turnManager;
    @Getter private final PlayerManager playerManager;
    @Getter private final BotManager botManager;
    @Getter private final WinConditionManager winConditionManager;
    @Getter private final GameManager gameManager;
    @Getter private final GameModeManager gameModeManager;
    @Getter private final DeckManager deckManager;
    @Getter private final VictoryHandler victoryHandler;
    @Getter private Board activeBoard;
    @Getter private GameSession activeSession;

    private GameContext() {
        this.eventBus = TypedEventBus.get();
        this.stateMachine = new GameStateMachine(this);
        this.ecsEngine = new Engine();
        this.ecsEngine.addSystem(new TurnSystem());
        this.ecsEngine.addSystem(new PieceSyncSystem());
        this.assets = new AssetService();
        this.turnManager = new TurnManager();
        this.playerManager = new PlayerManager();
        this.botManager = new BotManager();
        this.winConditionManager = new WinConditionManager();
        this.gameManager = new GameManager();
        this.gameModeManager = new GameModeManager();
        this.deckManager = new DeckManager();
        this.victoryHandler = new VictoryHandler();
    }

    public static GameContext create() {
        instance = new GameContext();
        return instance;
    }

    /**
     * Static accessor for migration. Use this when constructor injection isn't yet available.
     * Will be removed once all callers receive GameContext via constructor.
     */
    public static GameContext get() {
        return instance;
    }

    public void setActiveBoard(Board board) {
        this.activeBoard = board;
    }

    public void clearBoard() {
        this.activeBoard = null;
    }

    public void setActiveSession(GameSession session) {
        this.activeSession = session;
    }

    public void clearSession() {
        this.activeSession = null;
    }
}
