package com.riskcube.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Caches dashboard query results so repeated refreshes don't recompute the cube (#398).
 */
@Component
public class QueryResultCache {

    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        return (T) cache.computeIfAbsent(key, k -> loader.get());
    }

    public long size() {
        return cache.size();
    }
}
