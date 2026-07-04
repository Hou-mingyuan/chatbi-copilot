package com.chatbi.copilot.text2sql.dto;

import lombok.Data;

@Data
public class HistoryTurn {
    private String question;
    private String sql;
}
