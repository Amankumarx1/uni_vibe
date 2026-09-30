package com.univibe.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class LoginResponse(val token: String = "", val user_id: Long = 0, val next_step: String = "app")

@Serializable data class Interest(val id: Long = 0, val name: String = "", val category: String = "")

@Serializable data class Me(
    val user_id: Long = 0,
    val email: String = "",
    val account_status: Int = 0,
    val has_profile: Boolean = false,
    val unread_notifications: Int = 0,
    val display_name: String = "",
    val age: Int? = null,
    val birth_date: String? = null,
    val course: String? = null,
    val study_year: Int? = null,
    val bio: String = "",
    val photo: String = "",
    val university_name: String = "",
    val university_city: String = "",
    val student_verified: Boolean = false,
    val interests: List<Interest> = emptyList(),
)

@Serializable data class Prompt(val question: String = "", val answer: String = "")

@Serializable data class DiscoverProfile(
    val user_id: Long,
    val display_name: String = "",
    val age: Int? = null,
    val university_name: String = "",
    val course: String? = null,
    val study_year: Int? = null,
    val bio: String = "",
    val primary_photo_path: String = "",
    val photos: List<String> = emptyList(),
    val prompts: List<Prompt> = emptyList(),
    val student_verified: Boolean = false,
    val distance_label: String = "",
    val interests: List<Interest> = emptyList(),
    val compatibility_reasons: List<String> = emptyList(),
)
@Serializable data class DiscoverResponse(val profiles: List<DiscoverProfile> = emptyList(), val next_cursor: Long? = null)

@Serializable data class ActionRequest(val target_user_id: Long, val mode_id: Int, val action: String)
@Serializable data class ActionResponse(
    val success: Boolean = false,
    val is_match: Boolean = false,
    val conversation_id: Long? = null,
    val error: String? = null,
)

@Serializable data class ConnectionItem(
    val user_id: Long,
    val display_name: String = "",
    val course: String? = null,
    val photo: String = "",
    val university_name: String = "",
    val conversation_id: Long? = null,
    val since: String? = null,
)
@Serializable data class MatchesResponse(
    val dating: List<ConnectionItem> = emptyList(),
    val friends: List<ConnectionItem> = emptyList(),
    val networking: List<ConnectionItem> = emptyList(),
)

@Serializable data class NetworkMember(
    val user_id: Long,
    val display_name: String = "",
    val course: String? = null,
    val study_year: Int? = null,
    val bio: String = "",
    val photo: String = "",
    val student_verified: Boolean = false,
    val university_name: String = "",
)
@Serializable data class NetworkResponse(val members: List<NetworkMember> = emptyList())

@Serializable data class PublicProfile(
    val user_id: Long,
    val display_name: String = "",
    val age: Int? = null,
    val university_name: String? = null,
    val course: String? = null,
    val study_year: Int? = null,
    val bio: String = "",
    val photos: List<String> = emptyList(),
    val prompts: List<Prompt> = emptyList(),
    val interests: List<Interest> = emptyList(),
    val shared_tags: List<String> = emptyList(),
    val student_verified: Boolean = false,
)

@Serializable data class Conversation(
    val id: Long,
    val partner_id: Long,
    val partner_name: String = "",
    val partner_photo: String = "",
    val partner_university: String = "",
    val last_message_text: String = "",
    val last_message_at: String? = null,
    val unread: Int = 0,
)
@Serializable data class ConversationsResponse(val conversations: List<Conversation> = emptyList())

@Serializable data class ChatMessage(
    val id: Long,
    val sender_user_id: Long,
    val body: String? = null,
    val media_type: String? = null,
    val media_url: String? = null,
    val created_at: String? = null,
)
@Serializable data class MessagesResponse(val messages: List<ChatMessage> = emptyList())
@Serializable data class SendMessageRequest(val conversation_id: Long, val body: String)

@Serializable data class AppNotification(
    val id: Long,
    val type: Int = 0,
    val title: String = "",
    val body: String = "",
    val is_read: Boolean = false,
    val created_at: String? = null,
)
@Serializable data class NotificationsResponse(val notifications: List<AppNotification> = emptyList())

@Serializable data class WallPost(
    val id: Long,
    val category: String = "discussion",
    val title: String = "",
    val content: String = "",
    val likes_count: Int = 0,
    val comments_count: Int = 0,
    val is_pinned: Boolean = false,
    val liked: Boolean = false,
    val created_at: String? = null,
    val author_id: Long = 0,
    val author_name: String = "",
    val author_photo: String = "",
    val university_name: String = "",
)
@Serializable data class WallResponse(val posts: List<WallPost> = emptyList())
@Serializable data class NewPostRequest(val category: String, val title: String, val content: String)
@Serializable data class LikeResponse(val liked: Boolean = false, val likes_count: Int = 0)

@Serializable data class WallComment(
    val id: Long = 0,
    val display_name: String = "",
    val content: String = "",
    val created_at: String? = null,
)
@Serializable data class CommentsResponse(val comments: List<WallComment> = emptyList())
@Serializable data class CommentRequest(val content: String)

@Serializable data class UpdateProfileRequest(
    val display_name: String,
    val course: String,
    val study_year: Int,
    val bio: String,
)
@Serializable data class BlockRequest(val target_user_id: Long)
@Serializable data class ReportRequest(val reported_user_id: Long, val report_type: String, val description: String = "")
@Serializable data class SimpleResponse(val success: Boolean = false, val message: String? = null, val error: String? = null)
