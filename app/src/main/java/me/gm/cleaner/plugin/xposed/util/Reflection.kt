package me.gm.cleaner.plugin.xposed.util

import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/** Reflection for vendor MediaProvider internals, independent of the Xposed bridge. */
internal object Reflection {
    private data class MethodKey(
        val owner: Class<*>,
        val name: String,
        val types: List<Class<*>?>,
        val staticOnly: Boolean,
    )

    private val fields = ConcurrentHashMap<Pair<Class<*>, String>, Field>()
    private val methods = ConcurrentHashMap<MethodKey, Method>()
    private val boxed = mapOf(
        Boolean::class.javaPrimitiveType to java.lang.Boolean::class.java,
        Byte::class.javaPrimitiveType to java.lang.Byte::class.java,
        Short::class.javaPrimitiveType to java.lang.Short::class.java,
        Char::class.javaPrimitiveType to java.lang.Character::class.java,
        Int::class.javaPrimitiveType to java.lang.Integer::class.java,
        Long::class.javaPrimitiveType to java.lang.Long::class.java,
        Float::class.javaPrimitiveType to java.lang.Float::class.java,
        Double::class.javaPrimitiveType to java.lang.Double::class.java,
    )
    private val widening = mapOf(
        java.lang.Byte::class.java to listOf(java.lang.Short::class.java, java.lang.Integer::class.java, java.lang.Long::class.java, java.lang.Float::class.java, java.lang.Double::class.java),
        java.lang.Short::class.java to listOf(java.lang.Integer::class.java, java.lang.Long::class.java, java.lang.Float::class.java, java.lang.Double::class.java),
        java.lang.Character::class.java to listOf(java.lang.Integer::class.java, java.lang.Long::class.java, java.lang.Float::class.java, java.lang.Double::class.java),
        java.lang.Integer::class.java to listOf(java.lang.Long::class.java, java.lang.Float::class.java, java.lang.Double::class.java),
        java.lang.Long::class.java to listOf(java.lang.Float::class.java, java.lang.Double::class.java),
        java.lang.Float::class.java to listOf(java.lang.Double::class.java),
    )

    fun findClass(name: String, loader: ClassLoader?): Class<*> =
        Class.forName(name, false, loader)

    fun getObjectField(receiver: Any, name: String): Any? {
        val key = receiver.javaClass to name
        fields[key]?.let { return it.get(receiver) }
        for (owner in hierarchy(receiver.javaClass)) {
            val field = try {
                owner.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                continue
            }
            field.isAccessible = true
            if (fields.size >= 256) fields.clear()
            fields[key] = field
            return field.get(receiver)
        }
        throw NoSuchFieldException("${receiver.javaClass.name}.$name")
    }

    fun callMethod(receiver: Any?, name: String, vararg args: Any?): Any? {
        requireNotNull(receiver) { "Missing receiver for $name" }
        // Legacy call sites can address a static helper through an instance (e.g.
        // MediaProvider.resolveVolumeName). Method.invoke deliberately permits this.
        return invoke(resolve(receiver.javaClass, name, args, false), receiver, args)
    }

    fun callStaticMethod(owner: Class<*>, name: String, vararg args: Any?): Any? =
        invoke(resolve(owner, name, args, true), null, args)

    private fun invoke(method: Method, receiver: Any?, args: Array<out Any?>): Any? = try {
        method.invoke(receiver, *args)
    } catch (exception: InvocationTargetException) {
        // Preserve the actual provider exception, including cancellation and permission failures.
        throw exception.targetException
    }

    private fun resolve(owner: Class<*>, name: String, args: Array<out Any?>, staticOnly: Boolean): Method {
        val key = MethodKey(owner, name, args.map { it?.javaClass }, staticOnly)
        methods[key]?.let { return it }
        val candidates = (hierarchy(owner).flatMap { it.declaredMethods.asSequence() } + owner.methods.asSequence())
            .filter { it.name == name && (!staticOnly || Modifier.isStatic(it.modifiers)) && it.parameterCount == args.size }
            .distinctBy { it.parameterTypes.toList() }
            .mapNotNull { method ->
                val scores = method.parameterTypes.mapIndexed { index, type -> score(type, key.types[index]) }
                if (scores.any { it == null }) null else method to scores.sumOf { it!! }
            }.toList()
        val bestScore = candidates.minOfOrNull { it.second }
        val best = candidates.filter { it.second == bestScore }.map { it.first }
        val method = best.singleOrNull() ?: best.singleOrNull { candidate ->
            best.all { other -> candidate == other || moreSpecific(candidate, other) }
        } ?: throw NoSuchMethodException("No unambiguous match for ${owner.name}.$name(${key.types})")
        method.isAccessible = true
        if (methods.size >= 256) methods.clear()
        methods[key] = method
        return method
    }

    private fun score(parameter: Class<*>, argument: Class<*>?): Int? {
        if (argument == null) return if (parameter.isPrimitive) null else 0
        if (parameter == argument) return 0
        if (parameter.isPrimitive) {
            val target = boxed[parameter]
            if (target == argument) return 1
            val index = widening[argument]?.indexOf(target) ?: -1
            return if (index >= 0) index + 2 else null
        }
        return if (parameter.isAssignableFrom(argument)) 10 else null
    }

    private fun moreSpecific(first: Method, second: Method): Boolean =
        first.parameterTypes.zip(second.parameterTypes).all { (a, b) ->
            a == b || (!a.isPrimitive && !b.isPrimitive && b.isAssignableFrom(a))
        }

    private fun hierarchy(owner: Class<*>): Sequence<Class<*>> =
        generateSequence(owner) { it.superclass }
}
