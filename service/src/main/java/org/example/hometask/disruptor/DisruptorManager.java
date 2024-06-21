package org.example.hometask.disruptor;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import io.aeron.Aeron;
import io.aeron.CommonContext;
import io.aeron.Publication;
import io.aeron.driver.MediaDriver;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.example.hometask.Controller;
import org.example.hometask.messages.disruptor.Event;
import org.example.hometask.utils.PrefixThreadFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.lmax.disruptor.dsl.ProducerType.SINGLE;
import static io.aeron.driver.ThreadingMode.SHARED;

public class DisruptorManager {

    private static final int RING_BUFFER_SIZE = 1 << Integer.getInteger("org.example.hometask.ringBufferSize", 10);

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @NotNull
    private final Disruptor<EventHolder> disruptor;

    @NotNull
    private final MediaDriver mediaDriver;

    @NotNull
    private final Aeron aeron;

    @NotNull
    private final AgentRunner serverAgentRunner;

    @NotNull
    private final PublisherEventHandler publisherEventHandler;

    public DisruptorManager() {

        final var internalMessagesQueue = new ConcurrentLinkedQueue<Event>();

        mediaDriver = MediaDriver.launchEmbedded(
                new MediaDriver.Context()
                        .aeronDirectoryName(CommonContext.getAeronDirectoryName() + "-server")
                        .dirDeleteOnStart(true)
                        .dirDeleteOnShutdown(true)
                        .threadingMode(SHARED));

        aeron = Aeron.connect(
                new Aeron.Context()
                        .aeronDirectoryName(mediaDriver.aeronDirectoryName()));

        logger.info("Dir {}", mediaDriver.aeronDirectoryName());

        disruptor = new Disruptor<>(
                EventHolder::new,
                RING_BUFFER_SIZE,
                new PrefixThreadFactory("disruptor-"),
                SINGLE,
                new BlockingWaitStrategy()
        );

        disruptor.setDefaultExceptionHandler(new DisruptorExceptionHandler());

        final var publicationsBySessionId = new ConcurrentHashMap<Integer, Publication>();

        final var eventContext = new EventContext();
        final var publisher = new Publisher(eventContext);
        publisherEventHandler = new PublisherEventHandler(publicationsBySessionId);
        disruptor
                .handleEventsWith(new StateMachineEventHandler(eventContext, new Controller(publisher)))
                .then(
                        publisherEventHandler,
                        new ExternalConnectorEventHandler(internalMessagesQueue)
                )
                .then(new CleanerEventHandler());

        // It helps us to read from internalMessagesQueue ASAP,
        // but we would prefer to have a wakeup() method in IdleStrategy.
        // Unparking worker thread manually is also possible but is breaking abstractions.
        final var idleStrategy = new BusySpinIdleStrategy();

        serverAgentRunner = new AgentRunner(
                idleStrategy,
                throwable -> logger.error("Unhandled Aeron error", throwable),
                null,
                new ServerAgent(aeron, disruptor.getRingBuffer(), publicationsBySessionId, internalMessagesQueue)
        );
    }

    public void start() {
        disruptor.start();
        AgentRunner.startOnThread(serverAgentRunner);
    }

    public void shutdown() {

        logger.info("Initiating shutdown of event sources");

        CloseHelper.quietClose(serverAgentRunner);

        logger.info("Initiating shutdown of Disruptor");

        // some event handlers may prevent disruptor.shutdown() to complete, if we don't shut down them manually
        publisherEventHandler.shutdown();

        disruptor.shutdown();

        logger.info("Initiating shutdown of Aeron");

        CloseHelper.quietClose(aeron);
        CloseHelper.quietClose(mediaDriver);

        logger.info("Shutdown completed");
    }
}
