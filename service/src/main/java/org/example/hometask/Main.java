package org.example.hometask;

import org.example.hometask.disruptor.DisruptorManager;
import org.example.hometask.utils.PrefixThreadFactory;
import org.jetbrains.annotations.NotNull;

public class Main {

    public static void main(@NotNull String[] args) {
        final var disruptorManager = new DisruptorManager();
        Runtime.getRuntime()
                .addShutdownHook(new PrefixThreadFactory("shutdown-").newThread(disruptorManager::shutdown));
        disruptorManager.start();
    }
}
