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

@Mapper
public interface RecommendActionMapper extends BaseMapper<RecommendAction> {

    /**
     * 统计指定时间范围内各行为类型的数量
     */
    @Select("<script>" +
            "SELECT action_type, COUNT(*) as count " +
            "FROM recommend_action " +
            "WHERE created_at <![CDATA[>=]]> #{startTime} AND created_at <![CDATA[<]]> #{endTime} " +
            "<if test='source != null and source != &quot;&quot;'> AND source = #{source} </if>" +
            "GROUP BY action_type" +
            "</script>")
    List<Map<String, Object>> countByActionType(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("source") String source);

    /**
     * 统计各推荐来源的行为数量
     */
    @Select("<script>" +
            "SELECT source, action_type, COUNT(*) as count " +
            "FROM recommend_action " +
            "WHERE created_at <![CDATA[>=]]> #{startTime} AND created_at <![CDATA[<]]> #{endTime} " +
            "GROUP BY source, action_type" +
            "</script>")
    List<Map<String, Object>> countBySourceAndAction(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计有推荐行为的独立用户数
     */
    @Select("<script>" +
            "SELECT COUNT(DISTINCT user_id) " +
            "FROM recommend_action " +
            "WHERE created_at <![CDATA[>=]]> #{startTime} AND created_at <![CDATA[<]]> #{endTime} " +
            "<if test='actionType != null and actionType != &quot;&quot;'> AND action_type = #{actionType} </if>" +
            "</script>")
    Long countDistinctUsers(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("actionType") String actionType);

    /**
     * 统计各分类的推荐效果（返回DTO）
     */
    @Select("<script>" +
            "SELECT ra.category_id, c.name as category_name, " +
            "SUM(CASE WHEN ra.action_type = 'EXPOSURE' THEN 1 ELSE 0 END) as exposure_count, " +
            "SUM(CASE WHEN ra.action_type = 'CLICK' THEN 1 ELSE 0 END) as click_count, " +
            "SUM(CASE WHEN ra.action_type = 'BUY' THEN 1 ELSE 0 END) as buy_count " +
            "FROM recommend_action ra " +
            "LEFT JOIN category c ON ra.category_id = c.id " +
            "WHERE ra.created_at <![CDATA[>=]]> #{startTime} AND ra.created_at <![CDATA[<]]> #{endTime} " +
            "GROUP BY ra.category_id, c.name " +
            "ORDER BY exposure_count DESC" +
            "</script>")
    List<RecommendCategoryEffectDTO> selectCategoryEffect(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计各分类的推荐效果（返回Map - 兼容旧代码）
     */
    @Select("<script>" +
            "SELECT ra.category_id, c.name as category_name, " +
            "SUM(CASE WHEN ra.action_type = 'EXPOSURE' THEN 1 ELSE 0 END) as exposure_count, " +
            "SUM(CASE WHEN ra.action_type = 'CLICK' THEN 1 ELSE 0 END) as click_count, " +
            "SUM(CASE WHEN ra.action_type = 'BUY' THEN 1 ELSE 0 END) as buy_count " +
            "FROM recommend_action ra " +
            "LEFT JOIN category c ON ra.category_id = c.id " +
            "WHERE ra.created_at <![CDATA[>=]]> #{startTime} AND ra.created_at <![CDATA[<]]> #{endTime} " +
            "GROUP BY ra.category_id, c.name " +
            "ORDER BY exposure_count DESC" +
            "</script>")
    List<Map<String, Object>> countByCategory(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计每天的推荐效果趋势（返回DTO）
     */
    @Select("<script>" +
            "SELECT DATE(created_at) as date, " +
            "SUM(CASE WHEN action_type = 'EXPOSURE' THEN 1 ELSE 0 END) as exposure_count, " +
            "SUM(CASE WHEN action_type = 'CLICK' THEN 1 ELSE 0 END) as click_count, " +
            "SUM(CASE WHEN action_type = 'BUY' THEN 1 ELSE 0 END) as buy_count " +
            "FROM recommend_action " +
            "WHERE created_at <![CDATA[>=]]> #{startTime} AND created_at <![CDATA[<]]> #{endTime} " +
            "<if test='source != null and source != &quot;&quot;'> AND source = #{source} </if>" +
            "GROUP BY DATE(created_at) " +
            "ORDER BY date" +
            "</script>")
    List<RecommendTrendDTO> selectTrendByDay(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("source") String source);

    /**
     * 统计每天的推荐效果趋势（返回Map - 兼容旧代码）
     */
    @Select("<script>" +
            "SELECT DATE(created_at) as date, " +
            "SUM(CASE WHEN action_type = 'EXPOSURE' THEN 1 ELSE 0 END) as exposure_count, " +
            "SUM(CASE WHEN action_type = 'CLICK' THEN 1 ELSE 0 END) as click_count, " +
            "SUM(CASE WHEN action_type = 'BUY' THEN 1 ELSE 0 END) as buy_count " +
            "FROM recommend_action " +
            "WHERE created_at <![CDATA[>=]]> #{startTime} AND created_at <![CDATA[<]]> #{endTime} " +
            "<if test='source != null and source != &quot;&quot;'> AND source = #{source} </if>" +
            "GROUP BY DATE(created_at) " +
            "ORDER BY date" +
            "</script>")
    List<Map<String, Object>> countTrendByDay(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("source") String source);
}
