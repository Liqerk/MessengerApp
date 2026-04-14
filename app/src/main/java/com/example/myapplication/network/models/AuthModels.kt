import kotlinx.serialization.Serializable

@Serializable
data class LegacyAuthResponse(
    val token: String,
    val user: LegacyUserDto
)

@kotlinx.serialization.Serializable
data class LegacyUserDto(
    val id: Int = 0,
    val login: String,
    val mail: String,
    val isOnline: Boolean = false,
    val avatarUrl: String? = null
)