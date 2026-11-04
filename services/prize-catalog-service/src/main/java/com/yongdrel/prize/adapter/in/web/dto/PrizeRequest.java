package com.yongdrel.prize.adapter.in.web.dto;

import com.yongdrel.prize.domain.model.PrizeStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PrizeRequest(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 1000) String description,
    @NotNull @DecimalMin("0.00") BigDecimal value,
    @NotNull @Min(0) Integer quantity,
    @NotNull PrizeStatus status
) {}
