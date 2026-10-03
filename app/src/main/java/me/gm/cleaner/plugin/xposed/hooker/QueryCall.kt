package me.gm.cleaner.plugin.xposed.hooker

import android.os.OperationCanceledException
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Chain
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.CancellationException

/** Executes API 102 query chains without mutating caller arguments or re-entering hooks on retry. */
internal object QueryCall {
    data class Outcome(val value: Any?, val filterFailure: Throwable? = null)

    fun execute(
        framework: XposedInterface,
        chain: Chain,
        arguments: Array<Any?>,
        filterApplied: Boolean,
    ): Outcome {
        try {
            return Outcome(chain.proceed(arguments))
        } catch (failure: Throwable) {
            if (!filterApplied || failure is OperationCanceledException || failure is CancellationException) {
                throw failure
            }
            val result = try {
                framework.getInvoker(chain.executable as Method)
                    .setType(XposedInterface.Invoker.Type.ORIGIN)
                    .invoke(chain.thisObject, *chain.args.toTypedArray())
            } catch (exception: InvocationTargetException) {
                throw exception.targetException
            }
            return Outcome(result, failure)
        }
    }
}
