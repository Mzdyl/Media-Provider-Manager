package me.gm.cleaner.plugin.xposed.util

import org.junit.Assert.*
import org.junit.Test

class ReflectionTest {
    private open class Parent {
        private val identity = "caller"
        private fun inherited(value: String) = "parent:$value"
    }

    private class Provider : Parent() {
        fun flags(value: Int) = "int:$value"
        fun flags(value: Long) = "long:$value"
        fun widen(value: Long) = value
        fun selected(value: Any?) = "any:$value"
        fun selected(value: CharSequence?) = "text:$value"
        fun ambiguous(value: String?) = value
        fun ambiguous(value: IntArray?) = value
        fun projection(columns: Array<String>, selection: String?) = columns.joinToString() + selection
        fun fail(error: RuntimeException): Nothing = throw error
    }

    @Test
    fun readsInheritedPrivateIdentityAndCallsInheritedMethods() {
        val provider = Provider()
        assertEquals("caller", Reflection.getObjectField(provider, "identity"))
        assertEquals("parent:query", Reflection.callMethod(provider, "inherited", "query"))
    }

    @Test
    fun selectsIntAndLongPackageManagerSignaturesIndependently() {
        val provider = Provider()
        repeat(2) {
            assertEquals("int:12", Reflection.callMethod(provider, "flags", 12))
            assertEquals("long:12", Reflection.callMethod(provider, "flags", 12L))
        }
    }

    @Test
    fun widensPrimitiveArgumentsButRejectsNullForPrimitive() {
        val provider = Provider()
        assertEquals(7L, Reflection.callMethod(provider, "widen", 7))
        assertThrows(NoSuchMethodException::class.java) { Reflection.callMethod(provider, "widen", null) }
    }

    @Test
    fun choosesTheMostSpecificReferenceIncludingNull() {
        assertEquals("text:null", Reflection.callMethod(Provider(), "selected", null))
        assertEquals("text:media", Reflection.callMethod(Provider(), "selected", "media"))
    }

    @Test
    fun rejectsUnrelatedNullOverloadsInsteadOfInvokingArbitrarily() {
        assertThrows(NoSuchMethodException::class.java) { Reflection.callMethod(Provider(), "ambiguous", null) }
    }

    @Test
    fun preservesArrayArgumentsAndNullQuerySelection() {
        assertEquals("_id, _datanull", Reflection.callMethod(Provider(), "projection", arrayOf("_id", "_data"), null))
    }

    @Test
    fun propagatesTheProvidersActualException() {
        val failure = SecurityException("permission denied")
        assertSame(failure, assertThrows(SecurityException::class.java) {
            Reflection.callMethod(Provider(), "fail", failure)
        })
    }

    @Test
    fun staticCallsDoNotResolveInstanceOverloads() {
        assertEquals(9L, Reflection.callStaticMethod(java.lang.Math::class.java, "max", 7L, 9L))
        assertThrows(NoSuchMethodException::class.java) {
            Reflection.callStaticMethod(Provider::class.java, "flags", 12)
        }
    }
}
