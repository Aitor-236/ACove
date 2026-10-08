package com.acove.blog.article.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 文章标签表实体。
 */
@Data
@TableName("tag")
public class Tag {

    /** 标签ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签名称 */
    private String name;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
