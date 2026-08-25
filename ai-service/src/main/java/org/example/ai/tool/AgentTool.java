package org.example.ai.tool;

import java.util.Map;
import java.util.Set;

/**
 * A single read-only capability the agent can invoke. {@code parametersSchema()} is a plain
 * JSON-Schema-shaped map (see {@link org.example.ai.provider.ToolSpec}) — implementations must not
 * import any Gemini SDK type, keeping the tool layer provider-agnostic.
 */
public interface AgentTool {

    String name();

    String description();

    Map<String, Object> parametersSchema();

    /** Roles allowed to use this tool; empty means "any authenticated caller" (PLAN.md §4.2 item 9 style scoping). */
    Set<String> allowedRoles();

    ToolResult execute(Map<String, Object> args, ToolExecutionContext context);
}
