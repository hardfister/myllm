package com.example.myllm.controller;

import com.example.myllm.model.dto.ModelSetting;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/model")
public class ModelController {

    @PostMapping("/switch")
    public String switchModel(@RequestBody ModelSetting setting) {
        // TODO: 实现模型切换逻辑
        return "模型切换成功";
    }
}
