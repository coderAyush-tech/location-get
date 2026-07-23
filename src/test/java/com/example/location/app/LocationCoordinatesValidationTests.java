package com.example.location.app;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationCoordinatesValidationTests {
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsValidCoordinates() {
        LocationCordinates location = coordinates(28.6139, 77.2090);
        assertEquals(0, validator.validate(location).size());
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        LocationCordinates location = coordinates(91.0, 181.0);
        assertEquals(2, validator.validate(location).size());
    }

    @Test
    void rejectsMissingCoordinates() {
        LocationCordinates location = new LocationCordinates();
        assertEquals(2, validator.validate(location).size());
    }

    private LocationCordinates coordinates(double latitude, double longitude) {
        LocationCordinates location = new LocationCordinates();
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        return location;
    }
}
