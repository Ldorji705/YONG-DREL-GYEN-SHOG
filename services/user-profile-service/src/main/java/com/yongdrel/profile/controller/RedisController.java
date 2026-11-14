
package com.yongdrel.servicetemplate.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/redis")
public class RedisController {

    private final StringRedisTemplate redisTemplate;

    public RedisController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/reserve/{ticketId}")
    public ResponseEntity<String> reserveTicket(
            @PathVariable String ticketId,
            @RequestParam String userId) {

        String key = "reservation:ticket:" + ticketId;

        Boolean reserved = redisTemplate.opsForValue()
                .setIfAbsent(key, userId, Duration.ofSeconds(60));

        if (Boolean.TRUE.equals(reserved)) {
            return ResponseEntity.ok("Ticket reserved successfully");
        }

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Ticket is already reserved");
    }

    @GetMapping("/reservation/{ticketId}")
    public ResponseEntity<String> getReservation(
            @PathVariable String ticketId) {

        String key = "reservation:ticket:" + ticketId;

        String userId = redisTemplate.opsForValue().get(key);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No active reservation");
        }

        return ResponseEntity.ok("Reserved by: " + userId);
    }
}

