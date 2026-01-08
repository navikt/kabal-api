package no.nav.klage.oppgave.service
import no.nav.klage.oppgave.clients.skjermedepersonerpip.SkjermedePersonerPipRestClient
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Service
class EgenAnsattService(
    private val skjermedePersonerPipRestClient: SkjermedePersonerPipRestClient
) {

    private val isSkjermetMap: ConcurrentMap<String, Boolean> = ConcurrentHashMap()

    fun erEgenAnsatt(foedselsnr: String, systemContext: Boolean = false): Boolean {
        if (isSkjermetMap.containsKey(foedselsnr)) {
            return isSkjermetMap.getValue(foedselsnr)
        } else {
            val isSkjermet = skjermedePersonerPipRestClient.isSkjermet(foedselsnr, systemContext)
            isSkjermetMap[foedselsnr] = isSkjermet
            return isSkjermet
        }
    }

    fun setIsSkjermetCache(fnrList: List<String>, systemContext: Boolean) {
        val results = skjermedePersonerPipRestClient.isSkjermetBulk(fnrList = fnrList, systemContext = systemContext)
        isSkjermetMap.clear()
        isSkjermetMap.putAll(results)
    }

    //TODO: Listen to changes from PIP and update the cache accordingly
}