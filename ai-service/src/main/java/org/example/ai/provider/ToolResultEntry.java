package org.example.ai.provider;

import java.util.List;

/** Our reply to a {@link ModelToolCallEntry}: the executed tools' outcomes, in call order. */
public record ToolResultEntry(List<ToolCallOutcome> outcomes) implements ToolExchangeEntry {
}
