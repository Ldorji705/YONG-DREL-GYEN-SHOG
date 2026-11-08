
package com.yongdrel.event.adapter.in.web.dto;

import com.yongdrel.event.domain.model.EventStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record EventRequest(
    @NotBlank @Size(max = 150) String title,
    @Size(max = 1000) String description,
    @NotNull EventStatus status,
    @NotNull LocalDateTime startDate,
    @NotNull LocalDateTime endDate,
    @NotNull @Min(1) Integer maxTickets
) {}
