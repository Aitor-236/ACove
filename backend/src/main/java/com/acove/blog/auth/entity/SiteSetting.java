package com.acove.blog.auth.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 站点设置（单行配置表，主键固定为 1）。
 */
@Data
@TableName("site_setting")
public class SiteSetting {

    /** 主键，固定为 1：整张表永远只有这一行 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 网站名：浏览器标题和后台侧栏都用它 */
    private String siteName;

    /** 首页头图相对地址（/uploads/hero/xxx），空字符串表示用默认底色 */
    private String heroImage;

    /** 站点图标相对地址（/uploads/favicon/xxx），空字符串表示使用默认图标 */
    private String siteIcon;

    /** 首页中间的文字，空字符串表示回退成网站名 */
    private String heroText;

    /** ICP 备案号，空字符串表示尚未设置 */
    private String icpRecordNumber;

    /** 是否在首页展示 ICP 备案号 */
    private Boolean icpRecordEnabled;

    private LocalDateTime updatedAt;
}
