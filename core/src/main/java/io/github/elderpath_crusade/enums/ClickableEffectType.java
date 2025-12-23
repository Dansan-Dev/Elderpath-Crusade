package io.github.elderpath_crusade.enums;

public enum ClickableEffectType {
    IMMEDIATE,
    MULTI_INTERACTION,
    MULTI_CHOICE_LIMITED_INTERACTION,
    MULTI_CHOICE_UNLIMITED_INTERACTION;

    public String getInstructionText(int requiredOrLimit, int currentSelected) {
        return switch (this) {
            case MULTI_INTERACTION ->
                "Select " + requiredOrLimit + " target" + (requiredOrLimit == 1 ? "" : "s") + " (" + currentSelected + "/" + requiredOrLimit + ")";
            case MULTI_CHOICE_LIMITED_INTERACTION ->
                "Select up to " + requiredOrLimit + " target" + (requiredOrLimit == 1 ? "" : "s") + " (" + currentSelected + ")";
            case MULTI_CHOICE_UNLIMITED_INTERACTION ->
                "Select any number (" + currentSelected + ")";
            case IMMEDIATE -> "";
        };
    }

    public String getConfirmationHint() {
        return switch (this) {
            case MULTI_INTERACTION -> "Right-click to cancel, ESC to pause";
            case MULTI_CHOICE_LIMITED_INTERACTION, MULTI_CHOICE_UNLIMITED_INTERACTION ->
                "Enter to confirm, Right-click to cancel, ESC to pause";
            case IMMEDIATE -> "";
        };
    }
}
