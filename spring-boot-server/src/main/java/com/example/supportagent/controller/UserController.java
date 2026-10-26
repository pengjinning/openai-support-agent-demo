package com.example.supportagent.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.supportagent.service.SupportDataService;

/**
 * User endpoints (original {@code /api/users/...} routes).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final SupportDataService supportDataService;

    public UserController(SupportDataService supportDataService) {
        this.supportDataService = supportDataService;
    }

    @GetMapping("/{user_id}/order_history")
    public List<String> orderHistory(@PathVariable("user_id") String userId) {
        return supportDataService.getOrderHistory(userId);
    }

    @PostMapping("/{user_id}/reset_password")
    public Map<String, Object> resetPassword(@PathVariable("user_id") String userId) {
        return supportDataService.resetPassword(userId);
    }

    @PostMapping("/{user_id}/update_info")
    public Map<String, Object> updateInfo(@PathVariable("user_id") String userId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> info = body.get("info") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        return supportDataService.updateInfo(userId, info);
    }
}
