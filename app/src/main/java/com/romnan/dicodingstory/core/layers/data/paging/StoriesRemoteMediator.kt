package com.romnan.dicodingstory.core.layers.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.romnan.dicodingstory.core.layers.data.retrofit.CoreApi
import com.romnan.dicodingstory.core.layers.data.room.CoreDatabase
import com.romnan.dicodingstory.core.layers.data.room.entity.StoryEntity
import com.romnan.dicodingstory.core.layers.data.room.entity.StoryRemoteKeysEntity
import com.romnan.dicodingstory.core.layers.domain.repository.PreferencesRepository
import com.romnan.dicodingstory.core.util.espressoIdlingResource
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalPagingApi::class)
class StoriesRemoteMediator(
    private val coreDatabase: CoreDatabase,
    private val coreApi: CoreApi,
    private val preferencesRepository: PreferencesRepository
) : RemoteMediator<Int, StoryEntity>() {

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, StoryEntity>
    ): MediatorResult = espressoIdlingResource {
        val page: Int = when (loadType) {
            LoadType.REFRESH -> {
                val remoteKeys = getRemoteKeyClosestToCurrentPosition(state)
                remoteKeys?.nextKey?.minus(1) ?: INITIAL_PAGE_INDEX
            }

            LoadType.PREPEND -> {
                val remoteKeys = getRemoteKeyForFirstItem(state)
                val prevKey =
                    remoteKeys?.prevKey ?: return MediatorResult.Success(
                        endOfPaginationReached = remoteKeys != null
                    )
                prevKey
            }

            LoadType.APPEND -> {
                val remoteKeys = getRemoteKeyForLastItem(state)
                val nextKey = remoteKeys?.nextKey ?: return MediatorResult.Success(
                    endOfPaginationReached = remoteKeys != null
                )
                nextKey
            }
        }

        try {
            val loginResult = preferencesRepository.getAppPreferences().first().loginResult
            val bearerToken = "Bearer ${loginResult.token}"

            val response = coreApi.getPagedStories(
                bearerToken = bearerToken,
                page = page,
                size = state.config.pageSize
            )
            val storyEntitiesList = response.listStory?.map { StoryEntity(it) } ?: emptyList()

            val endOfPaginationReached = storyEntitiesList.isEmpty()

            coreDatabase.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    coreDatabase.storyRemoteKeysDao.deleteAll()
                    coreDatabase.storyDao.deleteAll()
                }

                val prevKey = if (page == 1) null else page - 1
                val nextKey = if (endOfPaginationReached) null else page + 1
                val keysList = storyEntitiesList.map {
                    StoryRemoteKeysEntity(id = it.id, prevKey = prevKey, nextKey = nextKey)
                }

                coreDatabase.storyRemoteKeysDao.insertList(keysList)
                coreDatabase.storyDao.insertList(storyEntitiesList)
            }

            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (t: Throwable) {
            return MediatorResult.Error(t)
        }
    }

    private suspend fun getRemoteKeyForLastItem(
        state: PagingState<Int, StoryEntity>
    ): StoryRemoteKeysEntity? {
        val lastNonEmptyPage = state.pages.lastOrNull { it.data.isNotEmpty() }
        val lastStory = lastNonEmptyPage?.data?.lastOrNull() ?: return null

        return coreDatabase.storyRemoteKeysDao.getById(lastStory.id)
    }

    private suspend fun getRemoteKeyForFirstItem(
        state: PagingState<Int, StoryEntity>
    ): StoryRemoteKeysEntity? {
        val firstNonEmptyPage = state.pages.firstOrNull { it.data.isNotEmpty() }
        val firstStory = firstNonEmptyPage?.data?.firstOrNull() ?: return null

        return coreDatabase.storyRemoteKeysDao.getById(firstStory.id)
    }

    private suspend fun getRemoteKeyClosestToCurrentPosition(
        state: PagingState<Int, StoryEntity>
    ): StoryRemoteKeysEntity? {
        val anchorPosition = state.anchorPosition ?: return null
        val closestStoryToPosition = state.closestItemToPosition(anchorPosition) ?: return null

        return coreDatabase.storyRemoteKeysDao.getById(closestStoryToPosition.id)
    }

    companion object {
        private const val INITIAL_PAGE_INDEX = 1
    }
}