package com.iss.backend.web;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory limit on how often one IP address can use a public form
 * (contact messages, forgot password), so the forms cannot be used to send floods of email.
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /** Records an attempt and returns false when the key has already used up its limit in the window. */
    public boolean allow(String action, String ip, int limit, Duration window) {
        String key = action + "|" + ip;
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        Deque<Instant> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) times.pollFirst();
            if (times.size() >= limit) return false;
            times.addLast(now);
        }
        if (hits.size() > 10_000) hits.entrySet().removeIf(e -> e.getValue().isEmpty());
        return true;
    }
}
