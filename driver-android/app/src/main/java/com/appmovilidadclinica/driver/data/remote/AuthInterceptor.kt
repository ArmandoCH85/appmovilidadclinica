package com.appmovilidadclinica.driver.data.remote

import com.appmovilidadclinica.driver.data.local.SessionDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionDataStore: SessionDataStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // Don't add auth header for login endpoint
        if (request.url.encodedPath.endsWith("/auth/login")) {
            return chain.proceed(request)
        }
        
        val token = runBlocking {
            sessionDataStore.getToken().first()
        }
        
        val newRequest = if (!token.isNullOrBlank()) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        
        return chain.proceed(newRequest)
    }
}
