package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribeUser(@RequestBody Map<String, String> request, Principal principal) {
        String planName = request.get("planName");
        String username = principal.getName();

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setSubscriptionPlan(planName);
        user.setSubscriptionStatus("ACTIVE");
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "Successfully subscribed to " + planName,
                "subscriptionPlan", planName,
                "subscriptionStatus", "ACTIVE"
        ));
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentSubscription(Principal principal) {
        UserEntity user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String plan = user.getSubscriptionPlan() != null ? user.getSubscriptionPlan() : "None";
        String status = user.getSubscriptionStatus() != null ? user.getSubscriptionStatus() : "INACTIVE";

        return ResponseEntity.ok(Map.of(
                "subscriptionPlan", plan,
                "subscriptionStatus", status
        ));
    }
}