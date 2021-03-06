@file:Suppress("EXPERIMENTAL_API_USAGE") // newSingleThreadContext

package studio.forface.theia

import android.graphics.Bitmap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.*
import studio.forface.theia.AbsAsyncImageSource.*
import studio.forface.theia.AbsSyncImageSource.*
import studio.forface.theia.TheiaConfig.cacheDuration
import studio.forface.theia.TheiaParams.ScaleType
import studio.forface.theia.cache.get
import studio.forface.theia.cache.set
import studio.forface.theia.log.TheiaLogger
import studio.forface.theia.minus
import studio.forface.theia.utils.applyDimensions
import studio.forface.theia.utils.applyTransformation
import studio.forface.theia.utils.applyTransformations
import studio.forface.theia.utils.toBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.System.currentTimeMillis

/**
 * A request for retrieve and apply the image.
 *
 * @author Davide Giuseppe Farella.
 */
internal abstract class TheiaRequest<in ImageSource: AbsImageSource<*>> : TheiaLogger by TheiaConfig.logger {

    /** The directory for caches */
    private val cacheDirectory get() = TheiaConfig.defaultCacheDirectory

    /** The [RequestParams] */
    abstract val params: RequestParams

    /**
     * Invoke the request with the given [ImageSource]
     * @throws TheiaException
     */
    suspend operator fun invoke( source: ImageSource, overrideScaleType: ScaleType? = null ): TheiaResponse {
        return try {
            val response = with( handleSource( source ) ) {
                // Cast response to BitmapResponse if required by user
                if ( params.forceBitmap && this is DrawableResponse )
                    withContext( SyncRequest.singleThreadContext ) { toBitmapResponse() }
                else this
            }
            prepareImage( response, overrideScaleType )

        } catch ( t: Throwable ) {
            throw t.toTheiaException()
        }
    }

    /**
     * Try to get [TheiaResponse] from cache, if null get from [ImageSource] and then store it in cache
     * @return [TheiaResponse]
     */
    private suspend fun handleSource( source: ImageSource ): TheiaResponse {
        return getCachedBitmap( source )?.let { BitmapResponse( it ) }
            ?: getImage( source ).also { storeInCacheIfNeeded( it, source ) }
    }


    /** @return a [TheiaResponse] get from the given [ImageSource] */
    abstract suspend fun getImage( source: ImageSource ): TheiaResponse

    /**
     * @return a [Bitmap] stored in cache from the given [ImageSource]
     * @return null if [params] has [RequestParams.useCache], [AbsImageSource.isLocalSource], [canUseCache] is false or
     * if cache is not available.
     */
    private fun getCachedBitmap( source: ImageSource ): Bitmap? {
        if ( ! params.useCache || source.isLocalSource || ! canUseCache() ) return null

        return cacheDirectory[source.cacheName, currentTimeMillis() - cacheDuration]?.readBytes()?.toBitmap()
            ?.also { info( "Loading ${source.source} from cache on ${Thread.currentThread().name}" ) }
    }

    /**
     * Store the given [TheiaResponse] in cache if is [BitmapResponse]
     * @see storeInCache
     */
    private fun storeInCacheIfNeeded( response: TheiaResponse, source: ImageSource ) {
        if ( response is BitmapResponse ) storeInCache( response.image, source )
    }

    /**
     * Store the given [Bitmap] in cache, if:
     * * [params] has [RequestParams.useCache]
     * * [AbsImageSource.isRemoteSource]
     * * [canUseCache]
     * * cache doesn't exist for this [ImageSource]
     */
    private fun storeInCache( bitmap: Bitmap, source: ImageSource ) {
        if ( ! params.useCache || source.isLocalSource || ! canUseCache() ) return
        if ( cacheDirectory[source.cacheName] != null ) return
        val stream = ByteArrayOutputStream()
        bitmap.compress( Bitmap.CompressFormat.PNG, 100, stream )
        cacheDirectory[source.cacheName] = stream.toByteArray()
    }

    /**
     * @return `true` if [TheiaConfig.defaultCacheDirectory] [File.canWrite], else false and log
     * [MissingCacheStoragePermissionsException]
     */
    private fun canUseCache() = cacheDirectory.canWrite()
        .also { allowed -> if ( ! allowed ) error( MissingCacheStoragePermissionsException() ) }

    /** Apply the needed transformations to the given [TheiaResponse] */
    private fun prepareImage(
        response: TheiaResponse,
        overrideScaleType: ScaleType? = null
    ): TheiaResponse = with( params ) {
        response
            .applyDimensions( dimensions,overrideScaleType ?: scaleType )
            .applyTransformation( shape.transformation )
            .applyTransformations( extraTransformations )
    }
}

/** A [TheiaRequest] that will be executed synchronously */
internal class SyncRequest( override val params: RequestParams ): TheiaRequest<SyncImageSource>() {

    internal companion object {
        /** A single [ExecutorCoroutineDispatcher] for all the [SyncRequest]s */
        val singleThreadContext = newSingleThreadContext( "SyncRequest Dispatcher" )
    }

    /** @return a [TheiaResponse] get from the given [ImageSource] */
    @ObsoleteCoroutinesApi
    override suspend fun getImage(source: SyncImageSource ) = coroutineScope {
        withContext( singleThreadContext ) {
            when ( source ) {
                is BitmapImageSource -> source.source.toResponse()
                is DrawableImageSource -> source.source.toResponse()
                is DrawableResImageSource -> source.resolveDrawable().toResponse()
                is FileImageSource -> source.source.readBytes().toBitmap().toResponse()
            }
        }
    }
}

/** A [TheiaRequest] that will be executed asynchronously */
internal class AsyncRequest(
    private val client: HttpClient,
    override val params: RequestParams
): TheiaRequest<AsyncImageSource>() {

    /** @return a [BitmapResponse] get from the given [ImageSource] */
    override suspend fun getImage( source: AsyncImageSource ) : BitmapResponse {
        return when ( source ) {
            is AsyncFileImageSource -> source.source.readBytes()
            is StringImageSource -> client.get( source.source )
            is UrlImageSource -> source.source.readBytes()
        }.toBitmap().toResponse()
    }
}