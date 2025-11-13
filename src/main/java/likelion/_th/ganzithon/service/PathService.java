package likelion._th.ganzithon.service;

import likelion._th.ganzithon.client.GoogleMapClient;
import likelion._th.ganzithon.client.TMapClient;
import likelion._th.ganzithon.client.UpstageAiClient;
import likelion._th.ganzithon.dto.request.PathSearchRequest;
import likelion._th.ganzithon.dto.response.PathInfo;
import likelion._th.ganzithon.dto.response.PathSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
// 경로 조회
public class PathService {

    private final GoogleMapClient googleMapClient;
    private final UpstageAiClient upstageAiClient;

    public List<PathInfo> getCandidatePaths(PathSearchRequest request) {
        // 구글 맵 API로 경로 최대 3개 가져옴
        List<PathInfo> paths = googleMapClient.getGoogleMapPaths(request)
                .stream()
                .limit(3)
                .collect(Collectors.toList());

        // 각 경로에 대한 ai 프리뷰 생성
        for (PathInfo path : paths) {
            String prompt = String.format("""
                    프리뷰 프롬프트 작성해야함
                    """, (double) path.getDistance(), path.getTime());

            String aiText = upstageAiClient.generateText(prompt);

            List<String> aiPreviewList = Arrays.stream(aiText.split("\n"))
                    .filter(line -> !line.isBlank())
                    .limit(3)
                    .collect(Collectors.toList());

            path.setAi_preview(aiPreviewList);
            path.setCpted(Map.of("avg", 0.0));
            path.setSummary_grade("");
        }

        return paths;
    }

}
