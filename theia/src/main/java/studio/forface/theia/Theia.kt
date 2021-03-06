@file:Suppress("unused")

package studio.forface.theia

import android.content.Context
import android.widget.ImageView
import androidx.core.view.doOnPreDraw
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import studio.forface.theia.AbsSyncImageSource.DrawableResImageSource
import studio.forface.theia.cache.CACHE_EXT
import studio.forface.theia.cache._cleanCache
import studio.forface.theia.dsl.CompletionCallback
import studio.forface.theia.dsl.ErrorCallback
import studio.forface.theia.dsl.PreTargetedTheiaBuilder
import studio.forface.theia.dsl.TheiaBuilder
import studio.forface.theia.log.TheiaLogger
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * An interface for load images into an [ImageView]
 * Implements [CoroutineScope] for async calls.
 *
 * @author Davide Giuseppe Farella.
 */
interface ITheia: TheiaLogger, CoroutineScope {
    /** [Context] needed for resolve [DrawableResImageSource] */
    val context : Context
}


/**
 * Create a Request to apply an image into an `ImageView`
 *
 * @param block a REQUIRED lambda that takes [TheiaBuilder] as receiver for create the request
 *
 * @param extra an optional [ExtraParams]
 * @see theiaParams for more details
 */
inline fun Theia.load(
    extra: ExtraParams = {},
    block: TheiaBuilder.() -> Unit
) {
    val params = with( TheiaBuilder( context ) ) {
        extra()
        block()
        build()
    }
    applyParams( params )
}

/**
 * Create a Request to apply an image into an `ImageView` from a [PreTargetedTheia]
 *
 * @param block a REQUIRED lambda that takes [PreTargetedTheiaBuilder] as receiver for create the request
 *
 * @param extra an optional [ExtraParams]
 * @see theiaParams for more details
 */
inline fun PreTargetedTheia.load(
    extra: ExtraParams = {},
    block: PreTargetedTheiaBuilder.() -> Unit
) {
    val params = with( PreTargetedTheiaBuilder( context, target ) ) {
        extra()
        block()
        build()
    }
    applyParams( params )
}


/**
 * Middle implementation between [ITheia] and [Theia] for hide some member */
abstract class AbsTheia internal constructor(): ITheia, TheiaLogger by TheiaConfig.logger  {

    override val coroutineContext: CoroutineContext = Job()

    /** A list of active [Job] */
    private val jobs = mutableListOf<Job>()

    /** A [CoroutineDispatcher] for background operation */
    private val backgroundDispatcher = IO

    /** A [CoroutineDispatcher] for Main operations */
    private val mainDispatcher = Main

    /**
     * Delete all the [File]s with extension [CACHE_EXT] with [File.lastModified] older than [olderThan] from
     * [TheiaConfig.defaultCacheDirectory]
     *
     * @param olderThan A duration for query files to delete.
     * Default is 0 minutes ( `0.mins` ), soo all the caches will be removed
     */
    fun cleanCache( olderThan: Duration = 0.mins ) = _cleanCache( olderThan )

    /** Apply [TheiaParams] */
    @PublishedApi
    internal fun applyParams( params: TheiaParams ): Unit = with( params ) {

        val load = suspend {

            // Set placeholder if image is Async
            if ( image is Async ) loadPlaceholder()

            // Set image and handle error
            // On completion call params.completionCallback
            loadImage {

                // Call errorCallback if not null.
                params.errorCallback( it )

                // Set placeholder if error is NOT Sync ( Async or null )
                if ( error !is Sync ) loadPlaceholder()

                // Set error image. On error, fallback on placeholder again
                loadError { loadPlaceholder() }
            }
        }

        fun requestLoad() = newRequest( backgroundDispatcher ) { load() }

        // If we have Dimensions, load now, else on target's pre-draw
        when {
            params.dimensions != null -> requestLoad()
            params.target != null -> params.target.doOnPreDraw { requestLoad() }
            else -> throw AssertionError()
        }
    }

    /** Apply [ImageSource] with its [TheiaParams] */
    private suspend fun applySource(
        source: ImageSource?,
        params: TheiaParams,
        applyScale: Boolean = true,
        animationLoop: AnimationLoop,
        onCompletion: CompletionCallback,
        onError: ErrorCallback
    ) = coroutineScope {
        // Avoid to `forceBitmap` if a scaling is not required
        val newParams = params.copy( forceBitmap = params.forceBitmap && applyScale )
        val requestParams = RequestParams of newParams

        // Create the request
        val request = when( source ) {
            is Async -> AsyncRequest( TheiaConfig.httpClient, requestParams )
            is Sync -> SyncRequest( requestParams )

            null -> return@coroutineScope // Source can be null if has not been set by user.
        }

        // Invoke the request
        val maybeBitmap = runCatching {
            val overrideScaleType = if ( ! applyScale ) TheiaConfig.defaultScaleType else null
            request( source, overrideScaleType )
        }

        // Deliver the request's result
        newRequest( mainDispatcher ) {
            maybeBitmap
                .onSuccess { response ->
                    onCompletion( response ) // Deliver to CompletionCallback
                    params.target?.setImage( response, animationLoop ) // Set into target
                }
                .onFailure { val e = it as TheiaException
                    onError( e ) // Deliver ErrorCallback
                    if ( e.isFatal ) error( e ) else info( e )
                }
        }
    }

    /** Append a new [TheiaRequest] to [jobs] */
    private fun appendRequest( job: Job ) {
        jobs.append( job )
    }

    /** Create a new [Job] with the given [CoroutineDispatcher] and append it to [jobs] */
    private fun newRequest( dispatcher: CoroutineDispatcher, block: suspend () -> Unit ) {
        appendRequest( launch( dispatcher ) { block() } )
    }

    /** Stop and remove the given [Job] from [jobs] */
    private fun dropRequest( job: Job ) {
        job.cancel()
        jobs -= job
    }

    /** Purge all the [jobs] */
    internal open fun purgeRequests() {
        jobs.purge()
    }

    /** Add the given [Job] to a [MutableList] of [Job]s */
    private fun MutableList<Job>.append( job: Job ) {
        this += job
    }

    /** Stop all the [TheiaRequest] and [clear] */
    private fun MutableList<Job>.purge() {
        try {
            jobs.forEach { it.cancel( CancelledRequestException().toCancellationException() ) }

        // Run again if ConcurrentModificationException
        } catch ( e: ConcurrentModificationException ) { purge() }

        this.clear()
    }

    /** Call [load] within [TheiaParams] */
    private suspend fun TheiaParams.load(
        source: ImageSource?,
        applyScale: Boolean = true,
        animationLoop: AnimationLoop,
        onCompletion: CompletionCallback = {},
        onError: ErrorCallback = {}
    ) {
        applySource( source, this, applyScale, animationLoop, onCompletion, onError )
    }

    /** Call [load] `image` within [TheiaParams] */
    private suspend fun TheiaParams.loadImage( onError: ErrorCallback = {} ) {
        load( image, animationLoop = animationLoop, onCompletion = completionCallback, onError = onError )
    }

    /** Call [load] `placeholder` within [TheiaParams] */
    private suspend fun TheiaParams.loadPlaceholder() {
        load( placeholder, scalePlaceholder, placeholderAnimationLoop )
    }

    /** Call [load] `error` within [TheiaParams] */
    private suspend fun TheiaParams.loadError( onError: ErrorCallback = {} ) {
        load( error, scaleError, errorAnimationLoop, onError = onError )
    }
}


/** Default implementation of [ITheia] */
open class Theia internal constructor( override val context: Context ) : AbsTheia()


/** Implementation of [ITheia] with a pre-targeted [ImageView] */
class PreTargetedTheia internal constructor (
    override val context: Context,
    val target: ImageView
) : AbsTheia()


/** Implementation of [Theia] that exposes [purgeRequests] publicly */
class UnhandledTheia internal constructor( context: Context ) : Theia( context )


/** A typealias for [TheiaRequest] without generic */
private typealias Request = TheiaRequest<*>


/** Initialize [TheiaConfig.defaultCacheDirectory] if not set yet */
internal fun initDefaultCacheDir( context: Context ) {
    if ( TheiaConfig.defaultCacheDirectory == noDirectory )
        TheiaConfig.defaultCacheDirectory = context.cacheDir
}