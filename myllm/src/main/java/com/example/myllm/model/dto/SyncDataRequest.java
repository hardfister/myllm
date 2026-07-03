package com.example.myllm.model.dto;

import com.example.myllm.model.entity.MemoryConfig;
import com.example.myllm.model.entity.ModelConfig;
import com.example.myllm.model.entity.Rag;
import lombok.Data;
import java.util.List;

@Data
public class SyncDataRequest {
    private List<ModelConfig> models;
    private List<MemoryConfig> memories;
    private List<Rag> rags;
}
