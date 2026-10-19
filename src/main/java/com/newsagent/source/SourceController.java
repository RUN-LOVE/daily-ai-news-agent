package com.newsagent.source;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private final SourceCatalog sourceCatalog;

    public SourceController(SourceCatalog sourceCatalog) {
        this.sourceCatalog = sourceCatalog;
    }

    @GetMapping
    public List<NewsSource> list() {
        return sourceCatalog.list();
    }
}
