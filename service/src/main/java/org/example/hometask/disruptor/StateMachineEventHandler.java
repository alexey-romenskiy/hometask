package org.example.hometask.disruptor;

import com.lmax.disruptor.EventHandler;
import org.example.hometask.Controller;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class StateMachineEventHandler implements EventHandler<EventHolder> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @NotNull
    private final EventContext eventContext;

    @NotNull
    private final Controller controller;

    public StateMachineEventHandler(@NotNull EventContext eventContext, @NotNull Controller controller) {
        this.eventContext = eventContext;
        this.controller = controller;
    }

    @Override
    public void onEvent(@NotNull EventHolder holder, long sequence, boolean endOfBatch) throws Exception {
        final var event = holder.event;
        if (event != null) {
            logger.info("Processing event: {}", event);
            eventContext.holder = holder;
            controller.accept(event);
            logger.info("Result: envelope={} withdrawalRequests={}", holder.messages,
                    holder.withdrawalRequests);
        }
    }
}
