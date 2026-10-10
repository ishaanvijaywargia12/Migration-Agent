package io.migrationagent.agent;

import io.migrationagent.llm.ToolDefinition;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One harness-implemented tool the model can call. Every implementation
 * here is what makes "the agent has no shell access" a structural property
 * rather than a prompt-level request — there is no tool that executes an
 * arbitrary command, only these specific, narrow operations (DESIGN.md
 * section 8, section 12).
 */
public interface AgentTool {

    String name();

    ToolDefinition definition();

    /**
     * @param arguments the parsed JSON arguments the model supplied
     * @return the tool result text to feed back to the model
     */
    String execute(JsonNode arguments) throws Exception;
}
