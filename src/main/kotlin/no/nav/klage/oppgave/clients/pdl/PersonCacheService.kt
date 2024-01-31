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
}