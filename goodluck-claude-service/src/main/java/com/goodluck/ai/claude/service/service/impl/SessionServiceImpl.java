package com.goodluck.ai.claude.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.goodluck.ai.claude.api.model.resp.SessionInfoResponse;
import com.goodluck.ai.claude.api.model.resp.SessionMessageResponse;
import com.goodluck.ai.claude.service.entity.SessionDO;
import com.goodluck.ai.claude.service.entity.SessionMessageDO;
import com.goodluck.ai.claude.service.mapper.SessionMapper;
import com.goodluck.ai.claude.service.mapper.SessionMessageMapper;
import com.goodluck.ai.claude.service.service.SessionService;
import com.goodluck.common.model.BaseDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
 public class SessionServiceImpl extends ServiceImpl<SessionMapper,SessionDO > implements SessionService {


    @Autowired
    private SessionMapper sessionMapper;

    @Autowired
    private SessionMessageMapper messageMapper;

    @Override
    public SessionInfoResponse createSession() {
        String sid = UUID.randomUUID().toString();
        SessionDO entity = new SessionDO();
        entity.setSessionId(sid);
        initBaseDO(entity);
        sessionMapper.insert(entity);
        log.info("新建会话: {}", sid);
        return toResp(entity);
    }

    @Override
    public List<SessionInfoResponse> listSessions() {
        List<SessionDO> list = sessionMapper.selectList(
                new LambdaQueryWrapper<SessionDO>().orderByDesc(SessionDO::getGmtModified));
        return list.stream().map(this::toResp).toList();
    }

    @Override
    public void recordUsage(String sessionId, String projectName) {
        if (sessionId == null) return;
        SessionDO existing = sessionMapper.selectOne(
                new LambdaQueryWrapper<SessionDO>().eq(SessionDO::getSessionId, sessionId));
        if (existing == null) {
            SessionDO entity = new SessionDO();
            entity.setSessionId(sessionId);
            entity.setProjectName(projectName);
            initBaseDO(entity);
            sessionMapper.insert(entity);
        } else {
            if (projectName != null) existing.setProjectName(projectName);
            sessionMapper.updateById(existing);
        }
    }

    @Override
    public boolean hasConversationStarted(String sessionId, String projectName) {
        if (sessionId == null || projectName == null) return false;
        Long count = messageMapper.selectCount(
                new LambdaQueryWrapper<SessionMessageDO>()
                        .eq(SessionMessageDO::getSessionId, sessionId)
                        .eq(SessionMessageDO::getProjectName, projectName));
        return count != null && count > 0;
    }

    @Override
    public void markConversationStarted(String sessionId, String projectName) {
        // 不再需要额外操作，appendMessage 写入消息后 hasConversationStarted 自然为 true
    }

    @Override
    public void appendMessage(String sessionId, String projectName, String role, String content) {
        if (sessionId == null || projectName == null) return;
        SessionMessageDO entity = new SessionMessageDO();
        entity.setSessionId(sessionId);
        entity.setProjectName(projectName);
        entity.setRole(role);
        entity.setContent(content);
        initBaseDO(entity);
        messageMapper.insert(entity);
    }

    @Override
    public List<SessionMessageResponse> getMessages(String sessionId, String projectName) {
        if (sessionId == null || projectName == null) return List.of();
        List<SessionMessageDO> list = messageMapper.selectList(
                new LambdaQueryWrapper<SessionMessageDO>()
                        .eq(SessionMessageDO::getSessionId, sessionId)
                        .eq(SessionMessageDO::getProjectName, projectName)
                        .orderByAsc(SessionMessageDO::getGmtCreate));
        return list.stream()
                .map(m -> SessionMessageResponse.builder()
                        .role(m.getRole())
                        .content(m.getContent())
                        .time(m.getGmtCreate())
                        .build())
                .toList();
    }

    private void initBaseDO(BaseDO<?> entity) {
        Date now = new Date();
        entity.setGmtCreate(now);
        entity.setGmtModified(now);
        entity.setIsDelete(0);
        entity.setVersion(0);
    }

    private SessionInfoResponse toResp(SessionDO entity) {
        return SessionInfoResponse.builder()
                .sessionId(entity.getSessionId())
                .projectName(entity.getProjectName())
                .createdAt(entity.getGmtCreate())
                .lastUsedAt(entity.getGmtModified())
                .build();
    }
}
