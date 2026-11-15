package com.yongdrel.fraudriskservice.application.port.in;

/** The mutable part of a rule. */
public record RuleSettings(boolean enabled, int threshold, int windowMinutes, int weight, String description) {
}
