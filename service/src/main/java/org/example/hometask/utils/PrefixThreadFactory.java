package org.example.hometask.utils;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class PrefixThreadFactory implements ThreadFactory {

    @NotNull
    private final String threadNamePrefix;

    private final AtomicLong sequence = new AtomicLong();

    public PrefixThreadFactory(@NotNull String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    @Override
    public Thread newThread(@NotNull Runnable runnable) {
        return new Thread(runnable, threadNamePrefix + sequence.incrementAndGet());
    }
}
