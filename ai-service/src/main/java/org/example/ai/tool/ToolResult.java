package org.example.ai.tool;

import java.util.Map;

/**
 * The outcome of one {@link AgentTool} execution. Never thrown as an exception up through the
 * chat loop — a failed tool call is data the model reasons about ("if a tool fails, say so", per
 * the system prompt), not a fatal turn error.
 */
public record ToolResult(boolean success, Map<String, Object> data, String errorMessage, Integer httpStatus) {

    public static ToolResult ok(Map<String, Object> data) {
        return new ToolResult(true, data, null, null);
    }

    public static ToolResult notFound(String message) {
        return new ToolResult(false, null, message, 400);
    }

    public static ToolResult error(String message, Integer httpStatus) {
        return new ToolResult(false, null, message, httpStatus);
    }
}
