package com.example.location.app.config;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;





    @Configuration
    public class MongoConfig {

        // Properties file se URI utha raha hai
        @Value("${spring.data.mongodb.uri}")
        private String mongoUri;

        @Bean
        public MongoClient mongoClient() {
            // Ye console me print karega taaki humein pata chale connection ban raha hai
            System.out.println("🚀 INITIALIZING MONGODB CONNECTION WITH URI...");

            ConnectionString connectionString = new ConnectionString(mongoUri);

            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .build();

            return MongoClients.create(settings);
        }

        @Bean
        public MongoTemplate mongoTemplate() {
            // 'JournalDb' hamare database ka naam hai
            return new MongoTemplate(mongoClient(), "locationdb");
        }
    }



