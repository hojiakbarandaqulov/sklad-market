package org.example.ai.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side allowlist of {@link AgentTool}s, filtered per call by the caller's roles (PLAN.md
 * §4.2 item 4: "the tool registry is a server-side allowlist filtered by caller role"). The model
 * only ever sees the subset {@link #availableFor(Set)} returns — an unlisted or role-denied tool
 * name can never be invoked even if the model hallucinates a call for it.
 */
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> toolsByName;

    public ToolRegistry(List<AgentTool> tools) {
        Map<String, AgentTool> indexed = new LinkedHashMap<>();
        for (AgentTool tool : tools) {
            indexed.put(tool.name(), tool);
        }
        this.toolsByName = Map.copyOf(indexed);
    }

    public List<AgentTool> availableFor(Set<String> callerRoles) {
        return toolsByName.values().stream()
                .filter(tool -> isAllowed(tool, callerRoles))
                .toList();
    }

    /** Only returns a tool if it is both registered AND allowed for the given caller roles. */
    public Optional<AgentTool> findAllowed(String name, Set<String> callerRoles) {
        AgentTool tool = toolsByName.get(name);
        if (tool == null || !isAllowed(tool, callerRoles)) {
            return Optional.empty();
        }
        return Optional.of(tool);
    }

    /** Registry lookup without role filtering, used only for conservative legacy-history policy. */
    public Optional<AgentTool> findRegistered(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    private boolean isAllowed(AgentTool tool, Set<String> callerRoles) {
        Set<String> allowedRoles = tool.allowedRoles();
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return true;
        }
        return callerRoles != null && allowedRoles.stream().anyMatch(callerRoles::contains);
    }
}
