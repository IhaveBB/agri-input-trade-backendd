-- 农资上架模块数据库表结构
-- 请在数据库中执行此SQL脚本

-- 1. 区域表
CREATE TABLE IF NOT EXISTS `region` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '区域ID',
  `name` VARCHAR(50) NOT NULL COMMENT '区域名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序字段',
  `status` INT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='区域表';

-- 2. 季节表
CREATE TABLE IF NOT EXISTS `season` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '季节ID',
  `name` VARCHAR(20) NOT NULL COMMENT '季节名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序字段',
  `status` INT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='季节表';

-- 3. 商品适用作物关联表（作物使用category表的种子分类）
-- 作物数据存储在category表中，level=4表示具体作物品种
CREATE TABLE IF NOT EXISTS `product_crop` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `category_id` BIGINT NOT NULL COMMENT '作物分类ID（对应category表的种子分类四级分类）',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品适用作物关联表';

-- 4. 商品适用区域-季节关联表
CREATE TABLE IF NOT EXISTS `product_region_season` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `region_id` BIGINT NOT NULL COMMENT '区域ID',
  `season_id` BIGINT NOT NULL COMMENT '季节ID',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_region_id` (`region_id`),
  KEY `idx_season_id` (`season_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品适用区域-季节关联表';

-- 6. 为product表添加JSON扩展字段（MySQL 5.7+）
-- 如果product表已存在，执行以下ALTER语句
ALTER TABLE `product`
ADD COLUMN `extra_attributes` JSON DEFAULT NULL COMMENT '扩展属性（JSON格式）' AFTER `place_of_origin`;

-- 添加deleted字段（逻辑删除）
ALTER TABLE `product`
ADD COLUMN `deleted` INT DEFAULT 0 COMMENT '是否删除：0-否，1-是' AFTER `updated_at`;
