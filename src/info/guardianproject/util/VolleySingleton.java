package info.guardianproject.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.util.LruCache;
import info.guardianproject.util.LogCleaner;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;
import android.widget.ImageView;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;

import java.io.UnsupportedEncodingException;
import com.hipmob.gifanimationdrawable.GifAnimationDrawable;
import java.io.ByteArrayInputStream;
import java.io.IOException;

// Todo switch to more permanent LruDiskcache: https://github.com/rdrobinson3/VolleyImageCacheExample/blob/master/CaptechBuzz/src/com/captechconsulting/captechbuzz/model/images/DiskLruImageCache.java
// Todo: allow better support for gifs

public class VolleySingleton {
    private static VolleySingleton mInstance = null;
    private static final String LOG_TAG = "Volley";

    class DoubleCache implements ImageLoader.ImageCache {
        private final LruCache<String, Bitmap> mCache = new LruCache<String, Bitmap>(100);
        private final LruCache<String, GifAnimationDrawable> gifCache = new LruCache<String, GifAnimationDrawable>(10);
        public void putBitmap(String url, Bitmap bitmap) {
            mCache.put(url, bitmap);
        }
        public Bitmap getBitmap(String url) {
            return mCache.get(url);
        }
        public void putGif(String url, GifAnimationDrawable gif) {
            gifCache.put(url, gif);
        }
        public GifAnimationDrawable getGif(String url){
            return gifCache.get(url);
        }
    }

    class GifRequest extends Request<GifAnimationDrawable> {
        private final Listener<GifAnimationDrawable> mListener;

        /**
         * Creates a new request with the given method.
         *
         * @param method the request {@link Method} to use
         * @param url URL to fetch the string at
         * @param listener Listener to receive the String response
         * @param errorListener Error listener, or null to ignore errors
         */
        public GifRequest(int method, String url, Listener<GifAnimationDrawable> listener,
                ErrorListener errorListener) {
            super(method, url, errorListener);
            mListener = listener;
        }

        /**
         * Creates a new GET request.
         *
         * @param url URL to fetch the string at
         * @param listener Listener to receive the String response
         * @param errorListener Error listener, or null to ignore errors
         */
        public GifRequest(String url, Listener<GifAnimationDrawable> listener, ErrorListener errorListener) {
            this(Method.GET, url, listener, errorListener);
        }

        @Override
        protected void deliverResponse(GifAnimationDrawable response) {
            mListener.onResponse(response);
        }

        @Override
        protected Response<GifAnimationDrawable> parseNetworkResponse(NetworkResponse response){
            GifAnimationDrawable decoder = null;
            LogCleaner.warn(LOG_TAG, "decoding");
            try {
                 decoder = new GifAnimationDrawable(new ByteArrayInputStream(response.data));
            } catch (IOException e) {
                LogCleaner.error(LOG_TAG, "IO exception", e);
            } // pass
            return Response.success(decoder, HttpHeaderParser.parseCacheHeaders(response));
        }
    }

    private RequestQueue mRequestQueue;
    private ImageLoader mImageLoader;
    private DoubleCache mImageCache;

    private VolleySingleton(Context context){
        mRequestQueue = Volley.newRequestQueue(context);

        mImageCache = new DoubleCache();
        mImageLoader = new ImageLoader(this.mRequestQueue, mImageCache);
    }

    public static VolleySingleton getInstance(Context context){
        if(mInstance == null){
            mInstance = new VolleySingleton(context);
        }
        return mInstance;
    }

    public void loadImage(final String url, final ImageView imageView, final int defaultImageResId, final int errorImageResId){
        LogCleaner.warn(LOG_TAG, "loading image:" + url);
        if (url.endsWith(".gif")) {
            LogCleaner.warn(LOG_TAG, "going for gif");
            GifAnimationDrawable cached = mImageCache.getGif(url);
            if (cached != null){
                LogCleaner.warn(LOG_TAG, "cached gif");
                imageView.setImageDrawable(cached);
            } else {
                imageView.setImageResource(defaultImageResId);
                Request<?> newRequest = new GifRequest(url, new Listener<GifAnimationDrawable>() {
                        @Override
                        public void onResponse(GifAnimationDrawable response) {
                            LogCleaner.warn(LOG_TAG, "got response");
                            mImageCache.putGif(url, response);
                            imageView.setImageDrawable(response);
                        }
                    },
                    new ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // we currently don't do anything.
                            LogCleaner.error(LOG_TAG, "Error in response", error);
                        }
                    }
                );
                mRequestQueue.add(newRequest);
            }

        } else {
            // default loader
            mImageLoader.get(url, ImageLoader.getImageListener(imageView, defaultImageResId, errorImageResId));
        }
    }

    public RequestQueue getRequestQueue(){
        return this.mRequestQueue;
    }

    public ImageLoader getImageLoader(){
        return this.mImageLoader;
    }

}