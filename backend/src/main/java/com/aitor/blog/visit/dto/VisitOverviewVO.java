package com.aitor.blog.visit.dto;

import lombok.Data;

/**
 * 后台访问统计概览：今日 / 昨日两天的数据 + 对比增幅。
 */
@Data
public class VisitOverviewVO {

    /** 今日访问量 */
    private VisitDailyVO today;

    /** 昨日访问量 */
    private VisitDailyVO yesterday;

    /** PV 绝对增量（今日 - 昨日） */
    private long pvDelta;

    /** UV 绝对增量（今日 - 昨日） */
    private long uvDelta;

    /** PV 增幅百分比，保留两位小数；昨日为 0 时是 null（没法算增幅，前端显示“—”而不是 0%） */
    private Double pvGrowth;

    /** UV 增幅百分比，保留两位小数；昨日为 0 时是 null */
    private Double uvGrowth;
}
