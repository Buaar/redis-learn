package com.hmdp.controller;

import com.alibaba.fastjson.JSON;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 从threadLocal获取用户信息
        UserDTO user = UserHolder.getUser();

        // 没有用户信息，拦截
        if (user == null) {
            response.getWriter().write(JSON.toJSONString(Result.fail("NoLOGIN!")));
            return false;
        }

        // 放行
        return true;
    }
}
