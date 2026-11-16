package io.github.elderpath_crusade.input;

/**
 * The text a text field is editing: a bounded string that accepts typed characters and
 * backspace, with no LibGDX dependency so it can be tested without a window. A masked entry
 * (passwords) shows bullets instead of its characters.
 */
public final class TextEntry {
    private final StringBuilder text = new StringBuilder();
    private final int maxLength;
    private final boolean masked;

    public TextEntry(int maxLength, boolean masked) {
        this.maxLength = maxLength;
        this.masked = masked;
    }

    public TextEntry(String initial, int maxLength, boolean masked) {
        this(maxLength, masked);
        type(initial);
    }

    /** Adds every acceptable character of s (printable, no control characters), up to the limit. */
    public void type(String s) {
        if (s == null) return;
        for (int i = 0; i < s.length(); i++) typeChar(s.charAt(i));
    }

    /** Returns whether the character was accepted. */
    public boolean typeChar(char c) {
        if (c < 32 || c == 127 || Character.isISOControl(c)) return false;
        if (text.length() >= maxLength) return false;
        text.append(c);
        return true;
    }

    public void backspace() {
        if (text.length() > 0) text.setLength(text.length() - 1);
    }

    public void clear() {
        text.setLength(0);
    }

    public String getText() {
        return text.toString();
    }

    public boolean isEmpty() {
        return text.length() == 0;
    }

    public boolean isMasked() {
        return masked;
    }

    /** What to draw: the text, or one bullet per character when masked. */
    public String display() {
        return masked ? "*".repeat(text.length()) : text.toString();
    }
}
