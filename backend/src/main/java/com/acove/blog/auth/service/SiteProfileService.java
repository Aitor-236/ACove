package com.acove.blog.auth.service;

import com.acove.blog.auth.dto.SiteOwnerVO;

/** 前台展示用的站点资料（当前只有站长本人）。 */
public interface SiteProfileService {

    /**
     * 取"最高级"的用户作为站长：角色优先级 owner &gt; admin &gt; user，
     * 同优先级取 id 最小的（最早注册的那个）。
     */
    SiteOwnerVO getSiteOwner();
}
