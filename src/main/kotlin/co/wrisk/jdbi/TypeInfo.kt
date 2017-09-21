package co.wrisk.jdbi

import java.lang.reflect.*

//see klutter/reflect-core-jdk6/src/main/kotlin/uy/klutter/reflect/TypeInfo.kt

@Suppress("UNCHECKED_CAST") fun Type.erasedType(): Class<Any> {
    return when (this) {
        is Class<*> -> this as Class<Any>
        is ParameterizedType -> this.rawType.erasedType()
        is GenericArrayType -> {
            // getting the array type is a bit trickier
            val elementType = this.genericComponentType.erasedType()
            val testArray = java.lang.reflect.Array.newInstance(elementType, 0)
            testArray.javaClass
        }
        is TypeVariable<*> -> {
            // not sure yet
            throw IllegalStateException("Not sure what to do here yet")
        }
        is WildcardType -> {
            this.upperBounds[0].erasedType()
        }
        else -> throw IllegalStateException("Should not get here.")
    }
}
//see klutter/db-jdbi-v2-jdk6/src/main/kotlin/uy/klutter/db/jdbi/v2/Extensions.kt

