package org.example.hometask.disruptor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.requireNonNull;

public class EventContext {

    @Nullable
    public EventHolder holder;

    @NotNull
    public EventHolder holder() {
        return requireNonNull(holder);
    }
}
