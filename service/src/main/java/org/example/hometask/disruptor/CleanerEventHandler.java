package org.example.hometask.disruptor;

import com.lmax.disruptor.EventHandler;
import org.jetbrains.annotations.NotNull;

class CleanerEventHandler implements EventHandler<EventHolder> {

    @Override
    public void onEvent(@NotNull EventHolder holder, long sequence, boolean endOfBatch) {
        holder.clean();
    }
}
