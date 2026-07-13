package com.example.switching.fees;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations/tariffs")
public class TariffOperationsController {

    private final TariffQueryService queryService;

    public TariffOperationsController(TariffQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public List<TariffPlanSummaryResponse> list() {
        return queryService.list();
    }
}
