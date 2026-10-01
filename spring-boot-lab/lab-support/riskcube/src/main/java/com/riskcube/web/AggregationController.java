package com.riskcube.web;

import com.riskcube.domain.Dimension;
import com.riskcube.service.AggregationService;
import com.riskcube.service.DeskSummary;
import com.riskcube.service.Exposure;
import com.riskcube.service.QueryResultCache;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AggregationController {

    private final AggregationService service;
    private final QueryResultCache cache;

    public AggregationController(AggregationService service, QueryResultCache cache) {
        this.service = service;
        this.cache = cache;
    }

    /** Example: GET /api/v1/aggregates?groupBy=DESK */
    @GetMapping("/aggregates")
    public Map<String, BigDecimal> aggregate(@RequestParam Dimension groupBy, HttpServletRequest request) {
        return cache.get(request.getQueryString(), () -> service.aggregate(groupBy));
    }

    @GetMapping("/desks/{desk}/summary")
    public DeskSummary deskSummary(@PathVariable String desk) {
        return service.deskSummary(desk);
    }

    @GetMapping("/exposure/{currency}")
    public Exposure exposure(@PathVariable String currency) {
        return service.exposure(currency);
    }
}
