package io.github.duplexsystem.projectelegant

import io.github.duplexsystem.projectelegant.ConcurrentJsExecutor.ComputeFromJavaFunction
import org.graalvm.polyglot.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import io.netty.util.internal.logging.Log4J2LoggerFactory


internal class ConcurrentJsExecutor(private val jsCode: String) {
    private class ContextProvider(val context: Context) {
        private val lock: ReentrantLock = ReentrantLock()
        fun getLock(): Lock {
            return lock
        }

    }

    private val sharedEngine = Engine.newBuilder().build()
    private val jsContext = ThreadLocal.withInitial {
        val cx =
            Context.newBuilder(JS).allowHostAccess(HostAccess.ALL)
                .allowPolyglotAccess(PolyglotAccess.ALL)
                .engine(sharedEngine).build()
        val provider = ContextProvider(cx)
        cx.getBindings(JS)
            .putMember("computeFromJava", createJavaInteropComputeFunction(provider))
        println("Created new JS context for thread " + Thread.currentThread())
        provider
    }

    private fun createJavaInteropComputeFunction(cx: ContextProvider): Function<*, *> {
        return Function<Any, Any?> { requestId: Any ->
            ComputeFromJavaFunction { onResolve, onReject ->
                CompletableFuture.supplyAsync(
                    Supplier {
                        cx.getLock().lock()
                        try {
                            if ((requestId as Int) < 42) {
                                return@Supplier onReject!!.execute("$requestId is not a valid request id!")
                            }
                            val v = requestId as Int + Math.random()
                            return@Supplier onResolve!!.execute(v)
                        } catch (e: PolyglotException) {
                            return@Supplier onReject!!.execute(if (e.guestObject == null) e.guestObject else e.message)
                        } finally {
                            cx.getLock().unlock()
                        }
                    })
            }
        }
    }

    fun callJavaScriptAsyncFunction(requestId: Int): CompletionStage<Any> {
        val jsExecution = CompletableFuture<Any>()
        val cx = jsContext.get()
        cx.getLock().lock()
        try {
            val jsAsyncFunctionPromise = cx.context.eval(
                JS,
                jsCode
            ).execute(requestId)
            jsAsyncFunctionPromise.invokeMember(
                THEN,
                Consumer { value: Any -> jsExecution.complete(value) } as Consumer<*>)
                .invokeMember(
                    CATCH,
                    Consumer { ex: Throwable? -> jsExecution.completeExceptionally(ex) })
        } catch (t: Throwable) {
            jsExecution.completeExceptionally(t)
        } finally {
            cx.getLock().unlock()
        }
        return jsExecution
    }

    fun interface ComputeFromJavaFunction {
        fun then(onResolve: Value?, onReject: Value?)
    }

    companion object {
        private const val JS = "js"
        private const val THEN = "then"
        private const val CATCH = "catch"
    }
}