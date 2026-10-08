package com.acove.blog.visit.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.acove.blog.visit.dto.VisitDailyVO;
import com.acove.blog.visit.dto.VisitOverviewVO;
import com.acove.blog.visit.entity.VisitDailyStat;
import com.acove.blog.visit.mapper.VisitDailyStatMapper;
import com.acove.blog.visit.mapper.VisitVisitorMapper;

class VisitStatServiceImplTest {

    private VisitDailyStatMapper visitDailyStatMapper;
    private VisitVisitorMapper visitVisitorMapper;
    private VisitStatServiceImpl visitStatService;

    private final LocalDate today = LocalDate.now();
    private final LocalDate yesterday = today.minusDays(1);

    @BeforeEach
    void setUp() {
        visitDailyStatMapper = mock(VisitDailyStatMapper.class);
        visitVisitorMapper = mock(VisitVisitorMapper.class);
        visitStatService = new VisitStatServiceImpl(visitDailyStatMapper, visitVisitorMapper);
    }

    @Test
    void recordVisit_countsPvAndAddsUvOnlyForNewVisitor() {
        when(visitVisitorMapper.insertIgnore(today, "key-new")).thenReturn(1);
        when(visitVisitorMapper.insertIgnore(today, "key-repeat")).thenReturn(0);

        visitStatService.recordVisit("key-new");
        visitStatService.recordVisit("key-repeat");

        // 两次上报都算 PV
        verify(visitDailyStatMapper, times(2)).increasePv(today);
        // 只有当天第一次出现的访客才算 UV
        verify(visitDailyStatMapper, times(1)).increaseUv(today);
    }

    @Test
    void recordVisit_neverAddsUvWhenVisitorAlreadySeenToday() {
        when(visitVisitorMapper.insertIgnore(any(LocalDate.class), any())).thenReturn(0);

        visitStatService.recordVisit("key-repeat");

        verify(visitDailyStatMapper).increasePv(today);
        verify(visitDailyStatMapper, never()).increaseUv(any(LocalDate.class));
    }

    @Test
    void getOverview_returnsDeltasAndGrowthAgainstYesterday() {
        when(visitDailyStatMapper.selectList(any()))
                .thenReturn(List.of(stat(today, 4L, 2L), stat(yesterday, 2L, 1L)));

        VisitOverviewVO overview = visitStatService.getOverview();

        assertEquals(4L, overview.getToday().getPv());
        assertEquals(2L, overview.getToday().getUv());
        assertEquals(2L, overview.getYesterday().getPv());
        assertEquals(1L, overview.getYesterday().getUv());
        assertEquals(2L, overview.getPvDelta());
        assertEquals(1L, overview.getUvDelta());
        assertEquals(100.0, overview.getPvGrowth().doubleValue());
        assertEquals(100.0, overview.getUvGrowth().doubleValue());
    }

    @Test
    void getOverview_growthIsNullWhenYesterdayHasNoData() {
        when(visitDailyStatMapper.selectList(any())).thenReturn(List.of(stat(today, 4L, 2L)));

        VisitOverviewVO overview = visitStatService.getOverview();

        assertEquals(4L, overview.getPvDelta());
        assertEquals(2L, overview.getUvDelta());
        // 昨日为 0 时算不出增幅，前端显示“—”
        assertNull(overview.getPvGrowth());
        assertNull(overview.getUvGrowth());
    }

    @Test
    void getOverview_roundsGrowthToTwoDecimalsAndAllowsNegative() {
        when(visitDailyStatMapper.selectList(any()))
                .thenReturn(List.of(stat(today, 1L, 1L), stat(yesterday, 3L, 3L)));

        VisitOverviewVO overview = visitStatService.getOverview();

        // (1 - 3) / 3 * 100 = -66.666...%
        assertEquals(-66.67, overview.getPvGrowth().doubleValue());
        assertEquals(-66.67, overview.getUvGrowth().doubleValue());
    }

    @Test
    void getOverview_handlesMissingTodayRowAsZero() {
        when(visitDailyStatMapper.selectList(any())).thenReturn(List.of());

        VisitOverviewVO overview = visitStatService.getOverview();

        assertEquals(0L, overview.getToday().getPv());
        assertEquals(0L, overview.getToday().getUv());
        assertEquals(today, overview.getToday().getDate());
        assertEquals(yesterday, overview.getYesterday().getDate());
    }

    @Test
    void listRecentDays_fillsMissingDaysWithZeroInAscendingOrder() {
        when(visitDailyStatMapper.selectList(any())).thenReturn(List.of(stat(today, 7L, 3L)));

        List<VisitDailyVO> trend = visitStatService.listRecentDays(3);

        assertEquals(3, trend.size());
        assertEquals(today.minusDays(2), trend.get(0).getDate());
        assertEquals(0L, trend.get(0).getPv());
        assertEquals(0L, trend.get(1).getPv());
        assertEquals(7L, trend.get(2).getPv());
        assertEquals(3L, trend.get(2).getUv());
        assertEquals(today, trend.get(2).getDate());
    }

    @Test
    void listRecentDays_clampsDaysIntoRange() {
        when(visitDailyStatMapper.selectList(any())).thenReturn(List.of());

        assertEquals(1, visitStatService.listRecentDays(0).size());
        assertEquals(1, visitStatService.listRecentDays(-5).size());
        assertEquals(90, visitStatService.listRecentDays(500).size());
    }

    private static VisitDailyStat stat(LocalDate date, Long pv, Long uv) {
        VisitDailyStat stat = new VisitDailyStat();
        stat.setStatDate(date);
        stat.setPv(pv);
        stat.setUv(uv);
        return stat;
    }
}
