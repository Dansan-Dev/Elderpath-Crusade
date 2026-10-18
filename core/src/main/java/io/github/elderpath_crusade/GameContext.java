package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import io.github.elderpath_crusade.assets.AssetService;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.ecs.systems.MovementSystem;
import io.github.elderpath_crusade.ecs.systems.AttackSystem;
import io.github.elderpath_crusade.ecs.systems.DeathSystem;
import io.github.elderpath_crusade.ecs.systems.HealSystem;
import io.github.elderpath_crusade.ecs.systems.ModifierResolutionSystem;
import io.github.elderpath_crusade.ecs.systems.PassiveModifierSystem;
import io.github.elderpath_crusade.ecs.systems.PieceSyncSystem;
import io.github.elderpath_crusade.ecs.systems.PlayerSystem;
import io.github.elderpath_crusade.ecs.systems.CombatSystem;
import io.github.elderpath_crusade.ecs.systems.AbilityResolverSystem;
import io.github.elderpath_crusade.ecs.systems.PieceRenderSystem;
import io.github.elderpath_crusade.ecs.systems.TurnSystem;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.bot.BotManager;
import io.github.elderpath_crusade.game.DeckManager;
import io.github.elderpath_crusade.rendering.FontManager;
import io.github.elderpath_crusade.game.GameManager;
import io.github.elderpath_crusade.game.GameModeManager;
import io.github.elderpath_crusade.rendering.GraphicsManager;
import io.github.elderpath_crusade.rendering.highlight.HighlightManager;
import io.github.elderpath_crusade.config.InfoDataManager;
import io.github.elderpath_crusade.input.InputManager;
import io.github.elderpath_crusade.input.InteractionManager;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.match.MatchClock;
import io.github.elderpath_crusade.audio.MusicManager;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.rendering.RenderPipeline;
import io.github.elderpath_crusade.multiplayer.net.OnlineMatchSession;
import io.github.elderpath_crusade.rooms.RoomManager;
import io.github.elderpath_crusade.server.GameServer;
import io.github.elderpath_crusade.match.CardRules;
import io.github.elderpath_crusade.config.SettingsManager;
import io.github.elderpath_crusade.rendering.ShaderManager;
import io.github.elderpath_crusade.audio.SoundManager;
import io.github.elderpath_crusade.rendering.TextureManager;
import io.github.elderpath_crusade.game.TurnManager;
import io.github.elderpath_crusade.game.WinConditionManager;
import io.github.elderpath_crusade.rendering.ZIndexRegistry;
import io.github.elderpath_crusade.session.GameSession;
import io.github.elderpath_crusade.session.LocalControl;
import io.github.elderpath_crusade.state.GameStateMachine;
import io.github.elderpath_crusade.interaction.LocalUiPickResponder;
import io.github.elderpath_crusade.match.PickRouter;
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
    @Getter private final GameServer gameServer;
    @Getter private final CardRules cardRules;
    @Getter private final LocalControl localControl;
    @Getter private final OnlineMatchSession onlineMatch;
    @Getter private final MatchClock matchClock;
    /** Routes the picks game logic asks players for (local UI / bot / remote client). */
    @Getter private final PickRouter pickRouter;
    /** True for a headless (server/test) context: no render system, nothing that needs a GL context. */
    @Getter private final boolean headless;
    /**
     * True for a game client of a match server (see createClient): it renders and sends commands
     * but never simulates — no gameplay systems, no simulation services.
     */
    @Getter private final boolean client;
    @Getter private Board activeBoard;
    /** The logical board of the active match — what game logic reads; activeBoard is its view. */
    @Getter private BoardGrid activeGrid;
    @Getter private GameSession activeSession;

    private enum Kind { WINDOWED, HEADLESS, CLIENT }

    private GameContext(Kind kind) {
        this.headless = kind == Kind.HEADLESS;
        this.client = kind == Kind.CLIENT;
        boolean simulates = kind != Kind.CLIENT;
        this.eventBus = TypedEventBus.get();
        this.stateMachine = new GameStateMachine(this);
        this.ecsEngine = new Engine();
        this.combatSystem = simulates ? new CombatSystem() : null;
        this.pieceRenderSystem = headless ? null : new PieceRenderSystem();
        if (simulates) {
            this.ecsEngine.addSystem(new TurnSystem());
            this.ecsEngine.addSystem(new PlayerSystem());
            this.ecsEngine.addSystem(new PieceSyncSystem());
        }
        // The spatial index (no rules) — board views look pieces up through it on a client too.
        this.ecsEngine.addSystem(new GridIndexSystem());
        if (simulates) {
            this.ecsEngine.addSystem(new PassiveModifierSystem());
            this.ecsEngine.addSystem(new ModifierResolutionSystem());
            this.ecsEngine.addSystem(new MovementSystem());
            this.ecsEngine.addSystem(new AttackSystem());
            this.ecsEngine.addSystem(this.combatSystem);
            this.ecsEngine.addSystem(new DeathSystem());
            this.ecsEngine.addSystem(new HealSystem());
        }
        if (!headless) this.ecsEngine.addSystem(this.pieceRenderSystem);
        if (simulates) this.ecsEngine.addSystem(new AbilityResolverSystem());
        this.assets = new AssetService();
        this.turnManager = simulates ? new TurnManager() : null;
        this.playerManager = simulates ? new PlayerManager() : null;
        this.botManager = simulates ? new BotManager() : null;
        this.winConditionManager = simulates ? new WinConditionManager() : null;
        this.gameManager = new GameManager();
        this.graphicsManager = new GraphicsManager();
        this.inputManager = new InputManager();
        this.highlightManager = new HighlightManager();
        this.interactionManager = new InteractionManager();
        this.gameModeManager = new GameModeManager();
        this.deckManager = new DeckManager();
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
        this.gameServer = simulates ? new GameServer() : null;
        this.cardRules = simulates ? new CardRules() : null;
        this.localControl = simulates ? new LocalControl() : null;
        this.onlineMatch = simulates ? new OnlineMatchSession() : null;
        this.matchClock = simulates ? new MatchClock() : null;
        this.pickRouter = simulates ? new PickRouter() : null;
        // Headless: no screen to prompt on — a human slot's picks need a remote (or test) responder.
        if (kind == Kind.WINDOWED) this.pickRouter.setLocalResponder(new LocalUiPickResponder());
    }

    public static GameContext create() {
        instance = new GameContext(Kind.WINDOWED);
        return instance;
    }

    /**
     * A context for running a match with no window — a headless server or a test. The
     * simulation (ECS gameplay systems, turns, players, cards, bots, win conditions) is fully
     * functional; nothing that renders is registered.
     */
    public static GameContext createHeadless() {
        instance = new GameContext(Kind.HEADLESS);
        return instance;
    }

    /**
     * A game client's context: the match is run by a match server (see ClientMatchSession), so
     * this context only renders and sends commands. Its ECS engine holds view entities mirrored
     * from the server and runs no gameplay system — only the spatial index (GridIndexSystem) and
     * PieceRenderSystem. Simulation services are absent (null): GameServer, CardRules,
     * LocalControl, TurnManager, PlayerManager, BotManager, WinConditionManager, PickRouter,
     * MatchClock, CombatSystem and the prototype OnlineMatchSession — so nothing on a client
     * can ever decide a game rule.
     */
    public static GameContext createClient() {
        instance = new GameContext(Kind.CLIENT);
        return instance;
    }

    /**
     * Static accessor for migration. Use this when constructor injection isn't yet available.
     * Will be removed once all callers receive GameContext via constructor.
     */
    public static GameContext get() {
        return instance;
    }

    /** Sets the active board view and its logical grid. */
    public void setActiveBoard(Board board) {
        this.activeBoard = board;
        this.activeGrid = board != null ? board.getGrid() : null;
    }

    /** Sets the logical grid alone — for a headless match with no board view. */
    public void setActiveGrid(BoardGrid grid) {
        this.activeGrid = grid;
    }

    public void clearBoard() {
        this.activeBoard = null;
        this.activeGrid = null;
    }

    public void setActiveSession(GameSession session) {
        this.activeSession = session;
    }

    public void clearSession() {
        this.activeSession = null;
    }
}
