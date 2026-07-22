package com.tradingassistant.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/instruments")
public class SecurityCatalogController {
    private final SecurityCatalogService catalog;

    public SecurityCatalogController(SecurityCatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/search")
    public List<SecurityCatalogService.View> search(
            @RequestParam(defaultValue = "") @Size(max = 64) String query,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return catalog.search(query, limit);
    }
}
