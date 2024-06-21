package org.example.hometask.disruptor;

import com.lmax.disruptor.ExceptionHandler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DisruptorExceptionHandler implements ExceptionHandler<EventHolder> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void handleEventException(@NotNull Throwable throwable, long sequence, @NotNull EventHolder event) {
        logger.error("Uncaught exception while processing event sequence={}: {}", sequence, event, throwable);
        throw new RuntimeException(throwable);
    }

    @Override
    public void handleOnStartException(@NotNull Throwable throwable) {
        logger.error("Disruptor startup exception", throwable);
    }

    @Override
    public void handleOnShutdownException(@NotNull Throwable throwable) {
        logger.error("Disruptor shutdown exception", throwable);
    }
}
