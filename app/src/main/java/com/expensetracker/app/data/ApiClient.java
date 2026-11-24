package com.expensetracker.app.data;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    // Android emulator -> your computer is 10.0.2.2.
    // On a real phone use your computer's Wi-Fi address, e.g. "http://192.168.1.20:8080/"
    // (and add that address to res/xml/network_security_config.xml).
    public static final String BASE_URL = "https://expense-tracker-h55g.onrender.com/";

    private static ApiService service;

    private ApiClient() {}

    public static synchronized ApiService get() {
        if (service == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(75, TimeUnit.SECONDS)
                    // adds "Authorization: Bearer <token>" to every request once logged in
                    .addInterceptor(chain -> {
                        Request.Builder builder = chain.request().newBuilder();
                        String token = SessionManager.token();
                        if (token != null) {
                            builder.header("Authorization", "Bearer " + token);
                        }
                        return chain.proceed(builder.build());
                    })
                    // BASIC logs only method, URL and status (never passwords or tokens)
                    .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                    .build();

            service = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService.class);
        }
        return service;
    }
}