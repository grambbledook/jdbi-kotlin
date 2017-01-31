package co.wrisk.jdbi

import org.skife.jdbi.v2.DBI
import org.skife.jdbi.v2.Handle

class KotlinPlugin {

    fun customizeHandle(handle: Handle): Handle {
        handle.registerMapper(KotlinMapperFactory())
        return handle
    }

    fun customizeDBI(dbi: DBI): DBI {
        dbi.registerMapper(KotlinMapperFactory())
        return dbi
    }
}

fun Handle.attachKotlinPlugin(): Handle {
    return KotlinPlugin().customizeHandle(this)
}

fun DBI.attachKotlinPlugin(): DBI {
    return KotlinPlugin().customizeDBI(this)
}

