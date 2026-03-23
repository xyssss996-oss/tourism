package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI聊天消息实体类
 * 
 * @author AI Assistant
 */
@Data
@TableName("ai_chat_message")
@Schema(description = "AI聊天消息实体类")
public class AiChatMessage {
    
    @TableId(type = IdType.AUTO)
    @Schema(description = "消息ID")
    private Long id;
    
    @Schema(description = "会话ID")
    private String sessionId;
    
    @Schema(description = "角色：user-用户，assistant-AI助手")
    private String role;
    
    @Schema(description = "消息内容")
    private String content;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
