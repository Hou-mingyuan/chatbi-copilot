package com.chatbi.copilot.history.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.copilot.audit.service.AuditService;
import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.auth.service.CurrentUserService;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.history.dto.FavoriteReq;
import com.chatbi.copilot.history.entity.Favorite;
import com.chatbi.copilot.history.entity.QueryHistory;
import com.chatbi.copilot.history.mapper.FavoriteMapper;
import com.chatbi.copilot.permission.Capability;
import com.chatbi.copilot.permission.DataAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FavoriteService {

    private final FavoriteMapper mapper;
    private final HistoryService historyService;
    private final CurrentUserService currentUser;
    private final DataAccessPolicy accessPolicy;
    private final AuditService audit;

    public FavoriteService(FavoriteMapper mapper, HistoryService historyService,
                           CurrentUserService currentUser, DataAccessPolicy accessPolicy,
                           AuditService audit) {
        this.mapper = mapper;
        this.historyService = historyService;
        this.currentUser = currentUser;
        this.accessPolicy = accessPolicy;
        this.audit = audit;
    }

    @Transactional
    public Favorite create(FavoriteReq req) {
        AppUserPrincipal user = currentUser.required();
        QueryHistory history = historyService.requiredOwned(req.getQueryId(), Capability.QUERY);
        if (!"SUCCEEDED".equals(history.getStatus()) || history.getResultHash() == null) {
            throw new BusinessException("Only a successful query result can be favorited");
        }
        Favorite existing = mapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, user.userId())
                .eq(Favorite::getQueryId, history.getId())
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setTitle(req.getTitle().trim());
            mapper.updateById(existing);
            return existing;
        }
        Favorite f = new Favorite();
        f.setUserId(user.userId());
        f.setDatasourceId(history.getDatasourceId());
        f.setQueryId(history.getId());
        f.setTitle(req.getTitle().trim());
        f.setQuestion(history.getQuestion());
        f.setGeneratedSql(history.getGeneratedSql());
        mapper.insert(f);
        audit.record("FAVORITE_CREATE", "QUERY", history.getId(), "SUCCESS", Map.of());
        return f;
    }

    public List<Favorite> list(Long datasourceId) {
        AppUserPrincipal user = currentUser.required();
        if (datasourceId != null) {
            accessPolicy.requireDatasource(user, datasourceId, Capability.QUERY);
        }
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, user.userId())
                .eq(datasourceId != null, Favorite::getDatasourceId, datasourceId)
                .orderByDesc(Favorite::getId);
        if (datasourceId == null && !user.isAdmin()) {
            Set<Long> ids = accessPolicy.accessibleDatasourceIds(user, Capability.QUERY);
            if (ids.isEmpty()) {
                return List.of();
            }
            wrapper.in(Favorite::getDatasourceId, ids);
        }
        return mapper.selectList(wrapper);
    }

    @Transactional
    public void delete(Long id) {
        AppUserPrincipal user = currentUser.required();
        Favorite favorite = mapper.selectById(id);
        if (favorite == null || !user.userId().equals(favorite.getUserId())) {
            throw BusinessException.notFound("Favorite not found");
        }
        accessPolicy.requireDatasource(user, favorite.getDatasourceId(), Capability.QUERY);
        mapper.deleteById(favorite.getId());
        audit.record("FAVORITE_DELETE", "FAVORITE", id, "SUCCESS", Map.of());
    }
}
