package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 * 基于session，服务端生成session的同时，会生成一个sessionId传给前端（请求头setCookies）
 * 前端下次请求携带上cookie：sessionId，服务端根据sessionId获取session
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        // 1、验证手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2、不符合，返回错误信息
            return Result.fail("手机格式错误");
        }
        // 3、符合，生成验证码 利用hutool工具包
        String code = RandomUtil.randomNumbers(6);

        // 4、保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5、发生验证码
        log.debug("发送验证码成功，验证码：{}", code);
        // ok结束流程
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1、验证手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误");
        }
        // 2、验证验证码
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (loginForm.getCode() == null || !loginForm.getCode().equals(code)) {
            return Result.fail("验证码错误");
        }

        // 3、检查用户是否存在
        User user = query().eq("phone", phone).one();

        // 4、创建新用户
        if (user == null) {
            user = creatUserWithPhone(phone);
        }

        // 5、保存用户信息到redis，支持下次登入
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        stringRedisTemplate.opsForValue().set(
                LOGIN_USER_KEY + token,
                JSON.toJSONString(userDTO),
                LOGIN_USER_TTL,
                TimeUnit.MINUTES);

        // 6、登入成功
        log.info("用户登入成功：{}", user.getPhone());
        return Result.ok(token);
    }

    private User creatUserWithPhone(String phone) {
        // 创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(10));
        // 存入数据库
        save(user);
        log.info("用户创建成功，用户为：{}", user.getPhone());
        return user;
    }
}
