package com.example.supportagent.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.supportagent.service.SupportDataService;

/**
 * Ticket endpoint (original {@code /api/tickets/create}).
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final SupportDataService supportDataService;

    public TicketController(SupportDataService supportDataService) {
        this.supportDataService = supportDataService;
    }

    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String userId = str(body, "user_id");
        String type = str(body, "type");
        String details = str(body, "details");
        String orderId = str(body, "order_id");
        return supportDataService.createTicket(userId, type, details, orderId);
    }

    private static String str(Map<String, Object> body, String key) {
        return body.get(key) == null ? null : String.valueOf(body.get(key));
    }
}
