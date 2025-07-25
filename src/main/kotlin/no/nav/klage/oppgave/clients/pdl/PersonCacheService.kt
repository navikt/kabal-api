package no.nav.klage.oppgave.clients.pdl

import no.nav.klage.oppgave.domain.person.Person
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Service
class PersonCacheService {

    private val personMap: ConcurrentMap<String, Person> = ConcurrentHashMap()

    fun isCached(foedselsnr: String): Boolean = personMap.containsKey(foedselsnr)

    fun getPerson(foedselsnr: String): Person = personMap.getValue(foedselsnr)

    fun updatePersonCache(person: Person) {
        personMap[person.foedselsnr] = person
    }

    fun findFnrsNotInCache(fnrList: List<String>): List<String> {
        return fnrList.filter { !isCached(it) }
    }

    fun emptyCache() {
        personMap.clear()
    }
}