package com.goodluck.ai.claude.service.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.goodluck.ai.claude.api.model.resp.SessionInfoResponse;
import com.goodluck.ai.claude.api.model.resp.SessionMessageResponse;
import com.goodluck.ai.claude.service.entity.SessionDO;

import java.util.List;

public interface SessionService extends IService<SessionDO> {

    SessionInfoResponse createSession();

    List<SessionInfoResponse> listSessions();

    void recordUsage(String sessionId, String projectName);

    boolean hasConversationStarted(String sessionId, String projectName);

    void markConversationStarted(String sessionId, String projectName);

    void appendMessage(String sessionId, String projectName, String role, String content);

    List<SessionMessageResponse> getMessages(String sessionId, String projectName);
}
