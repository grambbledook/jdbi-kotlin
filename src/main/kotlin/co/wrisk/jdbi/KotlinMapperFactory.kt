package co.wrisk.jdbi

import org.skife.jdbi.v2.ResultSetMapperFactory
import org.skife.jdbi.v2.StatementContext
import org.skife.jdbi.v2.tweak.ResultSetMapper

class KotlinMapperFactory : ResultSetMapperFactory {
    override fun mapperFor(forClass: Class<*>, ctx: StatementContext): ResultSetMapper<*>? {
        return KotlinMapper(forClass)
    }

    override fun accepts(forClass: Class<*>, ctx: StatementContext): Boolean {
        return forClass.isKotlinClass()
    }
}