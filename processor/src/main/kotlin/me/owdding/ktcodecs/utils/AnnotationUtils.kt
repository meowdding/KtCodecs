package me.owdding.ktcodecs.utils

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ksp.toClassName

internal object AnnotationUtils {

    inline fun <reified T> KSAnnotated.getAnnotation(): KSAnnotation? = this.annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName!!.asString() == T::class.qualifiedName
    }

    inline fun <reified A : Annotation, T> KSAnnotated.getField(name: String): T? {
        return this.getAnnotation<A>()?.getAs(name)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> KSAnnotation.getAs(id: String) =
        this.arguments.firstOrNull { it.name?.asString() == id }?.value as? T

    fun KSTypeReference.resolveClassName() = this.resolve().resolveClassName()
    fun KSType.resolveClassName() = (this.starProjection().declaration as KSClassDeclaration).toClassName()
    @OptIn(KspExperimental::class)
    inline fun <reified T : Annotation>  KSAnnotated.getAnnotationInstance() = this.getAnnotationsByType(T::class).first()

}