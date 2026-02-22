-- 农资分类模块数据库表结构
-- 请在数据库中执行此SQL脚本

-- 分类表
CREATE TABLE IF NOT EXISTS `category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name` VARCHAR(100) NOT NULL COMMENT '分类名称',
  `icon` VARCHAR(255) DEFAULT NULL COMMENT '分类图标',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '分类描述',
  `parent_id` BIGINT DEFAULT 0 COMMENT '父分类ID，顶级分类为0',
  `level` INT NOT NULL DEFAULT 1 COMMENT '分类层级：1-一级分类，2-二级分类，3-三级分类',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序字段，数字越小越靠前',
  `status` INT NOT NULL DEFAULT 1 COMMENT '分类状态：0-禁用，1-启用',
  `is_custom` INT NOT NULL DEFAULT 0 COMMENT '是否为商家自定义分类：0-系统预置，1-商家自定义',
  `create_user_id` BIGINT DEFAULT NULL COMMENT '创建用户ID',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_level` (`level`),
  KEY `idx_status` (`status`),
  KEY `idx_is_custom` (`is_custom`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- 初始化预置分类数据（系统启动时会自动初始化，这里仅作参考）
-- 注意：如果数据库已有数据，CategoryDataInitializer会自动跳过初始化
