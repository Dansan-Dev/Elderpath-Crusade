package io.github.elderpath_crusade.ecs;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * Static helpers to read common data from entities.
 * Replaces piece.getEffectiveDamage(), piece.getAlignment(), etc.
 */
public final class EntityUtils {
    private static final ComponentMapper<StatsComponent> STATS = ComponentMapper.getFor(StatsComponent.class);
    private static final ComponentMapper<ComputedStatsComponent> COMPUTED = ComponentMapper.getFor(ComputedStatsComponent.class);
    private static final ComponentMapper<PositionComponent> POS = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<AlignmentComponent> ALIGN = ComponentMapper.getFor(AlignmentComponent.class);
    private static final ComponentMapper<IdentityComponent> ID = ComponentMapper.getFor(IdentityComponent.class);
    private static final ComponentMapper<StunComponent> STUN = ComponentMapper.getFor(StunComponent.class);

    private EntityUtils() {}

    public static int getDamage(Entity e) {
        ComputedStatsComponent c = COMPUTED.get(e);
        return c != null ? c.damage : (STATS.has(e) ? STATS.get(e).damage : 0);
    }

    public static int getSpeed(Entity e) {
        ComputedStatsComponent c = COMPUTED.get(e);
        return c != null ? c.speed : (STATS.has(e) ? STATS.get(e).speed : 0);
    }

    public static int getActions(Entity e) {
        ComputedStatsComponent c = COMPUTED.get(e);
        return c != null ? c.actions : (STATS.has(e) ? STATS.get(e).actions : 0);
    }

    public static int getMaxHealth(Entity e) {
        ComputedStatsComponent c = COMPUTED.get(e);
        return c != null ? c.maxHealth : (STATS.has(e) ? STATS.get(e).maxHealth : 0);
    }

    public static int getCost(Entity e) {
        ComputedStatsComponent c = COMPUTED.get(e);
        return c != null ? c.cost : (STATS.has(e) ? STATS.get(e).cost : 0);
    }

    public static int getRange(Entity e) {
        ComputedStatsComponent c = COMPUTED.get(e);
        return c != null ? c.range : 0;
    }

    public static int getCurrentHealth(Entity e) {
        StatsComponent s = STATS.get(e);
        return s != null ? s.currentHealth : 0;
    }

    public static int getRemainingActions(Entity e) {
        StatsComponent s = STATS.get(e);
        return s != null ? s.remainingActions : 0;
    }

    public static PieceAlignment getAlignment(Entity e) {
        AlignmentComponent a = ALIGN.get(e);
        return a != null ? a.alignment : PieceAlignment.NEUTRAL;
    }

    public static int getRow(Entity e) {
        PositionComponent p = POS.get(e);
        return p != null ? p.row : -1;
    }

    public static int getCol(Entity e) {
        PositionComponent p = POS.get(e);
        return p != null ? p.col : -1;
    }

    public static String getId(Entity e) {
        IdentityComponent id = ID.get(e);
        return id != null ? id.id : "";
    }

    public static String getName(Entity e) {
        IdentityComponent id = ID.get(e);
        return id != null ? id.name : "";
    }

    public static boolean isStunned(Entity e) {
        StunComponent s = STUN.get(e);
        return s != null && s.isStunned();
    }

    public static boolean isExhausted(Entity e) {
        return getRemainingActions(e) <= 0;
    }

    public static boolean isDead(Entity e) {
        return getCurrentHealth(e) <= 0;
    }
}
