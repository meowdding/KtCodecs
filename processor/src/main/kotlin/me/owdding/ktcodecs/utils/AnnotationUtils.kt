package me.owdding.ktcodecs.utils

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.*
import me.owdding.kotlinpoet.ksp.toClassName
import kotlin.reflect.KClass

internal object AnnotationUtils {

    fun <T : Any> KSAnnotated.getAnnotation(clazz: KClass<T>): KSAnnotation? = this.annotations.firstOrNull {
        it.annotationType.resolve().toClassName().canonicalName == clazz.qualifiedName
    }
    inline fun <reified T : Any> KSAnnotated.getAnnotation(): KSAnnotation? = getAnnotation(T::class)

    inline fun <reified T : Any> KSAnnotated.hasAnnotation(): Boolean = getAnnotation<T>() != null

    fun <A : Annotation, T> KSAnnotated.getField(clazz: KClass<A>, name: String): T? =
        this.getAnnotation(clazz)?.getAs(name)

    inline fun <reified A : Annotation, T> KSAnnotated.getField(name: String): T? = getField(A::class, name)

    @Suppress("UNCHECKED_CAST")
    fun <T> KSAnnotation.getAs(id: String) =
        this.arguments.firstOrNull { it.name?.asString() == id }?.value as? T

    fun KSTypeReference.resolveClassName() = this.resolve().resolveClassName()
    fun KSType.resolveClassName() = (this.starProjection().declaration as KSClassDeclaration).toClassName()
    @OptIn(KspExperimental::class)
    inline fun <reified T : Annotation>  KSAnnotated.getAnnotationInstance() = this.getAnnotationsByType(T::class).first()

}