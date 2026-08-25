package org.example.ai.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    private static AgentTool tool(String name, Set<String> allowedRoles) {
        return new AgentTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "desc";
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return Map.of("type", "OBJECT", "properties", Map.of());
            }

            @Override
            public Set<String> allowedRoles() {
                return allowedRoles;
            }

            @Override
            public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
                return ToolResult.ok(Map.of());
            }
        };
    }

    @Test
    void availableFor_openTool_isAvailableToAnyRole() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("open_tool", Set.of())));

        assertThat(registry.availableFor(Set.of("BUYER")).stream().map(AgentTool::name)).containsExactly("open_tool");
        assertThat(registry.availableFor(Set.of())).hasSize(1);
    }

    @Test
    void availableFor_roleGatedTool_filtersOutDisallowedCallers() {
        ToolRegistry registry = new ToolRegistry(List.of(
                tool("open_tool", Set.of()),
                tool("seller_tool", Set.of("SELLER"))));

        assertThat(registry.availableFor(Set.of("BUYER")).stream().map(AgentTool::name)).containsExactly("open_tool");
        assertThat(registry.availableFor(Set.of("SELLER")).stream().map(AgentTool::name))
                .containsExactlyInAnyOrder("open_tool", "seller_tool");
    }

    @Test
    void findAllowed_deniesRoleGatedToolForWrongRole() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("seller_tool", Set.of("SELLER"))));

        assertThat(registry.findAllowed("seller_tool", Set.of("BUYER"))).isEmpty();
        assertThat(registry.findAllowed("seller_tool", Set.of("SELLER"))).isPresent();
    }

    @Test
    void findAllowed_unknownToolName_isAbsentRegardlessOfRole() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("open_tool", Set.of())));

        assertThat(registry.findAllowed("does_not_exist", Set.of("ADMIN"))).isEmpty();
    }
}
