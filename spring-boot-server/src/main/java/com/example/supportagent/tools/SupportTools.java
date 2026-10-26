package com.example.supportagent.tools;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.supportagent.service.KnowledgeBaseService;
import com.example.supportagent.service.SupportDataService;
import com.example.supportagent.util.Json;

/**
 * The 12 tools exposed to the model, migrated from {@code config/tools-list.ts}.
 *
 * <p>Spring AI derives each tool's JSON schema from the annotated method. The
 * tool bodies are functional (used if a tool is ever executed server-side), but
 * in this demo the client executes tools via the REST endpoints to preserve the
 * human-in-the-loop workflow.
 */
@Component
public class SupportTools {

    private final KnowledgeBaseService knowledgeBaseService;
    private final SupportDataService supportDataService;

    public SupportTools(KnowledgeBaseService knowledgeBaseService, SupportDataService supportDataService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.supportDataService = supportDataService;
    }

    @Tool(name = "search_knowledge_base", description = "Search the internal knowledge base and public FAQ for relevant content (returns policies, guidelines and article snippets)")
    public String searchKnowledgeBase(
            @ToolParam(description = "Search query describing the information to look for") String query) {
        return Json.write(Map.of("results",
                knowledgeBaseService.search(query, 6).stream().map(r -> r.toMap()).toList()));
    }

    @Tool(name = "get_order", description = "Get order details by order ID")
    public String getOrder(@ToolParam(description = "Order ID to get details for") String orderId) {
        return Json.write(supportDataService.getOrder(orderId));
    }

    @Tool(name = "get_order_history", description = "Get order history for a user")
    public String getOrderHistory(@ToolParam(description = "User ID to get order history for") String userId) {
        return Json.write(supportDataService.getOrderHistory(userId));
    }

    @Tool(name = "cancel_order", description = "Cancel an order")
    public String cancelOrder(@ToolParam(description = "Order ID to cancel") String orderId) {
        return Json.write(supportDataService.cancelOrder(orderId));
    }

    @Tool(name = "reset_password", description = "Send a password reset email to the user")
    public String resetPassword(@ToolParam(description = "User ID to send password reset email to") String userId) {
        return Json.write(supportDataService.resetPassword(userId));
    }

    @Tool(name = "send_replacement", description = "Send a replacement product for an order")
    public String sendReplacement(
            @ToolParam(description = "Product ID to send replacement for") String productId,
            @ToolParam(description = "Order ID to send replacement for") String orderId) {
        return Json.write(supportDataService.sendReplacement(orderId, productId));
    }

    @Tool(name = "create_refund", description = "Create a refund for an order")
    public String createRefund(
            @ToolParam(description = "Order ID to create refund for") String orderId,
            @ToolParam(description = "Amount to refund") double amount,
            @ToolParam(description = "Reason for refund") String reason) {
        return Json.write(supportDataService.createRefund(orderId, amount, reason));
    }

    @Tool(name = "issue_voucher", description = "Issue a voucher to a user")
    public String issueVoucher(
            @ToolParam(description = "User ID to issue voucher for") String userId,
            @ToolParam(description = "Amount to issue voucher for") double amount,
            @ToolParam(description = "Reason for issuing voucher") String reason) {
        return Json.write(supportDataService.issueVoucher(userId, amount, reason));
    }

    @Tool(name = "create_return", description = "Create a return for an order")
    public String createReturn(
            @ToolParam(description = "Order ID to create return for") String orderId,
            @ToolParam(description = "Product IDs to create return for") List<String> productIds) {
        return Json.write(supportDataService.createReturn(orderId, productIds));
    }

    @Tool(name = "create_complaint", description = "Create a complaint for a user (types: product_quality, order_delay, delivery_issues, other)")
    public String createComplaint(
            @ToolParam(description = "User ID to create complaint for") String userId,
            @ToolParam(description = "Type of complaint (product_quality, order_delay, delivery_issues, other)") String type,
            @ToolParam(description = "Details of the complaint") String details,
            @ToolParam(description = "Order ID linked to the complaint, N/A if not linked to an order") String orderId) {
        return Json.write(supportDataService.createComplaint(userId, type, details, orderId));
    }

    @Tool(name = "create_ticket", description = "Create a support ticket for a user (types: bug_reported, damaged_product, other)")
    public String createTicket(
            @ToolParam(description = "User ID to create ticket for") String userId,
            @ToolParam(description = "Type of ticket (bug_reported, damaged_product, other)") String type,
            @ToolParam(description = "Details of the ticket") String details,
            @ToolParam(description = "Order ID linked to the ticket, N/A if not linked to an order") String orderId) {
        return Json.write(supportDataService.createTicket(userId, type, details, orderId));
    }

    @Tool(name = "update_info", description = "Update user information (field: email, phone, address or name)")
    public String updateInfo(
            @ToolParam(description = "User ID to update information for") String userId,
            @ToolParam(description = "Information to update, with 'field' (email, phone, address, name) and 'value'") Map<String, String> info) {
        return Json.write(supportDataService.updateInfo(userId, Map.copyOf(info)));
    }
}
