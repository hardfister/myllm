package com.example.myllm.controller;

import com.example.myllm.model.entity.ModelConfig;
import com.example.myllm.service.ModelConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PutMapping("/{id}/activate")
    public ModelConfig activateModel(@PathVariable Long id) {
        return modelConfigService.activateModel(id);
    }
}
