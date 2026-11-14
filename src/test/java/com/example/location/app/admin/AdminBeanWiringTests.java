package com.example.location.app.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class AdminBeanWiringTests {
    @Test
    void gridFsStoreExplicitlySelectsItsMongoConstructor() throws NoSuchMethodException {
        assertNotNull(GridFsVideoStore.class
                .getConstructor(MongoDatabaseFactory.class, MongoTemplate.class)
                .getAnnotation(Autowired.class));
    }

    @Test
    void springCanSelectProductionConstructors() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AdminProperties.class, () -> AdminAuthControllerTests.properties(5));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(MongoTemplate.class, () -> mock(MongoTemplate.class));
            context.registerBean(MongoDatabaseFactory.class, () -> mock(MongoDatabaseFactory.class));
            context.registerBean(GridFsVideoStore.class, () -> mock(GridFsVideoStore.class));
            context.register(AdminTokenService.class, AdminLoginRateLimiter.class, MongoAdminCaptureStore.class);
            context.refresh();

            assertNotNull(context.getBean(AdminTokenService.class));
            assertNotNull(context.getBean(AdminLoginRateLimiter.class));
            assertNotNull(context.getBean(MongoAdminCaptureStore.class));
        }
    }
}
