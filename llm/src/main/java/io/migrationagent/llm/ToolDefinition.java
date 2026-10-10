package io.migrationagent.llm;

public record ToolDefinition(String type, FunctionDefinition function) {

    public static ToolDefinition function(FunctionDefinition function) {
        return new ToolDefinition("function", function);
    }
}
