package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.springboot.AiService.StructOutPut;
import org.example.springboot.entity.AiChatMessage;
import org.example.springboot.entity.AiChatSession;
import org.example.springboot.mapper.AiChatMessageMapper;
import org.example.springboot.mapper.AiChatSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI聊天会话管理服务
 * 
 * 职责：管理AI聊天会话和消息的CRUD操作
 * 
 * @author AI Assistant
 */
@Slf4j
@Service
public class AiChatSessionService {

    @Resource
    private AiChatSessionMapper aiChatSessionMapper;
    
    @Resource
    private AiChatMessageMapper aiChatMessageMapper;

    /**
     * 创建新会话
     * 
     * @param sessionId 会话唯一标识
     * @param userId 用户ID
     * @param title 会话标题
     * @return 是否创建成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean createSession(String sessionId, Long userId, String title) {
        log.info("创建AI聊天会话: sessionId={}, userId={}, title={}", sessionId, userId, title);
        
        try {
            AiChatSession session = new AiChatSession();
            session.setSessionId(sessionId);
            session.setUserId(userId);
            session.setTitle(title != null ? title : "新对话");
            session.setCreateTime(LocalDateTime.now());
            session.setUpdateTime(LocalDateTime.now());
            
            int result = aiChatSessionMapper.insert(session);
            log.info("会话创建{}", result > 0 ? "成功" : "失败");
            return result > 0;
            
        } catch (Exception e) {
            log.error("创建会话失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 保存消息到会话
     * 
     * @param sessionId 会话ID
     * @param role 角色（user/assistant）
     * @param content 消息内容
     * @return 是否保存成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean saveMessage(String sessionId, String role, String content) {
        log.debug("保存消息: sessionId={}, role={}, content长度={}", sessionId, role, content.length());
        
        try {
            AiChatMessage message = new AiChatMessage();
            message.setSessionId(sessionId);
            message.setRole(role);
            message.setContent(content);
            message.setCreateTime(LocalDateTime.now());
            
            int result = aiChatMessageMapper.insert(message);
            
            // 更新会话的更新时间
            if (result > 0) {
                updateSessionTime(sessionId);
            }
            
            return result > 0;
            
        } catch (Exception e) {
            log.error("保存消息失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 获取会话的所有消息
     * 
     * @param sessionId 会话ID
     * @return 消息列表
     */
    public List<StructOutPut.ChatMessage> getSessionMessages(String sessionId) {
        log.debug("获取会话消息: sessionId={}", sessionId);
        
        LambdaQueryWrapper<AiChatMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiChatMessage::getSessionId, sessionId);
        queryWrapper.orderByAsc(AiChatMessage::getCreateTime);
        
        List<AiChatMessage> messages = aiChatMessageMapper.selectList(queryWrapper);
        
        // 转换为Record结构
        return messages.stream()
            .map(msg -> new StructOutPut.ChatMessage(
                msg.getId(),
                msg.getSessionId(),
                msg.getRole(),
                msg.getContent(),
                msg.getCreateTime()
            ))
            .collect(Collectors.toList());
    }

    /**
     * 获取用户的所有会话
     * 
     * @param userId 用户ID
     * @return 会话列表
     */
    public List<AiChatSession> getUserSessions(Long userId) {
        log.debug("获取用户会话列表: userId={}", userId);
        
        LambdaQueryWrapper<AiChatSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiChatSession::getUserId, userId);
        queryWrapper.orderByDesc(AiChatSession::getUpdateTime);
        
        return aiChatSessionMapper.selectList(queryWrapper);
    }

    /**
     * 根据sessionId获取会话信息
     * 
     * @param sessionId 会话ID
     * @return 会话信息
     */
    public AiChatSession getSessionBySessionId(String sessionId) {
        log.debug("获取会话信息: sessionId={}", sessionId);
        
        LambdaQueryWrapper<AiChatSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiChatSession::getSessionId, sessionId);
        
        return aiChatSessionMapper.selectOne(queryWrapper);
    }

    /**
     * 删除会话（包括所有消息）
     * 
     * @param sessionId 会话ID
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSession(String sessionId) {
        log.info("删除会话: sessionId={}", sessionId);
        
        try {
            // 删除所有消息
            LambdaQueryWrapper<AiChatMessage> messageWrapper = new LambdaQueryWrapper<>();
            messageWrapper.eq(AiChatMessage::getSessionId, sessionId);
            aiChatMessageMapper.delete(messageWrapper);
            
            // 删除会话
            LambdaQueryWrapper<AiChatSession> sessionWrapper = new LambdaQueryWrapper<>();
            sessionWrapper.eq(AiChatSession::getSessionId, sessionId);
            int result = aiChatSessionMapper.delete(sessionWrapper);
            
            log.info("会话删除{}", result > 0 ? "成功" : "失败");
            return result > 0;
            
        } catch (Exception e) {
            log.error("删除会话失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 更新会话标题
     * 
     * @param sessionId 会话ID
     * @param title 新标题
     * @return 是否更新成功
     */
    public boolean updateSessionTitle(String sessionId, String title) {
        log.info("更新会话标题: sessionId={}, title={}", sessionId, title);
        
        AiChatSession session = getSessionBySessionId(sessionId);
        if (session == null) {
            log.warn("会话不存在: {}", sessionId);
            return false;
        }
        
        session.setTitle(title);
        session.setUpdateTime(LocalDateTime.now());
        
        return aiChatSessionMapper.updateById(session) > 0;
    }

    /**
     * 更新会话的更新时间
     * 
     * @param sessionId 会话ID
     */
    private void updateSessionTime(String sessionId) {
        AiChatSession session = getSessionBySessionId(sessionId);
        if (session != null) {
            session.setUpdateTime(LocalDateTime.now());
            aiChatSessionMapper.updateById(session);
        }
    }

    /**
     * 清空会话的所有消息（保留会话）
     * 
     * @param sessionId 会话ID
     * @return 是否清空成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean clearSessionMessages(String sessionId) {
        log.info("清空会话消息: sessionId={}", sessionId);
        
        try {
            LambdaQueryWrapper<AiChatMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AiChatMessage::getSessionId, sessionId);
            
            int result = aiChatMessageMapper.delete(wrapper);
            log.info("清空会话消息{}，删除{}条消息", result >= 0 ? "成功" : "失败", result);
            
            return result >= 0;
            
        } catch (Exception e) {
            log.error("清空会话消息失败: {}", e.getMessage(), e);
            throw e;
        }
    }
}
