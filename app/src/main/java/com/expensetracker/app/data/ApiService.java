package com.expensetracker.app.data;

import com.expensetracker.app.model.AuthResponse;
import com.expensetracker.app.model.BudgetRequest;
import com.expensetracker.app.model.BudgetStatus;
import com.expensetracker.app.model.CategoryItem;
import com.expensetracker.app.model.DayTotal;
import com.expensetracker.app.model.ExpensePage;
import com.expensetracker.app.model.ExpenseRequest;
import com.expensetracker.app.model.ExpenseResponse;
import com.expensetracker.app.model.LoginRequest;
import com.expensetracker.app.model.PetStatus;
import com.expensetracker.app.model.RegisterRequest;
import com.expensetracker.app.model.User;
import com.expensetracker.app.model.Expense;
import com.expensetracker.app.model.MonthSummary;
import com.expensetracker.app.model.CategoryRequest;
import com.expensetracker.app.model.CountResponse;
import com.expensetracker.app.model.DeletedResponse;
import com.expensetracker.app.model.IdsRequest;
import com.expensetracker.app.model.ProcessedResponse;
import com.expensetracker.app.model.SubscriptionOverview;
import com.expensetracker.app.model.SubscriptionRequest;

import retrofit2.http.PUT;
import com.expensetracker.app.model.UpdateProfileRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // ---- auth ----
    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest body);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest body);

    @GET("api/me")
    Call<User> me();

    @PUT("api/me")
    Call<User> updateMe(@Body UpdateProfileRequest body);

    // ---- expenses ----
    @GET("api/expenses")
    Call<ExpensePage> expenses(@Query("page") int page,
                               @Query("pageSize") int pageSize,
                               @Query("categoryId") Integer categoryId);   // null = all, left out of the URL

    @GET("api/expenses/today")
    Call<DayTotal> dayTotal(@Query("date") String date);

    @POST("api/expenses")
    Call<ExpenseResponse> addExpense(@Body ExpenseRequest body);

    @PUT("api/expenses/{id}")
    Call<ExpenseResponse> updateExpense(@Path("id") int id, @Body ExpenseRequest body);

    @DELETE("api/expenses/{id}")
    Call<Void> deleteExpense(@Path("id") int id);

    // ---- budgets ----
    @GET("api/budgets")
    Call<List<BudgetStatus>> budgets(@Query("month") String month);   // "2026-11"

    @PUT("api/budgets/{categoryId}")
    Call<Void> saveBudget(@Path("categoryId") int categoryId, @Body BudgetRequest body);

    // ---- categories ----
    @GET("api/categories")
    Call<List<CategoryItem>> categories();

    // ---- pet ----
    @GET("api/pet")
    Call<PetStatus> pet(@Query("date") String date);

    // ---- charts ----
    @GET("api/charts/month")
    Call<MonthSummary> chartMonth(@Query("month") String month);   // "2026-11"

    // categoryId = null means the "Uncategorized" group
    @GET("api/charts/category-expenses")
    Call<List<Expense>> categoryExpenses(@Query("month") String month,
                                         @Query("categoryId") Integer categoryId,
                                         @Query("limit") int limit);

    // ---- categories: create / edit / delete ----
    @POST("api/categories")
    Call<Void> createCategory(@Body CategoryRequest body);

    @PUT("api/categories/{id}")
    Call<Void> updateCategory(@Path("id") int id, @Body CategoryRequest body);

    // how many expenses would be unlinked if this category were deleted
    @GET("api/categories/{id}/unlink-count")
    Call<CountResponse> unlinkCount(@Path("id") int id);

    // bulk delete; built-in (default) categories are skipped by the server
    @POST("api/categories/purge")
    Call<DeletedResponse> purgeCategories(@Body IdsRequest body);

    // ---- subscriptions ----
    @GET("api/subscriptions")
    Call<SubscriptionOverview> subscriptions(@Query("date") String date);   // the phone's date

    @POST("api/subscriptions")
    Call<Void> addSubscription(@Body SubscriptionRequest body);

    // PROCESS DUE: turns every due payment into an expense
    @POST("api/subscriptions/process-due")
    Call<ProcessedResponse> processDue(@Query("date") String date);

    @POST("api/subscriptions/delete")
    Call<DeletedResponse> deleteSubscriptions(@Body IdsRequest body);
}