package me.owdding.ktcodecs.generators

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import me.owdding.kotlinpoet.CodeBlock
import me.owdding.kotlinpoet.NOTHING
import me.owdding.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import me.owdding.kotlinpoet.ksp.toClassName
import me.owdding.kotlinpoet.ksp.toTypeParameterResolver
import me.owdding.kotlinpoet.ksp.toTypeVariableName
import me.owdding.ktcodecs.generators.RecordCodecGenerator.usesTypeParameter

internal object RecordCodecInstanceGenerator {

    fun generateCodecInstance(
        code: CodeBlock.Builder,
        parameters: List<Pair<KSValueParameter, RecordCodecGenerator.Type>>,
        declaration: KSClassDeclaration,
    ): Unit = runCatching {
        with(code) {
            val type = declaration.toClassName().let { className ->
                if (declaration.typeParameters.isEmpty()) className
                else {
                    val resolver = declaration.typeParameters.toTypeParameterResolver()
                    val typeVariables = declaration.typeParameters.map { it.toTypeVariableName(resolver) }
                    declaration.toClassName().parameterizedBy(typeVariables)
                }
            }
            val defaults = parameters.filter { it.second == RecordCodecGenerator.Type.DEFAULT }
            val normal = parameters.filter { it.second != RecordCodecGenerator.Type.DEFAULT }

            if (defaults.isEmpty()) {
                add(
                    "%T(${
                        normal.joinToString(", ") {
                            val name = it.first
                            if (it.second == RecordCodecGenerator.Type.NULLABLE) "$name = p_$name.orElse(null)" else "$name = p_$name"
                        }
                    })\n",
                    type,
                )
            } else if (defaults.size > 6) {
                add(
                    "var obj = %T(${
                        normal.joinToString(", ") {
                            val name = it.first
                            if (it.second == RecordCodecGenerator.Type.NULLABLE) "$name = p_$name.orElse(null)" else "$name = p_$name"
                        }
                    })\n",
                    type,
                )
                for (pair in defaults) {
                    val name = pair.first
                    add("if (p_$name.isPresent) obj = obj.copy($name = p_$name.get())\n")
                }
                add("obj\n")
            } else {
                val possibilities = powerSet(defaults.map { it.first }).sortedByDescending { it.size }

                add("when {\n")
                indent()

                for (defaultParams in possibilities) {
                    if (defaultParams.isEmpty()) continue
                    add(defaultParams.joinToString(" && ") { "p_$it.isPresent" })
                    add(" -> %T(", type)
                    add(
                        normal.joinToString(", ") {
                            val name = it.first
                            if (it.second == RecordCodecGenerator.Type.NULLABLE) "$name = p_$name.orElse(null)" else "$name = p_$name"
                        }
                    )
                    if (normal.isNotEmpty()) add(", ")
                    add("${defaultParams.joinToString(", ") { "$it = p_$it.get()" }})\n")
                }
                add("else -> ")
                generateCodecInstance(code, normal, declaration)
                unindent()
                add("}\n")
            }
        }
        Unit
    }.onFailure {
        RecordCodecGenerator.logger.error("Failed create codec instance method for ${declaration.location}")
    }.getOrThrow()

    private fun <T> powerSet(originalSet: List<T>): List<List<T>> {
        return originalSet.fold(listOf(listOf())) { acc, element ->
            acc + acc.map { it + element }
        }
    }
}
