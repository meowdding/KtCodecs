package me.owdding.ktcodecs.utils

import me.owdding.kotlinpoet.CodeBlock

internal class CodeLineBuilder {

    private val code: StringBuilder = StringBuilder()
    private val args: MutableList<Any> = mutableListOf()

    fun add(line: String, vararg args: Any) {
        code.append(line)
        this.args.addAll(args)
    }

    fun build(block: CodeBlock.Builder) {
        block.add(code.toString(), *args.toTypedArray())
    }
}