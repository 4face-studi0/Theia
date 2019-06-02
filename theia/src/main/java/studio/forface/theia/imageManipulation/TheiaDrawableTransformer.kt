package studio.forface.theia.imageManipulation

import android.graphics.drawable.Drawable
import studio.forface.theia.Dimensions

/**
 * A base [ImageTransformer] for [Drawable]
 *
 * @param baseImage the [Drawable] to transform
 *
 *
 * @author Davide Giuseppe Farella
 */
open class TheiaDrawableTransformer ( private var baseImage: Drawable ) : ImageTransformer<Drawable> {

    /** @return transformed [Drawable] */
    override val image: Drawable get() = baseImage

    /**
     * Scale the Image to fit the given [Dimensions]. The whole image will be visible and a transparent background
     * will be applied to fill
     *
     * Original dimensions will change
     * Original proportions will be maintained
     */
    override fun center(dimensions: Dimensions) {
        TODO("not implemented")
    }

    /**
     * Crop the Image if exceed the given [Dimensions] else a transparent background will be applied to fill
     *
     * Original dimensions will be maintained
     * Original proportions will be maintained
     */
    override fun crop(dimensions: Dimensions) {
        TODO("not implemented")
    }

    /**
     * Scale the Image to fill the given [Dimensions]. The image will be cropped to fit the dimensions
     *
     * Original dimensions will change
     * Original proportions will be maintained
     */
    override fun fit(dimensions: Dimensions) {
        TODO("not implemented")
    }

    /**
     * Stretch the Image to the given [Dimensions]
     *
     * Original dimensions will change
     * Original proportions will be ignored
     */
    override fun stretch(dimensions: Dimensions) {
        TODO("not implemented")
    }

}