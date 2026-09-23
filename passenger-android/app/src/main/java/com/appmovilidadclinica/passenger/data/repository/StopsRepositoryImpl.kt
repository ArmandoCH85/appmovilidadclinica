package com.appmovilidadclinica.passenger.data.repository

import com.appmovilidadclinica.passenger.data.mapper.toDomain
import com.appmovilidadclinica.passenger.data.remote.ApiErrorMapper
import com.appmovilidadclinica.passenger.data.remote.KtorApiClient
import com.appmovilidadclinica.passenger.data.remote.safeApiCall
import com.appmovilidadclinica.passenger.shared.data.remote.dto.StopDto
import com.appmovilidadclinica.passenger.shared.domain.error.AppResult
import com.appmovilidadclinica.passenger.shared.domain.error.map
import com.appmovilidadclinica.passenger.shared.domain.model.Stop
import com.appmovilidadclinica.passenger.domain.repository.StopsRepository
import io.ktor.client.call.body
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StopsRepositoryImpl @Inject constructor(
    private val apiClient: KtorApiClient,
    private val errorMapper: ApiErrorMapper,
) : StopsRepository {
    override suspend fun list(): AppResult<List<Stop>> =
        safeApiCall<List<StopDto>>(
            errorMapper = errorMapper,
            call = { apiClient.stopsApi.list() },
            parseBody = { it.body() },
        ).map { list -> list.map { it.toDomain() } }
}