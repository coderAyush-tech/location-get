package com.example.location.app.photo;

public final class ImageEnhancementPrompt {
    public static final String VERSION = "PHOTO_ENHANCEMENT_V1";

    public static final String TEXT = """
            Enhance this photograph while strictly preserving the person's identity.

            Improve photographic quality including natural sharpness, facial clarity,
            lighting, exposure, white balance, contrast, natural skin tone, subtle
            background separation, and realistic noise reduction.

            Do not change facial structure, eyes, nose, lips, jaw, defining features,
            age, body shape, gender presentation, or identity. Do not create
            plastic-looking skin, heavily beautify the face, or turn the image into an
            illustration.

            Keep the output photorealistic, natural, and faithful to the original person.
            """;

    private ImageEnhancementPrompt() {
    }
}
