package io.github.elderpath_crusade.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class or method as requiring game assets (pieces.yaml, etc.).
 * Skipped automatically when assets are absent or testProfile=cloud.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(AssetsCondition.class)
public @interface RequiresAssets {
}
