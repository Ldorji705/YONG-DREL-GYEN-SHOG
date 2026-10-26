
package com.yongdrel.servicetemplate.controller;

import com.yongdrel.servicetemplate.messaging.MessagePublisher;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
public class RabbitMQController {

    private final MessagePublisher publisher;

    public RabbitMQController(MessagePublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/send")
    public String sendMessage(@RequestParam String message) {

        publisher.sendMessage(message);

        return "Message sent to RabbitMQ";
    }
}
