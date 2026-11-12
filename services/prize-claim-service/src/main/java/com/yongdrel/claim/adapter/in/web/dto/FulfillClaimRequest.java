
package com.yongdrel.claim.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FulfillClaimRequest(

        @NotBlank(message = "Fulfillment reference is required")
        @Size(max = 150, message = "Fulfillment reference must not exceed 150 characters")
        String fulfillmentReference

) {
}
