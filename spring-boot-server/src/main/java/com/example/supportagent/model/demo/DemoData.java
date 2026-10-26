package com.example.supportagent.model.demo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo data migrated from the original {@code config/demoData.ts}. Everything is
 * held in memory as plain maps so the JSON output matches the original API
 * (snake_case keys, same field names).
 */
public final class DemoData {

    public static final String AGENT_NAME = "Blossom";

    public static final Map<String, Object> CUSTOMER_DETAILS = customerDetails();

    public static final Map<String, Object> USER_INFO = userInfo();

    public static final List<Map<String, Object>> DEMO_ORDERS = demoOrders();

    public static final List<Map<String, Object>> DEFAULT_ARTICLES = defaultArticles();

    private DemoData() {
    }

    private static String dateDaysAgo(int daysAgo) {
        return LocalDate.now().minusDays(daysAgo).toString();
    }

    private static Map<String, Object> customerDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", "Janet Deer");
        details.put("id", "cus_28X44");
        details.put("orderNb", 8);
        details.put("signupDate", "2023-11-28");
        return details;
    }

    private static Map<String, Object> userInfo() {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("name", "Janet Deer");
        user.put("email", "janet.deer@gmail.com");
        user.put("phone", "+1234567890");
        user.put("address", "123 Main St, Anytown, USA");
        List<String> orderHistory = new ArrayList<>();
        orderHistory.add("ORD1001");
        orderHistory.add("ORD1002");
        orderHistory.add("ORD1003");
        orderHistory.add("ORD1004");
        orderHistory.add("ORD1005");
        orderHistory.add("ORD1006");
        orderHistory.add("ORD1007");
        user.put("order_history", orderHistory);
        return user;
    }

    private static Map<String, Object> item(String productId, String name, int quantity, double price) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("product_id", productId);
        item.put("name", name);
        item.put("quantity", quantity);
        item.put("price", price);
        return item;
    }

    private static List<Map<String, Object>> demoOrders() {
        List<Map<String, Object>> orders = new ArrayList<>();

        Map<String, Object> o1 = new LinkedHashMap<>();
        o1.put("id", "ORD1001");
        o1.put("date", dateDaysAgo(1));
        o1.put("status", "pending");
        o1.put("items", List.of(item("P003", "Smart Watch", 1, 149.99)));
        orders.add(o1);

        Map<String, Object> o2 = new LinkedHashMap<>();
        o2.put("id", "ORD1002");
        o2.put("date", dateDaysAgo(8));
        o2.put("status", "completed");
        o2.put("items", List.of(
                item("P001", "Wireless Headphones", 1, 99.99),
                item("P002", "Portable Charger", 1, 39.99)));
        orders.add(o2);

        Map<String, Object> o3 = new LinkedHashMap<>();
        o3.put("id", "ORD1003");
        o3.put("date", dateDaysAgo(24));
        o3.put("status", "shipped");
        o3.put("tracking_number", "TRK123456789");
        o3.put("items", List.of(item("P004", "Bluetooth Speaker", 2, 59.99)));
        orders.add(o3);

        Map<String, Object> o4 = new LinkedHashMap<>();
        o4.put("id", "ORD1004");
        o4.put("date", dateDaysAgo(28));
        o4.put("status", "cancelled");
        o4.put("cancellation_reason", "Customer requested cancellation before processing");
        orders.add(o4);

        Map<String, Object> o5 = new LinkedHashMap<>();
        o5.put("id", "ORD1005");
        o5.put("date", dateDaysAgo(44));
        o5.put("status", "refunded");
        o5.put("refund_status", "processing");
        o5.put("refund_amount", 149.99);
        o5.put("items", List.of(item("P005", "Laptop Stand", 1, 149.99)));
        orders.add(o5);

        Map<String, Object> o6 = new LinkedHashMap<>();
        o6.put("id", "ORD1006");
        o6.put("date", dateDaysAgo(96));
        o6.put("status", "delivered");
        o6.put("return_initiated", true);
        o6.put("items", List.of(item("P006", "Ergonomic Keyboard", 1, 89.99)));
        orders.add(o6);

        Map<String, Object> o7 = new LinkedHashMap<>();
        o7.put("id", "ORD1007");
        o7.put("date", dateDaysAgo(108));
        o7.put("status", "completed");
        o7.put("complaint", "Order delivered with damaged product");
        o7.put("items", List.of(item("P007", "Noise Cancelling Earbuds", 1, 129.99)));
        orders.add(o7);

        return orders;
    }

    private static List<Map<String, Object>> defaultArticles() {
        Map<String, Object> a1 = new LinkedHashMap<>();
        a1.put("title", "Interactions guidelines");
        a1.put("content", """
                Tone, Empathy, and Professionalism:
                      As a representative, you are the voice of our brand. Always:
                      - Be polite, patient, and empathetic.
                      - Assume the customer's intent is positive, even if they express frustration.
                      - Acknowledge and validate their concerns before providing solutions.
                      """);
        a1.put("link", "/kb?section=interactions_guidelines");
        a1.put("type", "knowledge_base");
        a1.put("score", 0.9);

        Map<String, Object> a2 = new LinkedHashMap<>();
        a2.put("title", "Customer support chat");
        a2.put("content", """
                If you have any questions or need any help, you can contact us through the customer support chat.
                    A customer support representative will help you with your questions or concerns.
                    Please note that our agents are available from Monday to Saturday, 9am to 6pm.
                    """);
        a2.put("link", "/faq?section=help_chat");
        a2.put("type", "faq");
        a2.put("score", 0.8);

        return List.of(a1, a2);
    }
}
