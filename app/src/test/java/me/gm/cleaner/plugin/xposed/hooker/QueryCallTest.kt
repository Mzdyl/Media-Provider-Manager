package me.gm.cleaner.plugin.xposed.hooker

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Chain
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class QueryCallTest {
    class Target {
        fun query(argument: Any?): Any? = argument
    }

    private class Call(private val run: (Array<Any?>) -> Any?) : Chain {
        val target = Target()
        val original = Any()
        var calls = 0
        override fun getExecutable() = Target::class.java.getDeclaredMethod("query", Any::class.java)
        override fun getThisObject(): Any = target
        override fun getArgs(): List<Any?> = listOf(original)
        override fun getArg(index: Int): Any? = args[index]
        override fun proceed(): Any? = proceed(args.toTypedArray())
        override fun proceed(arguments: Array<Any?>): Any? {
            calls++
            return run(arguments)
        }
        override fun proceedWith(receiver: Any): Any? = error("Unexpected receiver change")
        override fun proceedWith(receiver: Any, arguments: Array<Any?>): Any? = error("Unexpected receiver change")
    }

    private fun framework(original: (Any?, Array<*>) -> Any?): XposedInterface {
        val invoker = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Invoker::class.java)) { self, method, args ->
            when (method.name) {
                "setType" -> {
                    assertEquals(XposedInterface.Invoker.Type.ORIGIN, args!![0])
                    self
                }
                "invoke" -> original(args!![0], args[1] as Array<*>)
                else -> error("Unexpected invoker call: ${method.name}")
            }
        }
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface::class.java)) { _, method, _ ->
            check(method.name == "getInvoker")
            invoker
        } as XposedInterface
    }

    @Test
    fun passesModifiedArgumentsWithoutChangingCallerArgumentsOrResult() {
        val filtered = Any()
        val result = Any()
        val chain = Call { assertSame(filtered, it[0]); result }
        val outcome = QueryCall.execute(framework { _, _ -> error("Unexpected retry") }, chain, arrayOf(filtered), true)
        assertSame(result, outcome.value)
        assertNull(outcome.filterFailure)
        assertSame(chain.original, chain.args[0])
        assertEquals(1, chain.calls)
    }

    @Test
    fun rejectedFilterRetriesOnlyTheOriginalWithOriginalArguments() {
        val failure = IllegalArgumentException("unsupported SQL")
        val chain = Call { throw failure }
        val result = Any()
        var retries = 0
        val framework = framework { receiver, args ->
            retries++
            assertSame(chain.target, receiver)
            assertSame(chain.original, args[0])
            result
        }
        val outcome = QueryCall.execute(framework, chain, arrayOf(Any()), true)
        assertSame(result, outcome.value)
        assertSame(failure, outcome.filterFailure)
        assertEquals(1, retries)
        assertEquals(1, chain.calls)
    }

    @Test
    fun propagatesUnfilteredErrorsWithoutRetry() {
        val failure = SecurityException("not permitted")
        val chain = Call { throw failure }
        assertSame(failure, assertThrows(SecurityException::class.java) {
            QueryCall.execute(framework { _, _ -> error("Unexpected retry") }, chain, chain.args.toTypedArray(), false)
        })
        assertEquals(1, chain.calls)
    }

    @Test
    fun cancellationIsNeverRetried() {
        val failure = CancellationException("cancelled")
        val chain = Call { throw failure }
        assertSame(failure, assertThrows(CancellationException::class.java) {
            QueryCall.execute(framework { _, _ -> error("Unexpected retry") }, chain, arrayOf(Any()), true)
        })
    }

    @Test
    fun failedOriginalRetryPropagatesItsActualException() {
        val failure = SecurityException("original permission check")
        val chain = Call { throw IllegalArgumentException("rejected filter") }
        assertSame(failure, assertThrows(SecurityException::class.java) {
            QueryCall.execute(framework { _, _ -> throw InvocationTargetException(failure) }, chain, arrayOf(Any()), true)
        })
        assertEquals(1, chain.calls)
    }
}
