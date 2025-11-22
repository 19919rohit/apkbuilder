package com.flapchat.app.data.remote;

import android.util.Log;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;

/**
 * ApiClient
 * ------------
 * Provides a single shared Retrofit + OkHttp instance for all services.
 * Each service (ChatService, FileService, etc.) will request getService().
 */
public class ApiClient {

    private static final String BASE_URL = "https://YOUR_NETLIFY_OR_SUPABASE_URL/"; // change to your URL

    private static Retrofit retrofit;

    /** Shared OkHttp + Retrofit instance */
    public static Retrofit getClient() {
        if (retrofit == null) {

            // Logging interceptor for debugging API traffic
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient okHttp = new OkHttpClient.Builder()
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .addInterceptor(logging)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttp)
                    .addConverterFactory(MoshiConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    /** Generic service getter */
    public static <T> T getService(Class<T> serviceClass) {
        return getClient().create(serviceClass);
    }
}