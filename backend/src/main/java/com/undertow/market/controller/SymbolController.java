package com.undertow.market.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.undertow.market.dto.SymbolSearchResult;
import com.undertow.market.service.SymbolDirectory;

@RestController
@RequestMapping("/api/v1/symbols")
public class SymbolController {

    private final SymbolDirectory symbolDirectory;

    public SymbolController(SymbolDirectory symbolDirectory) {
        this.symbolDirectory = symbolDirectory;
    }

    @GetMapping("/search")
    public List<SymbolSearchResult> search(@RequestParam(name = "q", required = false) String query) {
        return symbolDirectory.search(query).stream()
                .map(SymbolSearchResult::from)
                .toList();
    }
}
