package likelion._th.ganzithon.client;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

    @PostConstruct
    public void logProject() {
        log.info("[Firebase] projectId={}", firestore.getOptions().getProjectId());
    }

    public SafetyCell getCellData(String cellId) throws ExecutionException, InterruptedException, TimeoutException {
        if (testMode) {
            log.warn("[TEST MODE DISABLED] Mock data requested but generateMockData() has been removed. Returning null.");
            return null;
        }

        try {
            DocumentReference docRef = firestore.collection("cpted_grid").document(cellId);
            DocumentSnapshot snapshot = docRef.get().get(3, TimeUnit.SECONDS);

            if (snapshot.exists()) {
                log.debug("[DB SUCCESS] Fetched cell: {}", cellId);
                return snapshot.toObject(SafetyCell.class);
            } else {
                log.warn("[DB NOT FOUND] Cell ID not found: {}", cellId);
                return null;
            }
        } catch (ExecutionException e) {
            log.error("[DB ERROR] Firebase execution failed for {}: {}", cellId, e.getMessage());
            return null;
        } catch (InterruptedException | TimeoutException e) {
            log.error("[DB TIMEOUT] Firebase query timed out for {}: {}", cellId, e.getMessage());
            return null;
        }
    }

    // SafetyCell 내부 클래스는 Firestore가 객체로 변환할 때 사용합니다.
    @Getter
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