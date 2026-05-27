package io.github.elderpath_crusade.model.player;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.model.card.DeckModel;
import io.github.elderpath_crusade.model.card.HandModel;

/**
 * Pure model for a player's state during a match.
 */
public class PlayerModel {
    private final PieceAlignment alignment;
    private int mana;
    private DeckModel deck;
    private HandModel hand;

    public PlayerModel(PieceAlignment alignment) {
        this.alignment = alignment;
        this.mana = 0;
    }

    public PieceAlignment getAlignment() { return alignment; }
    public int getMana() { return mana; }
    public DeckModel getDeck() { return deck; }
    public HandModel getHand() { return hand; }

    public void setDeck(DeckModel deck) { this.deck = deck; }
    public void setHand(HandModel hand) { this.hand = hand; }

    public void addMana(int amount) { mana += amount; }
    public boolean spendMana(int amount) {
        if (amount > mana) return false;
        mana -= amount;
        return true;
    }

    public void resetMana() { mana = 0; }
}
