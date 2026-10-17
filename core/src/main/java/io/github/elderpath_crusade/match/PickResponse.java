package io.github.elderpath_crusade.match;

/** A player's answer to a PickRequest: the chosen coordinate, or cancelled (choice == null). */
public record PickResponse(long pickId, Coord choice) {

    public static PickResponse chose(long pickId, Coord choice) {
        return new PickResponse(pickId, choice);
    }

    public static PickResponse cancelled(long pickId) {
        return new PickResponse(pickId, null);
    }

    public boolean isCancelled() {
        return choice == null;
    }
}
