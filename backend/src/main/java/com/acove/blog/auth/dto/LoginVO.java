package com.acove.blog.auth.dto;

import lombok.Data;

@Data 
public class LoginVO {
    private String token;
    private String username;
    private String email;

    public LoginVO(String token, String username, String email) {
        this.token = token;
        this.username = username;
        this.email = email;
    }
}
