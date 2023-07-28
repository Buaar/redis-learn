package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryWithCache(Long id) {
        if (id == null) return Result.fail("参数错误");
        return Result.ok(queryWithLogicExpire(id));
    }

    private Shop queryWithThrough(Long id) {
        // 解决缓存穿透问题
        // 1、根据商户id从缓存中取
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String s = stringRedisTemplate.opsForValue().get(key);
        // 2、命中，返回商户信息
        if (!StrUtil.isBlank(s)) {
            log.info("命中缓存！");
            return JSON.parseObject(s, Shop.class);
        }
        // 2.1 检查是否为空，为空直接返回
        if (s != null) {
            return null;
        }
        // 3、未命中，查询数据库
        log.info("未命中缓存，查询数据库！");
        Shop shop = getById(id);
        // 4、数据库没有商户信息，将空值写入redis
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "");
            return null;
        }
        // 5、数据库有商户信息, 缓存商户信息
        stringRedisTemplate.opsForValue().set(
                key,
                JSON.toJSONString(shop),
                RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.MINUTES);
        // 6、返回商铺数据
        return shop;
    }

    private Shop queryWithMutex(Long id) {
        // 1、根据商户id从缓存中取
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        String s = stringRedisTemplate.opsForValue().get(key);
        // 2、命中，返回商户信息
        if (!StrUtil.isBlank(s)) {
            log.info("命中缓存！");
            return JSON.parseObject(s, Shop.class);
        }
        // 2.1 检查是否为空，为空直接返回
        if (s != null) {
            return null;
        }

        // 3、未命中
        Shop shop;
        try {
            // 3.1 尝试获取锁
            if (!tryLock(lockKey)) {
                // 3.2 未获得，休眠一会，重新访问redis缓存
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 3.3 获得锁成功
            // 3.3.1 DoubleCheck缓存, 检查缓存是否存在
            String sD = stringRedisTemplate.opsForValue().get(key);
            if (!StrUtil.isBlank(sD)) {
                log.info("命中缓存！");
                return JSON.parseObject(sD, Shop.class);
            }
            // 3.3.2 检查是否为空，为空直接返回
            if (sD != null) {
                return null;
            }
            // 3.4 查询数据库
            log.info("未命中缓存，查询数据库！");
            shop = getById(id);
            Thread.sleep(200); // 模拟数据库复杂业务
            // 3.4.1 数据库没有商户信息，将空值写入redis
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "");
                return null;
            }
            // 3.4.2 有商户信息，缓存商户信息
            stringRedisTemplate.opsForValue().set(
                    key,
                    JSON.toJSONString(shop),
                    RedisConstants.CACHE_SHOP_TTL,
                    TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException();
        } finally {
            // 释放锁
            unLock(lockKey);
        }
        // 4、返回商铺数据
        return shop;
    }

    private Shop queryWithLogicExpire(Long id) {
        // 1、根据商户id从缓存中取
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        String s = stringRedisTemplate.opsForValue().get(key);
        // 2、未命中，直接返回
        if (StrUtil.isBlank(s)) {
            log.info("没有该热点数据");
            return null;
        }

        // 3、检查过期时间
        RedisData redisData = JSON.parseObject(s, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject jsonObject = (JSONObject) redisData.getData();
        Shop shop = JSON.parseObject(jsonObject.toJSONString(), Shop.class);
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 3.1 未过期，返回商户信息
            log.info("商户信息未过期，命中缓存！");
            return shop;
        }
        if (!tryLock(lockKey)) {
            log.info("未抢到锁，返回旧信息！");
            return shop;
        }
        // 5、成功,缓存重建
        log.info("开始启动另一个线程重建缓存！");
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                savaShopRedis(id, 10);
            } catch (InterruptedException e) {
                    e.printStackTrace();
            } finally {
                unLock(lockKey);
            }
        });

        // 6、返回商铺数据
        return shop;
    }

    // 缓存重建
    public void savaShopRedis(long id, long expireSeconds) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(100);
        if (shop != null) {
            RedisData data = new RedisData();
            data.setData(shop);
            data.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
            stringRedisTemplate.opsForValue().set(
                    RedisConstants.CACHE_SHOP_KEY + id,
                    JSON.toJSONString(data)
                    );
        }
    }

    private boolean tryLock(String lockKey) {
        Boolean flag = stringRedisTemplate.opsForValue().
                setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
        log.info("释放互斥锁！");
    }

    @Override
    @Transactional
    public void updateWithCache(Shop shop) {
        // 先更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
    }
}
