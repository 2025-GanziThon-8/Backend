package likelion._th.ganzithon.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${FIREBASE_KEY:}")
    private String firebaseKeyJson;

    @Value("${FIREBASE_PROJECT_ID:ganzithon-39944}")
    private String projectId;

    @Value("${firebase.database-url:}")
    private String databaseUrl;

    private GoogleCredentials loadCredentials() throws IOException {
        if (firebaseKeyJson == null || firebaseKeyJson.isBlank()) {
            log.warn("[Firebase] FIREBASE_KEY 가 비어있습니다. Application Default Credentials 사용 시도");
            return GoogleCredentials.getApplicationDefault();
        }

        String json = firebaseKeyJson.replace("\\n", "\n");

        return GoogleCredentials.fromStream(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        GoogleCredentials credentials = loadCredentials();

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .setDatabaseUrl(databaseUrl)
                .build();

        FirebaseApp app;
        if (FirebaseApp.getApps().isEmpty()) {
            app = FirebaseApp.initializeApp(options);
            log.info("[Firebase] FirebaseApp initialized. projectId={}", projectId);
        } else {
            app = FirebaseApp.getInstance();
        }

        return app;
    }

    @Bean
    public Firestore firestore(FirebaseApp firebaseApp) throws IOException {
        // 같은 credentials 로 Firestore 생성
        GoogleCredentials credentials = loadCredentials();

        FirestoreOptions options = FirestoreOptions.newBuilder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build();

        Firestore firestore = options.getService();
        log.info("[Firebase] Firestore initialized. projectId={}",
                firestore.getOptions().getProjectId());

        return firestore;
    }
}
