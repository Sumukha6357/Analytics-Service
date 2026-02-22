package com.example.analytics.service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    public RedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAvailable() {
        return ping();
    }

    public boolean ping() {
        try {
            String pong = redisTemplate.execute(RedisConnection::ping);
            return "PONG".equalsIgnoreCase(pong);
        } catch (DataAccessException ex) {
            return false;
        }
    }

    public void put(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
