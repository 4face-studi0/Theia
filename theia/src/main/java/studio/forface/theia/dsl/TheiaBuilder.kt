@file:Suppress("MemberVisibilityCanBePrivate")

package studio.forface.theia.dsl

import android.content.Context
import android.widget.ImageView
import studio.forface.theia.*
import studio.forface.theia.TheiaConfig.defaultAnimationLoop
import studio.forface.theia.TheiaConfig.defaultError
import studio.forface.theia.TheiaConfig.defaultErrorAnimationLoop
import studio.forface.theia.TheiaConfig.defaultForceBitmap
import studio.forface.theia.TheiaConfig.defaultPlaceholder
import studio.forface.theia.TheiaConfig.defaultPlaceholderAnimationLoop
import studio.forface.theia.TheiaConfig.defaultScaleError
import studio.forface.theia.TheiaConfig.defaultScalePlaceholder
import studio.forface.theia.TheiaConfig.defaultScaleType
import studio.forface.theia.TheiaConfig.defaultShape
import studio.forface.theia.TheiaConfig.defaultUseCache
import studio.forface.theia.transformation.TheiaTransformation

/**
 * A builder for [ITheia]
 *
 * @constructor is internal, use [Theia.invoke], [Theia.load], [PreTargetedTheia.load] or [PreTargetedTheia.invoke]
 *
 * @author Davide Giuseppe Farella
 */
@TheiaDsl
abstract class AbsTheiaBuilder internal constructor ( internal val context: Context ) {

    /** The [AsyncImageSource] for the image to load into [target] */
    var image: ImageSource? = null

    /** The [ImageView] where to load the [image]. Default is `null` */
    protected open var target: ImageView? = null

    /**
     * The image to load as placeholder for async requests.
     * It will be ignored for *successful* [Sync] requests, but it will be used anyway if:
     * * there is an error loading [image] and no [error] is supplied
     * * [error] is [Async]
     *
     * Default is [TheiaConfig.defaultPlaceholder]
     */
    var placeholder: SyncImageSource? = defaultPlaceholder

    /**
     * The image to load if some error occurred while loading [image]
     * Default is [TheiaConfig.defaultError]
     */
    var error: ImageSource? = defaultError

    /**
     * Override dimensions of images to load, this param is required if [target] is not set
     * Default is `null`
     */
    var dimensions: Dimensions? = null

    /**
     * If `true` [error] will respect [scaleType], else use [TheiaParams.ScaleType.Center]
     * Default is [TheiaConfig.defaultScaleError]
     */
    var scaleError = defaultScaleError

    /**
     * If `true` [placeholder] will respect [scaleType], else use [TheiaParams.ScaleType.Center]
     * Default is [TheiaConfig.defaultScalePlaceholder]
     */
    var scalePlaceholder = defaultScalePlaceholder

    /**
     * The [TheiaParams.ScaleType] to apply to [image]
     * Default is [TheiaConfig.defaultScaleType]
     */
    var scaleType = defaultScaleType

    /**
     * The [TheiaParams.Shape] to apply to [image]
     * Default is [TheiaConfig.defaultShape]
     */
    var shape: TheiaParams.Shape = defaultShape

    /**
     * How the animation should loop for `image`
     * Default is [TheiaConfig.defaultAnimationLoop]
     */
    var animationLoop: AnimationLoop = defaultAnimationLoop

    /**
     * How the animation should loop for `placeholder`
     * Default is [TheiaConfig.defaultPlaceholderAnimationLoop]
     */
    var placeholderAnimationLoop: AnimationLoop = defaultPlaceholderAnimationLoop

    /**
     * How the animation should loop for `error`
     * Default is [TheiaConfig.defaultErrorAnimationLoop]
     */
    var errorAnimationLoop: AnimationLoop = defaultErrorAnimationLoop

    /** If `true` cache will be used for this request. Default is [TheiaConfig.defaultUseCache] */
    var useCache = defaultUseCache

    /**
     * If `true` the Images will be transformed as `Bitmap` even if they're `Drawable`.
     * Default is [TheiaConfig.defaultForceBitmap]
     *
     * Note this won't be applied to [placeholder] and [error] if [scalePlaceholder] or [scaleError] are disabled
     */
    var forceBitmap = defaultForceBitmap

    /** Set a [CompletionCallback] that will be called when [image] is ready */
    fun onCompletion( callback: CompletionCallback ) {
        this.completionCallback = callback
    }

    /** Set an [ErrorCallback] that will be called when something went wrong loading [image] */
    fun onError( callback: ErrorCallback ) {
        this.errorCallback = callback
    }

    /**
     * Add a new [TheiaTransformation] to [extraTransformations] within plus operator.
     * E.g. >
    theia {
        ...
        + SomeCustomTransformation
        + AnotherTransformation
    }
     */
    operator fun plus( transformation: TheiaTransformation ) {
        extraTransformations += transformation
    }

    /**
     * @return [image] or [placeholder]
     * @throws ImageSourceNotSetException if both of them are null
     */
    private val actualImage get() = image ?: placeholder ?: throw ImageSourceNotSetException()

    /**
     * A [CompletionCallback] that will be called when [image] is ready
     * Default is `null`
     * Set it via [onCompletion] function
     */
    private var completionCallback: CompletionCallback? = null

    /**
     * An [ErrorCallback] that will be called when something went wrong loading [image]
     * Default is empty lambda
     * Set it via [onError] function
     */
    private var errorCallback: ErrorCallback = {}

    /** A [Set] of [TheiaTransformation] to apply to the images ( [image], [placeholder], [error] ) */
    private val extraTransformations = mutableListOf<TheiaTransformation>()

    /**
     * @return [TheiaParams]
     *
     * @throws PointlessRequestException if both [target] and [completionCallback] are null
     * @throws UndefinedDimensionsException if both [target] and [dimensions] are null
     */
    fun build(): TheiaParams {

        if ( target == null ) {
            if ( completionCallback == null ) throw PointlessRequestException()
            if ( dimensions == null ) throw UndefinedDimensionsException()
        }

        return TheiaParams(
            image =                     actualImage,
            target =                    target,
            placeholder =               placeholder,
            error =                     error ?: placeholder,
            scaleError =                scaleError,
            scalePlaceholder =          scalePlaceholder,
            scaleType =                 scaleType,
            shape =                     shape,
            extraTransformations =      extraTransformations,
            animationLoop =             animationLoop,
            placeholderAnimationLoop =  placeholderAnimationLoop,
            errorAnimationLoop =        errorAnimationLoop,
            useCache =                  useCache,
            forceBitmap =               forceBitmap,
            dimensions =                dimensions,
            completionCallback =        completionCallback ?: {},
            errorCallback =             errorCallback
        )
    }
}

/** Implementation of [AbsTheiaBuilder] that receives [AbsTheiaBuilder.target] as constructor params */
@TheiaDsl
open class PreTargetedTheiaBuilder @PublishedApi internal constructor(
    context: Context, override var target: ImageView?
): AbsTheiaBuilder( context )

/** Default implementation of [AbsTheiaBuilder] that exposes [AbsTheiaBuilder.target] */
@TheiaDsl
class TheiaBuilder @PublishedApi internal constructor(
    context: Context
): AbsTheiaBuilder( context ) {
    public override var target: ImageView? = null
}

/** A typealias for a lambda that receives a [TheiaResponse] */
typealias CompletionCallback = suspend (TheiaResponse) -> Unit

/** A typealias for a lambda that receives a [TheiaException] */
typealias ErrorCallback = suspend (TheiaException) -> Unit