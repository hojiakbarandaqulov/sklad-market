package org.example.ai.provider;

/**
 * A live, single-use handle over a streaming generation call. {@link #close()} must abort the
 * underlying connection so a client disconnect can cancel an in-flight provider call (PLAN.md
 * Phase 1: "disconnect cancellation").
 */
public interface ChatStream extends Iterable<ChatStreamChunk>, AutoCloseable {
    @Override
    void close();
}
