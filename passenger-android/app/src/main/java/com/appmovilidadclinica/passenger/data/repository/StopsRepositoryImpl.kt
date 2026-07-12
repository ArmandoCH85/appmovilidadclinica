package com.appmovilidadclinica.passenger.data.repository

import com.appmovilidadclinica.passenger.data.mapper.toDomain
import com.appmovilidadclinica.passenger.data.remote.ApiErrorMapper
import com.appmovilidadclinica.passenger.data.remote.StopsApi
import com.appmovilidadclinica.passenger.data.remote.safeApiCall
import com.appmovilidadclinica.passenger.domain.error.AppResult
import com.appmovilidadclinica.passenger.domain.error.map
import com.appmovilidadclinica.passenger.domain.model.Stop
import com.appmovilidadclinica.passenger.domain.repository.StopsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StopsRepositoryImpl @Inject constructor(
    private val stopsApi: StopsApi,
    private val errorMapper: ApiErrorMapper,
) : StopsRepository {
    override suspend fun list(): AppResult<List<Stop>> =
        safeApiCall(errorMapper) { stopsApi.list() }.map { list -> list.map { it.toDomain() } }
}
