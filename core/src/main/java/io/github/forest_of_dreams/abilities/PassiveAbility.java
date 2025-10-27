package io.github.forest_of_dreams.abilities;

/**
 * Passive abilities provide ongoing modifiers or rules that influence gameplay while attached.
 * Actual stat modification plumbing will be introduced in a later chunk (effective stats calculation).
 */
public interface PassiveAbility extends Ability {
    @Override
    default AbilityType getType() { return AbilityType.PASSIVE; }
}
