package com.example.location.app.admin;

record AdminStoredAudio(byte[] bytes, String contentType) {
    AdminStoredAudio { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
}
