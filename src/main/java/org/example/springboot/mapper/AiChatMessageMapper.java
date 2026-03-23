package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.springboot.entity.AiChatMessage;

/**
 * AI聊天消息Mapper接口
 * 
 * @author AI Assistant
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}
