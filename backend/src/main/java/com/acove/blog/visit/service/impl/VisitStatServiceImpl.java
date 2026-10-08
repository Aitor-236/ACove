package com.acove.blog.visit.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acove.blog.visit.dto.VisitDailyVO;
import com.acove.blog.visit.dto.VisitOverviewVO;
import com.acove.blog.visit.entity.VisitDailyStat;
import com.acove.blog.visit.mapper.VisitDailyStatMapper;
import com.acove.blog.visit.mapper.VisitVisitorMapper;
import com.acove.blog.visit.service.VisitStatService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VisitStatServiceImpl implements VisitStatService {

    /** 趋势查询的天数上限，防止一次拉回太多行。 */
    private static final int MAX_TREND_DAYS = 90;

    private final VisitDailyStatMapper visitDailyStatMapper;
    private final VisitVisitorMapper visitVisitorMapper;

    @Override
    @Transactional
    public void recordVisit(String visitorKey) {
        LocalDate today = LocalDate.now();
        visitDailyStatMapper.increasePv(today);
        // 只有当天第一次出现的访客才加 UV，重复访问只累加 PV
        if (visitVisitorMapper.insertIgnore(today, visitorKey) > 0) {
            visitDailyStatMapper.increaseUv(today);
        }
    }

    @Override
    public VisitOverviewVO getOverview() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        Map<LocalDate, VisitDailyStat> stats = loadStats(yesterday, today);
        VisitDailyVO todayVo = VisitDailyVO.from(today, stats.get(today));
        VisitDailyVO yesterdayVo = VisitDailyVO.from(yesterday, stats.get(yesterday));

        VisitOverviewVO vo = new VisitOverviewVO();
        vo.setToday(todayVo);
        vo.setYesterday(yesterdayVo);
        vo.setPvDelta(todayVo.getPv() - yesterdayVo.getPv());
        vo.setUvDelta(todayVo.getUv() - yesterdayVo.getUv());
        vo.setPvGrowth(growth(todayVo.getPv(), yesterdayVo.getPv()));
        vo.setUvGrowth(growth(todayVo.getUv(), yesterdayVo.getUv()));
        return vo;
    }

    @Override
    public List<VisitDailyVO> listRecentDays(int days) {
        int safeDays = days < 1 ? 1 : Math.min(days, MAX_TREND_DAYS);
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(safeDays - 1L);

        Map<LocalDate, VisitDailyStat> stats = loadStats(from, today);

        List<VisitDailyVO> result = new ArrayList<>(safeDays);
        for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
            result.add(VisitDailyVO.from(date, stats.get(date)));
        }
        return result;
    }

    /** 按闭区间 [from, to] 查汇总行，返回按日期索引的 Map。 */
    private Map<LocalDate, VisitDailyStat> loadStats(LocalDate from, LocalDate to) {
        List<VisitDailyStat> rows = visitDailyStatMapper.selectList(
                new LambdaQueryWrapper<VisitDailyStat>()
                        .ge(VisitDailyStat::getStatDate, from)
                        .le(VisitDailyStat::getStatDate, to));
        return rows.stream().collect(Collectors.toMap(
                VisitDailyStat::getStatDate,
                row -> row,
                (left, right) -> left,
                HashMap::new));
    }

    /**
     * 增幅百分比：昨日为 0 时无法计算（返回 null），否则保留两位小数。
     */
    private static Double growth(long current, long previous) {
        if (previous <= 0) {
            return null;
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
