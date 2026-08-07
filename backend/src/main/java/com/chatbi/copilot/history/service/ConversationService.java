package com.chatbi.copilot.history.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.auth.service.CurrentUserService;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.history.entity.QueryHistory;
import com.chatbi.copilot.history.entity.QuerySession;
import com.chatbi.copilot.history.mapper.QueryHistoryMapper;
import com.chatbi.copilot.history.mapper.QuerySessionMapper;
import com.chatbi.copilot.permission.Capability;
import com.chatbi.copilot.permission.DataAccessPolicy;
import com.chatbi.copilot.text2sql.dto.HistoryTurn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {
    private static final int MAX_CONTEXT_TURNS = 6;

    private final QuerySessionMapper sessionMapper;
    private final QueryHistoryMapper historyMapper;
    private final CurrentUserService currentUser;
    private final DataAccessPolicy accessPolicy;

    public ConversationService(QuerySessionMapper sessionMapper, QueryHistoryMapper historyMapper,
                               CurrentUserService currentUser, DataAccessPolicy accessPolicy) {
        this.sessionMapper = sessionMapper;
        this.historyMapper = historyMapper;
        this.currentUser = currentUser;
        this.accessPolicy = accessPolicy;
    }

    @Transactional
    public QuerySession resolve(Long datasourceId, String requestedId, String firstQuestion) {
        AppUserPrincipal user = currentUser.required();
        accessPolicy.requireDatasource(user, datasourceId, Capability.QUERY);
        if (requestedId == null || requestedId.isBlank()) {
            QuerySession session = new QuerySession();
            session.setId(UUID.randomUUID().toString());
            session.setUserId(user.userId());
            session.setDatasourceId(datasourceId);
            session.setTitle(title(firstQuestion));
            sessionMapper.insert(session);
            return session;
        }
        QuerySession session = sessionMapper.selectById(requestedId);
        if (session == null) {
            throw BusinessException.notFound("Query session not found");
        }
        if (!session.getUserId().equals(user.userId()) || !session.getDatasourceId().equals(datasourceId)) {
            throw BusinessException.forbidden("Query session does not belong to this user and datasource");
        }
        return session;
    }

    public List<HistoryTurn> context(String sessionId) {
        AppUserPrincipal user = currentUser.required();
        List<QueryHistory> rows = historyMapper.selectList(new LambdaQueryWrapper<QueryHistory>()
                .eq(QueryHistory::getSessionId, sessionId)
                .eq(QueryHistory::getUserId, user.userId())
                .in(QueryHistory::getStatus, List.of("SUCCEEDED", "CLARIFICATION"))
                .orderByDesc(QueryHistory::getId)
                .last("LIMIT " + MAX_CONTEXT_TURNS));
        Collections.reverse(rows);
        List<HistoryTurn> turns = new ArrayList<>();
        for (QueryHistory row : rows) {
            HistoryTurn turn = new HistoryTurn();
            turn.setQuestion(row.getQuestion());
            turn.setSql(StringUtils.hasText(row.getEditedSql()) ? row.getEditedSql() : row.getGeneratedSql());
            turn.setClarification(row.getClarification());
            turns.add(turn);
        }
        return List.copyOf(turns);
    }

    public List<QuerySession> list(Long datasourceId) {
        AppUserPrincipal user = currentUser.required();
        if (datasourceId != null) {
            accessPolicy.requireDatasource(user, datasourceId, Capability.QUERY);
        }
        LambdaQueryWrapper<QuerySession> query = new LambdaQueryWrapper<QuerySession>()
                .eq(QuerySession::getUserId, user.userId())
                .eq(datasourceId != null, QuerySession::getDatasourceId, datasourceId)
                .orderByDesc(QuerySession::getUpdatedAt)
                .last("LIMIT 100");
        return sessionMapper.selectList(query);
    }

    @Transactional
    public void delete(String id) {
        QuerySession session = sessionMapper.selectById(id);
        AppUserPrincipal user = currentUser.required();
        if (session == null || !session.getUserId().equals(user.userId())) {
            throw BusinessException.notFound("Query session not found");
        }
        accessPolicy.requireDatasource(user, session.getDatasourceId(), Capability.QUERY);
        historyMapper.delete(new LambdaQueryWrapper<QueryHistory>()
                .eq(QueryHistory::getSessionId, id).eq(QueryHistory::getUserId, user.userId()));
        sessionMapper.deleteById(id);
    }

    private String title(String question) {
        String value = question == null ? "新问数会话" : question.trim();
        return value.length() <= 80 ? value : value.substring(0, 80);
    }
}
