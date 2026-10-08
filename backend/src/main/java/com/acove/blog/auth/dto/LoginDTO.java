package com.acove.blog.auth.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;


@Data 
public class LoginDTO {
    // username or email can be used for login
    private String username;
    private String email;

    @NotBlank(message = "Password cannot be blank")
    private String password;
}
