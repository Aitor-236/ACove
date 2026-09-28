package com.aitor.blog.visit.mapper;

import java.time.LocalDate;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.aitor.blog.visit.entity.VisitDailyStat;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface VisitDailyStatMapper extends BaseMapper<VisitDailyStat> {

    /**
     * 当天 PV 加一：没有当天的行就插入 pv=1，有就累加。
     * 走主键的 INSERT ... ON DUPLICATE KEY UPDATE，并发下也不会丢计数。
     */
    @Insert("INSERT INTO visit_daily_stat (stat_date, pv, uv) VALUES (#{statDate}, 1, 0) "
            + "ON DUPLICATE KEY UPDATE pv = pv + 1")
    int increasePv(@Param("statDate") LocalDate statDate);

    /**
     * 当天 UV 加一：只在 visit_visitor 插入成功（当天首次出现）时调用。
     */
    @Update("UPDATE visit_daily_stat SET uv = uv + 1 WHERE stat_date = #{statDate}")
    int increaseUv(@Param("statDate") LocalDate statDate);
}
