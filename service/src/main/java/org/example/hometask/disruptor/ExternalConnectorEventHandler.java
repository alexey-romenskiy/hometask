package org.example.hometask.disruptor;

import com.lmax.disruptor.EventHandler;
import org.example.hometask.external.DuplicateWithdrawalIdException;
import org.example.hometask.external.UnknownWithdrawalIdException;
import org.example.hometask.external.WithdrawalService;
import org.example.hometask.external.WithdrawalServiceStub;
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
    public void onEvent(@NotNull EventHolder holder, long sequence, boolean endOfBatch) throws Exception {

        for (final var request : holder.withdrawalRequests) {
            request.accept(new WithdrawalRequest.Visitor<Void, RuntimeException>() {
                @Override
                public Void visit(@NotNull CreateWithdrawalRequest request) {
                    try {
                        withdrawalService.requestWithdrawal(request.id(), request.address(), request.amount());
                        publish(new CreateWithdrawalSuccessEvent(request.id()));
                    } catch (DuplicateWithdrawalIdException e) {
                        publish(new CreateWithdrawalDuplicationFailureEvent(request.id()));
                    }
                    return null;
                }

                @Override
                public Void visit(@NotNull QueryWithdrawalRequest request) {
                    try {
                        final var state = withdrawalService.getRequestState(request.id());
                        publish(new QueryWithdrawalSuccessEvent(request.id(), state));
                    } catch (UnknownWithdrawalIdException e) {
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
