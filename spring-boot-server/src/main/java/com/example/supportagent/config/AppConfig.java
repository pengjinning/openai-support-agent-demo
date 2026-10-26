package com.example.supportagent.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * App-level constants migrated from the original {@code config/constants.ts} and
 * {@code config/demoData.ts} (agent identity, prompts and the default action).
 */
@Component
public class AppConfig {

    @Value("${app.agent-name:Blossom}")
    private String agentName;

    public String agentName() {
        return agentName;
    }

    /**
     * Initial message shown in the chat (original {@code INITIAL_MESSAGE}).
     */
    public String initialMessage() {
        return "\nHi, I'm " + agentName + ", your support representative. How can I help you today?\n";
    }

    /**
     * System prompt migrated from the original {@code DEVELOPER_PROMPT}.
     */
    public String developerPrompt() {
        return """
                You are an assistant helping a customer service representative named %s. Respond as if you were %s.

                Response style:
                - Keep replies concise: default to 3–6 sentences or ≤5 bullets; simple yes/no questions ≤2 sentences.
                - Use plain prose; avoid long lists unless needed for clarity.
                - Stay within the customer’s ask; do not add extra steps or speculative details.

                Ambiguity and accuracy:
                - If the request is unclear or missing details, briefly state what’s unclear and ask one concise clarifying question.
                - Do not fabricate specifics (order IDs, totals, dates). Qualify assumptions when unsure.

                Tool guidance:
                - For general queries, search the knowledge base with search_knowledge_base.
                - If no order ID is provided, fetch order history with get_order_history, then ask the customer which order to view; do not call get_order until they specify one.
                - Call one tool at a time; wait for the tool result before calling another.
                - Use tools to take or propose actions. Do not confirm an action as done until the representative confirms it.
                - When proposing an action, tell the customer you are looking into it and wait for confirmation before offering anything else (unless the customer asks for something new).
                """.formatted(agentName, agentName);
    }

    /**
     * Default recommended action shown in the agent view (original
     * {@code DEFAULT_ACTION}: create a generic support ticket).
     */
    public Map<String, Object> defaultAction() {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("name", "create_ticket");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("user_id", "cus_28X44");
        parameters.put("type", "other");
        parameters.put("details", "Need more help with the request");
        action.put("parameters", parameters);
        return action;
    }
}
