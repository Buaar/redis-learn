package com.hmdp;

import com.hmdp.utils.RedisWorkId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private RedisWorkId redisWorkId;

    private ExecutorService threadPool = Executors.newFixedThreadPool(500);

    @Test
    void testWorkId() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        // 1、创建任务，每个任务执行 100 次订单
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisWorkId.getNextId("order");
                System.out.println(id);
            }
            latch.countDown();
        };

        long start = System.currentTimeMillis();

        // 2、执行 300 个任务
        for (int i = 0; i < 300; i++) {
            threadPool.submit(task);
        }

        latch.await();
        long end = System.currentTimeMillis();

        // 3、计算用时
        System.out.println(end - start);
    }
}
