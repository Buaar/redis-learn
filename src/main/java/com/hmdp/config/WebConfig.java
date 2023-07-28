package com.hmdp.config;

import com.hmdp.controller.LoginInterceptor;
import com.hmdp.controller.RefreshTokenInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(refreshTokenInterceptor());

        registry.addInterceptor(loginInterceptor())
                .excludePathPatterns("/user/login",
                        "/user/code",
                        "/shop/**",
                        "/shop-type/**",
                        "/blog/hot",
                        "/voucher/**",
                        "/voucher-order/**");
    }

    @Bean
    public HandlerInterceptor refreshTokenInterceptor() {
        return new RefreshTokenInterceptor();
    }

    @Bean
    public HandlerInterceptor loginInterceptor() {
        return new LoginInterceptor();
    }
}
