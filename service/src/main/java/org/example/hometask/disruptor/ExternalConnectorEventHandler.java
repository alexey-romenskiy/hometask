package org.example.hometask.disruptor;

import com.lmax.disruptor.EventHandler;
import org.example.hometask.external.WithdrawalService;
import org.example.hometask.external.WithdrawalServiceStub;
import org.example.hometask.messages.WithdrawalState;
import org.example.hometask.messages.disruptor.CreateWithdrawalDuplicationFailureEvent;
import org.example.hometask.messages.disruptor.CreateWithdrawalSuccessEvent;
import org.example.hometask.messages.disruptor.Event;
import org.example.hometask.messages.disruptor.QueryWithdrawalSuccessEvent;
import org.example.hometask.messages.disruptor.QueryWithdrawalUnknownIdFailureEvent;
import org.example.hometask.messages.external.CreateWithdrawalRequest;
import org.example.hometask.messages.external.QueryWithdrawalRequest;
import org.example.hometask.messages.external.WithdrawalRequest;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentLinkedQueue;

class ExternalConnectorEventHandler implements EventHandler<EventHolder> {

    private final WithdrawalService withdrawalService = new WithdrawalServiceStub();

    @NotNull
    private final ConcurrentLinkedQueue<Event> internalMessagesQueue;

    public ExternalConnectorEventHandler(@NotNull ConcurrentLinkedQueue<Event> internalMessagesQueue) {
        this.internalMessagesQueue = internalMessagesQueue;
    }

    @Override
    public void onEvent(@NotNull EventHolder holder, long sequence, boolean endOfBatch) {

        for (final var request : holder.withdrawalRequests) {
            request.accept(new WithdrawalRequest.Visitor<Void, RuntimeException>() {
                @Override
                public Void visit(@NotNull CreateWithdrawalRequest request) {
                    try {
                        withdrawalService.requestWithdrawal(request.id(), request.address(), request.amount());
                        publish(new CreateWithdrawalSuccessEvent(request.id()));
                    } catch (IllegalStateException e) {
                        publish(new CreateWithdrawalDuplicationFailureEvent(request.id()));
                    }
                    return null;
                }

                @Override
                public Void visit(@NotNull QueryWithdrawalRequest request) {
                    try {
                        final var externalState = withdrawalService.getRequestState(request.id());
                        final var state = switch (externalState) {
                            case PROCESSING -> WithdrawalState.PROCESSING;
                            case COMPLETED -> WithdrawalState.COMPLETED;
                            case FAILED -> WithdrawalState.FAILED;
                            default -> throw new IllegalArgumentException("Unsupported state: " + externalState);
                        };
                        publish(new QueryWithdrawalSuccessEvent(request.id(), state));
                    } catch (IllegalArgumentException e) {
                        publish(new QueryWithdrawalUnknownIdFailureEvent(request.id()));
                    }
                    return null;
                }
            });
        }
    }

    private void publish(@NotNull Event event) {
        // InternalMessagesQueue is unbounded to prevent deadlock here.
        // Actually, Disruptor pattern could allow to pass this event back via its ring buffer, but LMAX implementation
        // used here has some implications not allowing us to do it this way.
        internalMessagesQueue.add(event);
    }
}
