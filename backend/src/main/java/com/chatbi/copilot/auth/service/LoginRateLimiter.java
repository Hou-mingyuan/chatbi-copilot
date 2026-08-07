package com.chatbi.copilot.auth.service;

import com.chatbi.copilot.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return;
        }
        if (attempt.started.plus(WINDOW).isBefore(clock.instant())) {
            attempts.remove(key, attempt);
            return;
        }
        if (attempt.failures >= MAX_FAILURES) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Try again in a few minutes.");
        }
    }

    public void failed(String key) {
        Instant now = clock.instant();
        attempts.compute(key, (ignored, current) -> {
            if (current == null || current.started.plus(WINDOW).isBefore(now)) {
                return new Attempt(now, 1);
            }
            return new Attempt(current.started, current.failures + 1);
        });
    }

    public void succeeded(String key) {
        attempts.remove(key);
    }

    private record Attempt(Instant started, int failures) {
    }
}
