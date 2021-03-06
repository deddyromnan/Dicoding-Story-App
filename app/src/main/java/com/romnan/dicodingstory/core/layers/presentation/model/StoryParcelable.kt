package com.romnan.dicodingstory.core.layers.presentation.model

import android.os.Parcelable
import com.romnan.dicodingstory.core.layers.domain.model.Story
import kotlinx.parcelize.Parcelize

@Parcelize
data class StoryParcelable(
    val createdAt: String?,
    val description: String?,
    val id: String?,
    val lat: Double?,
    val lon: Double?,
    val name: String?,
    val photoUrl: String?
) : Parcelable {
    constructor(story: Story) : this(
        createdAt = story.createdAt,
        description = story.description,
        id = story.id,
        lat = story.lat,
        lon = story.lon,
        name = story.name,
        photoUrl = story.photoUrl
    )

    fun toStory(): Story = Story(
        createdAt = createdAt,
        description = description,
        id = id,
        lat = lat,
        lon = lon,
        name = name,
        photoUrl = photoUrl
    )
}