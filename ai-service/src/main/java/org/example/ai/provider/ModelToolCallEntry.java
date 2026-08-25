package org.example.ai.provider;

import java.util.List;

/** The model's turn where it requested one or more tool calls, optionally preceded by text. */
public record ModelToolCallEntry(String text, List<ToolCallRequest> calls) implements ToolExchangeEntry {
}
