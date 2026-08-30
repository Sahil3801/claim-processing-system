package com.claim.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.claim.demo.dto.NotificationSubscriptionRequest;
import com.claim.demo.dto.NotificationUnsubscriptionRequest;
import com.claim.demo.service.NotificationService;

import jakarta.validation.Valid;

/**
 * Controller for handling notification subscriptions.
 * Maps all notification-related actions under the "/notifications" path.
 */
@RestController
@RequestMapping("/notifications") // Base URI for all handlers in this controller.
public class NotificationsController {
    private final NotificationService notificationService;

    public NotificationsController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Endpoint to subscribe to notifications.
     * Accepts a POST request with NotificationDTO that includes user ID and message details.
     * 
     * @param notificationDTO Data Transfer Object containing the notification details.
     * @return a response entity with a success message and the HTTP status.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<String> subscribe(@Valid @RequestBody NotificationSubscriptionRequest request) {
        // Call the notification service to handle the subscription logic.
        notificationService.subscribeToNotifications(request.userId(), request.message());
        // Return a success response.
        return ResponseEntity.ok("Subscribed successfully to notifications.");
    }

    /**
     * Endpoint to unsubscribe from notifications.
     * Accepts a POST request with NotificationDTO that includes user ID.
     * 
     * @param notificationDTO Data Transfer Object containing the user ID to unsubscribe.
     * @return a response entity with a success message and the HTTP status.
     */
    @PostMapping("/unsubscribe")
    public ResponseEntity<String> unsubscribe(@Valid @RequestBody NotificationUnsubscriptionRequest request) {
        // Call the notification service to handle the unsubscription logic.
        notificationService.unsubscribeFromNotifications(request.userId());
        // Return a success response.
        return ResponseEntity.ok("Unsubscribed successfully from notifications.");
    }
}
