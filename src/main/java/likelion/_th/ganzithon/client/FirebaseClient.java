package likelion._th.ganzithon.client;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import jakarta.annotation.PostConstruct;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseClient {

    private final Firestore firestore;

    @Value("${firebase.test-mode:false}")
    private boolean testMode;

    // firestore 권장 배치 크기
    private static final int BATCH_SIZE = 30;
    // 동시 실행 배치 수
    private static final int PARALLEL_BATCHES = 5;

    @PostConstruct
    public void logProject() {
        log.info("[Firebase] projectId={}, testMode={}",
                firestore.getOptions().getProjectId(),
                testMode);
    }

    // 여러 cellId를 한번에 조회(Batch)
    public Map<String, SafetyCell> getCellDataBatch(List<String> cellIds)
            throws ExecutionException, InterruptedException, TimeoutException{
        if (cellIds == null || cellIds.isEmpty()) {
            return Collections.emptyMap();
        }

        long startTime = System.currentTimeMillis();

        if(testMode) {
            log.warn("[Test Mode]Mock data disabled");
            return Collections.emptyMap();
        }

        log.info("Batch 조회 시작: {} 개 셀", cellIds.size());

        Map<String, SafetyCell> results = new ConcurrentHashMap<>();

        // cellIds를 30개씩 분하
        List<List<String>> batches = partitionList(cellIds, BATCH_SIZE);
        log.info("   ➜ {} 개 배치로 분할", batches.size());

        // 병렬 실행용 ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_BATCHES);

        try {
            // 각 배치를 병렬로 조회
            List<CompletableFuture<Void>> futures = batches.stream()
                    .map(batch -> CompletableFuture.runAsync(() -> {
                        try {
                            fetchBatchSync(batch, results);
                        } catch (Exception e) {
                            log.error("배치 조회 실패: {}", e.getMessage());
                        }
                    }, executor))
                    .collect(Collectors.toList());

            // 모든 배치 완료 대기 (최대 30초)
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);

        } finally {
            executor.shutdown();
        }

        long endTime = System.currentTimeMillis();
        long elapsed = endTime - startTime;

        log.info("✅ Batch 조회 완료: {} 개 로드, {} ms 소요, 초당 {}/s",
                results.size(), elapsed,
                elapsed > 0 ? (cellIds.size() * 1000 / elapsed) : 0);

        return results;
    }

    // 배치 하나를 동기적으로 조회
    private void fetchBatchSync(List<String> cellIds, Map<String, SafetyCell> results) {
        try {
            // Firestore는 여러 Document를 동시에 가져오는 API 제공
            List<ApiFuture<DocumentSnapshot>> futures = cellIds.stream()
                    .map(cellId -> firestore.collection("cpted_grid")
                            .document(cellId)
                            .get())
                    .collect(Collectors.toList());

            // 모든 Future 완료 대기
            for (int i = 0; i < futures.size(); i++) {
                String cellId = cellIds.get(i);
                try {
                    DocumentSnapshot snapshot = futures.get(i).get(5, TimeUnit.SECONDS);

                    if (snapshot.exists()) {
                        SafetyCell cell = snapshot.toObject(SafetyCell.class);
                        if (cell != null) {
                            cell.setCellId(cellId); // cellId 명시적 설정
                            results.put(cellId, cell);
                            log.debug("✓ {}", cellId);
                        }
                    } else {
                        log.debug("✗ {} (not found)", cellId);
                    }
                } catch (Exception e) {
                    log.warn("⚠ {} 조회 실패: {}", cellId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("fetchBatchSync 실패: {}", e.getMessage());
        }
    }

    // 리스트를 N개씩 분할하는 메서드
    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    // 기존 cellId 조회 함수
    public SafetyCell getCellData(String cellId) {
        // 1. testMode 여부 먼저 확인
        if (testMode) {
            log.warn("[TEST MODE] testMode=true → Firestore 조회 생략, null 반환 (cellId={})", cellId);
            return null;
        }

        long start = System.currentTimeMillis();

        try {
            DocumentReference docRef = firestore.collection("cpted_grid").document(cellId);
            // 한 셀 조회 타임아웃 30초
            DocumentSnapshot snapshot = docRef.get().get(30, TimeUnit.SECONDS);
            long took = System.currentTimeMillis() - start;

            if (snapshot.exists()) {
                SafetyCell cell = snapshot.toObject(SafetyCell.class);
                if (cell != null) {
                    cell.setCellId(cellId);
                }
                log.info("[DB SUCCESS] cellId={} ({}ms, score={})",
                        cellId, took,
                        (cell != null ? cell.getCptedScore() : null));
                return cell;
            } else {
                log.warn("[DB NOT FOUND] cellId={} ({}ms)", cellId, took);
                return null;
            }

        } catch (ExecutionException e) {
            long took = System.currentTimeMillis() - start;
            log.error("[DB ERROR] cellId={} ({}ms) - {}", cellId, took, e.getMessage());
            return null;

        } catch (InterruptedException | TimeoutException e) {
            long took = System.currentTimeMillis() - start;
            log.error("[DB TIMEOUT] cellId={} ({}ms) - {}", cellId, took, e.getMessage());
            return null;
        }
    }

    // SafetyCell 내부 클래스는 Firestore가 객체로 변환할 때 사용합니다.
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SafetyCell {
        private String cellId;
        private Integer cctvCount;
        private Integer lightCount;
        private Integer storeCount;
        private Integer policeCount;
        private Integer schoolCount;
        private Double cptedScore;
    }
}