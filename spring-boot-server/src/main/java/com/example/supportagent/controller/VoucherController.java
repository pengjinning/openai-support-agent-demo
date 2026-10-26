package com.example.supportagent.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.supportagent.service.SupportDataService;

/**
 * Voucher endpoint (original {@code /api/vouchers/create}).
 */
@RestController
@RequestMapping("/api/vouchers")
public class VoucherController {

    private final SupportDataService supportDataService;

    public VoucherController(SupportDataService supportDataService) {
        this.supportDataService = supportDataService;
    }

    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String userId = body.get("user_id") == null ? null : String.valueOf(body.get("user_id"));
        Number amount = body.get("amount") instanceof Number n ? n : 0;
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return supportDataService.issueVoucher(userId, amount, reason);
    }
}
