package com.nicebao.springboot.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import com.nicebao.springboot.entity.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 原子扣减余额（带余额充足校验）
     *
     * @param userId  用户ID
     * @param amount  扣减金额
     * @return 影响行数，1=成功，0=余额不足
     */
    @Update("UPDATE user SET balance = balance - #{amount} WHERE id = #{userId} AND balance >= #{amount}")
    int decreaseBalance(@Param("userId") Long userId, @Param("amount") java.math.BigDecimal amount);

    /**
     * 原子增加余额
     *
     * @param userId  用户ID
     * @param amount  增加金额
     * @return 影响行数
     */
    @Update("UPDATE user SET balance = balance + #{amount} WHERE id = #{userId}")
    int increaseBalance(@Param("userId") Long userId, @Param("amount") java.math.BigDecimal amount);
}
