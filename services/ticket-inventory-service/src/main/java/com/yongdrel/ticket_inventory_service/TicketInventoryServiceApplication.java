package com.yongdrel.ticket_inventory_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TicketInventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketInventoryServiceApplication.class, args);
    }
}
