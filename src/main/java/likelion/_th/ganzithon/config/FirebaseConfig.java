package likelion._th.ganzithon.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.database-url}")
    private String databaseUrl;

    @Bean(destroyMethod = "")
    public Firestore firestore() throws Exception {
        GoogleCredentials cred = GoogleCredentials.getApplicationDefault();
        String projectId = System.getenv("FIREBASE_PROJECT_ID");

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(cred)
                .setProjectId(projectId)
                .setDatabaseUrl(databaseUrl)
                .build();

        synchronized (FirebaseConfig.class) {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
        }
        return FirestoreClient.getFirestore();
    }
}


