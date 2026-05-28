package io.github.elderpath_crusade.data;

import java.util.List;

public record PieceDefinition(String id, int cost, int health, int damage, int speed, int actions, List<String> abilities) {}
