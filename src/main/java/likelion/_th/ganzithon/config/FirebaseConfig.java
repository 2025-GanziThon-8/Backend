package likelion._th.ganzithon.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions; // 👈 이게 핵심 Import!
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    @Bean
    public Firestore firestore() throws Exception {
        // 1. Cloud Run 환경변수 가져오기
        String jsonKey = System.getenv("FIREBASE_KEY");
        String projectId = System.getenv("FIREBASE_PROJECT_ID");

        GoogleCredentials credentials;

        if (jsonKey != null && !jsonKey.isEmpty()) {
            InputStream serviceAccount = new ByteArrayInputStream(jsonKey.getBytes(StandardCharsets.UTF_8));
            credentials = GoogleCredentials.fromStream(serviceAccount);
        }
        else {
            credentials = GoogleCredentials.getApplicationDefault();
        }

        FirestoreOptions.Builder builder = FirestoreOptions.newBuilder()
                .setCredentials(credentials);

        if (projectId != null && !projectId.isEmpty()) {
            builder.setProjectId(projectId);
        }

        return builder.build().getService();
    }
}