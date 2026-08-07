package com.chatbi.copilot.datasource.service.readonly;

import java.util.List;

public record ReadOnlyVerification(boolean readOnly, List<String> evidence) {
}
