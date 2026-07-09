package com.example.myllm.controller;

import com.example.myllm.model.entity.ModelConfig;
import com.example.myllm.service.ModelConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
@CrossOrigin(origins = "*")
public class ModelController {

    private final ModelConfigService modelConfigService;

    public ModelController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @GetMapping
    public List<ModelConfig> getAllModels() {
        return modelConfigService.getAllModels();
    }

    @PostMapping
    public ModelConfig createModel(@RequestBody ModelConfig model) {
        return modelConfigService.createModel(model);
    }

    @PutMapping("/{id}")
    public ModelConfig updateModel(@PathVariable Long id, @RequestBody ModelConfig model) {
        return modelConfigService.updateModel(id, model);
    }

    @DeleteMapping("/{id}")
    public String deleteModel(@PathVariable Long id) {
        modelConfigService.deleteModel(id);
        return "删除成功";
    }

    /** 多选切换 — 不排他 */
    @PutMapping("/{id}/toggle")
    public ModelConfig toggleModel(@PathVariable Long id) {
        return modelConfigService.toggleModel(id);
    }

    /** 批量更新排序 — 拖拽后保存 [{id:1,sortOrder:0},{id:2,sortOrder:1}] */
    @PutMapping("/reorder")
    public String reorder(@RequestBody List<Map<String, Object>> items) {
        modelConfigService.reorder(items);
        return "排序已保存";
    }
}
