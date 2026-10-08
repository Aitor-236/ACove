package com.acove.blog.visit.dto;

import java.time.LocalDate;

import com.acove.blog.visit.entity.VisitDailyStat;

import lombok.Data;

/**
 * 某一天的访问量（PV / UV）。当天没有数据时按 0 返回，前端画图不用再补空洞。
 */
@Data
public class VisitDailyVO {

    /** 统计日期 */
    private LocalDate date;

    /** 浏览量 PV */
    private long pv;

    /** 独立访客数 UV */
    private long uv;

    public VisitDailyVO() {
    }

    public VisitDailyVO(LocalDate date, long pv, long uv) {
        this.date = date;
        this.pv = pv;
        this.uv = uv;
    }

    /**
     * 由汇总行构建，stat 为 null（当天没有任何访问）时 PV / UV 都是 0。
     */
    public static VisitDailyVO from(LocalDate date, VisitDailyStat stat) {
        if (stat == null) {
            return new VisitDailyVO(date, 0L, 0L);
        }
        return new VisitDailyVO(
                date,
                stat.getPv() == null ? 0L : stat.getPv(),
                stat.getUv() == null ? 0L : stat.getUv());
    }
}
