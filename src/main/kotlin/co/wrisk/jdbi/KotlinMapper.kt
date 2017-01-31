/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.wrisk.jdbi

import org.skife.jdbi.v2.StatementContext
import org.skife.jdbi.v2.tweak.ResultColumnMapper
import org.skife.jdbi.v2.tweak.ResultSetMapper
import java.lang.reflect.InvocationTargetException
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

private typealias ValueProvider = (r: ResultSet, c: StatementContext) -> Any?
private val NULL_VALUE_PROVIDER: ValueProvider = { _, _ -> null }


class KotlinMapper<C : Any>(clazz: Class<C>) : ResultSetMapper<C> {

    private val kClass: KClass<C> = clazz.kotlin

    private val constructor = findConstructor(kClass)
    private val constructorParameters = constructor.parameters
    private val nullableMandatoryParameters = constructorParameters.filter { !it.isOptional && it.type.isMarkedNullable }
    private val mutableProperties = kClass.memberProperties.map { it as? KMutableProperty1 }.filterNotNull()

    private val propertyByColumnCache = ConcurrentHashMap<String, Optional<KMutableProperty1<C, *>>>()
    private val parameterByColumnCache = ConcurrentHashMap<String, Optional<KParameter>>()


    @Throws(SQLException::class)
    override fun map(i: Int, rs: ResultSet, ctx: StatementContext): C {
        val parameters = mutableMapOf<KParameter, ValueProvider>()
        val properties = mutableListOf<Pair<KMutableProperty1<C, *>, ValueProvider>>()

        val metadata = rs.metaData
        val columns = (metadata.columnCount downTo 1).map { Pair(it, metadata.getColumnLabel(it)) }.distinctBy { (_, name) -> name.toLowerCase() }

        for ((columnNumber, columnName) in columns) {

            val parameter = parameterByColumnCache.computeIfAbsent(columnName, {
                Optional.ofNullable(constructorParameters.find { matches(columnName, it.name) })
            }).orElse(null)

            if (parameter != null) {
                parameters.put(parameter, valueProvider(parameter.type, columnNumber, ctx))
                continue
            }

            val property = propertyByColumnCache.computeIfAbsent(columnName, {
                Optional.ofNullable(mutableProperties.find { matches(columnName, it.name) })
            }).orElse(null)

            if (property != null) {
                properties.add(Pair(property, valueProvider(property.returnType, columnNumber, ctx)))
            }
        }

        // things missing from the result set that are Nullable and not optional should be set to Null
        nullableMandatoryParameters.forEach { parameters.putIfAbsent(it, NULL_VALUE_PROVIDER) }

        val parametersWithValue = parameters.mapValues { (_, valueProvider) -> valueProvider(rs, ctx) }
        val propertiesWithValue = properties.map { (property, valueProvider) -> Pair(property, valueProvider(rs, ctx)) }

        try {
            constructor.isAccessible = true
            val instance = constructor.callBy(parametersWithValue)

            propertiesWithValue.forEach { (prop, value) ->
                prop.isAccessible = true
                prop.setter.call(instance, value)
            }

            return instance
        } catch (e: InvocationTargetException) {
            throw IllegalArgumentException("A bean, ${kClass.simpleName} was mapped which was not instantiable", e.targetException)
        } catch (e: ReflectiveOperationException) {
            throw IllegalArgumentException("A bean, ${kClass.simpleName} was mapped which was not instantiable", e)
        }

    }


}

private fun <C : Any> findConstructor(kClass: KClass<C>) = kClass.primaryConstructor ?: findSecondaryConstructor(kClass)

private fun <C : Any> findSecondaryConstructor(kClass: KClass<C>): KFunction<C> {
    if (kClass.constructors.size == 1) {
        return kClass.constructors.first()
    } else {
        throw IllegalArgumentException("A bean, ${kClass.simpleName} was mapped which was not instantiable (cannot find appropriate constructor)")
    }
}

private fun matches(columnName: String, paramName: String?): Boolean {
    val param = paramName ?: return false
    return columnName.toLowerCase() == param.toLowerCase()
}


private fun valueProvider(paramType: KType, columnNumber: Int, ctx: StatementContext): ValueProvider {
    val columnMapper = ctx.columnMapperFor(paramType.javaType.erasedType()) ?: (object : ResultColumnMapper<Any> {
        override fun mapColumn(r: ResultSet, columnNumber: Int, ctx: StatementContext): Any {
            return r.getObject(columnNumber)
        }

        override fun mapColumn(r: ResultSet, columnLabel: String, ctx: StatementContext): Any {
            return r.getObject(columnLabel)
        }
    })
    return { r, c -> columnMapper.mapColumn(r, columnNumber, c) }
}
