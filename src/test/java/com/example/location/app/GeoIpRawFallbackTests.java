package com.example.location.app;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeoIpRawFallbackTests {

    @Test
    void providerFailureReturnsRawIpAndPersistsItWithoutFakeCoordinates() {
        AddressRepository repository = mock(AddressRepository.class);
        when(repository.save(any(SavedAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        GeoIpService service = new GeoIpService(repository, true);

        // A loopback address is rejected before any external provider call, which
        // deterministically exercises the same provider-unavailable fallback path.
        LocationResponse response = service.locateOrRawIp("127.0.0.1");

        assertEquals("raw_ip", response.source());
        assertEquals("127.0.0.1", response.clientIp());
        assertNull(response.latitude());
        assertNull(response.longitude());

        ArgumentCaptor<SavedAddress> saved = ArgumentCaptor.forClass(SavedAddress.class);
        verify(repository).save(saved.capture());
        assertEquals("raw_ip", saved.getValue().getSource());
        assertEquals("127.0.0.1", saved.getValue().getClientIp());
        assertNull(saved.getValue().getLatitude());
        assertNull(saved.getValue().getLongitude());
    }

    @Test
    void databaseFailureStillReturnsRawIpInsteadOf502() {
        AddressRepository repository = mock(AddressRepository.class);
        when(repository.save(any(SavedAddress.class)))
                .thenThrow(new DataAccessResourceFailureException("Mongo unavailable"));
        GeoIpService service = new GeoIpService(repository, true);

        LocationResponse response = service.locateOrRawIp("127.0.0.1");

        assertEquals("raw_ip", response.source());
        assertEquals("127.0.0.1", response.clientIp());
        assertTrue(response.accuracyNote().contains("Database storage is temporarily unavailable"));
    }
}
