package com.acove.blog.auth.service;

import org.springframework.web.multipart.MultipartFile;

import com.acove.blog.auth.dto.SiteSettingDTO;
import com.acove.blog.auth.dto.SiteSettingVO;

/** 站点设置：网站名、首页头图、首页中间的文字。 */
public interface SiteSettingService {

    /** 读取站点设置；数据库里还没有那一行时返回默认值。 */
    SiteSettingVO getSettings();

    /** 保存网站名 / 首页文字，或按空串清除头图；只更新传了值的字段。 */
    SiteSettingVO updateSettings(SiteSettingDTO settingDTO);

    /** 上传一张新的首页头图并保存，返回更新后的设置。 */
    SiteSettingVO updateHeroImage(MultipartFile file);
}
