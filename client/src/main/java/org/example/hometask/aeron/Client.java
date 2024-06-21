package org.example.hometask.aeron;

import io.aeron.Aeron;
import io.aeron.CommonContext;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.example.hometask.messages.request.AeronRequest;
import org.example.hometask.messages.response.AeronResponse;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Client {

    private final ConcurrentLinkedQueue<AeronRequest> requestQueue = new ConcurrentLinkedQueue<>();
    private final BlockingQueue<AeronResponse> responseQueue = new LinkedBlockingQueue<>();

    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final AgentRunner clientAgentRunner;

    public Client() {

        // It helps us to read from requestQueue ASAP,
        // but we would prefer to have a wakeup() method in IdleStrategy.
        // Unparking worker thread manually is also possible but is breaking abstractions.
        final var idleStrategy = new BusySpinIdleStrategy();

        mediaDriver = MediaDriver.launchEmbedded(
                new MediaDriver.Context()
                        .aeronDirectoryName(CommonContext.getAeronDirectoryName() + "-client")
                        .dirDeleteOnStart(true)
                        .dirDeleteOnShutdown(true)
                        .threadingMode(ThreadingMode.SHARED));

        aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(mediaDriver.aeronDirectoryName()));

        System.out.println(mediaDriver.aeronDirectoryName());

        clientAgentRunner = new AgentRunner(
                idleStrategy,
                Throwable::printStackTrace,
                null,
                new ClientAgent(aeron, requestQueue, responseQueue)
        );

        AgentRunner.startOnThread(clientAgentRunner);
    }

    public void send(@NotNull AeronRequest message) {
        requestQueue.offer(message);
    }

    @NotNull
    public BlockingQueue<AeronResponse> responses() {
        return responseQueue;
    }

    public void shutdown() {
        CloseHelper.quietClose(clientAgentRunner);
        CloseHelper.quietClose(aeron);
        CloseHelper.quietClose(mediaDriver);
    }
}
