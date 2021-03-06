@file:Suppress("unused")

package studio.forface.theia.dsl

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.cancel
import studio.forface.theia.*
import studio.forface.theia.utils.doOnDestroy
import studio.forface.theia.utils.doOnDetach
import studio.forface.theia.utils.doOnViewDestroy
import java.io.File

/**
 * Create a Request to apply an image into an `ImageView`
 *
 * @param block a REQUIRED lambda that takes [TheiaBuilder] as receiver for create the request
 *
 * @param extra an optional [ExtraParams]
 * @see theiaParams for more details
 *
 *
 * @see Theia.load */
inline operator fun Theia.invoke(
    extra: ExtraParams = {},
    block: TheiaBuilder.() -> Unit
) {
    load( extra, block )
}

/**
 * Create a Request to apply an image into an `ImageView` from a [PreTargetedTheia]
 *
 * @param block a REQUIRED lambda that takes [PreTargetedTheiaBuilder] as receiver for create the request
 *
 * @param extra an optional [ExtraParams]
 * @see theiaParams for more details
 *
 *
 * @see PreTargetedTheia.load */
inline operator fun PreTargetedTheia.invoke(
    extra: ExtraParams = {},
    block: PreTargetedTheiaBuilder.() -> Unit
) {
    load( extra, block )
}

/**
 * Get [Theia] within a [LifecycleOwner] and [ContextWrapper]
 *
 * @return a new instance of [Theia] binded on this [FragmentActivity]
 * When [FragmentActivity] is destroyed [AbsTheia.jobs] are purged and [Theia] is canceled with
 * [ActivityDestroyedException]
 *
 * Usually you would like to keep a strong reference to this and reuse during the [FragmentActivity]'s [Lifecycle],
 * otherwise use [TheiaActivity]
 */
val FragmentActivity.newTheiaInstance: Theia
    get() {
        initDefaultCacheDir( this )
        val instance = Theia( this )
        doOnDestroy( removeObserver = true ) {
            instance.purgeRequests()
            instance.cancel( ActivityDestroyedException().toCancellationException() )
        }
        return instance
    }

/**
 * Get [Theia] within a [LifecycleOwner] and [Fragment]
 *
 * @return a new instance of [Theia] binded on this [Fragment]
 * When [Fragment]s View is destroyed [AbsTheia.jobs] are purged.
 * When [Fragment] is destroyed [Theia] is canceled with [FragmentDestroyedException]
 *
 * Usually you would like to keep a strong reference to this and reuse during the [Fragment]'s [Lifecycle], otherwise
 * use [TheiaFragment]
 */
val Fragment.newTheiaInstance: Theia
    get() {
        initDefaultCacheDir( requireContext() )
        val instance = Theia( requireContext() )
        doOnViewDestroy( removeObserver = false ) { instance.purgeRequests() }
        doOnDestroy( removeObserver = true ) {
            instance.cancel( FragmentDestroyedException().toCancellationException() )
        }
        return instance
    }

/**
 * @return a new instance of [Theia] binded on this [View]
 * When [View] is detached [AbsTheia.jobs] are purged.
 *
 * Usually you would like to keep a strong reference to this and reuse during the [View]'s Lifecycle
 */
val View.newTheiaInstance: Theia
    get() {
        initDefaultCacheDir( context )
        val instance = Theia( context )
        doOnDetach( removeListener = false ) { instance.purgeRequests() }
        return instance
    }

/**
 * @return a new instance of [Theia] binded on this [RecyclerView.ViewHolder.itemView]
 * @see View.newTheiaInstance
 *
 * Usually you would like to keep a strong reference to this and reuse during the [RecyclerView.ViewHolder]'s Lifecycle,
 * otherwise use [TheiaViewHolder]
 */
val RecyclerView.ViewHolder.newTheiaInstance get() = itemView.newTheiaInstance

/**
 * @return a new instance of [PreTargetedTheia] binded on this [ImageView]
 * When [View] is detached [AbsTheia.jobs] are purged.
 *
 * This extension on [ImageView] will create a [PreTargetedTheia] that will pass the
 * [PreTargetedTheia.target] to the [TheiaBuilder] so it doesn't need to be declared explicitly
 */
val ImageView.theia : PreTargetedTheia get() {
    initDefaultCacheDir( context )
    val instance = PreTargetedTheia( context, this )
    doOnDetach( removeListener = false ) { instance.purgeRequests() }
    return instance
}

/**
 * @return a new instance of [UnhandledTheia]
 * NOTE: this instance won't be tied to any Lifecycle, so you will need to manually stop the requests by
 * [Theia.purgeRequests]
 */
fun newTheiaUnhandledInstance( context: Context ): UnhandledTheia {
    initDefaultCacheDir( context )
    return UnhandledTheia( context )
}


/* Extensions for TheiaBuilder's */

var AbsTheiaBuilder.imageBitmap:               Bitmap      by dsl { image = it.toImageSource() }
var AbsTheiaBuilder.imageDrawable:             Drawable    by dsl { image = it.toImageSource() }
var AbsTheiaBuilder.imageDrawableRes:          Int         by dsl { image = it.toImageSource( context ) }
var AbsTheiaBuilder.imageFile:                 File        by dsl { image = it.toImageSource() }
var AbsTheiaBuilder.asyncImageFile:            File        by dsl { image = it.toAsyncImageSource() }
var AbsTheiaBuilder.imageUrl:                  String      by dsl { image = it.toImageSource() }
//var AbsTheiaBuilder.imageURL:                  URL         by dsl { image = it.toImageSource() } // Commented due to NoDefClassFoundException

var AbsTheiaBuilder.placeholderBitmap:         Bitmap      by dsl { placeholder = it.toImageSource() }
var AbsTheiaBuilder.placeholderDrawable:       Drawable    by dsl { placeholder = it.toImageSource() }
var AbsTheiaBuilder.placeholderDrawableRes:    Int         by dsl { placeholder = it.toImageSource( context ) }
var AbsTheiaBuilder.placeholderFile:           File        by dsl { placeholder = it.toImageSource() }

var AbsTheiaBuilder.errorBitmap:               Bitmap      by dsl { error = it.toImageSource() }
var AbsTheiaBuilder.errorDrawable:             Drawable    by dsl { error = it.toImageSource() }
var AbsTheiaBuilder.errorDrawableRes:          Int         by dsl { error = it.toImageSource( context ) }
var AbsTheiaBuilder.errorFile:                 File        by dsl { error = it.toImageSource() }
var AbsTheiaBuilder.asyncErrorFile:            File        by dsl { error = it.toAsyncImageSource() }
var AbsTheiaBuilder.errorUrl:                  String      by dsl { error = it.toImageSource() }
//var AbsTheiaBuilder.errorURL:                  URL         by dsl { error = it.toImageSource() } // Commented due to NoDefClassFoundException


/* Extensions for TheiaConfig */

var TheiaConfig.defaultPlaceholderBitmap:           Bitmap      by dsl { defaultPlaceholder = it.toImageSource() }
var TheiaConfig.defaultPlaceholderDrawable:         Drawable    by dsl { defaultPlaceholder = it.toImageSource() }
// Unappliable here! See TheiaConfig extensions
// var TheiaConfig.defaultPlaceholderDrawableRes:      Int         by dsl { defaultPlaceholder = it.toImageSource( resources ) }
var TheiaConfig.defaultPlaceholderFile:             File        by dsl { defaultPlaceholder = it.toImageSource() }

var TheiaConfig.defaultErrorBitmap:                 Bitmap      by dsl { defaultError = it.toImageSource() }
var TheiaConfig.defaultErrorDrawable:               Drawable    by dsl { defaultError = it.toImageSource() }
// Unappliable here! See TheiaConfig extensions
// var TheiaConfig.defaultErrorDrawableRes:            Int         by dsl { defaultError = it.toImageSource( resources ) }
var TheiaConfig.defaultErrorFile:                   File        by dsl { defaultError = it.toImageSource() }
var TheiaConfig.defaultAsyncErrorFile:              File        by dsl { defaultError = it.toAsyncImageSource() }
var TheiaConfig.defaultErrorUrl:                    String      by dsl { defaultError = it.toImageSource() }
//var TheiaConfig.defaultErrorURL:                    URL         by dsl { defaultError = it.toImageSource() } // Commented due to NoDefClassFoundException