package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.match.ActionResult.Reason;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.Condition;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.data.SpellDefinition;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.factory.PieceFactory;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.CardDrawnEvent;
import io.github.elderpath_crusade.events.CardPlayedEvent;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game.PlayerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless coverage of card-play rules: no renderable Card/Hand/Deck/Board is constructed
 * (or class-loaded) — hands and decks are pure state, the board is a BoardGrid.
 */
class CardRulesTest {
    private static final PieceDefinition WOLF = new PieceDefinition("Wolf", 2, 3, 1, 1, 1, List.of());

    private final List<GameEvent> events = new ArrayList<>();
    private BoardGrid grid;
    private CardRules rules;
    private PlayerManager.PlayerState p1;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        GameContext.get().getTurnManager().reset(); // P1 to act
        grid = new BoardGrid(7, 5);
        GameContext.get().setActiveGrid(grid);

        PieceRegistry.register(WOLF);
        CardCatalog.clear();
        CardCatalog.registerSummon("Wolf", "Wolf");
        CardCatalog.registerSpell("Fireball", new SpellDefinition("Fireball", "", 3,
                new SpellDefinition.TargetingSpec(List.of(new Condition("IsEnemy", Map.of("target", "$target")))),
                List.of(new EffectNode("Damage", Map.of("target", "$chosen", "amount", 2)))));
        CardCatalog.registerSpell("Insight", new SpellDefinition("Insight", "", 1, null,
                List.of(new EffectNode("DrawCard", Map.of()))));

        rules = GameContext.get().getCardRules();
        rules.setPieceSpawner((def, owner, row, col) -> PieceFactory.createFromDefinition(
                GameContext.get().getEcsEngine(), def, owner, UUID.randomUUID().toString(), row, col));

        PlayerManager pm = GameContext.get().getPlayerManager();
        DeckState deck = pm.setDeck(PieceAlignment.P1, List.of("Wolf", "Fireball", "Insight", "Wolf"), new Random(1));
        deck.draw(); // P1-0 Wolf
        deck.draw(); // P1-1 Fireball
        deck.draw(); // P1-2 Insight — P1-3 Wolf stays in the draw pile
        pm.setDeck(PieceAlignment.P2, List.of("Wolf"), new Random(1)).draw(); // P2-0 Wolf
        p1 = pm.get(PieceAlignment.P1);
        p1.setMana(10);
        pm.get(PieceAlignment.P2).setMana(10);

        TypedEventBus.get().register(PieceSpawnedEvent.class, events::add);
        TypedEventBus.get().register(CardPlayedEvent.class, events::add);
        TypedEventBus.get().register(CardDrawnEvent.class, events::add);
    }

    @AfterEach
    void tearDown() {
        CardCatalog.clear();
    }

    private Entity placeEnemy(int row, int col) {
        Entity e = PieceFactory.createFromDefinition(GameContext.get().getEcsEngine(), WOLF, PieceAlignment.P2,
                UUID.randomUUID().toString(), row, col);
        grid.addEntity(row, col, e);
        return e;
    }

    // --- Summons ---

    @Test
    void playSummon_success_spawnsSpendsManaAndPlaysCard() {
        assertEquals(Reason.OK, rules.playSummon(PieceAlignment.P1, "P1-0", 0, 2).reason());

        Entity piece = grid.getEntityAt(0, 2);
        assertNotNull(piece);
        assertEquals(8, p1.getMana());
        assertNull(p1.hand.find("P1-0"));
        assertTrue(p1.deck.getDiscardPile().stream().anyMatch(c -> c.instanceId().equals("P1-0")));

        String pieceId = EntityUtils.getId(piece);
        assertEquals(List.of(
                new PieceSpawnedEvent(pieceId, PieceAlignment.P1, 0, 2),
                new CardPlayedEvent("Wolf", PieceAlignment.P1, 0, 2, pieceId, "P1-0")), events);
    }

    @Test
    void playSummon_notYourTurn_rejected() {
        assertEquals(Reason.NOT_YOUR_TURN, rules.playSummon(PieceAlignment.P2, "P2-0", 6, 2).reason());
        assertNull(grid.getEntityAt(6, 2));
    }

    @Test
    void playSummon_cardNotInHand_rejected() {
        assertEquals(Reason.CARD_NOT_IN_HAND, rules.playSummon(PieceAlignment.P1, "P1-3", 0, 2).reason()); // still in the draw pile
        assertEquals(Reason.CARD_NOT_IN_HAND, rules.playSummon(PieceAlignment.P1, "P2-0", 0, 2).reason()); // the opponent's card
        assertEquals(10, p1.getMana());
    }

    @Test
    void playSummon_notEnoughMana_rejectedAndKeepsCard() {
        p1.setMana(1);
        assertEquals(Reason.NOT_ENOUGH_MANA, rules.playSummon(PieceAlignment.P1, "P1-0", 0, 2).reason());
        assertNotNull(p1.hand.find("P1-0"));
        assertNull(grid.getEntityAt(0, 2));
        assertEquals(1, p1.getMana());
    }

    @Test
    void playSummon_outsideHomeRow_rejected() {
        assertEquals(Reason.INVALID_SUMMON_TARGET, rules.playSummon(PieceAlignment.P1, "P1-0", 3, 2).reason());
        assertEquals(Reason.INVALID_SUMMON_TARGET, rules.playSummon(PieceAlignment.P1, "P1-0", 6, 2).reason()); // P2's home row
        assertEquals(10, p1.getMana());
    }

    @Test
    void playSummon_occupiedTile_rejected() {
        placeEnemy(0, 2);
        assertEquals(Reason.INVALID_SUMMON_TARGET, rules.playSummon(PieceAlignment.P1, "P1-0", 0, 2).reason());
        assertEquals(10, p1.getMana());
    }

    @Test
    void playSummon_spellCard_rejected() {
        assertEquals(Reason.WRONG_CARD_TYPE, rules.playSummon(PieceAlignment.P1, "P1-1", 0, 2).reason());
    }

    // --- Spells ---

    @Test
    void playSpell_targeted_appliesEffectsSpendsManaAndPlaysCard() {
        Entity enemy = placeEnemy(4, 2);

        assertEquals(Reason.OK, rules.playSpell(PieceAlignment.P1, "P1-1", 4, 2).reason());

        assertEquals(1, EntityUtils.getCurrentHealth(enemy));
        assertEquals(7, p1.getMana());
        assertNull(p1.hand.find("P1-1"));
        assertEquals(List.of(new CardPlayedEvent("Fireball", PieceAlignment.P1, 4, 2, null, "P1-1")), events);
    }

    @Test
    void playSpell_targetFailsCondition_rejected() {
        Entity friendly = PieceFactory.createFromDefinition(GameContext.get().getEcsEngine(), WOLF, PieceAlignment.P1,
                UUID.randomUUID().toString(), 3, 3);
        grid.addEntity(3, 3, friendly);

        assertEquals(Reason.INVALID_SPELL_TARGET, rules.playSpell(PieceAlignment.P1, "P1-1", 3, 3).reason());
        assertEquals(3, EntityUtils.getCurrentHealth(friendly));
        assertEquals(10, p1.getMana());
        assertNotNull(p1.hand.find("P1-1"));
    }

    @Test
    void playSpell_targetedWithoutTargetOrOnEmptyTile_rejected() {
        assertEquals(Reason.INVALID_SPELL_TARGET, rules.playSpell(PieceAlignment.P1, "P1-1", null, null).reason());
        assertEquals(Reason.INVALID_SPELL_TARGET, rules.playSpell(PieceAlignment.P1, "P1-1", 4, 4).reason());
        assertEquals(Reason.INVALID_SPELL_TARGET, rules.playSpell(PieceAlignment.P1, "P1-1", 99, 99).reason());
        assertEquals(10, p1.getMana());
    }

    @Test
    void playSpell_untargeted_runsEffectsThenPlaysCard() {
        assertEquals(Reason.OK, rules.playSpell(PieceAlignment.P1, "P1-2", null, null).reason());

        assertEquals(9, p1.getMana());
        assertNull(p1.hand.find("P1-2"));
        assertNotNull(p1.hand.find("P1-3"), "DrawCard effect drew the next card");
        assertEquals(List.of(
                new CardDrawnEvent(PieceAlignment.P1, "P1-3", "Wolf", 4),
                new CardPlayedEvent("Insight", PieceAlignment.P1, -1, -1, null, "P1-2")), events);
    }

    @Test
    void playSpell_summonCard_rejected() {
        assertEquals(Reason.WRONG_CARD_TYPE, rules.playSpell(PieceAlignment.P1, "P1-0", null, null).reason());
    }

    // --- UI gating queries ---

    @Test
    void canPlay_requiresTurnAndMana() {
        assertTrue(rules.canPlay(PieceAlignment.P1, "Wolf"));
        assertTrue(rules.canPlay(PieceAlignment.P1, "Fireball"));
        assertFalse(rules.canPlay(PieceAlignment.P2, "Wolf"));
        assertFalse(rules.canPlay(PieceAlignment.P1, "NoSuchCard"));
        p1.setMana(2);
        assertTrue(rules.canPlay(PieceAlignment.P1, "Wolf"));
        assertFalse(rules.canPlay(PieceAlignment.P1, "Fireball"));
    }

    @Test
    void isValidSpellTarget_untargetedOrUnknownSpell_false() {
        placeEnemy(4, 2);
        assertTrue(rules.isValidSpellTarget(PieceAlignment.P1, "Fireball", 4, 2));
        assertFalse(rules.isValidSpellTarget(PieceAlignment.P1, "Insight", 4, 2));
        assertFalse(rules.isValidSpellTarget(PieceAlignment.P1, "Wolf", 4, 2));
    }

    @Test
    void isValidSummonTarget_usesAbsoluteHomeRows() {
        assertTrue(rules.isValidSummonTarget(PieceAlignment.P1, 0, 0));
        assertFalse(rules.isValidSummonTarget(PieceAlignment.P1, 6, 0));
        assertTrue(rules.isValidSummonTarget(PieceAlignment.P2, 6, 0));
    }
}
