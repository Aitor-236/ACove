package com.aitor.blog.visit.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 每日访客去重表实体：(stat_date, visitor_key) 唯一，插入成功才代表当天来了一个新访客。
 */
@Data
@TableName("visit_visitor")
public class VisitVisitor {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计日期 */
    private LocalDate statDate;

    /** 访客标识（Cookie 里 IP+UA 的哈希值） */
    private String visitorKey;

    /** 当天首次来访时间 */
    private LocalDateTime createdAt;
}
