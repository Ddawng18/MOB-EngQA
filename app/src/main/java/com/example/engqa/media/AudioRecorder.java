package com.example.engqa.media;

import android.content.Context;
import android.media.MediaRecorder;

import java.io.File;
import java.io.IOException;

/** Records audio to an {@code .m4a} file in the app cache (requires RECORD_AUDIO). */
public class AudioRecorder {

    private MediaRecorder recorder;
    private File outputFile;

    public File start(Context context) throws IOException {
        File dir = new File(context.getCacheDir(), "audio");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        outputFile = new File(dir, "record_" + System.currentTimeMillis() + ".m4a");
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        // Firestore-only media budget: mono AAC at 24 kbps keeps 2 minutes under 450 KB.
        recorder.setAudioChannels(1);
        recorder.setAudioSamplingRate(22050);
        recorder.setAudioEncodingBitRate(24000);
        recorder.setOutputFile(outputFile.getAbsolutePath());
        recorder.prepare();
        recorder.start();
        return outputFile;
    }

    /** Stops recording and returns the recorded file, or {@code null} if nothing was captured. */
    public File stop() {
        if (recorder == null) {
            return null;
        }
        try {
            recorder.stop();
        } catch (RuntimeException e) {
            // stop() throws if it was stopped too early; discard the file in that case.
            if (outputFile != null && outputFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outputFile.delete();
            }
            outputFile = null;
        } finally {
            recorder.release();
            recorder = null;
        }
        return outputFile;
    }

    public void cancel() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
                // nothing recorded
            }
            recorder.release();
            recorder = null;
        }
        if (outputFile != null && outputFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outputFile.delete();
        }
        outputFile = null;
    }
}
