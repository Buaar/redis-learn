package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisWorkId {
    // 开始时间戳
    private static final long BEGIN_TIMESTAMP = 1688169600L;

    // 序列号位数
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long getNextId(String keyPrefix) {
        // 1、生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;

        // 2、生成序列号
        String day = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long serialNumber = stringRedisTemplate.opsForValue().increment("inc:" + keyPrefix + day);

        // 3、拼接时间戳和序列号
        return timeStamp << COUNT_BITS | serialNumber;
    }

    public static void main(String[] args) {
        LocalDateTime localDateTime = LocalDateTime.of(2023, 7, 1, 0, 0, 0);
        long second = localDateTime.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
    }
}
