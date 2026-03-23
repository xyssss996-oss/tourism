package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.springboot.entity.AiChatSession;

/**
 * AI聊天会话Mapper接口
 * 
 * @author AI Assistant
 */
@Mapper
public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {
}
