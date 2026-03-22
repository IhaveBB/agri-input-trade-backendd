package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.springboot.entity.*;
import org.example.springboot.entity.dto.LoginDTO;
import org.example.springboot.entity.dto.UserRegisterDTO;
import org.example.springboot.entity.dto.UserUpdateDTO;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.enumClass.AccountStatus;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.*;
import org.example.springboot.util.JwtTokenUtils;
import org.example.springboot.util.MenusUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private MenuMapper menuMapper;
    @Value("${user.defaultPassword}")
    private String DEFAULT_PWD;



    @Resource
    private AddressMapper addressMapper;
    @Resource
    private OrderMapper orderMapper;
    @Resource
    private ProductMapper productMapper;
@Resource
private ReviewMapper reviewMapper;
@Resource
private CartMapper cartMapper;



@Resource
private FavoriteMapper favoriteMapper;

    @Resource
    private PasswordEncoder bCryptPasswordEncoder;

    @Resource
    private StockInMapper stockInMapper;

    @Resource
    private LogisticsMapper logisticsMapper;


    /**
     * 根据邮箱查询用户
     *
     * @param email 邮箱地址
     * @return 用户实体，不存在时返回null
     * @author IhaveBB
     * @date 2026/03/22
     */
    public User getByEmail(String email){
        LambdaQueryWrapper<User> studentLambdaQueryWrapper = new LambdaQueryWrapper<User>().eq(User::getEmail, email);
        return userMapper.selectOne(studentLambdaQueryWrapper);
    }

    /**
     * 用户登录（使用 DTO）
     *
     * @param dto 登录信息
     * @return 登录成功的用户实体（含token和菜单权限）
     * @author IhaveBB
     * @date 2026/03/22
     */
    public User login(LoginDTO dto){
        // getByUsername 找不到用户时直接抛异常，此处无需再判空
        User compare = getByUsername(dto.getUsername());
        if(compare.getStatus() != null && compare.getStatus().equals(AccountStatus.DISABLED.getValue())){
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "账号被禁用");
        }
        if(bCryptPasswordEncoder.matches(dto.getPassword(), compare.getPassword())){
            List<Menu> roleMenuList = menuMapper.selectList(null);
            String token = JwtTokenUtils.genToken(String.valueOf(compare.getId()), compare.getPassword());
            compare.setMenuList(MenusUtils.allocMenus(roleMenuList,compare.getRole()));
            compare.setToken(token);
            return compare;
        }
        throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "用户名或密码错误");
    }

    /**
     * 用户注册（使用 DTO）
     *
     * @param dto 用户注册信息
     * @return 创建后的用户实体
     * @author IhaveBB
     * @date 2026/03/22
     */
    public User createUser(UserRegisterDTO dto) {
        if (userMapper.selectOne(new QueryWrapper<User>().eq("username", dto.getUsername())) != null) {
            throw new BusinessException(ErrorCodeEnum.ALREADY_EXISTS, "用户名已存在");
        }
        if (dto.getEmail() != null && userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, dto.getEmail())) != null) {
            throw new BusinessException(ErrorCodeEnum.ALREADY_EXISTS, "邮箱已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(bCryptPasswordEncoder.encode(dto.getPassword()));
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setLocation(dto.getLocation());
        user.setInterestedCrops(dto.getInterestedCrops());
        user.setRole(StringUtils.isNotBlank(dto.getRole()) ? dto.getRole() : "USER");
        user.setStatus(AccountStatus.ENABLED.getValue());

        int result = userMapper.insert(user);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "创建用户失败");
        }
        return user;
    }

    /**
     * 更新用户信息（使用 DTO）
     *
     * @param id          用户ID
     * @param dto         用户更新信息
     * @param currentRole 当前操作者角色（用于权限判断）
     * @return 更新后的用户实体
     * @author IhaveBB
     * @date 2026/03/22
     */
    public User updateUser(Long id, UserUpdateDTO dto, String currentRole) {
        User existingUser = userMapper.selectById(id);
        if (existingUser == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }

        // 更新允许修改的字段
        if (dto.getName() != null) {
            existingUser.setName(dto.getName());
        }
        if (dto.getEmail() != null) {
            // 检查邮箱是否已被其他用户使用
            User userWithEmail = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, dto.getEmail())
                    .ne(User::getId, id));
            if (userWithEmail != null) {
                throw new BusinessException(ErrorCodeEnum.ALREADY_EXISTS, "邮箱已被使用");
            }
            existingUser.setEmail(dto.getEmail());
        }
        if (dto.getLocation() != null) {
            existingUser.setLocation(dto.getLocation());
        }
        if (dto.getInterestedCrops() != null) {
            existingUser.setInterestedCrops(dto.getInterestedCrops());
        }

        // 只有管理员可以修改状态
        if (dto.getStatus() != null && "ADMIN".equals(currentRole)) {
            existingUser.setStatus(dto.getStatus());
        }

        int result = userMapper.updateById(existingUser);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "更新用户失败");
        }
        return existingUser;
    }

    /**
     * 根据角色获取用户列表
     *
     * @param role 角色标识
     * @return 该角色下的用户列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    public List<User> getUserByRole(String role) {
        LambdaQueryWrapper<User> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(User::getRole, role);
        return userMapper.selectList(queryWrapper);
    }

    /**
     * 根据ID删除用户及其所有关联数据
     * <p>
     * 先执行库存和商品关联检查，全部通过后再删除关联数据，避免检查失败时数据已被误删。
     * </p>
     *
     * @param id 用户ID
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserById(int id) {
        // 先执行所有前置检查，通过后再删除关联数据，避免检查失败时数据已被误删
        if(!checkStockRelation(id)){
            throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "删除失败，请检查关联库存");
        }
        if(!checkUserProducts(id)){
            throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "删除失败，请检查关联商品");
        }
        deleteUserRelations(id);
        int result = userMapper.deleteById(id);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "删除用户失败");
        }
    }

    /**
     * 根据用户名查询用户，不存在时抛出异常
     *
     * @param username 用户名
     * @return 用户实体
     * @author IhaveBB
     * @date 2026/03/22
     */
    public User getByUsername(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 忘记密码 - 通过邮箱重置密码
     *
     * @param email       用户邮箱
     * @param newPassword 新密码（明文，方法内部加密存储）
     * @author IhaveBB
     * @date 2026/03/22
     */
    public void forgetPassword(String email, String newPassword) {
        User oldUser = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (oldUser == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND, "用户不存在");
        }
        oldUser.setPassword(bCryptPasswordEncoder.encode(newPassword));
        int result = userMapper.updateById(oldUser);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "密码重置失败");
        }
    }

    /**
     * 更新用户所在位置
     *
     * @param userId   用户ID
     * @param location 位置字符串
     * @author IhaveBB
     * @date 2026/03/22
     */
    public void updateUserLocation(Long userId, String location) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        user.setLocation(location);
        int result = userMapper.updateById(user);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "更新位置失败");
        }
    }

    /**
     * 修改密码（需验证旧密码）
     *
     * @param id                 用户ID
     * @param userPasswordUpdate 包含旧密码和新密码的更新对象
     * @author IhaveBB
     * @date 2026/03/22
     */
    public void updatePassword(int id, UserPasswordUpdate userPasswordUpdate) {
        User oldUser = userMapper.selectById(id);
        if (oldUser == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        if (!bCryptPasswordEncoder.matches(userPasswordUpdate.getOldPassword(), oldUser.getPassword())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "原密码错误");
        }
        oldUser.setPassword(bCryptPasswordEncoder.encode(userPasswordUpdate.getNewPassword()));
        int result = userMapper.updateById(oldUser);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "密码修改失败");
        }
    }

    /**
     * 批量删除用户及其所有关联数据
     * <p>
     * 先对所有用户执行前置检查，全部通过后再统一删除关联数据。
     * </p>
     *
     * @param ids 用户ID列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Integer> ids) {
        // 先对所有用户执行前置检查，全部通过后再统一删除关联数据
        for (Integer id : ids) {
            if(!checkStockRelation(id)){
                throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "删除失败，请检查关联库存");
            }
            if(!checkUserProducts(id)){
                throw new BusinessException(ErrorCodeEnum.HAS_ASSOCIATED_DATA, "删除失败，请检查关联商品");
            }
        }
        for (Integer id : ids) {
            deleteUserRelations(id);
        }
        int result = userMapper.deleteByIds(ids);
        if (result <= 0) {
            throw new BusinessException(ErrorCodeEnum.ERROR, "批量删除用户失败");
        }
    }

    /**
     * 获取所有用户列表
     *
     * @return 全量用户列表
     * @author IhaveBB
     * @date 2026/03/22
     */
    public List<User> getAllUsers() {
        return userMapper.selectList(new QueryWrapper<>());
    }

    /**
     * 根据ID获取用户，账号禁用时抛出异常
     *
     * @param id 用户ID
     * @return 用户实体
     * @author IhaveBB
     * @date 2026/03/22
     */
    public User getUserById(int id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        if (!user.getStatus().equals(AccountStatus.ENABLED.getValue())) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN, "账号已被禁用");
        }
        return user;
    }

    /**
     * 分页查询用户列表，支持用户名、姓名、角色、状态过滤
     *
     * @param username    用户名（模糊查询）
     * @param name        姓名（模糊查询）
     * @param role        角色
     * @param status      账号状态
     * @param currentPage 当前页码
     * @param size        每页条数
     * @return 分页用户列表（密码已隐藏）
     * @author IhaveBB
     * @date 2026/03/22
     */
    public Page<User> getUsersByPage(String username, String name, String role, String status, Integer currentPage, Integer size) {
        LambdaQueryWrapper<User> queryWrapper = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(username)) {
            queryWrapper.like(User::getUsername, username);
        }

        if (StringUtils.isNotBlank(name)) {
            queryWrapper.like(User::getName, name);
        }
        if (StringUtils.isNotBlank(role)) {
            queryWrapper.eq(User::getRole, role);
        }
        if (StringUtils.isNotBlank(status)) {
            queryWrapper.eq(User::getStatus, status);
        }
        Page<User> page = new Page<>(currentPage, size);
        Page<User> result = userMapper.selectPage(page, queryWrapper);

        // 隐藏密码
        if (result != null && result.getRecords() != null) {
            for (User user : result.getRecords()) {
                user.setPassword(null);
            }
        }

        return result;
    }

    private boolean checkUserProducts(int id){
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>().eq(Product::getMerchantId, id));
        for (Product product : products) {
            if(product!=null){
                return false;

            }
        }
        return true;
    }

    private void deleteUserRelations(int id){

                List<Address> addresses = addressMapper.selectList(new LambdaQueryWrapper<Address>().eq(Address::getUserId, id));
        for (Address address : addresses) {
            if(address!=null){
                addressMapper.deleteById(address);
            }
        }

        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>().eq(Order::getUserId, id));
        for (Order order : orders) {
            if(order!=null){
                // 先删除该订单关联的物流记录，避免产生孤立数据
                logisticsMapper.delete(new LambdaQueryWrapper<Logistics>().eq(Logistics::getOrderId, order.getId()));
                orderMapper.deleteById(order);
            }
        }

        List<Review> reviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>().eq(Review::getUserId, id));
        for (Review review : reviews) {
            if(review!=null){
                reviewMapper.deleteById(review);
            }
        }
        List<Cart> carts = cartMapper.selectList(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, id));
        for (Cart cart : carts) {
            if(cart!=null){
                cartMapper.deleteById(cart);
            }
        }

        List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, id));
        for (Favorite favorite : favorites) {
            if(favorite!=null){
                favoriteMapper.deleteById(favorite);
            }
        }

    }

    private Boolean checkStockRelation(int id){
      List<StockIn> stockIns = stockInMapper.selectList(new LambdaQueryWrapper<StockIn>().eq(StockIn::getOperatorId, id));
      for (StockIn stockIn : stockIns) {
          if(stockIn!=null){
                return false;
          }
      }
      return true;
    }

    private void deleteProductRelations(Long productId){
        orderMapper.delete(new LambdaQueryWrapper<Order>().eq(Order::getProductId,productId));
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>().eq(Favorite::getProductId,productId));
        cartMapper.delete(new LambdaQueryWrapper<Cart>().eq(Cart::getProductId,productId));
        reviewMapper.delete(new LambdaQueryWrapper<Review>().eq(Review::getProductId,productId));

    }
}
