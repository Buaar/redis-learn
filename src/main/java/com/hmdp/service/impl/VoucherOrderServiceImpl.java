package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorkId;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.Wrapper;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisWorkId workId;

    @Override
    public Result seckillVoucher(long voucherId) {
        // 查询秒杀券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        // 判断是否在活动期间内
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("活动还未开始");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("活动已经结束");
        }

        // 是否有库存
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("没有库存了");
        }

//        // 检查订单是否存在  用户ID + 优惠券ID
//        QueryWrapper<VoucherOrder> wrapper = new QueryWrapper<>();
//        wrapper.eq("user_id", UserHolder.getUser().getId()).eq("voucher_id", voucherId);
//        int count = count(wrapper);
//        if (count > 1) {
//            return Result.fail("一人仅限一单");
//        }

        // 库存减一
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }


        // 创建秒杀券订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = workId.getNextId("order");
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        save(voucherOrder);

        return Result.ok(orderId);
    }
}
