package com.aitor.blog.visit.service;

import java.util.List;

import com.aitor.blog.visit.dto.VisitDailyVO;
import com.aitor.blog.visit.dto.VisitOverviewVO;

public interface VisitStatService {

    /**
     * 记录一次页面浏览：当天 PV 加一，访客当天第一次出现时 UV 也加一。
     *
     * @param visitorKey 访客标识，由 {@code VisitorKeyResolver} 从 Cookie / 请求头算出来
     */
    void recordVisit(String visitorKey);

    /**
     * 后台统计概览：今日与昨日的 PV / UV 及对比增幅。
     */
    VisitOverviewVO getOverview();

    /**
     * 最近若干天的每日访问量，按日期升序，含今天，没有数据的日子补 0。
     *
     * @param days 天数，小于 1 按 1 处理，超过上限按上限处理
     */
    List<VisitDailyVO> listRecentDays(int days);
}
