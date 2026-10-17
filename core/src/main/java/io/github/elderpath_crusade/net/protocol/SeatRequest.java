package io.github.elderpath_crusade.net.protocol;

/** Which side a client asks to play in its Hello. */
public enum SeatRequest {
    P1,
    P2,
    /** The first free human side. */
    ANY
}
