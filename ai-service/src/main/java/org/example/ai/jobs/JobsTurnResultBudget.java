package org.example.ai.jobs;

import org.example.ai.tool.ToolResult;
import java.util.*;

/** Per-turn state only; repeated tool calls cannot create a multi-page Jobs answer. */
public final class JobsTurnResultBudget {
    private final Map<String, Set<Long>> shown = new HashMap<>();
    public ToolResult apply(ToolResult result) {
        if (!result.success() || result.data() == null) return result;
        String kind = String.valueOf(result.data().get("kind"));
        String bucket = switch (kind) {
            case "job_search", "job_recommendations" -> "jobs";
            case "candidate_search" -> "candidates";
            default -> null;
        };
        if (bucket == null || !(result.data().get("items") instanceof List<?> items)) return result;
        var seen = shown.computeIfAbsent(bucket, unused -> new HashSet<>());
        var kept = new ArrayList<Object>();
        for (Object value : items) {
            if (value instanceof Map<?, ?> item && item.get("id") instanceof Number id
                    && id.longValue() > 0 && seen.size() < 5 && seen.add(id.longValue())) kept.add(value);
        }
        var safe = new LinkedHashMap<String, Object>(result.data()); safe.put("items", kept);
        if (kept.size() < items.size()) safe.put("hasMore", true);
        if (kept.isEmpty() && !items.isEmpty()) { safe.put("alreadyShown", true); safe.put("shownCount", seen.size()); }
        return ToolResult.ok(safe);
    }
}
