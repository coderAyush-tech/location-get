package com.example.location.app.photo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.photo")
public class PhotoFeatureProperties {
    private long maxImageBytes = 10_485_760;
    private int maxImageDimension = 12_000;
    private double maxLocationAccuracy = 100_000;
    private final Gemini gemini = new Gemini();
    private final Cloudinary cloudinary = new Cloudinary();
    private final Processing processing = new Processing();

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public int getMaxImageDimension() {
        return maxImageDimension;
    }

    public void setMaxImageDimension(int maxImageDimension) {
        this.maxImageDimension = maxImageDimension;
    }

    public double getMaxLocationAccuracy() {
        return maxLocationAccuracy;
    }

    public void setMaxLocationAccuracy(double maxLocationAccuracy) {
        this.maxLocationAccuracy = maxLocationAccuracy;
    }

    public Gemini getGemini() {
        return gemini;
    }

    public Cloudinary getCloudinary() {
        return cloudinary;
    }

    public Processing getProcessing() {
        return processing;
    }

    public static class Gemini {
        private String apiKey = "";
        private String model = "gemini-3.1-flash-image";
        private int connectTimeoutMillis = 5_000;
        private int readTimeoutMillis = 120_000;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public int getReadTimeoutMillis() {
            return readTimeoutMillis;
        }

        public void setReadTimeoutMillis(int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
        }
    }

    public static class Cloudinary {
        private String cloudName = "";
        private String apiKey = "";
        private String apiSecret = "";
        private int connectTimeoutMillis = 5_000;
        private int readTimeoutMillis = 45_000;

        public String getCloudName() {
            return cloudName;
        }

        public void setCloudName(String cloudName) {
            this.cloudName = cloudName;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiSecret() {
            return apiSecret;
        }

        public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public int getReadTimeoutMillis() {
            return readTimeoutMillis;
        }

        public void setReadTimeoutMillis(int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
        }
    }

    public static class Processing {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 20;

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }
}
