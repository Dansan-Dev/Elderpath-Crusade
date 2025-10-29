### Abilities System (design, usage, and verification)

This section documents the project’s Abilities architecture: how abilities are modeled, attached to pieces, how passives affect stats, how actionable interactions work, and the small helpers available to keep ability code lean and consistent.

#### 1. Core model
- Ability interfaces live under `core/src/main/java/io/github/forest_of_dreams/abilities`.
  - `Ability`: name, description, type, lifecycle hooks `onAttach(owner)` and `onDetach()`.
  - `AbilityType`: `TRIGGERED`, `ACTIONABLE`, `PASSIVE`.
  - `TriggeredAbility`: optional hooks: `onOwnerSpawned`, `onOwnerMoved`, `onOwnerAttack`, `onOwnerDamaged`, `onOwnerDied`, `onTurnStarted`, `onTurnEnded`, plus `onGameEvent(GameEvent)`.
  - `ActionableAbility`: exposes `getClickableEffectData()` (selection flow) and `execute(entities)` where `entities` is a `HashMap<Integer, CustomBox>` using the indexing contract below.
  - `PassiveAbility`: returns a `StatsModifier` (owned by the ability) and may expose a condition (see passive strategy below).
- Event forwarding:
  - `AbilityRelay` subscribes to all `GameEventType` and forwards them to all `TriggeredAbility` instances on living pieces across active Boards.

#### 2. Piece integration and lifecycle
- Pieces attach abilities in their constructors (e.g., `Wolf`, `WarpMage`, `Shockling`).
- `MonsterGamePiece` maintains an internal `List<Ability>` and:
  - Calls `onAttach` when an ability is added, and `onDetach` when removed/cleanup.
  - Notifies abilities of lifecycle events: spawned, moved, attack, damaged, died, and turn start/end.
- On piece death, `detachAllAbilities()` is invoked before board removal.

#### 3. Passive modifiers (stats auras/buffs)
- Each `MonsterGamePiece` owns a `StatsAccumulator` which holds active `StatsModifier` instances affecting the piece.
- Effective stats (`getEffectiveDamage/Speed/Actions/MaxHealth/Cost`) iterate the accumulator, not abilities directly.
- Abilities add or remove their `StatsModifier` to/from recipients based on their own logic, keeping reads fast and deterministic.
  - Example: `PackHunterAbility` (Wolf aura) owns a single `StatsModifier` (`+1 damage`) and, on relevant events (spawn/move/death), applies it to adjacent allied `WolfCub` pieces and removes it when no longer eligible. It also invokes `StatsModifier.clear()` on detach to ensure cleanup from all recipients.

#### 4. Actionable interactions
- Actionable abilities declare a selection flow via `ClickableEffectData` (e.g., `getMulti(targetType, n)` for exactly `n` targets).
- The `InteractionManager` maintains an entities map with the following index contract:
  - `0`: the source `Clickable` (the initiating UI/button or plot)
  - `1..k`: selected targets in order
- Ability UI:
  - `AbilityPopup` shows small “bubble” buttons above eligible current-player pieces (sticky hover corridor allows moving from piece to bubble without hiding it). Clicking a bubble starts the ability’s selection and, on successful execution, consumes an action.
- Example: `DisplaceAbility` (WarpMage)
  - Flow: select a target `MonsterGamePiece` within range 2 (Chebyshev), then select an adjacent empty plot (cardinal) next to that target. Moves the target there and spends 1 action from the WarpMage.

#### 5. Utilities for ability code
- `AbilityUtils` (abilities package):
  - `emit(GameEventType, Map)` and varargs `emit(GameEventType, Object...)`: convenience for event emission.
  - `getRemainingActions(MonsterGamePiece)`: read remaining actions (falls back to base actions).
  - `spendAction(MonsterGamePiece)`: decrement remaining actions by 1 (never below 0) and emit `ACTION_SPENT`.
  - `dealDamage(MonsterGamePiece target, int amount, MonsterGamePiece source, boolean emitDeathEvent)`: apply damage; if the piece dies, call `die()` and optionally emit `PIECE_DIED`. Returns `true` if the target remains alive.
- `AbilityContext` (abilities package):
  - `getOwnerPos(GamePiece)`: returns the current `Board.Position` of a piece.
  - `getOwnerBoard(GamePiece)`: returns the `Board` a piece resides on.

#### 6. Triggered examples
- `OnSummonShockAbility` (Shockling)
  - On owner spawn: deals 1 damage to cardinally adjacent pieces using `AbilityUtils.dealDamage(...)`. Guarded as one-shot via an internal `executed` flag.
- `PackHunterAbility` (Wolf)
  - Applies +1 damage `StatsModifier` to adjacent allied `WolfCub` recipients; updates on PIECE_MOVED/PIECE_SPAWNED/PIECE_DIED; clears on detach/death.

#### 7. Card rules text (source of truth and wrapping)
- Cards and the large hover preview display ability descriptions taken directly from each ability’s `getDescription()`. Multiple abilities are joined with exactly two newlines (`"\n\n"`).
- Line breaks are provided by the abilities themselves using `\n`. The `Text` UI class fits the text into a defined area using `withWrapBounds(width, height)` and an internal search for the largest font scale that fits.

#### 8. Manual verification checklist
- Wolf aura:
  - Place a Wolf adjacent to a Wolf Cub; the cub’s effective attack should be +1. Move them apart; the bonus disappears.
- WarpMage Displace:
  - Hover the WarpMage; click the “Displace” bubble; select a valid target in range 2, then an adjacent empty plot next to that target. The target moves, and the WarpMage spends 1 action.
- Shockling on-summon:
  - Play Shockling next to other pieces; exactly adjacent (N/E/S/W) pieces take 1 damage; dead pieces are removed; no extra/distant hits occur.

#### 9. Implementation notes & safeguards
- Avoid per-frame allocations in abilities. Reuse `StatsModifier` and button instances; respond to events to maintain state.
- Use `StatsModifier.clear()` on `onDetach()` to remove modifiers from all recipients.
- Use `AbilityUtils.spendAction(...)` to keep action spending uniform and eventful.
- Use `AbilityContext.getOwnerPos/Board` to locate context rather than re-scanning boards.

#### 10. Adding a new ability (quick recipe)
1) Decide the type: `PASSIVE`, `TRIGGERED`, or `ACTIONABLE`.
2) Implement the interface:
   - Passive: own a single `StatsModifier` and add/remove it from recipients’ `StatsAccumulator` on relevant events.
   - Triggered: implement needed hooks; guard against double execution if appropriate.
   - Actionable: define `ClickableEffectData` and perform validation inside `execute(...)`.
3) Attach the ability in the piece’s constructor (`this.addAbility(new YourAbility(...))`).
4) If user-facing, provide `getDescription()` with explicit `\n` line breaks for the card/preview.
5) Verify using the checklist above.
