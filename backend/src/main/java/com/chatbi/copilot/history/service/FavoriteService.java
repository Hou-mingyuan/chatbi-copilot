package com.chatbi.copilot.history.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.copilot.history.dto.FavoriteReq;
import com.chatbi.copilot.history.entity.Favorite;
import com.chatbi.copilot.history.mapper.FavoriteMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FavoriteService {

    private final FavoriteMapper mapper;

    public FavoriteService(FavoriteMapper mapper) {
        this.mapper = mapper;
    }

    public Favorite create(FavoriteReq req) {
        Favorite f = new Favorite();
        f.setDatasourceId(req.getDatasourceId());
        f.setTitle(req.getTitle());
        f.setQuestion(req.getQuestion());
        f.setGeneratedSql(req.getSql());
        mapper.insert(f);
        return f;
    }

    public List<Favorite> list(Long datasourceId) {
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<Favorite>()
                .eq(datasourceId != null, Favorite::getDatasourceId, datasourceId)
                .orderByDesc(Favorite::getId);
        return mapper.selectList(wrapper);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }
}
