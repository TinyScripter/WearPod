package de.danoeh.antennapod;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.ChannelClient;
import com.google.android.gms.wearable.Wearable;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.ui.notifications.NotificationUtils;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Sends the downloaded media file of an episode to the watch using a ChannelClient channel.
 * The channel starts with the file size and the offset the transfer starts at, followed by the file content.
 * A file size of -1 tells the watch that the episode is not downloaded on the phone.
 */
public class WearFileTransferWorker extends Worker {
    private static final String TAG = "WearFileTransferWorker";
    private static final String KEY_NODE_ID = "node_id";
    private static final String KEY_ITEM_ID = "item_id";
    private static final String KEY_OFFSET = "offset";
    private static final int NOTIFICATION_ID = 0x5745;
    private static final int BUFFER_SIZE = 8192;

    public WearFileTransferWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(Context context, String nodeId, long itemId, long offset) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WearFileTransferWorker.class)
                .setInputData(new Data.Builder()
                        .putString(KEY_NODE_ID, nodeId)
                        .putLong(KEY_ITEM_ID, itemId)
                        .putLong(KEY_OFFSET, offset)
                        .build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork("wear_file_transfer_" + itemId,
                ExistingWorkPolicy.REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        String nodeId = getInputData().getString(KEY_NODE_ID);
        long itemId = getInputData().getLong(KEY_ITEM_ID, -1);
        long offset = getInputData().getLong(KEY_OFFSET, 0);
        if (nodeId == null) {
            return Result.failure();
        }
        ChannelClient client = Wearable.getChannelClient(getApplicationContext());
        try {
            ChannelClient.Channel channel = Tasks.await(
                    client.openChannel(nodeId, WearDataPaths.episodeFilePath(itemId)));
            try (DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(Tasks.await(client.getOutputStream(channel))))) {
                send(itemId, offset, output);
            } finally {
                client.close(channel);
            }
            return Result.success();
        } catch (ExecutionException | IOException e) {
            Log.e(TAG, "Failed to send episode " + itemId + " to the watch", e);
            return Result.failure();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure();
        }
    }

    private void send(long itemId, long offset, DataOutputStream output) throws IOException {
        FeedItem item = DBReader.getFeedItem(itemId);
        FeedMedia media = item != null ? item.getMedia() : null;
        if (media == null || !media.localFileAvailable()) {
            Log.d(TAG, "Episode " + itemId + " is not downloaded");
            output.writeLong(-1);
            output.writeLong(0);
            return;
        }
        String url = media.getLocalFileUrl();
        Uri uri = url.startsWith("content://") ? Uri.parse(url) : Uri.fromFile(new File(url));
        try (AssetFileDescriptor descriptor = getApplicationContext().getContentResolver()
                .openAssetFileDescriptor(uri, "r")) {
            if (descriptor == null) {
                throw new IOException("Unable to open " + uri);
            }
            long length = descriptor.getLength();
            if (length < 0) {
                throw new IOException("Unknown length of " + uri);
            }
            long start = offset <= length ? offset : 0;
            output.writeLong(length);
            output.writeLong(start);
            try (FileInputStream input = descriptor.createInputStream()) {
                long skipped = 0;
                while (skipped < start) {
                    long count = input.skip(start - skipped);
                    if (count <= 0) {
                        throw new IOException("Unable to skip to " + start);
                    }
                    skipped += count;
                }
                byte[] buffer = new byte[BUFFER_SIZE];
                long remaining = length - start;
                while (remaining > 0 && !isStopped()) {
                    int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read == -1) {
                        break;
                    }
                    output.write(buffer, 0, read);
                    remaining -= read;
                }
            }
        }
    }

    @NonNull
    @Override
    public ForegroundInfo getForegroundInfo() {
        return new ForegroundInfo(NOTIFICATION_ID, new NotificationCompat.Builder(getApplicationContext(),
                NotificationUtils.CHANNEL_ID_DOWNLOADING)
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setContentTitle(getApplicationContext().getString(R.string.wearos_sending_to_watch))
                .setOngoing(true)
                .build());
    }
}
