package com.chatbi.copilot.semantic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.datasource.dto.ColumnSchema;
import com.chatbi.copilot.datasource.dto.SchemaInfo;
import com.chatbi.copilot.datasource.dto.TableSchema;
import com.chatbi.copilot.semantic.dto.SemanticModelReq;
import com.chatbi.copilot.semantic.entity.SemanticModel;
import com.chatbi.copilot.semantic.mapper.SemanticModelMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SemanticService {

    private final SemanticModelMapper mapper;

    public SemanticService(SemanticModelMapper mapper) {
        this.mapper = mapper;
    }

    public List<SemanticModel> list(Long datasourceId) {
        LambdaQueryWrapper<SemanticModel> wrapper = new LambdaQueryWrapper<SemanticModel>()
                .eq(SemanticModel::getDatasourceId, datasourceId)
                .orderByAsc(SemanticModel::getTableName)
                .orderByAsc(SemanticModel::getColumnName);
        return mapper.selectList(wrapper);
    }

    public SemanticModel create(SemanticModelReq req) {
        SemanticModel model = new SemanticModel();
        model.setDatasourceId(req.getDatasourceId());
        model.setTableName(req.getTableName());
        model.setColumnName(blankToNull(req.getColumnName()));
        model.setBusinessAlias(req.getBusinessAlias());
        model.setDescription(req.getDescription());
        mapper.insert(model);
        return model;
    }

    public SemanticModel update(Long id, SemanticModelReq req) {
        SemanticModel model = mapper.selectById(id);
        if (model == null) {
            throw new BusinessException("Semantic entry not found: " + id);
        }
        model.setTableName(req.getTableName());
        model.setColumnName(blankToNull(req.getColumnName()));
        model.setBusinessAlias(req.getBusinessAlias());
        model.setDescription(req.getDescription());
        mapper.updateById(model);
        return model;
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    /**
     * Enrich a raw schema with business aliases / descriptions from the semantic layer.
     */
    public void applyTo(SchemaInfo info) {
        if (info == null || info.getDatasourceId() == null) {
            return;
        }
        List<SemanticModel> models = list(info.getDatasourceId());
        Map<String, SemanticModel> tableMap = new HashMap<>();
        Map<String, SemanticModel> columnMap = new HashMap<>();
        for (SemanticModel m : models) {
            if (m.getColumnName() == null || m.getColumnName().isBlank()) {
                tableMap.put(m.getTableName().toLowerCase(), m);
            } else {
                columnMap.put((m.getTableName() + "." + m.getColumnName()).toLowerCase(), m);
            }
        }
        for (TableSchema t : info.getTables()) {
            SemanticModel tm = tableMap.get(t.getName().toLowerCase());
            if (tm != null) {
                t.setBusinessAlias(tm.getBusinessAlias());
                t.setBusinessDescription(tm.getDescription());
            }
            for (ColumnSchema c : t.getColumns()) {
                SemanticModel cm = columnMap.get((t.getName() + "." + c.getName()).toLowerCase());
                if (cm != null) {
                    c.setBusinessAlias(cm.getBusinessAlias());
                    c.setBusinessDescription(cm.getDescription());
                }
            }
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
