package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Omezuje počet požadavků na POST /auth/login a POST /auth/register podle klientské IP.
 * Každá IP má povoleno 10 pokusů v 10minutovém okně.
 * Pokud je přítomná hlavička CF-Connecting-IP (Cloudflare), použije ji, jinak náhradně TCP remote address.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    // Jak dlouho po posledním přístupu je bucket kandidát na odstranění
    private static final Duration EVICT_AFTER = Duration.ofHours(1);

    private record BucketEntry(Bucket bucket, Instant lastAccess) {
        BucketEntry touch() {
            return new BucketEntry(bucket, Instant.now());
        }
    }

    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.equals("/auth/login") && !path.equals("/auth/register");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = request.getHeader("CF-Connecting-IP");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        BucketEntry entry = buckets.compute(ip, (key, existing) -> {
            if (existing == null) {
                Bucket bucket = Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(MAX_ATTEMPTS)
                                .refillIntervally(MAX_ATTEMPTS, WINDOW)
                                .build())
                        .build();
                return new BucketEntry(bucket, Instant.now());
            }
            return existing.touch();
        });

        if (entry.bucket().tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
            response.getWriter().write("{\"error\":\"too_many_requests\"}");
        }
    }

    @Scheduled(fixedDelay = 600_000) // každých 10 minut
    void evictStaleBuckets() {
        Instant cutoff = Instant.now().minus(EVICT_AFTER);
        buckets.entrySet().removeIf(e -> e.getValue().lastAccess().isBefore(cutoff));
    }
}
