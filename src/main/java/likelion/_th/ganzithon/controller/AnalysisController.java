package likelion._th.ganzithon.controller;

import jakarta.validation.Valid;
import likelion._th.ganzithon.dto.request.PathSearchRequest;
import likelion._th.ganzithon.dto.response.PathInfo;
import likelion._th.ganzithon.dto.response.PathSearchResponse;
import likelion._th.ganzithon.dto.response.ReportResponse;
import likelion._th.ganzithon.service.PathService;
import likelion._th.ganzithon.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping(value = "/api/v1/analysis/")
//@RequiredArgsConstructor
public class AnalysisController {

    private final PathService pathService;
    private final ReportService reportService;

    public AnalysisController(PathService pathService, ReportService reportService) {
        this.pathService = pathService;
        this.reportService = reportService;
    }

    @PostMapping("/paths")
    public ResponseEntity<PathSearchResponse> getPaths(
            @Valid @RequestBody PathSearchRequest request
    ) {
        List<PathInfo> candidatePaths = pathService.getCandidatePaths(request);

        PathSearchResponse response = PathSearchResponse.builder()
                .message("후보 경로 조회 성공")
                .paths(candidatePaths)
                .build();
        return ResponseEntity.ok(response);
    }

//    @PostMapping("/report")
//    public ResponseEntity<ReportResponse> generateResport(
//            @RequestBody PathSearchRequest request
//    ) {
//        return ResponseEntity.ok(reportService.createReport(request));
//    }
}
