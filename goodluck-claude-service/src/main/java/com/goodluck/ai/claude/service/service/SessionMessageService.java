package com.goodluck.ai.claude.service.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.goodluck.ai.claude.api.model.resp.SessionInfoResponse;
import com.goodluck.ai.claude.api.model.resp.SessionMessageResponse;
import com.goodluck.ai.claude.service.entity.SessionDO;
import com.goodluck.ai.claude.service.entity.SessionMessageDO;

import java.util.List;

public interface SessionMessageService extends IService<SessionMessageDO> {

}
