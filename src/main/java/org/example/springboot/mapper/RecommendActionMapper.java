package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.springboot.entity.RecommendAction;
import org.example.springboot.entity.dto.statistics.RecommendCategoryEffectDTO;
import org.example.springboot.entity.dto.statistics.RecommendTrendDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 推荐行为记录Mapper
 * <p>
 * 提供推荐行为数据的统计查询，支持按商品ID集合过滤（用于商户维度分析）。
 * </p>
 *
 * @author IhaveBB
 * @date 2026/03/22
 */
@Mapper
public interface RecommendActionMapper extends BaseMapper<RecommendAction> {

    /**
     * 统计指定时间范围内各行为类型的数量
     *
     * @param startTime  开始时间
     * @param endTime    结束时间
     * @param source     推荐来源（可为null）
     * @param productIds 商品ID集合（可为null，null时不做过滤）
     * @return 行为类型统计列表
     * @author IhaveBB
     * @date 2026/03/29
     */
    @Select("<script>" +
            "SELECT action_type, COUNT(*) as count " +
            "FROM recommend_action " +
            "WHERE created_at <![CDATA[>=]]> #{startTime} AND created_at <![CDATA[<]]> #{endTime} " +
            "<if test='source != null and source != &quot;&quot;'> AND source = #{source} </if>" +
            "<if test='productIds != null and productIds.size() > 0'> AND product_id IN " +
            "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach></if>" +
            " GROUP BY action_type" +
            "</script>")
    List<Map<String, Object>> countByActionType(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("source") String source,
            @Param("productIds") List<Long> productIds);

    /**
     * 统计各推荐来源的行为数量
     *
     * @param startTime  开始时间
     * @param endTime    结束时间
     * @param productIds 商品ID集合（可为null）
     * @return 来源行为统计列表
     * @author IhaveBB
     * @date 2026/03/29
     */
    @Select("<script>" +
            "SELECT source, action_type, COUNT(*) as count " +
            "FROM recommend_action " +
            "WHERE created_at <![CDATA[>=]]> #{startTime} AND created_at <![CDATA[<]]> #{endTime} " +
            "<if test='productIds != null and productIds.size() > 0'> AND product_id IN " +
            "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach></if>" +
            " GROUP BY source, action_type" +
            "</script>")
    List<Map<String, Object>> countBySourceAndAction(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("productIds") List<Long> productIds);

    /**
     * 统计有推荐行为的独立用户数
     *
     * @param startTime  开始时间
     * @param endTime    结束时间
     * @param actionType 行为类型（可为null）
     * @param productIds 商品ID集合（可为null）
     * @return 独立用户数
     * @author IhaveBB
     * @date 2026/03/29
     */
    @Select("<script>" +
            "SELECT COUNT(DISTINCT user_id) " +
            "FROM recommend_action " +
            "WHERE created_at <![CDATA[>=]]> #{startTime} AND created_at <![CDATA[<]]> #{endTime} " +
            "<if test='actionType != null and actionType != &quot;&quot;'> AND action_type = #{actionType} </if>" +
            "<if test='productIds != null and productIds.size() > 0'> AND product_id IN " +
            "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach></if>" +
            "</script>")
    Long countDistinctUsers(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("actionType") String actionType,
            @Param("productIds") List<Long> productIds);

    /**
     * 统计各分类的推荐效果（返回DTO）
     *
     * @param startTime  开始时间
     * @param endTime    结束时间
     * @param productIds 商品ID集合（可为null）
     * @return 分类推荐效果列表
     * @author IhaveBB
     * @date 2026/03/29
     */
    @Select("<script>" +
            "SELECT ra.category_id, c.name as category_name, " +
            "SUM(CASE WHEN ra.action_type = 'EXPOSURE' THEN 1 ELSE 0 END) as exposure_count, " +
            "SUM(CASE WHEN ra.action_type = 'CLICK' THEN 1 ELSE 0 END) as click_count, " +
            "SUM(CASE WHEN ra.action_type = 'BUY' THEN 1 ELSE 0 END) as buy_count " +
            "FROM recommend_action ra " +
            "LEFT JOIN category c ON ra.category_id = c.id " +
            "WHERE ra.created_at <![CDATA[>=]]> #{startTime} AND ra.created_at <![CDATA[<]]> #{endTime} " +
            "<if test='productIds != null and productIds.size() > 0'> AND ra.product_id IN " +
            "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach></if>" +
            " GROUP BY ra.category_id, c.name " +
            "ORDER BY exposure_count DESC" +
            "</script>")
    List<RecommendCategoryEffectDTO> selectCategoryEffect(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("productIds") List<Long> productIds);

    /**
     * 统计各分类的推荐效果（返回Map - 兼容旧代码）
     *
     * @param startTime  开始时间
     * @param endTime    结束时间
     * @param productIds 商品ID集合（可为null）
     * @return 分类统计列表
     * @author IhaveBB
     * @date 2026/03/29
     */
    @Select("<script>" +
            "SELECT ra.category_id, c.name as category_name, " +
            "SUM(CASE WHEN ra.action_type = 'EXPOSURE' THEN 1 ELSE 0 END) as exposure_count, " +
            "SUM(CASE WHEN ra.action_type = 'CLICK' THEN 1 ELSE 0 END) as click_count, " +
            "SUM(CASE WHEN ra.action_type = 'BUY' THEN 1 ELSE 0 END) as buy_count " +
            "FROM recommend_action ra " +
            "LEFT JOIN category c ON ra.category_id = c.id " +
            "WHERE ra.created_at <![CDATA[>=]]> #{startTime} AND ra.created_at <![CDATA[<]]> #{endTime} " +
            "<if test='productIds != null and productIds.size() > 0'> AND ra.product_id IN " +
            "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach></if>" +
            " GROUP BY ra.category_id, c.name " +
            "ORDER BY exposure_count DESC" +
            "</script>")
    List<Map<String, Object>> countByCategory(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("productIds") List<Long> productIds);

    /**
     * 统计每天的推荐效果趋势（返回DTO）
     *
     * @param startTime  开始时间
     * @param endTime    结束时间
     * @param source     推荐来源（可为null）
     * @param productIds 商品ID集合（可为null）
     * @return 每日趋势列表
     * @author IhaveBB
     * @date 2026/03/29
     */
    @Select("<script>" +
            "SELECT DATE(created_at) as date, " +
            "SUM(CASE WHEN action_type = 'EXPOSURE' THEN 1 ELSE 0 END) as exposure_count, " +
            "SUM(CASE WHEN action_type = 'CLICK' THEN 1 ELSE 0 END) as click_count, " +
            "SUM(CASE WHEN action_type = 'BUY' THEN 1 ELSE 0 END) as buy_count " +
            "FROM recommend_action " +
            "WHERE created_at <![CDATA[>=]]> #{startTime} AND created_at <![CDATA[<]]> #{endTime} " +
            "<if test='source != null and source != &quot;&quot;'> AND source = #{source} </if>" +
            "<if test='productIds != null and productIds.size() > 0'> AND product_id IN " +
            "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach></if>" +
            " GROUP BY DATE(created_at) " +
            "ORDER BY date" +
            "</script>")
    List<RecommendTrendDTO> selectTrendByDay(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("source") String source,
            @Param("productIds") List<Long> productIds);

    /**
     * 统计每天的推荐效果趋势（返回Map - 兼容旧代码）
     *
     * @param startTime  开始时间
     * @param endTime    结束时间
     * @param source     推荐来源（可为null）
     * @param productIds 商品ID集合（可为null）
     * @return 每日趋势列表
     * @author IhaveBB
     * @date 2026/03/29
     */
    @Select("<script>" +
            "SELECT DATE(created_at) as date, " +
            "SUM(CASE WHEN action_type = 'EXPOSURE' THEN 1 ELSE 0 END) as exposure_count, " +
            "SUM(CASE WHEN action_type = 'CLICK' THEN 1 ELSE 0 END) as click_count, " +
            "SUM(CASE WHEN action_type = 'BUY' THEN 1 ELSE 0 END) as buy_count " +
            "FROM recommend_action " +
            "WHERE created_at <![CDATA[>=]]> #{startTime} AND created_at <![CDATA[<]]> #{endTime} " +
            "<if test='source != null and source != &quot;&quot;'> AND source = #{source} </if>" +
            "<if test='productIds != null and productIds.size() > 0'> AND product_id IN " +
            "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach></if>" +
            " GROUP BY DATE(created_at) " +
            "ORDER BY date" +
            "</script>")
    List<Map<String, Object>> countTrendByDay(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("source") String source,
            @Param("productIds") List<Long> productIds);
}
