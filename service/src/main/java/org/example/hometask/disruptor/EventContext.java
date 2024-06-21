package org.example.hometask.disruptor;

import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

public class EventContext {

    public EventHolder holder;

    @NotNull
    public EventHolder holder() {
        return requireNonNull(holder);
    }
}
