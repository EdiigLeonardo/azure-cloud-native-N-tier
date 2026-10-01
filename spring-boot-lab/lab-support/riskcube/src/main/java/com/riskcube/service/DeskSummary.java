package com.riskcube.service;

import java.math.BigDecimal;
import java.util.Map;

public record DeskSummary(String desk, Map<String, BigDecimal> pnlByBook, BigDecimal totalPnl) {
}
