package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import io.github.elderpath_crusade.assets.AssetService;
import io.github.elderpath_crusade.ecs.systems.PieceSyncSystem;
import io.github.elderpath_crusade.ecs.systems.CombatSystem;
import io.github.elderpath_crusade.ecs.systems.PieceRenderSystem;
import io.github.elderpath_crusade.ecs.systems.TurnSystem;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.managers.BotManager;
import io.github.elderpath_crusade.managers.DeckManager;
import io.github.elderpath_crusade.managers.FontManager;
import io.github.elderpath_crusade.managers.GameManager;
import io.github.elderpath_crusade.managers.GameModeManager;
import io.github.elderpath_crusade.managers.GraphicsManager;
import io.github.elderpath_crusade.managers.HighlightManager;
import io.github.elderpath_crusade.managers.InfoDataManager;
import io.github.elderpath_crusade.managers.InputManager;
import io.github.elderpath_crusade.managers.InteractionManager;
import io.github.elderpath_crusade.managers.MusicManager;
import io.github.elderpath_crusade.managers.PlayerManager;
import io.github.elderpath_crusade.managers.RenderPipeline;
import io.github.elderpath_crusade.managers.RoomManager;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.managers.ShaderManager;
import io.github.elderpath_crusade.managers.SoundManager;
import io.github.elderpath_crusade.managers.TextureManager;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.managers.VictoryHandler;
import io.github.elderpath_crusade.managers.WinConditionManager;
import io.github.elderpath_crusade.managers.ZIndexRegistry;
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
    @Getter private final CombatSystem combatSystem;
    @Getter private final PieceRenderSystem pieceRenderSystem;
    @Getter private final AssetService assets;
    @Getter private final TurnManager turnManager;
    @Getter private final PlayerManager playerManager;
    @Getter private final BotManager botManager;
    @Getter private final WinConditionManager winConditionManager;
    @Getter private final GameManager gameManager;
    @Getter private final GraphicsManager graphicsManager;
    @Getter private final InputManager inputManager;
    @Getter private final HighlightManager highlightManager;
    @Getter private final InteractionManager interactionManager;
    @Getter private final GameModeManager gameModeManager;
    @Getter private final DeckManager deckManager;
    @Getter private final VictoryHandler victoryHandler;
    @Getter private final RoomManager roomManager;
    @Getter private final SettingsManager settingsManager;
    @Getter private final MusicManager musicManager;
    @Getter private final SoundManager soundManager;
    @Getter private final ShaderManager shaderManager;
    @Getter private final RenderPipeline renderPipeline;
    @Getter private final ZIndexRegistry zIndexRegistry;
    @Getter private final TextureManager textureManager;
    @Getter private final FontManager fontManager;
    @Getter private final InfoDataManager infoDataManager;
    @Getter private Board activeBoard;
    @Getter private GameSession activeSession;

    private GameContext() {
        this.eventBus = TypedEventBus.get();
        this.stateMachine = new GameStateMachine(this);
        this.ecsEngine = new Engine();
        this.combatSystem = new CombatSystem();
        this.pieceRenderSystem = new PieceRenderSystem();
        this.ecsEngine.addSystem(new TurnSystem());
        this.ecsEngine.addSystem(new PieceSyncSystem());
        this.ecsEngine.addSystem(this.combatSystem);
        this.ecsEngine.addSystem(this.pieceRenderSystem);
        this.assets = new AssetService();
        this.turnManager = new TurnManager();
        this.playerManager = new PlayerManager();
        this.botManager = new BotManager();
        this.winConditionManager = new WinConditionManager();
        this.gameManager = new GameManager();
        this.graphicsManager = new GraphicsManager();
        this.inputManager = new InputManager();
        this.highlightManager = new HighlightManager();
        this.interactionManager = new InteractionManager();
        this.gameModeManager = new GameModeManager();
        this.deckManager = new DeckManager();
        this.victoryHandler = new VictoryHandler();
        this.roomManager = new RoomManager();
        this.settingsManager = new SettingsManager();
        this.musicManager = new MusicManager();
        this.soundManager = new SoundManager();
        this.shaderManager = new ShaderManager();
        this.renderPipeline = new RenderPipeline();
        this.zIndexRegistry = new ZIndexRegistry();
        this.textureManager = new TextureManager();
        this.fontManager = new FontManager();
        this.infoDataManager = new InfoDataManager();
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
