package likelion._th.ganzithon.client;

import com.google.firebase.database.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true")
@Slf4j
// Firebase 접근 담당
public class FirebaseClient {

//    private final FirebaseDatabase firebaseDatabase;

    @Value("${firebase.enabled:false}")
    private boolean firebaseEnabled;

    // 테스트 모드 활성화 (Firebase 키 받으면 false로 변경)
    @Value("${firebase.test-mode:true}")
    private boolean testMode;

    // db에서 격자 셀 데이터 조회
    public SafetyCell getCellData(String cellId) throws ExecutionException, InterruptedException, TimeoutException {
        // ========== 테스트 모드 (Mock 데이터) ==========
        if (testMode || !firebaseEnabled) {
            log.debug("[TEST MODE] Mock 데이터 반환: {}", cellId);
            return generateMockData(cellId);
        }

        try {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("cpted_grid").child(cellId);
            CompletableFuture<SafetyCell> future = new CompletableFuture<>();

            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        SafetyCell cell = parseSafetyCell(snapshot, cellId);
                        future.complete(cell);
                    } else {
                        future.complete(null);
                    }
                }
                @Override
                public void onCancelled(DatabaseError error) {
                    future.complete(null);
                }
            });
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Firebase 조회 실패, Mock 데이터 반환: {}", e.getMessage());
            return generateMockData(cellId);
        }

    }

    // --------------------테스트용 Mock 데이터 생성 -------------------------
    private SafetyCell generateMockData(String cellId) {
        // cellId를 기반으로 랜덤하게 보이지만 일관된 데이터 생성
        int hash = cellId.hashCode();

        // 해시값 기반으로 0-5 사이 값 생성
        int cctvCount = Math.abs(hash % 6);
        int lightCount = Math.abs((hash / 10) % 8);
        int storeCount = Math.abs((hash / 100) % 4);
        int policeCount = Math.abs((hash / 1000) % 2);
        int schoolCount = Math.abs((hash / 10000) % 3);

        // CPTED 점수 계산 (0.4, 0.3, 0.2, 0.1 가중치)
        double cptedScore = (cctvCount * 0.4) + (lightCount * 0.3) +
                (storeCount * 0.2) + (policeCount * 0.1) + (schoolCount * 0.1);

        log.debug("[MOCK] cellId={}, cpted={}, cctv={}, light={}",
                cellId, cptedScore, cctvCount, lightCount);

        return SafetyCell.builder()
                .cellId(cellId)
                .cctvCount(cctvCount)
                .lightCount(lightCount)
                .storeCount(storeCount)
                .policeCount(policeCount)
                .schoolCount(storeCount)
                .cptedScore(Math.round(cptedScore * 10.0) / 10.0)
                .build();
    }

    // db snapshot을 SafetyCell로 파싱
    private SafetyCell parseSafetyCell(DataSnapshot snapshot, String cellId) {
        try {
            Integer cctvCount = snapshot.child("cctvCount").getValue(Integer.class);
            Integer lightCount = snapshot.child("lightCount").getValue(Integer.class);
            Integer storeCount = snapshot.child("storeCount").getValue(Integer.class);
            Integer policeCount = snapshot.child("policeCount").getValue(Integer.class);
            Integer schoolCount = snapshot.child("schoolCount").getValue(Integer.class);
            Double cptedScore = snapshot.child("cptedScore").getValue(Double.class);

            return SafetyCell.builder()
                    .cellId(cellId)
                    .cctvCount(cctvCount != null ? cctvCount : 0)
                    .lightCount(lightCount != null ? lightCount : 0)
                    .storeCount(storeCount != null ? storeCount : 0)
                    .policeCount(policeCount != null ? policeCount : 0)
                    .schoolCount(schoolCount != null ? schoolCount : 0)
                    .cptedScore(cptedScore != null ? cptedScore : 0.0)
                    .build();
        } catch (Exception e) {
            return SafetyCell.builder()
                    .cellId(cellId)
                    .cctvCount(0)
                    .lightCount(0)
                    .storeCount(0)
                    .policeCount(0)
                    .schoolCount(0)
                    .cptedScore(0.0)
                    .build();
        }
    }

    @lombok.Getter
    @lombok.Builder
    public static class SafetyCell {
        private String cellId;
        private Integer cctvCount;
        private Integer lightCount;
        private Integer storeCount;
        private Integer policeCount;
        private Integer schoolCount;
        private Double cptedScore;
    }

/*
    데이터 예시 가정
   {
    "cpted_grid": {
        "37.54400_127.05570": {
            "cctvCount": 3,
            "lightCount": 5,
            "storeCount": 1,
            "policeCount": 0,
            "cptedScore": 1.9
        },
        "37.54600_127.05770": {
            "cctvCount": 2,
            "lightCount": 8,
            "storeCount": 0,
            "policeCount": 0,
            "cptedScore": 2.6
            }
        }
    }
*/

}
