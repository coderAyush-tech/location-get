package com.example.location.app.capture;

import com.example.location.app.GeoIpService;
import com.example.location.app.LocationLookupException;
import com.example.location.app.LocationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static com.example.location.app.capture.CapturedPhotoValidatorTests.ONE_PIXEL_PNG;
import static com.example.location.app.capture.CapturedPhotoValidatorTests.file;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptureServiceTests {
    @Mock
    private CapturedPhotoRepository repository;
    @Mock
    private CapturedPhotoValidator validator;
    @Mock
    private GeoIpService geoIpService;

    private CaptureService service;
    private MockMultipartFile photo;

    @BeforeEach
    void setUp() {
        service = new CaptureService(repository, validator, geoIpService);
        photo = file("camera.png", "image/png", ONE_PIXEL_PNG);
        when(validator.validate(photo)).thenReturn(new ValidatedCapture(ONE_PIXEL_PNG, "image/png"));
        lenient().when(repository.save(any(CapturedPhoto.class))).thenAnswer(invocation -> {
            CapturedPhoto saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", "capture-1");
            return saved;
        });
    }

    @Test
    void savesPhotoAndGpsTogether() {
        CaptureResponse response = service.save(photo, 28.6139, 77.209, 14.5, "106.222.248.114");

        assertEquals("capture-1", response.id());
        assertEquals("gps", response.locationSource());
        assertEquals(28.6139, response.latitude());
        assertEquals(14.5, response.accuracy());
        verify(geoIpService, never()).locate(any());

        ArgumentCaptor<CapturedPhoto> captor = ArgumentCaptor.forClass(CapturedPhoto.class);
        verify(repository).save(captor.capture());
        assertArrayEquals(ONE_PIXEL_PNG, captor.getValue().getPhoto());
        assertEquals("106.222.248.114", captor.getValue().getClientIp());
    }

    @Test
    void usesGeoIpWhenBrowserLocationIsMissing() {
        when(geoIpService.locate("106.222.248.114")).thenReturn(new LocationResponse(
                28.6, 77.2, "Delhi, India", "ip", "Approximate", "106.222.248.114"
        ));

        CaptureResponse response = service.save(photo, null, null, null, "106.222.248.114");

        assertEquals("ip", response.locationSource());
        assertEquals("Delhi, India", response.address());
        assertEquals(28.6, response.latitude());
    }

    @Test
    void stillSavesPhotoAndRawIpWhenGeoIpIsUnavailable() {
        when(geoIpService.locate("106.222.248.114"))
                .thenThrow(new LocationLookupException("Provider unavailable"));

        CaptureResponse response = service.save(photo, null, null, null, "106.222.248.114");

        assertEquals("raw_ip", response.locationSource());
        assertNull(response.latitude());
        assertEquals("106.222.248.114", response.clientIp());
        verify(repository).save(any(CapturedPhoto.class));
    }

    @Test
    void rejectsPartialOrInvalidGpsWithoutSaving() {
        assertThrows(CaptureApiException.class,
                () -> service.save(photo, 28.6, null, null, "106.222.248.114"));
        assertThrows(CaptureApiException.class,
                () -> service.save(photo, 28.6, 77.2, -1.0, "106.222.248.114"));

        verify(repository, never()).save(any());
    }
}
