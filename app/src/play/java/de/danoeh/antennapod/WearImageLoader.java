package de.danoeh.antennapod;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.DBReader;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Loads small versions of podcast and episode images for the watch.
 */
public final class WearImageLoader {
    private static final String TAG = "WearImageLoader";
    private static final int SIZE = 96;
    private static final int QUALITY = 85;
    private static final int TIMEOUT_SECONDS = 10;

    private WearImageLoader() {
    }

    @NonNull
    public static byte[] loadFeedImage(Context context, long feedId) {
        Feed feed = DBReader.getFeed(feedId, false, 0, 0);
        return feed != null ? load(context, feed.getImageUrl()) : new byte[0];
    }

    @NonNull
    public static byte[] loadItemImage(Context context, long itemId) {
        FeedItem item = DBReader.getFeedItem(itemId);
        if (item == null) {
            return new byte[0];
        }
        byte[] image = load(context, item.getImageLocation());
        if (image.length == 0 && item.getFeed() != null) {
            image = load(context, item.getFeed().getImageUrl());
        }
        return image;
    }

    @NonNull
    private static byte[] load(Context context, @Nullable String location) {
        if (location == null) {
            return new byte[0];
        }
        try {
            Bitmap bitmap = Glide.with(context)
                    .asBitmap()
                    .load(location)
                    .apply(new RequestOptions().centerCrop())
                    .submit(SIZE, SIZE)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, output);
            return output.toByteArray();
        } catch (Exception e) {
            Log.d(TAG, "Unable to load image " + location, e);
            return new byte[0];
        }
    }
}
