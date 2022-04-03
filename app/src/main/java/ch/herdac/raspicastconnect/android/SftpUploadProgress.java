package ch.herdac.raspicastconnect.android;

import android.view.View;

import com.jcraft.jsch.SftpProgressMonitor;

import java.io.InputStream;

public class SftpUploadProgress implements SftpProgressMonitor {
    private final MainActivity main;
    private final InputStream in;
    private final long size;
    private long total;

    public SftpUploadProgress(MainActivity main, InputStream in, long size) {
        this.main = main;
        this.in = in;
        this.size = size;
    }

    @Override
    public void init(int op, String src, String dest, long max) {
        main.runOnUiThread(() -> {
            main.progressBar.setVisibility(View.VISIBLE);
            main.progressBar.setProgress(0);
            main.progressBar.setMax((int) size);
        });
        total = 0;
    }

    @Override
    public boolean count(long count) {
        total += count;
        main.runOnUiThread(() -> main.progressBar.setProgress((int) total));
        return true;
    }

    @Override
    public void end() {
        main.runOnUiThread(() -> main.progressBar.setVisibility(View.INVISIBLE));
    }
}
