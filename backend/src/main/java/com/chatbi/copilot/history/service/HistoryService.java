package com.chatbi.copilot.history.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatbi.copilot.history.entity.QueryHistory;
import com.chatbi.copilot.history.mapper.QueryHistoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HistoryService {

    private final QueryHistoryMapper mapper;

    public HistoryService(QueryHistoryMapper mapper) {
        this.mapper = mapper;
    }

    /** Best-effort recording; never breaks the main query flow. */
    public void record(Long datasourceId, String question, String sql, boolean success,
                       String errorMsg, Integer rowCount, Long elapsedMs, String chartType) {
        try {
            QueryHistory h = new QueryHistory();
            h.setDatasourceId(datasourceId);
            h.setQuestion(question);
            h.setGeneratedSql(sql);
            h.setSuccess(success ? 1 : 0);
            h.setErrorMsg(errorMsg);
            h.setRowCount(rowCount);
            h.setElapsedMs(elapsedMs);
            h.setChartType(chartType);
            mapper.insert(h);
        } catch (Exception e) {
            log.warn("Failed to record query history: {}", e.getMessage());
        }
    }

    public IPage<QueryHistory> page(Long datasourceId, long page, long size) {
        LambdaQueryWrapper<QueryHistory> wrapper = new LambdaQueryWrapper<QueryHistory>()
                .eq(datasourceId != null, QueryHistory::getDatasourceId, datasourceId)
                .orderByDesc(QueryHistory::getId);
        return mapper.selectPage(new Page<>(page, size), wrapper);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    public void clear(Long datasourceId) {
        LambdaQueryWrapper<QueryHistory> wrapper = new LambdaQueryWrapper<QueryHistory>()
                .eq(datasourceId != null, QueryHistory::getDatasourceId, datasourceId);
        mapper.delete(wrapper);
    }
}
