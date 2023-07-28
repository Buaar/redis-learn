package com.hmdp.service.impl;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest  // 将测试类也交给IOC容器管理
@RunWith(SpringRunner.class)  // 启动前，启动SpringBoot上下文
public class ShopServiceImplTest {
    @Autowired
    private ShopServiceImpl shopService;

    @Test
    public void savaShopRedis() throws InterruptedException {
        shopService.savaShopRedis(1, 10);
    }
}