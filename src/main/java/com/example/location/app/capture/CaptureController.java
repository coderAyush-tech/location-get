package com.example.location.app.capture;

import com.example.location.app.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/captures")
public class CaptureController {
    private final CaptureService captureService;

    public CaptureController(CaptureService captureService) {
        this.captureService = captureService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<CaptureResponse> capture(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double accuracy,
            HttpServletRequest request
    ) {
        CaptureResponse response = captureService.save(
                photo,
                latitude,
                longitude,
                accuracy,
                ClientIpResolver.resolve(request)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
