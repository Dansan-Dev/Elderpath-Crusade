package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game.DeckManager;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.CardFactory;
import io.github.elderpath_crusade.game_objects.cards.DraftCard;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.MatchSetup;
import io.github.elderpath_crusade.match.SyntheticCardNames;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.run.RunCardModification;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.ButtonList;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.HoverUtils;
import io.github.elderpath_crusade.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The one way every "choose a card" screen of a run shows its choices (UI-011, with UI-010's layout): the player
 * sees WHICH card, with its real stats and rules text — a modified card included — not just its name.
 * <ul>
 *   <li>{@value #MAX_AS_CARDS} options or fewer: the cards themselves, side by side, click one to choose it
 *       (an optional caption under each, e.g. a recruit's upgrade).</li>
 *   <li>More: a button list laid out in as many columns as it needs ({@link ButtonList#alignButtonsFitting}),
 *       and the card of the hovered button shown large on the right.</li>
 * </ul>
 * Not GL-testable here; {@link #layout} is called by the room from its own layout/resize code. If a card can't be
 * built for display the picker falls back to plain buttons, so a choice is never lost.
 */
final class CardPicker {
    /** One choice: {@code cardName} is the {@link CardCatalog} name to draw (a synthetic one for a modified card). */
    record Option(String label, String caption, String cardName, Runnable onPick) {}

    static final int MAX_AS_CARDS = 3;
    private static final int CARD_WIDTH = 160;
    private static final int CARD_HEIGHT = 256;
    private static final int CARD_GAP = 40;
    private static final int CAPTION_SPACE = 34;
    private static final int LIST_BUTTON_WIDTH = 220;
    private static final int LIST_BUTTON_HEIGHT = 60;
    private static final int LIST_SPACING = 70;
    private static final int LIST_SHIFT_LEFT = 150;
    private static final int PREVIEW_RIGHT_MARGIN = 190;
    private static final int BOTTOM_MARGIN = 90;

    private final List<Option> options;
    private final boolean asCards;
    private boolean picked;

    // cards mode
    private final List<Hand> hands = new ArrayList<>();
    private final List<Text> captions = new ArrayList<>();

    // list mode
    private final ButtonList list = new ButtonList();
    private final List<Button> buttons = new ArrayList<>();
    private Hand previewHand;
    private final Map<Integer, DraftCard> previewCache = new HashMap<>();
    private int shown = -1;

    CardPicker(Consumer<Renderable> add, List<Option> options) {
        this.options = List.copyOf(options);
        List<DraftCard> cards = new ArrayList<>();
        boolean allCardsBuild = this.options.size() <= MAX_AS_CARDS;
        if (allCardsBuild) {
            for (int i = 0; i < this.options.size(); i++) {
                final int index = i;
                DraftCard card = createCard(this.options.get(i).cardName(), () -> choose(index));
                if (card == null) {
                    allCardsBuild = false;
                    break;
                }
                cards.add(card);
            }
        }
        asCards = allCardsBuild && !cards.isEmpty();
        if (asCards) {
            for (int i = 0; i < cards.size(); i++) {
                Hand hand = new Hand(0, 0, CARD_WIDTH, CARD_HEIGHT, 0);
                hand.addCard(cards.get(i));
                hands.add(hand);
                add.accept(hand);
                Text caption = new Text(options.get(i).caption() == null ? " " : options.get(i).caption(), FontType.SILKSCREEN, 0, 0, 0,
                        ColorSettings.TEXT_DEFAULT.getColor()).withFontSize(FontSize.CAPTION);
                captions.add(caption);
                add.accept(caption);
            }
        } else {
            for (int i = 0; i < this.options.size(); i++) {
                final int index = i;
                Button button = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), this.options.get(i).label(), FontType.SILKSCREEN,
                                FontSize.BUTTON_DEFAULT.getSize(), 0, 0, LIST_BUTTON_WIDTH, LIST_BUTTON_HEIGHT, 0)
                        .withOnClick((e) -> choose(index), ClickableEffectData.getImmediate())
                        .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                        .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                        .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
                buttons.add(button);
                list.addButton(button);
                add.accept(button);
            }
            previewHand = new Hand(0, 0, CARD_WIDTH, CARD_HEIGHT, 0);
            add.accept(previewHand);
            add.accept(new Ticker(delta -> followHover()));
            showPreview(0);
        }
    }

    private void choose(int index) {
        if (picked) return;
        picked = true;
        options.get(index).onPick().run();
    }

    private static DraftCard createCard(String cardName, Runnable onClick) {
        try {
            if (CardCatalog.get(cardName) == null) return null;
            DeckManager.CardCreationParams params = new DeckManager.CardCreationParams(
                    PieceAlignment.P1, 0, 0, CARD_WIDTH, CARD_HEIGHT, 0);
            Card real = CardFactory.create(cardName, null, params);
            real.showFront();
            DraftCard card = new DraftCard(real, (e) -> onClick.run());
            card.showFront();
            return card;
        } catch (RuntimeException e) {
            Logger.error("CardPicker", "could not build a card view for " + cardName, e);
            return null;
        }
    }

    private void followHover() {
        for (int i = 0; i < buttons.size(); i++) {
            Box b = buttons.get(i).getBounds();
            if (HoverUtils.isHovered(b.getX(), b.getY(), b.getWidth(), b.getHeight())) {
                showPreview(i);
                return;
            }
        }
    }

    private void showPreview(int index) {
        if (index == shown || index < 0 || index >= options.size()) return;
        DraftCard card = previewCache.containsKey(index) ? previewCache.get(index)
                : cacheAndGet(index);
        for (Card old : new ArrayList<>(previewHand.getCards())) previewHand.removeCard(old);
        shown = index;
        if (card != null) {
            previewHand.addCard(card);
        }
        previewHand.updateBounds();
    }

    private DraftCard cacheAndGet(int index) {
        DraftCard card = createCard(options.get(index).cardName(), () -> { });
        previewCache.put(index, card);
        return card;
    }

    /**
     * Positions everything; call from the room's own layout (construction and resize).
     * {@code topClearance} is how far down from the top of the window the room's header (and flavour line) reach.
     */
    void layout(int topClearance) {
        int screenWidth = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenHeight = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        int centerX = GameContext.get().getSettingsManager().screenSize.getScreenCenter()[0];
        int centerY = GameContext.get().getSettingsManager().screenSize.getScreenCenter()[1];
        int areaTop = screenHeight - topClearance;
        int cardBottom = BOTTOM_MARGIN + CAPTION_SPACE + Math.max(0, (areaTop - BOTTOM_MARGIN - CAPTION_SPACE - CARD_HEIGHT) / 2);

        if (asCards) {
            int total = hands.size() * CARD_WIDTH + (hands.size() - 1) * CARD_GAP;
            int firstCenter = centerX - total / 2 + CARD_WIDTH / 2;
            for (int i = 0; i < hands.size(); i++) {
                int cardCenter = firstCenter + i * (CARD_WIDTH + CARD_GAP);
                Hand hand = hands.get(i);
                hand.setCenterX(cardCenter);
                hand.setBottomY(cardBottom);
                hand.updateBounds();
                Text caption = captions.get(i);
                caption.update();
                caption.getBounds().setX(cardCenter - caption.getBounds().getWidth() / 2);
                caption.getBounds().setY(cardBottom - 8 - caption.getBounds().getHeight());
            }
        } else {
            list.alignButtonsFitting(LIST_SPACING, centerX - LIST_SHIFT_LEFT, centerY);
            previewHand.setCenterX(screenWidth - PREVIEW_RIGHT_MARGIN);
            previewHand.setBottomY(cardBottom);
            previewHand.updateBounds();
        }
    }

    // --- naming helpers: what to draw for a run card ---

    /** The catalog name to draw for a deck/Elder instance: its plain name, or a registered synthetic one if modified. */
    static String cardNameFor(RunCardInstance instance) {
        return cardNameFor(instance.cardName(), instance.modification(), instance.instanceId());
    }

    /**
     * The catalog name to draw {@code baseCardName} with {@code modification} applied. A modified card exists only as a
     * synthetic name (UPG-003) that must be registered before it can be drawn; {@code previewId} stands in for the instance
     * id of a card that doesn't exist yet (a recruit on offer).
     */
    static String cardNameFor(String baseCardName, RunCardModification modification, String previewId) {
        if (modification == null || modification.isEmpty() || !CardCatalog.isSummon(baseCardName)) return baseCardName;
        String synthetic = SyntheticCardNames.encode(baseCardName, previewId);
        try {
            MatchSetup.registerSyntheticCards(Map.of(PieceAlignment.P1, List.of(synthetic)), Map.of(),
                    Map.of(PieceAlignment.P1, Map.of(previewId, modification)));
            return synthetic;
        } catch (RuntimeException e) {
            Logger.error("CardPicker", "could not register the modified card " + synthetic, e);
            return baseCardName;
        }
    }

    boolean showsCards() {
        return asCards;
    }
}
