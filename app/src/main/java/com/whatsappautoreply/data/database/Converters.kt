package com.whatsappautoreply.data.database

import androidx.room.TypeConverter
import com.whatsappautoreply.data.database.entity.*

class Converters {
    @TypeConverter
    fun fromMessageDirection(value: MessageDirection): String {
        return value.name
    }

    @TypeConverter
    fun toMessageDirection(value: String): MessageDirection {
        return try { MessageDirection.valueOf(value) } catch (e: Exception) { MessageDirection.INCOMING }
    }

    @TypeConverter
    fun fromMediaType(value: MediaType): String {
        return value.name
    }

    @TypeConverter
    fun toMediaType(value: String): MediaType {
        return try { MediaType.valueOf(value) } catch (e: Exception) { MediaType.TEXT }
    }

    @TypeConverter
    fun fromMessageSource(value: MessageSource): String {
        return value.name
    }

    @TypeConverter
    fun toMessageSource(value: String): MessageSource {
        return try { MessageSource.valueOf(value) } catch (e: Exception) { MessageSource.NOTIFICATION }
    }

    @TypeConverter
    fun fromUserFeedback(value: UserFeedback): String {
        return value.name
    }

    @TypeConverter
    fun toUserFeedback(value: String): UserFeedback {
        return try { UserFeedback.valueOf(value) } catch (e: Exception) { UserFeedback.NONE }
    }
}

