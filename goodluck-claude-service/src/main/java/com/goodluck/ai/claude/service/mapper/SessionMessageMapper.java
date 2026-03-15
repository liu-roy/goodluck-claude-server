package com.goodluck.ai.claude.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.goodluck.ai.claude.service.entity.SessionMessageDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SessionMessageMapper extends BaseMapper<SessionMessageDO> {
}
