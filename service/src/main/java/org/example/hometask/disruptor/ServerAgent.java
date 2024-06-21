package org.example.hometask.disruptor;

import com.lmax.disruptor.RingBuffer;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import org.agrona.concurrent.Agent;
import org.example.hometask.messages.disruptor.Event;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.example.hometask.ServerConstants.RPC_STREAM;
import static org.example.hometask.ServerConstants.SERVER_URI;

public class ServerAgent implements Agent {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @NotNull
    private final RingBuffer<EventHolder> ringBuffer;

    @NotNull
    private final ServerAdapter serverAdapter;

    /**
     * This queue is intentionally unbounded to prevent deadlocks under heavy load.
     * We have guarantees it will not grow indefinitely.
     */
    @NotNull
    private final ConcurrentLinkedQueue<Event> internalMessagesQueue;

    @NotNull
    private final Subscription subscription;

    public ServerAgent(
            @NotNull Aeron aeron,
            @NotNull RingBuffer<EventHolder> ringBuffer,
            @NotNull ConcurrentHashMap<Integer, Publication> publicationsBySessionId,
            @NotNull ConcurrentLinkedQueue<Event> internalMessagesQueue
    ) {
        this.ringBuffer = ringBuffer;
        this.serverAdapter = new ServerAdapter(aeron, ringBuffer, publicationsBySessionId);
        this.internalMessagesQueue = internalMessagesQueue;
        subscription = aeron.addSubscription(SERVER_URI, RPC_STREAM);
    }

    @Override
    public void onStart() {
        logger.info("Server starting");
    }

    @Override
    public int doWork() throws Exception {

        // internally generated messages have priority over all external messages
        // to make a guarantee the queue will not grow indefinitely under heavy load:

        while (true) {
            final var message = internalMessagesQueue.poll();
            if (message == null) {
                break;
            }
            final long sequence = ringBuffer.next();
            try {
                final var holder = ringBuffer.get(sequence);
                holder.event = message;
            } finally {
                ringBuffer.publish(sequence);
            }
        }

        return subscription.poll(serverAdapter, 1);
    }

    @Override
    public void onClose() {
        logger.info("Server stopping");
    }

    @Override
    public String roleName() {
        return "server";
    }
}
