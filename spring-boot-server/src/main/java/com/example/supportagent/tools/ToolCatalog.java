package com.example.supportagent.tools;

import java.util.Set;

/**
 * Tool classification migrated from {@code config/tools-list.ts}. Tools listed
 * as {@code AGENT_TOOLS} require confirmation by the human representative before
 * execution; all others are executed automatically.
 */
public final class ToolCatalog {

    public static final Set<String> AGENT_TOOLS = Set.of(
            "cancel_order",
            "reset_password",
            "send_replacement",
            "create_refund",
            "issue_voucher",
            "create_return",
            "create_complaint",
            "update_info");

    private ToolCatalog() {
    }

    public static boolean isAgentTool(String toolName) {
        return AGENT_TOOLS.contains(toolName);
    }
}
