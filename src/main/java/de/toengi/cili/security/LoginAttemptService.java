package de.toengi.cili.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class LoginAttemptService {

    @Value("${cili.security.max-login-attempts:10}")
    private int maxAttempts;

    @Value("${cili.security.login-lockout-minutes:15}")
    private int lockoutMinutes;

    private static class Entry {
        final AtomicInteger count = new AtomicInteger(0);
        volatile Instant lockedUntil = null;
    }

    private final ConcurrentHashMap<String, Entry> ipCache   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Entry> userCache = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip, String username) {
        return isBlockedKey(ip, ipCache) || isBlockedKey(username, userCache);
    }

    private boolean isBlockedKey(String key, ConcurrentHashMap<String, Entry> cache) {
        Entry entry = cache.get(key);
        if (entry == null) return false;
        Instant locked = entry.lockedUntil;
        return locked != null && Instant.now().isBefore(locked);
    }

    public void loginFailed(String ip, String username) {
        recordFailed(ip, ipCache, "IP");
        recordFailed(username, userCache, "Benutzer");
    }

    private void recordFailed(String key, ConcurrentHashMap<String, Entry> cache, String label) {
        Entry entry = cache.computeIfAbsent(key, k -> new Entry());
        int count = entry.count.incrementAndGet();
        if (count >= maxAttempts) {
            entry.lockedUntil = Instant.now().plusSeconds(lockoutMinutes * 60L);
            log.warn("{} '{}' nach {} Fehlversuchen für {} Minuten gesperrt", label, key, count, lockoutMinutes);
        }
    }

    public void loginSucceeded(String ip, String username) {
        ipCache.remove(ip);
        userCache.remove(username);
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanExpired() {
        Instant now = Instant.now();
        removeExpired(ipCache, now);
        removeExpired(userCache, now);
    }

    private void removeExpired(ConcurrentHashMap<String, Entry> cache, Instant now) {
        cache.entrySet().removeIf(e -> {
            Instant locked = e.getValue().lockedUntil;
            return locked != null && now.isAfter(locked);
        });
    }
}
