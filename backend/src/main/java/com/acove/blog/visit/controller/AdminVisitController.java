package com.acove.blog.visit.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acove.blog.common.result.Result;
import com.acove.blog.visit.dto.VisitDailyVO;
import com.acove.blog.visit.dto.VisitOverviewVO;
import com.acove.blog.visit.service.VisitStatService;

import lombok.RequiredArgsConstructor;

/**
 * 后台访问统计接口，需要登录（不在白名单里）。
 */
@RestController
@RequestMapping("/admin/visit")
@RequiredArgsConstructor
public class AdminVisitController {

    private final VisitStatService visitStatService;

    /** 概览：今日 / 昨日的 PV、UV 与对比增幅，后台统计卡片用。 */
    @GetMapping("/overview")
    public Result<VisitOverviewVO> overview() {
        return Result.success(visitStatService.getOverview());
    }

    /** 最近若干天的每日 PV / UV，趋势图用；days 默认 30，上限 90。 */
    @GetMapping("/trend")
    public Result<List<VisitDailyVO>> trend(@RequestParam(defaultValue = "30") int days) {
        return Result.success(visitStatService.listRecentDays(days));
    }
}
