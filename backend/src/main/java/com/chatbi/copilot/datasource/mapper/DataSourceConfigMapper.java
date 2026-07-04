package com.chatbi.copilot.datasource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataSourceConfigMapper extends BaseMapper<DataSourceConfig> {
}
