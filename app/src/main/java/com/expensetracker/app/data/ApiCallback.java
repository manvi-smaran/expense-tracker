package com.expensetracker.app.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Use instead of Retrofit's Callback: gives you onSuccess(body) / onError(message),
// where message is the server's {"error": "..."} text (e.g. "Amount must be greater than 0")
public abstract class ApiCallback<T> implements Callback<T> {

    public abstract void onSuccess(T body);

    public abstract void onError(String message);

    @Override
    public void onResponse(Call<T> call, Response<T> response) {
        if (response.isSuccessful()) {
            onSuccess(response.body());   // body is null for 204 (no content) replies
            return;
        }
        if (response.code() == 401 && SessionManager.isLoggedIn()) {
            SessionManager.expire();      // token expired: back to the login screen
            return;
        }
        onError(errorMessage(response));
    }

    @Override
    public void onFailure(Call<T> call, Throwable t) {
        if (call.isCanceled()) {
            return;
        }
        onError("Can't reach the server. Is it running?");
    }

    private static String errorMessage(Response<?> response) {
        try {
            ResponseBody body = response.errorBody();
            if (body != null) {
                JsonObject json = JsonParser.parseString(body.string()).getAsJsonObject();
                if (json.has("error")) {
                    return json.get("error").getAsString();
                }
            }
        } catch (Exception ignored) {
            // fall through to the generic message
        }
        return "Something went wrong (" + response.code() + ")";
    }
}
