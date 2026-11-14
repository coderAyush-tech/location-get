package com.example.location.app.admin;

import java.io.InputStream;

record AdminStoredVideo(InputStream stream, long sizeBytes, String contentType) { }
