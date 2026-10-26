package com.example.supportagent.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.supportagent.model.demo.DemoData;

/**
 * In-memory support data actions migrated from the original NextJS API routes
 * ({@code /api/orders/...}, {@code /api/users/...}, {@code /api/complaints/...},
 * {@code /api/tickets/...}, {@code /api/vouchers/...}). Responses mirror the
 * original JSON bodies field-for-field.
 */
@Service
public class SupportDataService {

    public Map<String, Object> getOrder(String orderId) {
        return DemoData.DEMO_ORDERS.stream()
                .filter(order -> orderId.equals(order.get("id")))
                .findFirst()
                .orElse(Map.of("error", "Order not found"));
    }

    public List<String> getOrderHistory(String userId) {
        // The original endpoint ignores the user ID and returns the demo order history.
        @SuppressWarnings("unchecked")
        List<String> history = (List<String>) DemoData.USER_INFO.get("order_history");
        return history;
    }

    public Map<String, Object> cancelOrder(String orderId) {
        return Map.of("message", "Order " + orderId + " cancelled successfully");
    }

    public Map<String, Object> createRefund(String orderId, Number amount, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Refund of $" + amount + " for order " + orderId + " is processing");
        result.put("reason", reason);
        return result;
    }

    public Map<String, Object> createReturn(String orderId, List<String> productIds) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Return initiated for order " + orderId);
        result.put("product_ids", productIds);
        return result;
    }

    public Map<String, Object> sendReplacement(String orderId, String productId) {
        return Map.of("message",
                "Replacement for product " + productId + " in order " + orderId + " sent successfully");
    }

    public Map<String, Object> resetPassword(String userId) {
        return Map.of("message", "Password reset email sent to user " + userId);
    }

    public Map<String, Object> updateInfo(String userId, Map<String, Object> info) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "User " + userId + " info updated");
        result.put("updatedField", info.get("field"));
        result.put("newValue", info.get("value"));
        return result;
    }

    public Map<String, Object> createComplaint(String userId, String type, String details, String orderId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Complaint created for user " + userId);
        result.put("type", type);
        result.put("details", details);
        result.put("order_id", orderId);
        return result;
    }

    public Map<String, Object> createTicket(String userId, String type, String details, String orderId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Ticket created for user " + userId);
        result.put("type", type);
        result.put("details", details);
        result.put("order_id", orderId);
        return result;
    }

    public Map<String, Object> issueVoucher(String userId, Number amount, String reason) {
        return Map.of("message",
                "Voucher of $" + amount + " issued to user " + userId + " for: " + reason);
    }
}
