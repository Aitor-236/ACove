package com.acove.blog.auth.service;

import com.acove.blog.auth.dto.LoginDTO;
import com.acove.blog.auth.dto.LoginVO;

public interface AuthService {

    /** 校验账号密码并签发 token，失败时抛业务异常。 */
    LoginVO login(LoginDTO loginDTO);
}
