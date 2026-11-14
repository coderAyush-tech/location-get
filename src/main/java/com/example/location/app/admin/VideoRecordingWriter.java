package com.example.location.app.admin;

import java.io.IOException;

/** Streaming sink for one consented camera/microphone recording. */
public interface VideoRecordingWriter {
    String id();

    long bytesWritten();

    void append(byte[] chunk) throws IOException;

    void finish(boolean retain);

    void abort();
}
