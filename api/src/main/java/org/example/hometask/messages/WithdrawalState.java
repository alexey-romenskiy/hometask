package org.example.hometask.messages;

import org.jetbrains.annotations.NotNull;

public enum WithdrawalState {
    PROCESSING {
        @Override
        public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
            return visitor.visitProcessing();
        }
    },
    COMPLETED {
        @Override
        public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
            return visitor.visitCompleted();
        }
    },
    FAILED {
        @Override
        public <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E {
            return visitor.visitFailed();
        }
    };

    public abstract <V, E extends Throwable> V accept(@NotNull Visitor<V, E> visitor) throws E;

    public interface Visitor<V, E extends Throwable> {

        V visitProcessing() throws E;

        V visitCompleted() throws E;

        V visitFailed() throws E;
    }
}
