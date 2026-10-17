package io.github.elderpath_crusade.multiplayer.net;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.ActionDef;
import io.github.elderpath_crusade.abilities.data.Cost;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.TargetSelector;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.ecs.components.AbilityInstanceComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.factory.PieceFactory;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.PickRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BUG-022: the online guest can use actionable abilities — it asks the host to activate one,
 * the host relays the resulting pick to the guest, and the guest's answer resolves it on the
 * host (real TCP, ephemeral port).
 */
class GuestAbilityOverNetworkTest {
    private static final AbilityDefinition ZAP = new AbilityDefinition("Zap", "Zap", Map.of(), List.of(),
            List.of(new ActionDef(List.of(new Cost("Action", 1)), new TargetSelector("ChooseEnemy", Map.of("range", 2)),
                    List.of(new EffectNode("Damage", Map.of("target", "$chosen", "amount", 2))))),
            List.of());

    private GameHost host;
    private GameClient client;
    private BoardGrid grid;

    @BeforeEach
    void setUp() throws Exception {
        TypedEventBus.get().clear();
        GameContext.create();
        GameContext.get().getTurnManager().setCurrentPlayerForReplica(PieceAlignment.P2); // the guest's turn
        grid = new BoardGrid(7, 5);
        GameContext.get().setActiveGrid(grid);

        host = new GameHost();
        host.start(0);
        client = new GameClient("localhost", host.getPort());
        awaitTrue(() -> host.hasGuest() && client.isConnected(), "connection established");
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (host != null) host.stop();
    }

    @Test
    void guestActivatesAbility_answersTheRelayedPick_effectResolvesOnHost() throws InterruptedException {
        Entity mage = spawn("mage", PieceAlignment.P2, 5, 2, ZAP);
        Entity target = spawn("target", PieceAlignment.P1, 4, 2);

        List<PickRequest> prompts = new ArrayList<>();
        client.setPickListeners(prompts::add, id -> { });
        client.send(new NetworkCommand.ActivateAbility("mage", 0));

        awaitTrue(() -> {
            host.update();
            client.update();
            return !prompts.isEmpty();
        }, "host relayed the pick to the guest");
        PickRequest prompt = prompts.get(0);
        assertEquals(PieceAlignment.P2, prompt.owner());
        assertEquals(List.of(new Coord(4, 2)), prompt.candidates());

        client.send(new NetworkCommand.AnswerPick(prompt.pickId(), false, 4, 2));
        awaitTrue(() -> {
            host.update();
            return target.getComponent(StatsComponent.class).currentHealth == 1;
        }, "the guest's answer resolved the ability on the host");
        assertEquals(0, mage.getComponent(StatsComponent.class).remainingActions);
    }

    @Test
    void guestAnswerNotAmongCandidates_isAskedAgain() throws InterruptedException {
        spawn("mage", PieceAlignment.P2, 5, 2, ZAP);
        Entity target = spawn("target", PieceAlignment.P1, 4, 2);

        List<PickRequest> prompts = new ArrayList<>();
        client.setPickListeners(prompts::add, id -> { });
        client.send(new NetworkCommand.ActivateAbility("mage", 0));
        awaitTrue(() -> { host.update(); client.update(); return prompts.size() == 1; }, "first prompt");

        client.send(new NetworkCommand.AnswerPick(prompts.get(0).pickId(), false, 0, 0));
        awaitTrue(() -> { host.update(); client.update(); return prompts.size() == 2; }, "re-asked after a bad answer");
        assertEquals(prompts.get(0), prompts.get(1));
        assertEquals(3, target.getComponent(StatsComponent.class).currentHealth);
    }

    private Entity spawn(String id, PieceAlignment owner, int row, int col, AbilityDefinition... abilities) {
        Entity piece = PieceFactory.createFromDefinition(GameContext.get().getEcsEngine(),
                new PieceDefinition("Wolf", 1, 3, 1, 1, 1, List.of()), owner, id, row, col);
        piece.getComponent(StatsComponent.class).remainingActions = 1;
        AbilityInstanceComponent aic = new AbilityInstanceComponent();
        for (AbilityDefinition a : abilities) aic.addAbility(a);
        piece.add(aic);
        grid.addEntity(row, col, piece);
        return piece;
    }

    private void awaitTrue(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() - deadline < 0) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(20);
        }
        fail("Timed out waiting for: " + description);
    }
}
