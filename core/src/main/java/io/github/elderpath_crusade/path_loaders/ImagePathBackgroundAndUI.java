package io.github.elderpath_crusade.path_loaders;

import lombok.Getter;

@Getter
public enum ImagePathBackgroundAndUI {
    HOME_NAVBAR("home_navbar.png"),
    HOME_BACKGROUND("home_screen_background.png"),
    /**
     * ART-005: light-grey stone plates behind every color button, drawn as a nine-patch tinted by
     * the button's color. Five near-identical variants (different cracks/chips); each button keeps
     * one, chosen from its label (see Button).
     */
    BUTTON_PLATE_1("ui/button_plate_1.png"),
    BUTTON_PLATE_2("ui/button_plate_2.png"),
    BUTTON_PLATE_3("ui/button_plate_3.png"),
    BUTTON_PLATE_4("ui/button_plate_4.png"),
    BUTTON_PLATE_5("ui/button_plate_5.png"),
    /** Dark wooden panel with a bronze border, drawn as a nine-patch behind text-heavy screens (see TextPanel). */
    TEXT_PANEL("ui/text_panel.png");

    /** The button plate variants, in order. */
    public static final java.util.List<ImagePathBackgroundAndUI> BUTTON_PLATES =
        java.util.List.of(BUTTON_PLATE_1, BUTTON_PLATE_2, BUTTON_PLATE_3, BUTTON_PLATE_4, BUTTON_PLATE_5);

    private final String path;

    ImagePathBackgroundAndUI(String path) {
        this.path = "images/" + path;
    }

}
