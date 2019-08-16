import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Observable;

//This class download a file from URL.
public class Download extends Observable implements Runnable {

    // These are the status names.
    static final String[] STATUSES = {"Downloading", "Paused", "Complete", "Cancelled", "Error"};
    // These are the status codes.
    static final int DOWNLOADING = 0;
    static final int PAUSED = 1;
    private static final int COMPLETE = 2;
    private static final int CANCELLED = 3;
    static final int ERROR = 4;
    // These are the details.
    private URL url; // download URL
    private int size; // size of download in bytes
    private int downloaded; // number of bytes downloaded
    private int status; // current status of download

    Download(URL url) {
        this.url = url;
        size = -1;
        downloaded = 0;
        status = DOWNLOADING;

        // Begin the download.
        download();
    }

    // Get this download's URL.
    String getUrl() {
        return url.toString();
    }

    // Get this download's size.
    int getSize() {
        return size;
    }

    // Get this download's progress.
    float getProgress() {
        return ((float) downloaded / size) * 100;
    }

    // Get this download's status.
    int getStatus() {
        return status;
    }

    // Pause this download.
    void pause() {
        status = PAUSED;
        stateChanged();
    }

    // Resume this download.
    void resume() {
        status = DOWNLOADING;
        stateChanged();
        download();
    }

    // Cancel this download.
    void cancel() {
        status = CANCELLED;
        stateChanged();
    }

    // Mark this download as having an error.
    private void error() {
        status = ERROR;
        stateChanged();
    }

    // Start or resume downloading.
    private void download() {
        Thread thread = new Thread(this);
        thread.start();
    }

    // Get file name portion of URL.
    private String getFileName(URL url) {
        String fileName = url.getFile();
        return fileName.substring(fileName.lastIndexOf('/') + 1);
    }

    // Download file.
    @Override
    public void run() {
        RandomAccessFile file = null;
        InputStream stream = null;

        try {
            // Open connection to URL.
            // HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Specify what portion of file to download.
            connection.setRequestProperty("Range", "bytes=" + downloaded + "-");

            // Connect to server.
            connection.connect();

            // Make sure response code is in the 200 range.
            if (connection.getResponseCode() / 100 != 2) {
                error();
            }

            // Check for valid content length.
            int contentLength = connection.getContentLength();
            if (contentLength < 1) {
                error();
            }

            // Set the size for this download if it hasn't been already set.
            if (size == -1) {
                size = contentLength;
                stateChanged();
            }

            // Open file and seek to the end of it.
            file = new RandomAccessFile(getFileName(url), "rw");
            file.seek(downloaded);

            stream = connection.getInputStream();

            while (status == DOWNLOADING) {
                // Size buffer according to how much of the file is left to download.
                byte[] buffer;
                // Max size of the download buffer.
                int MAX_BUFFER_SIZE = 1024;
                if (size - downloaded > MAX_BUFFER_SIZE) {
                    buffer = new byte[MAX_BUFFER_SIZE];
                } else {
                    buffer = new byte[size - downloaded];
                }

                // Read from server into buffer.
                int read = stream.read(buffer);
                if (read == -1)
                    break;

                // Write buffer to file.
                file.write(buffer, 0, read);
                downloaded += read;
                stateChanged();
            }

            // Change status to complete if this point was reached because downloading has finished.
            if (status == DOWNLOADING) {
                status = COMPLETE;
                stateChanged();
            }
        } catch (Exception e) {
            error();
        } finally {
            // Close file.
            try {
                if (file != null) {
                    file.close();
                }
            } catch (Exception ignored) {
            }

            // Close connection to server.
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    // Notify observers that this download's status has changed.
    private void stateChanged() {
        setChanged();
        notifyObservers();
    }
}
