package com.example.supportagent.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.supportagent.service.SupportDataService;

/**
 * Order endpoints (original {@code /api/orders/...} routes).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final SupportDataService supportDataService;

    public OrderController(SupportDataService supportDataService) {
        this.supportDataService = supportDataService;
    }

    @GetMapping("/{order_id}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable("order_id") String orderId) {
        Map<String, Object> order = supportDataService.getOrder(orderId);
        if (order.containsKey("error")) {
            return ResponseEntity.status(404).body(order);
        }
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{order_id}/cancel")
    public Map<String, Object> cancelOrder(@PathVariable("order_id") String orderId) {
        return supportDataService.cancelOrder(orderId);
    }

    @PostMapping("/{order_id}/create_refund")
    public Map<String, Object> createRefund(@PathVariable("order_id") String orderId,
            @RequestBody Map<String, Object> body) {
        Number amount = body.get("amount") instanceof Number n ? n : 0;
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return supportDataService.createRefund(orderId, amount, reason);
    }

    @PostMapping("/{order_id}/create_return")
    public Map<String, Object> createReturn(@PathVariable("order_id") String orderId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> productIds = body.get("product_ids") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        return supportDataService.createReturn(orderId, productIds);
    }

    @PostMapping("/{order_id}/send_replacement")
    public Map<String, Object> sendReplacement(@PathVariable("order_id") String orderId,
            @RequestBody Map<String, Object> body) {
        String productId = body.get("product_id") == null ? null : String.valueOf(body.get("product_id"));
        return supportDataService.sendReplacement(orderId, productId);
    }
}
