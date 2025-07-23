package no.nav.klage.oppgave.clients.pdl

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

    fun removePersonFromCache(foedselsnr: String) {
        personMap.remove(foedselsnr)
    }

    fun findFnrsNotInCache(fnrList: List<String>): List<String> {
        return fnrList.filter { !isCached(it) }
    }

    fun emptyCache() {
        personMap.clear()
    }

    fun getCache(): ConcurrentMap<String, Person> {
        return personMap
    }
}