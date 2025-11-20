package likelion._th.ganzithon.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import likelion._th.ganzithon.dto.request.PathSearchRequest;
import likelion._th.ganzithon.dto.request.ReportRequest;
import likelion._th.ganzithon.dto.response.PathSearchResponse;
import likelion._th.ganzithon.dto.response.ReportResponse;
import likelion._th.ganzithon.service.PathService;
import likelion._th.ganzithon.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final PathService pathService;
    private final ReportService reportService;

    @PostMapping("/paths")
    public ResponseEntity<PathSearchResponse> getPaths(
            @Valid @RequestBody PathSearchRequest request
    ) throws ExecutionException, InterruptedException, TimeoutException {
        return ResponseEntity.ok(pathService.searchPaths(request));
    }

    @PostMapping("/report")
    public ResponseEntity<ReportResponse> generateReport(
            @RequestBody ReportRequest request
    ) throws JsonProcessingException, ExecutionException, InterruptedException, TimeoutException {
        return ResponseEntity.ok(reportService.generateReport(request));
    }
}
