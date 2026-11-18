package io.github.elderpath_crusade.ui_objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TitleBannerTest {
    @Test
    void scaleFor_standardWindows_isAWholeMultipleOfTheNativeArt() {
        assertEquals(2, TitleBanner.scaleFor(1280)); // 576x144, every art pixel 2x2
        assertEquals(3, TitleBanner.scaleFor(1920));
        assertEquals(1, TitleBanner.scaleFor(640));
        assertEquals(1, TitleBanner.scaleFor(100)); // never 0
    }

    @Test
    void artFile_isTwiceTheNativeGrid() {
        assertEquals(TitleBanner.NATIVE_WIDTH * 2, TitleBanner.ART_WIDTH);
        assertEquals(TitleBanner.NATIVE_HEIGHT * 2, TitleBanner.ART_HEIGHT);
    }
}
