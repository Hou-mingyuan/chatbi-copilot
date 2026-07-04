package com.chatbi.copilot.history.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatbi.copilot.history.entity.QueryHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QueryHistoryMapper extends BaseMapper<QueryHistory> {
}
