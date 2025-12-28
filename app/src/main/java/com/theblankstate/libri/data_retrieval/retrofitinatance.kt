package com.theblankstate.libri.data_retrieval

import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit


object retrofitinatance {

    private var cacheDir: File? = null
    
    fun initialize(cacheDirectory: File) {
        cacheDir = cacheDirectory
    }

    private val okHttpClient: OkHttpClient by lazy {
        val cacheSize = 50L * 1024L * 1024L // 50 MB
        val cache = cacheDir?.let { Cache(File(it, "http_cache"), cacheSize) }
        
        OkHttpClient.Builder()
            .apply { if (cache != null) cache(cache) }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    // Separate client for Gutendex with longer timeout
    private val gutendexHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val api: apiservices by lazy {
        Retrofit.Builder()
            .baseUrl("https://openlibrary.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(apiservices::class.java)
    }
    
    /**
     * Gutendex API for Project Gutenberg ebook metadata
     * Base URL: https://gutendex.com/
     */
    val gutendexApi: GutendexApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://gutendex.com/")
            .client(gutendexHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GutendexApiService::class.java)
    }
}