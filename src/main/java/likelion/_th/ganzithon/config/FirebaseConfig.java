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

    @Value("${firebase.database-url:}")
    private String databaseUrl;

    @Value("${FIREBASE_PROJECT_ID:}")
    private String projectIdEnv;

    private GoogleCredentials loadCredentials() throws IOException {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        log.info("[Firebase] Using ADC credentials: {}", credentials.getClass().getName());
        return credentials;
    }

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        GoogleCredentials credentials = loadCredentials();

        FirebaseOptions.Builder builder = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setDatabaseUrl(databaseUrl);

        if (projectIdEnv != null && !projectIdEnv.isBlank()) {
            builder.setProjectId(projectIdEnv);
        }

        FirebaseApp app;
        if (FirebaseApp.getApps().isEmpty()) {
            app = FirebaseApp.initializeApp(builder.build());
            log.info("[Firebase] FirebaseApp initialized. projectId={}",
                    app.getOptions().getProjectId());
        } else {
            app = FirebaseApp.getInstance();
        }

        return app;
    }

    @Bean
    public Firestore firestore(FirebaseApp firebaseApp) throws IOException {
        GoogleCredentials credentials = loadCredentials();

        FirestoreOptions.Builder builder = FirestoreOptions.newBuilder()
                .setCredentials(credentials);

        if (projectIdEnv != null && !projectIdEnv.isBlank()) {
            builder.setProjectId(projectIdEnv);
        }

        Firestore firestore = builder.build().getService();
        log.info("[Firebase] Firestore initialized. projectId={}",
                firestore.getOptions().getProjectId());

        return firestore;
    }
}
