package org.example.ai.jobs;

import org.example.ai.tool.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class SearchJobsTool implements AgentTool {
    public static final String NAME = "search_jobs";
    private final JobMatchingService matching;
    public SearchJobsTool(JobMatchingService matching) { this.matching = matching; }
    public String name() { return NAME; }
    public String description() {
        return "Search live public Sklad Jobs vacancies with a short specific job title and optional hard filters. "
                + "Returns at most three initial grounded job cards (max six), never applies for a job. "
                + "Use for public job search across all roles. No private resumes or contact details. "
                + "Do not infer numeric region/category IDs; use only IDs supplied by the user or actual Jobs taxonomy.";
    }
    public Map<String, Object> parametersSchema() { return schema(true); }
    public Set<String> allowedRoles() { return Set.of(); }
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        try { return withPresentation(matching.search(criteria(args), context.acceptLanguage()), args); }
        catch (IllegalArgumentException invalid) { return ToolResult.error(invalid.getMessage(), 400); }
        catch (RuntimeException unavailable) { return ToolResult.error("Jobs search is temporarily unavailable; this is not a no-match result", 503); }
    }
    static Map<String, Object> schema(boolean search) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("query", Map.of("type", "STRING", "description", "Short desired occupation/title, up to 120 characters; not the whole user message or resume."));
        fields.put("regionId", Map.of("type", "INTEGER", "description", "Known actual region ID only; explicit hard constraint."));
        fields.put("categoryId", Map.of("type", "INTEGER", "description", "Known actual JOB category ID only, never a product category ID."));
        fields.put("salaryMin", Map.of("type", "NUMBER", "description", "Minimum stated UZS amount; explicit hard constraint. Do not invent salary periods."));
        fields.put("experienceLevel", Map.of("type", "STRING", "enum", JobPreferences.EXPERIENCE_LEVELS));
        fields.put("employmentType", Map.of("type", "STRING", "description", "Exact existing platform employment type when explicitly specified."));
        fields.put("workSchedule", Map.of("type", "STRING", "description", "Exact existing platform schedule when explicitly specified."));
        fields.put("limit", Map.of("type", "INTEGER", "description", "Default three, maximum six. Use three initially."));
        fields.put("presentation", Map.of("type", "STRING", "enum", List.of("CARDS", "PLAIN_TEXT"), "description", "PLAIN_TEXT only on explicit user request."));
        return Map.of("type", "OBJECT", "properties", fields, "required", search ? List.of("query") : List.of());
    }
    static JobCriteria criteria(Map<String, Object> args) {
        return new JobCriteria(ToolArgs.asString(args.get("query")), number(args.get("regionId")), number(args.get("categoryId")),
                args.get("salaryMin") instanceof Number n ? new BigDecimal(n.toString()) : null,
                ToolArgs.asString(args.get("experienceLevel")), ToolArgs.asString(args.get("employmentType")),
                ToolArgs.asString(args.get("workSchedule")), ToolArgs.asInt(args.get("limit"), 3));
    }
    private static Long number(Object value) { return value instanceof Number n ? n.longValue() : null; }
    static ToolResult withPresentation(Map<String, Object> result, Map<String, Object> args) {
        var data = new LinkedHashMap<>(result); data.put("presentation", ToolArgs.presentation(args.get("presentation"))); return ToolResult.ok(data);
    }
}
