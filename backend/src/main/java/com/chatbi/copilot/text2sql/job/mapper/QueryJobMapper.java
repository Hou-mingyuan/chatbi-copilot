package com.chatbi.copilot.text2sql.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatbi.copilot.text2sql.job.QueryJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QueryJobMapper extends BaseMapper<QueryJob> {
}
