package com.hmdp.controller;

import com.alibaba.fastjson.JSON;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1、重请求头获取token
        String token = request.getHeader("Authorization");
        if (StringUtil.isNullOrEmpty(token)) {
            return true;
        }

        // 2、根据token从redis获取用户信息
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        String o = stringRedisTemplate.opsForValue().get(tokenKey);
        if (StringUtil.isNullOrEmpty(o)) {
            return true;
        }

        // 3、保存用户信息到ThreadLocal
        UserDTO userDTO = JSON.parseObject(o, UserDTO.class);
        UserHolder.saveUser(userDTO);

        // 4、刷新token有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
