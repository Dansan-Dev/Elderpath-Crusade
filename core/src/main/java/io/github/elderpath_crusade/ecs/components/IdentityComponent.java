package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;

import java.util.UUID;

/**
 * Identity: unique ID and display name.
 */
public class IdentityComponent implements Component {
    public String id;
    public String name;

    public IdentityComponent set(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        return this;
    }

    public IdentityComponent set(String id, String name) {
        this.id = id;
        this.name = name;
        return this;
    }
}
