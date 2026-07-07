package com.example.myllm.controller;

import com.example.myllm.model.entity.Rag;
import com.example.myllm.service.RagService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档控制器
 * -----------
 * POST   /api/rags              — 上传文档（multipart: file + 切片参数）
 * GET    /api/rags              — 获取全部文档列表
 * PUT    /api/rags/{id}         — 更新文档元数据 / 切片配置
 * DELETE /api/rags/{id}         — 删除文档及磁盘文件
 * PUT    /api/rags/{id}/toggle  — 启用/禁用切换
 */
@RestController
@RequestMapping("/api/rags")
@CrossOrigin(origins = "*")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /** 获取全部文档列表 */
    @GetMapping
    public List<Rag> getAllRags() {
        return ragService.getAllRags();
    }

    /**
     * 上传文档 + 自定义切片配置
     * @param file           上传文件
     * @param collectionName 向量集合名称（默认 "default_collection"）
     * @param chunkSize      切片大小-字符数（默认 500）
     * @param chunkOverlap   切片重叠-字符数（默认 50）
     * @param chunkMethod    切片方式：fixed_size / paragraph / sentence（默认 fixed_size）
     */
    @PostMapping
    public Rag createRag(@RequestParam("file") MultipartFile file,
                         @RequestParam(value = "collectionName", required = false) String collectionName,
                         @RequestParam(value = "chunkSize", required = false) Integer chunkSize,
                         @RequestParam(value = "chunkOverlap", required = false) Integer chunkOverlap,
                         @RequestParam(value = "chunkMethod", required = false) String chunkMethod) throws Exception {
        return ragService.createRag(file, collectionName, chunkSize, chunkOverlap, chunkMethod);
    }

    /** 更新文档元数据 + 切片配置 */
    @PutMapping("/{id}")
    public Rag updateRag(@PathVariable Long id, @RequestBody Rag rag) {
        return ragService.updateRag(id, rag);
    }

    /** 删除文档 */
    @DeleteMapping("/{id}")
    public String deleteRag(@PathVariable Long id) {
        ragService.deleteRag(id);
        return "删除成功";
    }

    /** 启用/禁用 */
    @PutMapping("/{id}/toggle")
    public Rag toggleRag(@PathVariable Long id) {
        return ragService.toggleRag(id);
    }
}
