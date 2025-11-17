package likelion._th.ganzithon.util;

import likelion._th.ganzithon.client.FirebaseClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class PercentileCalculator {
    private final List<Integer> cctvValues;
    private final List<Integer> lightValues;
    private final List<Integer> storeValues;
    private final List<Integer> policeValues;
    private final List<Double> scoreValues;

    public PercentileCalculator(List<FirebaseClient.SafetyCell> cells) {
        log.info("PercentileCalculator 초기화 시작 - 샘플 수: {}", cells.size());

        this.cctvValues = cells.stream()
                .map(FirebaseClient.SafetyCell::getCctvCount)
                .sorted()
                .collect(Collectors.toList());

        this.lightValues = cells.stream()
                .map(FirebaseClient.SafetyCell::getLightCount)
                .sorted()
                .collect(Collectors.toList());

        this.storeValues = cells.stream()
                .map(FirebaseClient.SafetyCell::getStoreCount)
                .sorted()
                .collect(Collectors.toList());

        this.policeValues = cells.stream()
                .map(FirebaseClient.SafetyCell::getPoliceCount)
                .sorted()
                .collect(Collectors.toList());

        this.scoreValues = cells.stream()
                .map(FirebaseClient.SafetyCell::getCptedScore)
                .sorted()
                .collect(Collectors.toList());

        log.info("PercentileCalculator 초기화 완료");
    }

    /**
     * 특정 값이 전체 분포에서 몇 percentile인지 계산
     * @param type "cctv", "light", "store", "police", "score"
     * @param value 계산할 값
     * @return 0~100 사이의 percentile 값
     */
    public int getPercentile(String type, double value) {
        List<? extends Number> distribution = getDistribution(type);

        if (distribution.isEmpty()) {
            log.warn("분포 데이터 없음 - type: {}", type);
            return 50; // 기본값
        }

        int rank = 0;
        for (Number v : distribution) {
            if (v.doubleValue() <= value) {
                rank++;
            } else {
                break;
            }
        }

        int percentile = (int) ((double) rank / distribution.size() * 100);
        return Math.min(100, Math.max(0, percentile)); // 0~100 범위 보장
    }

    private List<? extends Number> getDistribution(String type) {
        switch(type.toLowerCase()) {
            case "cctv": return cctvValues;
            case "light": return lightValues;
            case "store": return storeValues;
            case "police": return policeValues;
            case "school": return scoreValues;
            case "score": return scoreValues;
            default:
                log.warn("알 수 없는 타입: {}", type);
                return Collections.emptyList();
        }
    }

    /**
     * 디버깅용 - 분포 통계 출력
     */
    public void printStats() {
        log.info("=== Percentile 분포 통계 ===");
        log.info("CCTV - Min: {}, Median: {}, Max: {}",
                cctvValues.get(0),
                cctvValues.get(cctvValues.size()/2),
                cctvValues.get(cctvValues.size()-1));
        log.info("Light - Min: {}, Median: {}, Max: {}",
                lightValues.get(0),
                lightValues.get(lightValues.size()/2),
                lightValues.get(lightValues.size()-1));
        log.info("Store - Min: {}, Median: {}, Max: {}",
                storeValues.get(0),
                storeValues.get(storeValues.size()/2),
                storeValues.get(storeValues.size()-1));
    }
}
