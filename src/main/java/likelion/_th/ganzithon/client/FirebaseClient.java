package likelion._th.ganzithon.client;

import com.google.firebase.database.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j
// Firebase 접근 담당
public class FirebaseClient {

    private final FirebaseDatabase firebaseDatabase;

    // db에서 격자 셀 데이터 조회
    public SafetyCell getCellData(String cellId) throws ExecutionException, InterruptedException, TimeoutException {
        DatabaseReference ref = firebaseDatabase.getReference("cpted_grid").child(cellId);
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
    }

    // db snapshot을 SafetyCell로 파싱
    private SafetyCell parseSafetyCell(DataSnapshot snapshot, String cellId) {
        try {
            Integer cctvCount = snapshot.child("cctvCount").getValue(Integer.class);
            Integer lightCount = snapshot.child("lightCount").getValue(Integer.class);
            Integer storeCount = snapshot.child("storeCount").getValue(Integer.class);
            Integer policeCount = snapshot.child("policeCount").getValue(Integer.class);
            Double cptedScore = snapshot.child("cptedScore").getValue(Double.class);

            return SafetyCell.builder()
                    .cellId(cellId)
                    .cctvCount(cctvCount != null ? cctvCount : 0)
                    .lightCount(lightCount != null ? lightCount : 0)
                    .storeCount(storeCount != null ? storeCount : 0)
                    .policeCount(policeCount != null ? policeCount : 0)
                    .cptedScore(cptedScore != null ? cptedScore : 0.0)
                    .build();
        } catch (Exception e) {
            return SafetyCell.builder()
                    .cellId(cellId)
                    .cctvCount(0)
                    .lightCount(0)
                    .storeCount(0)
                    .policeCount(0)
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
