package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI聊天会话实体类
 * 
 * @author AI Assistant
 */
@Data
@TableName("ai_chat_session")
@Schema(description = "AI聊天会话实体类")
public class AiChatSession {
    
    @TableId(type = IdType.AUTO)
    @Schema(description = "会话ID")
    private Long id;
    
    @Schema(description = "会话唯一标识")
    private String sessionId;
    
    @Schema(description = "用户ID")
    private Long userId;
    
    @Schema(description = "会话标题")
    private String title;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
