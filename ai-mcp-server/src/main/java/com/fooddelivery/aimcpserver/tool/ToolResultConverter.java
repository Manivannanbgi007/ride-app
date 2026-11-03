package com.fooddelivery.aimcpserver.tool;

import java.lang.reflect.Type;

import org.springframework.ai.tool.execution.ToolCallResultConverter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring AI turns a tool's return value into text with this converter. Ours keeps the whole ToolResult (summary and
 * card) as JSON; GuardedToolCallback reads it back, sends only the summary to the model and keeps the card for the UI.
 */
public class ToolResultConverter implements ToolCallResultConverter {

    // Spring AI creates converters itself (no constructor arguments), so the mapper is local to this class
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Override
    public String convert(Object result, Type returnType) {
        return MAPPER.writeValueAsString(result);
    }

    public static ToolResult decode(String json) {
        return MAPPER.readValue(json, ToolResult.class);
    }
}
