package com.example.myllm.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库切片/向量化过程中的错误详情。
 * 以 JSON 格式存于 Rag.errorDetail 列，前端可解析展示。
 */
public class RagErrorDetail {
    /** 当前失败的步骤：read_file / extract_text / chunk / embedding */
    public String step;
    /** 错误码：ENCODING_ERROR / EMPTY_FILE / CHUNK_ERROR / NO_CHUNKS / ALL_EMBED_FAILED / PARTIAL_SUCCESS */
    public String code;
    /** 人类可读的错误消息 */
    public String message;
    /** 总切片数 */
    public int chunkTotal;
    /** 成功的切片数 */
    public int chunkSuccess;
    /** 每切片的日志 */
    public List<ChunkLog> chunkLogs = new ArrayList<>();
    /** Chroma 集合名 */
    public String collectionName;
    /** 嵌入模型名称 */
    public String embeddingModelName;

    public static class ChunkLog {
        public int index;
        public int size;
        public boolean success;
        public String error;

        public ChunkLog() {}
        public ChunkLog(int index, int size) { this.index = index; this.size = size; }
    }
}
