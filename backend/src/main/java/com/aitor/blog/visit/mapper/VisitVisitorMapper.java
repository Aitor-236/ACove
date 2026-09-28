package com.aitor.blog.visit.mapper;

import java.time.LocalDate;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.aitor.blog.visit.entity.VisitVisitor;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface VisitVisitorMapper extends BaseMapper<VisitVisitor> {

    /**
     * 记录「这个访客今天来过」。唯一键 (stat_date, visitor_key) 命中时忽略，
     * 返回 1 表示当天第一个新访客，UV 才加一；返回 0 表示今天已经来过。
     */
    @Insert("INSERT IGNORE INTO visit_visitor (stat_date, visitor_key) "
            + "VALUES (#{statDate}, #{visitorKey})")
    int insertIgnore(@Param("statDate") LocalDate statDate, @Param("visitorKey") String visitorKey);
}
