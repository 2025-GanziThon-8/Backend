package likelion._th.ganzithon.client;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j

public class FirebaseClient {

    private final Firestore firestore;


    @Value("${firebase.test-mode:true}")
    private boolean testMode;

    public SafetyCell getCellData(String cellId) throws ExecutionException, InterruptedException, TimeoutException {
        // 1. 테스트 모드 확인
        if (testMode) {
            log.debug("[TEST MODE] Mock 데이터 반환: {}", cellId);
            return generateMockData(cellId);
        }

        // 2. 실제 Firestore 쿼리 (단일 조회)
        try {
            long startTime = System.currentTimeMillis();

            DocumentReference docRef = firestore.collection("cpted_grid").document(cellId);
            DocumentSnapshot snapshot = docRef.get().get(3, TimeUnit.SECONDS); // 3초 타임아웃 설정

            if (snapshot.exists()) {
                log.debug("[DB SUCCESS] Fetched cell: {}", cellId);
                return snapshot.toObject(SafetyCell.class);
            } else {
                log.warn("[DB NOT FOUND] Cell ID not found: {}", cellId);
                return null;
            }
        } catch (ExecutionException e) {
            log.error("[DB ERROR] Firebase execution failed for {}: {}", cellId, e.getMessage());
            // DB 연결 실패 시, 가짜 데이터가 아닌 null을 반환하여 상위 서비스(CptedService)에서 처리하도록 함
            return null;
        }
    }

//    // --------------------테스트용 Mock 데이터 생성 -------------------------
//    private SafetyCell generateMockData(String cellId) {
//        int hash = cellId.hashCode();
//        int cctvCount = Math.abs(hash % 6);
//        int lightCount = Math.abs((hash / 10) % 8);
//        int storeCount = Math.abs((hash / 100) % 4);
//        int policeCount = Math.abs((hash / 1000) % 2);
//        int schoolCount = Math.abs((hash / 10000) % 3);
//
//        double cptedScore = (cctvCount * 0.4) + (lightCount * 0.3) +
//                (storeCount * 0.2) + (policeCount * 0.1) + (schoolCount * 0.1);
//
//        return SafetyCell.builder()
//                .cellId(cellId)
//                .cctvCount(cctvCount)
//                .lightCount(lightCount)
//                .storeCount(storeCount)
//                .policeCount(policeCount)
//                .schoolCount(storeCount)
//                .cptedScore(Math.round(cptedScore * 10.0) / 10.0)
//                .build();
//    }

    @lombok.Getter
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
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