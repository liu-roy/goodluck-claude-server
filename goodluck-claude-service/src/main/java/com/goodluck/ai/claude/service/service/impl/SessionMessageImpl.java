package com.goodluck.ai.claude.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.goodluck.ai.claude.api.model.resp.SessionInfoResponse;
import com.goodluck.ai.claude.api.model.resp.SessionMessageResponse;
import com.goodluck.ai.claude.service.entity.SessionDO;
import com.goodluck.ai.claude.service.entity.SessionMessageDO;
import com.goodluck.ai.claude.service.mapper.SessionMapper;
import com.goodluck.ai.claude.service.mapper.SessionMessageMapper;
import com.goodluck.ai.claude.service.service.SessionMessageService;
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
public class SessionMessageImpl extends ServiceImpl<SessionMessageMapper, SessionMessageDO> implements SessionMessageService {

}
