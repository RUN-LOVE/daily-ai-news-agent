package com.newsagent.briefing;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
public class BriefingController {

    private final BriefingService briefingService;

    public BriefingController(BriefingService briefingService) {
        this.briefingService = briefingService;
    }

    @PostMapping("/api/agent/run")
    public Briefing runNow() {
        return briefingService.generateToday(true);
    }

    @GetMapping("/api/briefings")
    public List<BriefingSummary> list() {
        return briefingService.listRecent();
    }

    @GetMapping("/api/briefings/{date}")
    public Briefing detail(@PathVariable String date) {
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日期格式应为 YYYY-MM-DD");
        }
        return briefingService.findByDate(date)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "简报不存在"));
    }
}
