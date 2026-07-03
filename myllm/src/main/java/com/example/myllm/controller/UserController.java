package com.example.myllm.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @GetMapping
    public String getUsers() {
        return "用户管理接口（待实现）";
    }
}
