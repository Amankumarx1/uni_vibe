package com.univibe.app.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface Api {
    @POST("api/v1/auth/login") suspend fun login(@Body body: LoginRequest): LoginResponse
    @GET("api/v1/me") suspend fun me(): Me

    // Existing website JSON endpoints (they accept the bearer token too)
    @GET("api/discover") suspend fun discover(
        @Query("mode") mode: Int,
        @Query("network") network: String,
        @Query("cursor") cursor: Long? = null,
    ): DiscoverResponse
    @POST("api/action") suspend fun action(@Body body: ActionRequest): ActionResponse

    @GET("api/v1/matches") suspend fun matches(): MatchesResponse
    @GET("api/v1/network") suspend fun network(@Query("q") q: String): NetworkResponse
    @GET("api/v1/profile/{id}") suspend fun profile(@Path("id") id: Long): PublicProfile

    @GET("api/v1/conversations") suspend fun conversations(): ConversationsResponse
    @GET("api/v1/conversations/{id}/messages") suspend fun messages(
        @Path("id") id: Long,
        @Query("after") after: Long? = null,
    ): MessagesResponse
    @POST("api/v1/conversations/{id}/read") suspend fun markRead(@Path("id") id: Long): SimpleResponse
    @POST("api/messages/send") suspend fun send(@Body body: SendMessageRequest): SimpleResponse

    @GET("api/v1/notifications") suspend fun notifications(): NotificationsResponse
    @POST("api/notifications/read") suspend fun markNotificationsRead(): SimpleResponse

    @GET("api/v1/wall") suspend fun wall(
        @Query("scope") scope: String,
        @Query("category") category: String,
        @Query("sort") sort: String,
    ): WallResponse
    @POST("api/v1/wall/new") suspend fun newPost(@Body body: NewPostRequest): SimpleResponse
    @POST("api/campus-wall/{id}/like") suspend fun like(@Path("id") id: Long): LikeResponse
    @GET("api/campus-wall/{id}/comments") suspend fun comments(@Path("id") id: Long): CommentsResponse
    @POST("api/campus-wall/{id}/comment") suspend fun comment(@Path("id") id: Long, @Body body: CommentRequest): SimpleResponse

    @POST("api/profile/update") suspend fun updateProfile(@Body body: UpdateProfileRequest): SimpleResponse
    @POST("api/safety/block") suspend fun block(@Body body: BlockRequest): SimpleResponse
    @POST("api/safety/report") suspend fun report(@Body body: ReportRequest): SimpleResponse
}
