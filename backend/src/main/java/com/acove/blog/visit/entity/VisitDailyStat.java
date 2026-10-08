package com.acove.blog.visit.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 每日访问汇总表实体：一天一行，PV 每次上报加一，UV 按访客标识去重后加一。
 */
@Data
@TableName("visit_daily_stat")
public class VisitDailyStat {

    /** 统计日期，主键（由业务写入，不是自增） */
    @TableId(value = "stat_date", type = IdType.INPUT)
    private LocalDate statDate;

    /** 当日浏览量 PV */
    private Long pv;

    /** 当日独立访客数 UV */
    private Long uv;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
