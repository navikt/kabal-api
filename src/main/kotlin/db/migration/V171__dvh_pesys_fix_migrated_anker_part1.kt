package db.migration

import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime
import java.util.*

class V171__dvh_pesys_fix_migrated_anker_part1 : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val preparedStatement = context.connection.prepareStatement(
            """
                update klage.kafka_event
                    set json_payload = ?, status_id = ?
                    where id = ?
            """.trimIndent()
        )

        context.connection.createStatement().use { select ->
            select.executeQuery(
                """
                    select ke.id, ke.json_payload
                    from klage.kafka_event ke
                    where ke.type = 'STATS_DVH'
                      and ke.kilde_referanse in (
 '50190652',
 '50213593',
 '50819061',
 '50912418',
 '50988373',
 '51066345',
 '51126096',
 '51258272',
 '51299740',
 '51508669',
 '54844378',
 '54861548',
 '54919683',
 '56012058',
 '56049051',
 '56080293',
 '56132607',
 '57900468',
 '57923914',
 '57924379',
 '58074032',
 '58084112',
 '58102607',
 '58116850',
 '58143583',
 '58158150',
 '58158462',
 '58274427',
 '59561120',
 '59603592',
 '59662215',
 '59864895',
 '59904006',
 '59908264',
 '59929556',
 '59982339',
 '59985420',
 '59990470',
 '60002803',
 '60011534',
 '60014852',
 '60017279',
 '60017992',
 '60038002',
 '61745969',
 '61810294',
 '61825444',
 '61828932',
 '61848770',
 '61853853',
 '61861454',
 '61872471',
 '61878425',
 '61904176',
 '61916713',
 '61919270',
 '61919895',
 '61942123',
 '61944375',
 '62019921',
 '62022919',
 '62025703',
 '62376026',
 '62376310',
 '62404347',
 '62704278',
 '62715222',
 '62721773',
 '62726770',
 '62731962',
 '62744094',
 '62764405',
 '62772778',
 '63241859',
 '63249638',
 '63251321',
 '63252618',
 '63253343',
 '63256477',
 '63266285',
 '63268380',
 '63284493',
 '63289231',
 '63301556',
 '63304052',
 '63304635',
 '63307439',
 '63317669',
 '63338464',
 '64951446',
 '64972546',
 '64973599',
 '65041668',
 '65065939',
 '65069317',
 '65070129',
 '65086680',
 '65090556',
 '65091543',
 '65109628',
 '65130391',
 '65131595',
 '65136146',
 '65187021',
 '65190915',
 '65197937',
 '65197963',
 '65199954',
 '65212514',
 '65216514',
 '65219241',
 '65224579',
 '65225415',
 '65227363',
 '65227996',
 '65229993',
 '65251401',
 '65251641',
 '65253705',
 '65511140',
 '65514740',
 '65515420',
 '65516025',
 '65528392',
 '65529780',
 '65530076',
 '65530822',
 '65532412',
 '65532562',
 '65585388',
 '65586649',
 '65586802',
 '65587623',
 '65593174',
 '65593258',
 '65664704',
 '65676382',
 '65692483',
 '65693566',
 '65693646',
 '65693950',
 '65698613',
 '65716604',
 '65716885',
 '65882077',
 '65882265',
 '65882577',
 '65883435',
 '65883596',
 '65883923',
 '65885054',
 '65885487',
 '65892248',
 '66013039',
 '66015832',
 '66038470',
 '66044332',
 '66052395',
 '66052956',
 '66054057',
 '66054328',
 '66054357',
 '66055059',
 '66055206',
 '66055292',
 '66056135',
 '66056381',
 '66057832',
 '66060285',
 '66069490',
 '66070243',
 '66070878',
 '66071244',
 '66071371',
 '66071455',
 '66071816',
 '66073658',
 '66074582',
 '66077732',
 '66077847',
 '66078158',
 '66078646',
 '66079495',
 '66079864',
 '66080923',
 '66080975',
 '66081712',
 '66087111',
 '66087142',
 '66088144',
 '66089850',
 '66089856',
 '66090577',
 '66091342',
 '66091348',
 '66091950',
 '66093014',
 '66094685',
 '66095141',
 '66097093',
 '66097219',
 '66099156',
 '66100582',
 '66102707',
 '66102851',
 '66103113',
 '66103545',
 '66106381',
 '66106521',
 '66107003',
 '66108238',
 '66108427',
 '66108783',
 '66108788',
 '66108807',
 '66108890',
 '66109340',
 '66109862',
 '66110236',
 '66110882',
 '66114415',
 '66114719',
 '66114893',
 '66115162',
 '66116079',
 '66116197',
 '66116729',
 '66117055',
 '66117104',
 '66117762',
 '66118093',
 '66118857',
 '66119162',
 '66120410',
 '66120553',
 '66121155',
 '66123332',
 '66124730',
 '66125239',
 '66126479',
 '66126820',
 '66133489',
 '66133760',
 '66134249',
 '66134603',
 '66135437',
 '66135663',
 '66136484',
 '66137170',
 '66137177',
 '66137304',
 '66138535',
 '66138701',
 '66138900',
 '66139338',
 '66140753',
 '66141336',
 '66141433',
 '66142009',
 '66142426',
 '66142567',
 '66143942',
 '66144617',
 '66145004',
 '66145272',
 '66145959',
 '66146087',
 '66146947',
 '66147148',
 '66149303',
 '66149554',
 '66150671',
 '66150702',
 '66150842',
 '66150958',
 '66151022',
 '66151963',
 '66152151',
 '66152531',
 '66152570',
 '66152702',
 '66152745',
 '66152977',
 '66156931',
 '66156942',
 '66157171',
 '66157240',
 '66157786',
 '66158031',
 '66158225',
 '66158438',
 '66158562',
 '66160197',
 '66160661',
 '66161638',
 '66161797',
 '66163552',
 '66164308',
 '66164650',
 '66171666',
 '66173055',
 '66173406',
 '66173721',
 '66173817',
 '66173942',
 '66174184',
 '66174526',
 '66175062',
 '66175627',
 '66177766',
 '66178484',
 '66178909',
 '66178956',
 '66181581',
 '66182771',
 '66183068',
 '66183978',
 '66187531',
 '66187536',
 '66190583',
 '66190814',
 '66191257',
 '66191386',
 '66191990',
 '66192498',
 '66192778',
 '66193982',
 '66194219',
 '66194676',
 '66197334',
 '66197661',
 '66197771',
 '66197891',
 '66198083',
 '66198764',
 '66198787',
 '66200629',
 '66201876',
 '66204928',
 '66204947',
 '66212098',
 '66212368',
 '66212601',
 '66212663',
 '66214012',
 '66214482',
 '66215552',
 '66216339',
 '66217204',
 '66220478',
 '66222978',
 '66223224',
 '66223470',
 '66234730',
 '66235037',
 '66236526',
 '66236645',
 '66239102',
 '66239175',
 '66239252',
 '66239740',
 '66240524',
 '66241528',
 '67885329',
 '67885446',
 '67885670',
 '67885797',
 '67885812',
 '67886375',
 '67886463',
 '67890498',
 '67891100',
 '67892362',
 '67902279',
 '67902416',
 '67903083',
 '67908522',
 '67909046',
 '68071016',
 '68078578',
 '68083622',
 '68307546',
 '68308211',
 '68312354',
 '68320852',
 '68321184',
 '68325064',
 '68325211',
 '68331015',
 '68331750',
 '68333057',
 '68335164',
 '68335904',
 '68339943',
 '68341966',
 '68346480',
 '68346810',
 '68349027',
 '68356961',
 '68357181',
 '68357300',
 '68361518',
 '68365130',
 '68365486',
 '68370244',
 '68371184',
 '68372100',
 '68372403',
 '68372516',
 '68373130',
 '68374308',
 '68374374',
 '68376207',
 '68377861',
 '68378050',
 '68378289',
 '68378343',
 '68379674',
 '68380026',
 '68380595',
 '68383245',
 '68386349',
 '68386801',
 '68387393',
 '68387440',
 '68387727',
 '68387923',
 '68389019',
 '68389702',
 '68389818',
 '68396218',
 '68396547',
 '68397995',
 '68398156',
 '68399202',
 '68399209',
 '68401033',
 '68409038',
 '68429443',
 '68430534',
 '68435624',
 '68441630',
 '68461519',
 '68466018',
 '68510878',
 '29503382',
 '31893181',
 '32371901',
 '39209583',
 '39628406',
 '43111918',
 '43346320',
 '44301951',
 '48838916',
 '48909701',
 '48934417',
 '49268670',
 '49274622',
 '49330837',
 '49347474',
 '49477628',
 '49531818',
 '49598748',
 '50190652',
 '50782964',
 '50886746',
 '50912208',
 '51072581',
 '51150836',
 '51152892',
 '51186493',
 '51315211',
 '51337108',
 '51343962',
 '51395362',
 '54691899',
 '54756580',
 '55979739',
 '55997414',
 '56016109',
 '56032813',
 '56059026',
 '56062827',
 '56120295',
 '56146284',
 '57915050',
 '57922793',
 '58031003',
 '58033419',
 '58049119',
 '58104496',
 '58125721',
 '58134451',
 '58173488',
 '58185783',
 '58208292',
 '58231994',
 '58234889',
 '58235048',
 '58258388')
                    """
            )
                .use { rows ->
                    while (rows.next()) {
                        val kafkaEventId = rows.getObject(1, UUID::class.java)
                        val jsonPayload = rows.getString(2)

                        val statistikkTilDVH =
                            jacksonObjectMapper().readValue(jsonPayload, StatistikkTilDVH::class.java)

                        val modifiedVersion = when (statistikkTilDVH.behandlingId) {
                            "33904088" -> statistikkTilDVH.copy(behandlingId = "49293914", tekniskTid = LocalDateTime.now())
                            "34560882" -> statistikkTilDVH.copy(behandlingId = "49293420", tekniskTid = LocalDateTime.now())
                            "35794812" -> statistikkTilDVH.copy(behandlingId = "49225003", tekniskTid = LocalDateTime.now())
                            "35167228" -> statistikkTilDVH.copy(behandlingId = "49273116", tekniskTid = LocalDateTime.now())
                            "35906064" -> statistikkTilDVH.copy(behandlingId = "49281950", tekniskTid = LocalDateTime.now())
                            "35925354" -> statistikkTilDVH.copy(behandlingId = "49483847", tekniskTid = LocalDateTime.now())
                            "35889216" -> statistikkTilDVH.copy(behandlingId = "49232882", tekniskTid = LocalDateTime.now())
                            "36189440" -> statistikkTilDVH.copy(behandlingId = "48535777", tekniskTid = LocalDateTime.now())
                            "36194672" -> statistikkTilDVH.copy(behandlingId = "49772549", tekniskTid = LocalDateTime.now())
                            "36318586" -> statistikkTilDVH.copy(behandlingId = "49052782", tekniskTid = LocalDateTime.now())
                            "36310818" -> statistikkTilDVH.copy(behandlingId = "47283604", tekniskTid = LocalDateTime.now())
                            "36321848" -> statistikkTilDVH.copy(behandlingId = "50193872", tekniskTid = LocalDateTime.now())
                            "36404819" -> statistikkTilDVH.copy(behandlingId = "48387369", tekniskTid = LocalDateTime.now())
                            "36755912" -> statistikkTilDVH.copy(behandlingId = "49198118", tekniskTid = LocalDateTime.now())
                            "37006780" -> statistikkTilDVH.copy(behandlingId = "49293326", tekniskTid = LocalDateTime.now())
                            "38429750" -> statistikkTilDVH.copy(behandlingId = "48482547", tekniskTid = LocalDateTime.now())
                            "38398590" -> statistikkTilDVH.copy(behandlingId = "49071570", tekniskTid = LocalDateTime.now())
                            "39011125" -> statistikkTilDVH.copy(behandlingId = "48814793", tekniskTid = LocalDateTime.now())
                            "38638400" -> statistikkTilDVH.copy(behandlingId = "49106341", tekniskTid = LocalDateTime.now())
                            "38569768" -> statistikkTilDVH.copy(behandlingId = "48421458", tekniskTid = LocalDateTime.now())
                            "39000882" -> statistikkTilDVH.copy(behandlingId = "49425551", tekniskTid = LocalDateTime.now())
                            "38860791" -> statistikkTilDVH.copy(behandlingId = "48433641", tekniskTid = LocalDateTime.now())
                            "39430861" -> statistikkTilDVH.copy(behandlingId = "48824922", tekniskTid = LocalDateTime.now())
                            "38845358" -> statistikkTilDVH.copy(behandlingId = "45621910", tekniskTid = LocalDateTime.now())
                            "40229872" -> statistikkTilDVH.copy(behandlingId = "43241062", tekniskTid = LocalDateTime.now())
                            "40099591" -> statistikkTilDVH.copy(behandlingId = "43042881", tekniskTid = LocalDateTime.now())
                            "39779473" -> statistikkTilDVH.copy(behandlingId = "48814396", tekniskTid = LocalDateTime.now())
                            "41039962" -> statistikkTilDVH.copy(behandlingId = "49088330", tekniskTid = LocalDateTime.now())
                            "40555578" -> statistikkTilDVH.copy(behandlingId = "48349077", tekniskTid = LocalDateTime.now())
                            "40539571" -> statistikkTilDVH.copy(behandlingId = "49780197", tekniskTid = LocalDateTime.now())
                            "42533280" -> statistikkTilDVH.copy(behandlingId = "49183011", tekniskTid = LocalDateTime.now())
                            "42768712" -> statistikkTilDVH.copy(behandlingId = "48461130", tekniskTid = LocalDateTime.now())
                            "41039977" -> statistikkTilDVH.copy(behandlingId = "49106880", tekniskTid = LocalDateTime.now())
                            "40969539" -> statistikkTilDVH.copy(behandlingId = "47195706", tekniskTid = LocalDateTime.now())
                            "42717436" -> statistikkTilDVH.copy(behandlingId = "49138000", tekniskTid = LocalDateTime.now())
                            "42765315" -> statistikkTilDVH.copy(behandlingId = "48433633", tekniskTid = LocalDateTime.now())
                            "42923761" -> statistikkTilDVH.copy(behandlingId = "49246263", tekniskTid = LocalDateTime.now())
                            "42756348" -> statistikkTilDVH.copy(behandlingId = "49091025", tekniskTid = LocalDateTime.now())
                            "43110823" -> statistikkTilDVH.copy(behandlingId = "44481685", tekniskTid = LocalDateTime.now())
                            "43413310" -> statistikkTilDVH.copy(behandlingId = "44650420", tekniskTid = LocalDateTime.now())
                            "43416104" -> statistikkTilDVH.copy(behandlingId = "49293379", tekniskTid = LocalDateTime.now())
                            "43461250" -> statistikkTilDVH.copy(behandlingId = "44568491", tekniskTid = LocalDateTime.now())
                            "42931240" -> statistikkTilDVH.copy(behandlingId = "49056262", tekniskTid = LocalDateTime.now())
                            "43136588" -> statistikkTilDVH.copy(behandlingId = "49371288", tekniskTid = LocalDateTime.now())
                            "43191154" -> statistikkTilDVH.copy(behandlingId = "49414809", tekniskTid = LocalDateTime.now())
                            "44246103" -> statistikkTilDVH.copy(behandlingId = "49043347", tekniskTid = LocalDateTime.now())
                            "43800855" -> statistikkTilDVH.copy(behandlingId = "49324426", tekniskTid = LocalDateTime.now())
                            "43417002" -> statistikkTilDVH.copy(behandlingId = "48428307", tekniskTid = LocalDateTime.now())
                            "43017172" -> statistikkTilDVH.copy(behandlingId = "49074479", tekniskTid = LocalDateTime.now())
                            "44321290" -> statistikkTilDVH.copy(behandlingId = "49425567", tekniskTid = LocalDateTime.now())
                            "44207224" -> statistikkTilDVH.copy(behandlingId = "49311398", tekniskTid = LocalDateTime.now())
                            "43018263" -> statistikkTilDVH.copy(behandlingId = "48803374", tekniskTid = LocalDateTime.now())
                            "44160832" -> statistikkTilDVH.copy(behandlingId = "46350569", tekniskTid = LocalDateTime.now())
                            "43041250" -> statistikkTilDVH.copy(behandlingId = "48815546", tekniskTid = LocalDateTime.now())
                            "44598590" -> statistikkTilDVH.copy(behandlingId = "46573451", tekniskTid = LocalDateTime.now())
                            "44455714" -> statistikkTilDVH.copy(behandlingId = "49125177", tekniskTid = LocalDateTime.now())
                            "44563574" -> statistikkTilDVH.copy(behandlingId = "49386948", tekniskTid = LocalDateTime.now())
                            "44660159" -> statistikkTilDVH.copy(behandlingId = "49306409", tekniskTid = LocalDateTime.now())
                            "44357306" -> statistikkTilDVH.copy(behandlingId = "49323802", tekniskTid = LocalDateTime.now())
                            "44648100" -> statistikkTilDVH.copy(behandlingId = "49134620", tekniskTid = LocalDateTime.now())
                            "44356266" -> statistikkTilDVH.copy(behandlingId = "49905975", tekniskTid = LocalDateTime.now())
                            "44357123" -> statistikkTilDVH.copy(behandlingId = "49456039", tekniskTid = LocalDateTime.now())
                            "44819209" -> statistikkTilDVH.copy(behandlingId = "49478752", tekniskTid = LocalDateTime.now())
                            "45260345" -> statistikkTilDVH.copy(behandlingId = "49135976", tekniskTid = LocalDateTime.now())
                            "45318501" -> statistikkTilDVH.copy(behandlingId = "49407379", tekniskTid = LocalDateTime.now())
                            "45643198" -> statistikkTilDVH.copy(behandlingId = "49464004", tekniskTid = LocalDateTime.now())
                            "44784949" -> statistikkTilDVH.copy(behandlingId = "49215752", tekniskTid = LocalDateTime.now())
                            "45657398" -> statistikkTilDVH.copy(behandlingId = "49230147", tekniskTid = LocalDateTime.now())
                            "44264073" -> statistikkTilDVH.copy(behandlingId = "49238663", tekniskTid = LocalDateTime.now())
                            "44772007" -> statistikkTilDVH.copy(behandlingId = "46651952", tekniskTid = LocalDateTime.now())
                            "45739520" -> statistikkTilDVH.copy(behandlingId = "45883772", tekniskTid = LocalDateTime.now())
                            "44809543" -> statistikkTilDVH.copy(behandlingId = "49433495", tekniskTid = LocalDateTime.now())
                            "45821307" -> statistikkTilDVH.copy(behandlingId = "49780615", tekniskTid = LocalDateTime.now())
                            "44665013" -> statistikkTilDVH.copy(behandlingId = "49772181", tekniskTid = LocalDateTime.now())
                            "44219621" -> statistikkTilDVH.copy(behandlingId = "48610084", tekniskTid = LocalDateTime.now())
                            "44522255" -> statistikkTilDVH.copy(behandlingId = "46828521", tekniskTid = LocalDateTime.now())
                            "44255230" -> statistikkTilDVH.copy(behandlingId = "49874793", tekniskTid = LocalDateTime.now())
                            "44637650" -> statistikkTilDVH.copy(behandlingId = "49771785", tekniskTid = LocalDateTime.now())
                            "44271139" -> statistikkTilDVH.copy(behandlingId = "49187425", tekniskTid = LocalDateTime.now())
                            "45798493" -> statistikkTilDVH.copy(behandlingId = "49200099", tekniskTid = LocalDateTime.now())
                            "45167965" -> statistikkTilDVH.copy(behandlingId = "50199927", tekniskTid = LocalDateTime.now())
                            "46366002" -> statistikkTilDVH.copy(behandlingId = "49767811", tekniskTid = LocalDateTime.now())
                            "45394660" -> statistikkTilDVH.copy(behandlingId = "47223547", tekniskTid = LocalDateTime.now())
                            "46497412" -> statistikkTilDVH.copy(behandlingId = "49255176", tekniskTid = LocalDateTime.now())
                            "45848926" -> statistikkTilDVH.copy(behandlingId = "49168898", tekniskTid = LocalDateTime.now())
                            "45824700" -> statistikkTilDVH.copy(behandlingId = "49431601", tekniskTid = LocalDateTime.now())
                            "45875001" -> statistikkTilDVH.copy(behandlingId = "49468345", tekniskTid = LocalDateTime.now())
                            "46402073" -> statistikkTilDVH.copy(behandlingId = "49434943", tekniskTid = LocalDateTime.now())
                            "46592845" -> statistikkTilDVH.copy(behandlingId = "48655875", tekniskTid = LocalDateTime.now())
                            "46651066" -> statistikkTilDVH.copy(behandlingId = "48619300", tekniskTid = LocalDateTime.now())
                            "46713789" -> statistikkTilDVH.copy(behandlingId = "49083777", tekniskTid = LocalDateTime.now())
                            "45834805" -> statistikkTilDVH.copy(behandlingId = "49228227", tekniskTid = LocalDateTime.now())
                            "46813399" -> statistikkTilDVH.copy(behandlingId = "49091326", tekniskTid = LocalDateTime.now())
                            "46441615" -> statistikkTilDVH.copy(behandlingId = "48803354", tekniskTid = LocalDateTime.now())
                            "46588992" -> statistikkTilDVH.copy(behandlingId = "48151267", tekniskTid = LocalDateTime.now())
                            "46573211" -> statistikkTilDVH.copy(behandlingId = "49085748", tekniskTid = LocalDateTime.now())
                            "46680130" -> statistikkTilDVH.copy(behandlingId = "48400742", tekniskTid = LocalDateTime.now())
                            "46859003" -> statistikkTilDVH.copy(behandlingId = "48420786", tekniskTid = LocalDateTime.now())
                            "46921831" -> statistikkTilDVH.copy(behandlingId = "48611880", tekniskTid = LocalDateTime.now())
                            "46947793" -> statistikkTilDVH.copy(behandlingId = "49102370", tekniskTid = LocalDateTime.now())
                            "46480893" -> statistikkTilDVH.copy(behandlingId = "48375292", tekniskTid = LocalDateTime.now())
                            "46588127" -> statistikkTilDVH.copy(behandlingId = "48464951", tekniskTid = LocalDateTime.now())
                            "46503296" -> statistikkTilDVH.copy(behandlingId = "49776019", tekniskTid = LocalDateTime.now())
                            "46798316" -> statistikkTilDVH.copy(behandlingId = "49776114", tekniskTid = LocalDateTime.now())
                            "46844796" -> statistikkTilDVH.copy(behandlingId = "48455495", tekniskTid = LocalDateTime.now())
                            "46959012" -> statistikkTilDVH.copy(behandlingId = "49458067", tekniskTid = LocalDateTime.now())
                            "47172278" -> statistikkTilDVH.copy(behandlingId = "49216347", tekniskTid = LocalDateTime.now())
                            "46969180" -> statistikkTilDVH.copy(behandlingId = "48826343", tekniskTid = LocalDateTime.now())
                            "47170408" -> statistikkTilDVH.copy(behandlingId = "48455528", tekniskTid = LocalDateTime.now())
                            "47032686" -> statistikkTilDVH.copy(behandlingId = "49080112", tekniskTid = LocalDateTime.now())
                            "46802811" -> statistikkTilDVH.copy(behandlingId = "48472643", tekniskTid = LocalDateTime.now())
                            "47253615" -> statistikkTilDVH.copy(behandlingId = "48654031", tekniskTid = LocalDateTime.now())
                            "47044248" -> statistikkTilDVH.copy(behandlingId = "49063740", tekniskTid = LocalDateTime.now())
                            "46826996" -> statistikkTilDVH.copy(behandlingId = "48816471", tekniskTid = LocalDateTime.now())
                            "46978514" -> statistikkTilDVH.copy(behandlingId = "48622550", tekniskTid = LocalDateTime.now())
                            "46881930" -> statistikkTilDVH.copy(behandlingId = "49129144", tekniskTid = LocalDateTime.now())
                            "47261546" -> statistikkTilDVH.copy(behandlingId = "48523797", tekniskTid = LocalDateTime.now())
                            "47223588" -> statistikkTilDVH.copy(behandlingId = "49146160", tekniskTid = LocalDateTime.now())
                            "46955724" -> statistikkTilDVH.copy(behandlingId = "48415100", tekniskTid = LocalDateTime.now())
                            "46955774" -> statistikkTilDVH.copy(behandlingId = "49081568", tekniskTid = LocalDateTime.now())
                            "46911452" -> statistikkTilDVH.copy(behandlingId = "49091596", tekniskTid = LocalDateTime.now())
                            "46934794" -> statistikkTilDVH.copy(behandlingId = "49080296", tekniskTid = LocalDateTime.now())
                            "46933018" -> statistikkTilDVH.copy(behandlingId = "48830916", tekniskTid = LocalDateTime.now())
                            "46946067" -> statistikkTilDVH.copy(behandlingId = "48523943", tekniskTid = LocalDateTime.now())
                            "47598496" -> statistikkTilDVH.copy(behandlingId = "49293404", tekniskTid = LocalDateTime.now())
                            "46969120" -> statistikkTilDVH.copy(behandlingId = "49107757", tekniskTid = LocalDateTime.now())
                            "46898051" -> statistikkTilDVH.copy(behandlingId = "48633702", tekniskTid = LocalDateTime.now())
                            "47065466" -> statistikkTilDVH.copy(behandlingId = "48801543", tekniskTid = LocalDateTime.now())
                            "46993661" -> statistikkTilDVH.copy(behandlingId = "49080724", tekniskTid = LocalDateTime.now())
                            "47033518" -> statistikkTilDVH.copy(behandlingId = "48816948", tekniskTid = LocalDateTime.now())
                            "47581382" -> statistikkTilDVH.copy(behandlingId = "48638243", tekniskTid = LocalDateTime.now())
                            "47665244" -> statistikkTilDVH.copy(behandlingId = "49088755", tekniskTid = LocalDateTime.now())
                            "47604010" -> statistikkTilDVH.copy(behandlingId = "49091295", tekniskTid = LocalDateTime.now())
                            "47169428" -> statistikkTilDVH.copy(behandlingId = "48470953", tekniskTid = LocalDateTime.now())
                            "47028020" -> statistikkTilDVH.copy(behandlingId = "49077302", tekniskTid = LocalDateTime.now())
                            "47033545" -> statistikkTilDVH.copy(behandlingId = "49093984", tekniskTid = LocalDateTime.now())
                            "47088194" -> statistikkTilDVH.copy(behandlingId = "48802870", tekniskTid = LocalDateTime.now())
                            "47154976" -> statistikkTilDVH.copy(behandlingId = "49143234", tekniskTid = LocalDateTime.now())
                            "47288150" -> statistikkTilDVH.copy(behandlingId = "48639693", tekniskTid = LocalDateTime.now())
                            "47566066" -> statistikkTilDVH.copy(behandlingId = "49059476", tekniskTid = LocalDateTime.now())
                            "47559103" -> statistikkTilDVH.copy(behandlingId = "49226031", tekniskTid = LocalDateTime.now())
                            "47038003" -> statistikkTilDVH.copy(behandlingId = "49089386", tekniskTid = LocalDateTime.now())
                            "47788898" -> statistikkTilDVH.copy(behandlingId = "49485400", tekniskTid = LocalDateTime.now())
                            "47577704" -> statistikkTilDVH.copy(behandlingId = "49083253", tekniskTid = LocalDateTime.now())
                            "47822385" -> statistikkTilDVH.copy(behandlingId = "48646546", tekniskTid = LocalDateTime.now())
                            "47101739" -> statistikkTilDVH.copy(behandlingId = "49053473", tekniskTid = LocalDateTime.now())
                            "47776066" -> statistikkTilDVH.copy(behandlingId = "49056207", tekniskTid = LocalDateTime.now())
                            "47038194" -> statistikkTilDVH.copy(behandlingId = "49091307", tekniskTid = LocalDateTime.now())
                            "47206553" -> statistikkTilDVH.copy(behandlingId = "49106544", tekniskTid = LocalDateTime.now())
                            "47059594" -> statistikkTilDVH.copy(behandlingId = "49150180", tekniskTid = LocalDateTime.now())
                            "47160921" -> statistikkTilDVH.copy(behandlingId = "49094682", tekniskTid = LocalDateTime.now())
                            "46782229" -> statistikkTilDVH.copy(behandlingId = "49180006", tekniskTid = LocalDateTime.now())
                            "47597454" -> statistikkTilDVH.copy(behandlingId = "49088714", tekniskTid = LocalDateTime.now())
                            "47999948" -> statistikkTilDVH.copy(behandlingId = "48816528", tekniskTid = LocalDateTime.now())
                            "48151659" -> statistikkTilDVH.copy(behandlingId = "49464194", tekniskTid = LocalDateTime.now())
                            "47611102" -> statistikkTilDVH.copy(behandlingId = "49085783", tekniskTid = LocalDateTime.now())
                            "47215295" -> statistikkTilDVH.copy(behandlingId = "49095423", tekniskTid = LocalDateTime.now())
                            "47258707" -> statistikkTilDVH.copy(behandlingId = "49293411", tekniskTid = LocalDateTime.now())
                            "47680322" -> statistikkTilDVH.copy(behandlingId = "49269397", tekniskTid = LocalDateTime.now())
                            "47667948" -> statistikkTilDVH.copy(behandlingId = "49420424", tekniskTid = LocalDateTime.now())
                            "47223964" -> statistikkTilDVH.copy(behandlingId = "49080798", tekniskTid = LocalDateTime.now())
                            "48195880" -> statistikkTilDVH.copy(behandlingId = "49053985", tekniskTid = LocalDateTime.now())
                            "47979890" -> statistikkTilDVH.copy(behandlingId = "49268316", tekniskTid = LocalDateTime.now())
                            "48201042" -> statistikkTilDVH.copy(behandlingId = "49263561", tekniskTid = LocalDateTime.now())
                            "47244633" -> statistikkTilDVH.copy(behandlingId = "48536189", tekniskTid = LocalDateTime.now())
                            "47599606" -> statistikkTilDVH.copy(behandlingId = "49154898", tekniskTid = LocalDateTime.now())
                            "47285015" -> statistikkTilDVH.copy(behandlingId = "49148894", tekniskTid = LocalDateTime.now())
                            "47244640" -> statistikkTilDVH.copy(behandlingId = "49223326", tekniskTid = LocalDateTime.now())
                            "48200323" -> statistikkTilDVH.copy(behandlingId = "49178893", tekniskTid = LocalDateTime.now())
                            "48197479" -> statistikkTilDVH.copy(behandlingId = "49198351", tekniskTid = LocalDateTime.now())
                            "48224868" -> statistikkTilDVH.copy(behandlingId = "49192264", tekniskTid = LocalDateTime.now())
                            "47088580" -> statistikkTilDVH.copy(behandlingId = "49233945", tekniskTid = LocalDateTime.now())
                            "48009198" -> statistikkTilDVH.copy(behandlingId = "49228477", tekniskTid = LocalDateTime.now())
                            "47990610" -> statistikkTilDVH.copy(behandlingId = "49260111", tekniskTid = LocalDateTime.now())
                            "47222601" -> statistikkTilDVH.copy(behandlingId = "49860029", tekniskTid = LocalDateTime.now())
                            "48189892" -> statistikkTilDVH.copy(behandlingId = "49178883", tekniskTid = LocalDateTime.now())
                            "48227945" -> statistikkTilDVH.copy(behandlingId = "49270661", tekniskTid = LocalDateTime.now())
                            "48231645" -> statistikkTilDVH.copy(behandlingId = "49417795", tekniskTid = LocalDateTime.now())
                            "47664779" -> statistikkTilDVH.copy(behandlingId = "49431693", tekniskTid = LocalDateTime.now())
                            "47676351" -> statistikkTilDVH.copy(behandlingId = "49072483", tekniskTid = LocalDateTime.now())
                            "48243014" -> statistikkTilDVH.copy(behandlingId = "49229472", tekniskTid = LocalDateTime.now())
                            "47226330" -> statistikkTilDVH.copy(behandlingId = "49106162", tekniskTid = LocalDateTime.now())
                            "47821958" -> statistikkTilDVH.copy(behandlingId = "48814503", tekniskTid = LocalDateTime.now())
                            "47765396" -> statistikkTilDVH.copy(behandlingId = "49102374", tekniskTid = LocalDateTime.now())
                            "46947353" -> statistikkTilDVH.copy(behandlingId = "49100277", tekniskTid = LocalDateTime.now())
                            "48001672" -> statistikkTilDVH.copy(behandlingId = "49073437", tekniskTid = LocalDateTime.now())
                            "47789296" -> statistikkTilDVH.copy(behandlingId = "49088042", tekniskTid = LocalDateTime.now())
                            "47558494" -> statistikkTilDVH.copy(behandlingId = "49111100", tekniskTid = LocalDateTime.now())
                            "47553396" -> statistikkTilDVH.copy(behandlingId = "49287353", tekniskTid = LocalDateTime.now())
                            "47569845" -> statistikkTilDVH.copy(behandlingId = "49173996", tekniskTid = LocalDateTime.now())
                            "47577543" -> statistikkTilDVH.copy(behandlingId = "49094100", tekniskTid = LocalDateTime.now())
                            "47674539" -> statistikkTilDVH.copy(behandlingId = "49108074", tekniskTid = LocalDateTime.now())
                            "48002958" -> statistikkTilDVH.copy(behandlingId = "49474499", tekniskTid = LocalDateTime.now())
                            "47299550" -> statistikkTilDVH.copy(behandlingId = "49119136", tekniskTid = LocalDateTime.now())
                            "47600867" -> statistikkTilDVH.copy(behandlingId = "49118069", tekniskTid = LocalDateTime.now())
                            "47572544" -> statistikkTilDVH.copy(behandlingId = "49269254", tekniskTid = LocalDateTime.now())
                            "48198649" -> statistikkTilDVH.copy(behandlingId = "49138179", tekniskTid = LocalDateTime.now())
                            "48218360" -> statistikkTilDVH.copy(behandlingId = "49080635", tekniskTid = LocalDateTime.now())
                            "47795883" -> statistikkTilDVH.copy(behandlingId = "49083223", tekniskTid = LocalDateTime.now())
                            "47995294" -> statistikkTilDVH.copy(behandlingId = "49187512", tekniskTid = LocalDateTime.now())
                            "47989717" -> statistikkTilDVH.copy(behandlingId = "49107231", tekniskTid = LocalDateTime.now())
                            "47663444" -> statistikkTilDVH.copy(behandlingId = "49094755", tekniskTid = LocalDateTime.now())
                            "48011548" -> statistikkTilDVH.copy(behandlingId = "49407360", tekniskTid = LocalDateTime.now())
                            "48269404" -> statistikkTilDVH.copy(behandlingId = "49181586", tekniskTid = LocalDateTime.now())
                            "48008646" -> statistikkTilDVH.copy(behandlingId = "49099905", tekniskTid = LocalDateTime.now())
                            "47998980" -> statistikkTilDVH.copy(behandlingId = "49134607", tekniskTid = LocalDateTime.now())
                            "47225754" -> statistikkTilDVH.copy(behandlingId = "49179895", tekniskTid = LocalDateTime.now())
                            "47188802" -> statistikkTilDVH.copy(behandlingId = "49430574", tekniskTid = LocalDateTime.now())
                            "47182101" -> statistikkTilDVH.copy(behandlingId = "49129876", tekniskTid = LocalDateTime.now())
                            "47294271" -> statistikkTilDVH.copy(behandlingId = "49309113", tekniskTid = LocalDateTime.now())
                            "47284493" -> statistikkTilDVH.copy(behandlingId = "49099579", tekniskTid = LocalDateTime.now())
                            "48197747" -> statistikkTilDVH.copy(behandlingId = "49129078", tekniskTid = LocalDateTime.now())
                            "47583249" -> statistikkTilDVH.copy(behandlingId = "49187082", tekniskTid = LocalDateTime.now())
                            "48290512" -> statistikkTilDVH.copy(behandlingId = "49201343", tekniskTid = LocalDateTime.now())
                            "47988887" -> statistikkTilDVH.copy(behandlingId = "49129110", tekniskTid = LocalDateTime.now())
                            "48301502" -> statistikkTilDVH.copy(behandlingId = "49136391", tekniskTid = LocalDateTime.now())
                            "47575037" -> statistikkTilDVH.copy(behandlingId = "49228590", tekniskTid = LocalDateTime.now())
                            "47288254" -> statistikkTilDVH.copy(behandlingId = "49171246", tekniskTid = LocalDateTime.now())
                            "48232580" -> statistikkTilDVH.copy(behandlingId = "49199494", tekniskTid = LocalDateTime.now())
                            "48284829" -> statistikkTilDVH.copy(behandlingId = "49099886", tekniskTid = LocalDateTime.now())
                            "48203853" -> statistikkTilDVH.copy(behandlingId = "49229421", tekniskTid = LocalDateTime.now())
                            "48302260" -> statistikkTilDVH.copy(behandlingId = "49138019", tekniskTid = LocalDateTime.now())
                            "48195429" -> statistikkTilDVH.copy(behandlingId = "49316022", tekniskTid = LocalDateTime.now())
                            "48307547" -> statistikkTilDVH.copy(behandlingId = "49064665", tekniskTid = LocalDateTime.now())
                            "48299407" -> statistikkTilDVH.copy(behandlingId = "49167726", tekniskTid = LocalDateTime.now())
                            "48307248" -> statistikkTilDVH.copy(behandlingId = "48521043", tekniskTid = LocalDateTime.now())
                            "47786849" -> statistikkTilDVH.copy(behandlingId = "49299396", tekniskTid = LocalDateTime.now())
                            "47995352" -> statistikkTilDVH.copy(behandlingId = "49225390", tekniskTid = LocalDateTime.now())
                            "48273567" -> statistikkTilDVH.copy(behandlingId = "49231424", tekniskTid = LocalDateTime.now())
                            "47674993" -> statistikkTilDVH.copy(behandlingId = "49276158", tekniskTid = LocalDateTime.now())
                            "47998820" -> statistikkTilDVH.copy(behandlingId = "49269363", tekniskTid = LocalDateTime.now())
                            "47999952" -> statistikkTilDVH.copy(behandlingId = "49189138", tekniskTid = LocalDateTime.now())
                            "48322302" -> statistikkTilDVH.copy(behandlingId = "49121486", tekniskTid = LocalDateTime.now())
                            "48235019" -> statistikkTilDVH.copy(behandlingId = "49316194", tekniskTid = LocalDateTime.now())
                            "47822994" -> statistikkTilDVH.copy(behandlingId = "49382326", tekniskTid = LocalDateTime.now())
                            "47822415" -> statistikkTilDVH.copy(behandlingId = "49282767", tekniskTid = LocalDateTime.now())
                            "48318526" -> statistikkTilDVH.copy(behandlingId = "49469652", tekniskTid = LocalDateTime.now())
                            "48328891" -> statistikkTilDVH.copy(behandlingId = "49434825", tekniskTid = LocalDateTime.now())
                            "48302263" -> statistikkTilDVH.copy(behandlingId = "49255900", tekniskTid = LocalDateTime.now())
                            "48275421" -> statistikkTilDVH.copy(behandlingId = "49200127", tekniskTid = LocalDateTime.now())
                            "48218005" -> statistikkTilDVH.copy(behandlingId = "49270059", tekniskTid = LocalDateTime.now())
                            "47595446" -> statistikkTilDVH.copy(behandlingId = "49416313", tekniskTid = LocalDateTime.now())
                            "48243196" -> statistikkTilDVH.copy(behandlingId = "49297727", tekniskTid = LocalDateTime.now())
                            "47188666" -> statistikkTilDVH.copy(behandlingId = "49298745", tekniskTid = LocalDateTime.now())
                            "48322498" -> statistikkTilDVH.copy(behandlingId = "49358716", tekniskTid = LocalDateTime.now())
                            "47073946" -> statistikkTilDVH.copy(behandlingId = "49089797", tekniskTid = LocalDateTime.now())
                            "48257642" -> statistikkTilDVH.copy(behandlingId = "49165272", tekniskTid = LocalDateTime.now())
                            "47091862" -> statistikkTilDVH.copy(behandlingId = "49276155", tekniskTid = LocalDateTime.now())
                            "48326918" -> statistikkTilDVH.copy(behandlingId = "49231517", tekniskTid = LocalDateTime.now())
                            "48351026" -> statistikkTilDVH.copy(behandlingId = "49181559", tekniskTid = LocalDateTime.now())
                            "48351499" -> statistikkTilDVH.copy(behandlingId = "49394890", tekniskTid = LocalDateTime.now())
                            "48351914" -> statistikkTilDVH.copy(behandlingId = "49298845", tekniskTid = LocalDateTime.now())
                            "48294252" -> statistikkTilDVH.copy(behandlingId = "49274908", tekniskTid = LocalDateTime.now())
                            "45628745" -> statistikkTilDVH.copy(behandlingId = "48421757", tekniskTid = LocalDateTime.now())
                            "48352860" -> statistikkTilDVH.copy(behandlingId = "49420456", tekniskTid = LocalDateTime.now())
                            "47991509" -> statistikkTilDVH.copy(behandlingId = "49465711", tekniskTid = LocalDateTime.now())
                            "48354848" -> statistikkTilDVH.copy(behandlingId = "49397175", tekniskTid = LocalDateTime.now())
                            "48282743" -> statistikkTilDVH.copy(behandlingId = "49261511", tekniskTid = LocalDateTime.now())
                            "48319916" -> statistikkTilDVH.copy(behandlingId = "49324047", tekniskTid = LocalDateTime.now())
                            "48327131" -> statistikkTilDVH.copy(behandlingId = "49263724", tekniskTid = LocalDateTime.now())
                            "48280352" -> statistikkTilDVH.copy(behandlingId = "49228700", tekniskTid = LocalDateTime.now())
                            "48275025" -> statistikkTilDVH.copy(behandlingId = "49250853", tekniskTid = LocalDateTime.now())
                            "48308840" -> statistikkTilDVH.copy(behandlingId = "49220201", tekniskTid = LocalDateTime.now())
                            "48267019" -> statistikkTilDVH.copy(behandlingId = "49358705", tekniskTid = LocalDateTime.now())
                            "48235050" -> statistikkTilDVH.copy(behandlingId = "49407117", tekniskTid = LocalDateTime.now())
                            "48352233" -> statistikkTilDVH.copy(behandlingId = "49229531", tekniskTid = LocalDateTime.now())
                            "48193903" -> statistikkTilDVH.copy(behandlingId = "49275854", tekniskTid = LocalDateTime.now())
                            "48193914" -> statistikkTilDVH.copy(behandlingId = "49304513", tekniskTid = LocalDateTime.now())
                            "48240536" -> statistikkTilDVH.copy(behandlingId = "48527344", tekniskTid = LocalDateTime.now())
                            "48240205" -> statistikkTilDVH.copy(behandlingId = "49279675", tekniskTid = LocalDateTime.now())
                            "48312845" -> statistikkTilDVH.copy(behandlingId = "49372093", tekniskTid = LocalDateTime.now())
                            "48322794" -> statistikkTilDVH.copy(behandlingId = "49370974", tekniskTid = LocalDateTime.now())
                            "48287610" -> statistikkTilDVH.copy(behandlingId = "49362076", tekniskTid = LocalDateTime.now())
                            "48022722" -> statistikkTilDVH.copy(behandlingId = "49391731", tekniskTid = LocalDateTime.now())
                            "48324511" -> statistikkTilDVH.copy(behandlingId = "49276603", tekniskTid = LocalDateTime.now())
                            "48263352" -> statistikkTilDVH.copy(behandlingId = "49232080", tekniskTid = LocalDateTime.now())
                            "48346245" -> statistikkTilDVH.copy(behandlingId = "49362086", tekniskTid = LocalDateTime.now())
                            "48355989" -> statistikkTilDVH.copy(behandlingId = "49400196", tekniskTid = LocalDateTime.now())
                            "48309981" -> statistikkTilDVH.copy(behandlingId = "49395008", tekniskTid = LocalDateTime.now())
                            "48287623" -> statistikkTilDVH.copy(behandlingId = "49282721", tekniskTid = LocalDateTime.now())
                            "48352299" -> statistikkTilDVH.copy(behandlingId = "49426530", tekniskTid = LocalDateTime.now())
                            "48276747" -> statistikkTilDVH.copy(behandlingId = "49390299", tekniskTid = LocalDateTime.now())
                            "48286552" -> statistikkTilDVH.copy(behandlingId = "49259813", tekniskTid = LocalDateTime.now())
                            "48270976" -> statistikkTilDVH.copy(behandlingId = "49397204", tekniskTid = LocalDateTime.now())
                            "48379710" -> statistikkTilDVH.copy(behandlingId = "49136454", tekniskTid = LocalDateTime.now())
                            "48372343" -> statistikkTilDVH.copy(behandlingId = "49421193", tekniskTid = LocalDateTime.now())
                            "48374730" -> statistikkTilDVH.copy(behandlingId = "49231409", tekniskTid = LocalDateTime.now())
                            "48380307" -> statistikkTilDVH.copy(behandlingId = "49271396", tekniskTid = LocalDateTime.now())
                            "48173200" -> statistikkTilDVH.copy(behandlingId = "49359010", tekniskTid = LocalDateTime.now())
                            "48236403" -> statistikkTilDVH.copy(behandlingId = "49193300", tekniskTid = LocalDateTime.now())
                            "48380596" -> statistikkTilDVH.copy(behandlingId = "49073516", tekniskTid = LocalDateTime.now())
                            "48232041" -> statistikkTilDVH.copy(behandlingId = "49243096", tekniskTid = LocalDateTime.now())
                            "48197804" -> statistikkTilDVH.copy(behandlingId = "49276405", tekniskTid = LocalDateTime.now())
                            "48390297" -> statistikkTilDVH.copy(behandlingId = "49407805", tekniskTid = LocalDateTime.now())
                            "48386810" -> statistikkTilDVH.copy(behandlingId = "49435222", tekniskTid = LocalDateTime.now())
                            "48225146" -> statistikkTilDVH.copy(behandlingId = "49396764", tekniskTid = LocalDateTime.now())
                            "48394827" -> statistikkTilDVH.copy(behandlingId = "49774996", tekniskTid = LocalDateTime.now())
                            "48290901" -> statistikkTilDVH.copy(behandlingId = "49308244", tekniskTid = LocalDateTime.now())
                            "48222879" -> statistikkTilDVH.copy(behandlingId = "49860334", tekniskTid = LocalDateTime.now())
                            "48349513" -> statistikkTilDVH.copy(behandlingId = "49304405", tekniskTid = LocalDateTime.now())
                            "48355903" -> statistikkTilDVH.copy(behandlingId = "49417700", tekniskTid = LocalDateTime.now())
                            "48374806" -> statistikkTilDVH.copy(behandlingId = "49402409", tekniskTid = LocalDateTime.now())
                            "48332466" -> statistikkTilDVH.copy(behandlingId = "49874013", tekniskTid = LocalDateTime.now())
                            "48363647" -> statistikkTilDVH.copy(behandlingId = "49292251", tekniskTid = LocalDateTime.now())
                            "48355909" -> statistikkTilDVH.copy(behandlingId = "49373230", tekniskTid = LocalDateTime.now())
                            "48384259" -> statistikkTilDVH.copy(behandlingId = "49422858", tekniskTid = LocalDateTime.now())
                            "47152293" -> statistikkTilDVH.copy(behandlingId = "49263084", tekniskTid = LocalDateTime.now())
                            "48332567" -> statistikkTilDVH.copy(behandlingId = "48814531", tekniskTid = LocalDateTime.now())
                            "47578653" -> statistikkTilDVH.copy(behandlingId = "49435300", tekniskTid = LocalDateTime.now())
                            "48344894" -> statistikkTilDVH.copy(behandlingId = "49389694", tekniskTid = LocalDateTime.now())
                            "48400471" -> statistikkTilDVH.copy(behandlingId = "49420384", tekniskTid = LocalDateTime.now())
                            "48402369" -> statistikkTilDVH.copy(behandlingId = "49367907", tekniskTid = LocalDateTime.now())
                            "48376225" -> statistikkTilDVH.copy(behandlingId = "49281426", tekniskTid = LocalDateTime.now())
                            "48426788" -> statistikkTilDVH.copy(behandlingId = "49300534", tekniskTid = LocalDateTime.now())
                            "48373971" -> statistikkTilDVH.copy(behandlingId = "49425560", tekniskTid = LocalDateTime.now())
                            "48322464" -> statistikkTilDVH.copy(behandlingId = "49425893", tekniskTid = LocalDateTime.now())
                            "48336603" -> statistikkTilDVH.copy(behandlingId = "49394877", tekniskTid = LocalDateTime.now())
                            "48283001" -> statistikkTilDVH.copy(behandlingId = "49174842", tekniskTid = LocalDateTime.now())
                            "48339449" -> statistikkTilDVH.copy(behandlingId = "49290166", tekniskTid = LocalDateTime.now())
                            "48245910" -> statistikkTilDVH.copy(behandlingId = "49260666", tekniskTid = LocalDateTime.now())
                            "48365732" -> statistikkTilDVH.copy(behandlingId = "49432547", tekniskTid = LocalDateTime.now())
                            "47166313" -> statistikkTilDVH.copy(behandlingId = "49188104", tekniskTid = LocalDateTime.now())
                            "48445973" -> statistikkTilDVH.copy(behandlingId = "49099903", tekniskTid = LocalDateTime.now())
                            "47295698" -> statistikkTilDVH.copy(behandlingId = "49374513", tekniskTid = LocalDateTime.now())
                            "48291349" -> statistikkTilDVH.copy(behandlingId = "49414494", tekniskTid = LocalDateTime.now())
                            "48401494" -> statistikkTilDVH.copy(behandlingId = "49365920", tekniskTid = LocalDateTime.now())
                            "48354394" -> statistikkTilDVH.copy(behandlingId = "49853016", tekniskTid = LocalDateTime.now())
                            "48389322" -> statistikkTilDVH.copy(behandlingId = "49172399", tekniskTid = LocalDateTime.now())
                            "48005078" -> statistikkTilDVH.copy(behandlingId = "49295742", tekniskTid = LocalDateTime.now())
                            "48430373" -> statistikkTilDVH.copy(behandlingId = "49435694", tekniskTid = LocalDateTime.now())
                            "48373666" -> statistikkTilDVH.copy(behandlingId = "49425476", tekniskTid = LocalDateTime.now())
                            "48258618" -> statistikkTilDVH.copy(behandlingId = "49373176", tekniskTid = LocalDateTime.now())
                            "48353895" -> statistikkTilDVH.copy(behandlingId = "49903049", tekniskTid = LocalDateTime.now())
                            "48415644" -> statistikkTilDVH.copy(behandlingId = "49233201", tekniskTid = LocalDateTime.now())
                            "48445693" -> statistikkTilDVH.copy(behandlingId = "49475006", tekniskTid = LocalDateTime.now())
                            "48456703" -> statistikkTilDVH.copy(behandlingId = "49427954", tekniskTid = LocalDateTime.now())
                            "47162762" -> statistikkTilDVH.copy(behandlingId = "49276463", tekniskTid = LocalDateTime.now())
                            "48266912" -> statistikkTilDVH.copy(behandlingId = "49444447", tekniskTid = LocalDateTime.now())
                            "48322461" -> statistikkTilDVH.copy(behandlingId = "49246252", tekniskTid = LocalDateTime.now())
                            "48448895" -> statistikkTilDVH.copy(behandlingId = "49369058", tekniskTid = LocalDateTime.now())
                            "48456850" -> statistikkTilDVH.copy(behandlingId = "49276196", tekniskTid = LocalDateTime.now())
                            "48447245" -> statistikkTilDVH.copy(behandlingId = "49269261", tekniskTid = LocalDateTime.now())
                            "48462944" -> statistikkTilDVH.copy(behandlingId = "49301116", tekniskTid = LocalDateTime.now())
                            "48480373" -> statistikkTilDVH.copy(behandlingId = "49270347", tekniskTid = LocalDateTime.now())
                            "48450881" -> statistikkTilDVH.copy(behandlingId = "49290865", tekniskTid = LocalDateTime.now())
                            "47225016" -> statistikkTilDVH.copy(behandlingId = "49293859", tekniskTid = LocalDateTime.now())
                            "48339545" -> statistikkTilDVH.copy(behandlingId = "49313101", tekniskTid = LocalDateTime.now())
                            "48439043" -> statistikkTilDVH.copy(behandlingId = "49228320", tekniskTid = LocalDateTime.now())
                            "48320597" -> statistikkTilDVH.copy(behandlingId = "49266411", tekniskTid = LocalDateTime.now())
                            "48399343" -> statistikkTilDVH.copy(behandlingId = "49477006", tekniskTid = LocalDateTime.now())
                            "48500152" -> statistikkTilDVH.copy(behandlingId = "49458524", tekniskTid = LocalDateTime.now())
                            "48346549" -> statistikkTilDVH.copy(behandlingId = "49416946", tekniskTid = LocalDateTime.now())
                            "47200571" -> statistikkTilDVH.copy(behandlingId = "49780653", tekniskTid = LocalDateTime.now())
                            "48339902" -> statistikkTilDVH.copy(behandlingId = "49471248", tekniskTid = LocalDateTime.now())
                            "48511423" -> statistikkTilDVH.copy(behandlingId = "49215393", tekniskTid = LocalDateTime.now())
                            "48454983" -> statistikkTilDVH.copy(behandlingId = "49248600", tekniskTid = LocalDateTime.now())
                            "48269905" -> statistikkTilDVH.copy(behandlingId = "49295850", tekniskTid = LocalDateTime.now())
                            "48534382" -> statistikkTilDVH.copy(behandlingId = "49241104", tekniskTid = LocalDateTime.now())
                            "48411726" -> statistikkTilDVH.copy(behandlingId = "49280896", tekniskTid = LocalDateTime.now())
                            "48389497" -> statistikkTilDVH.copy(behandlingId = "49372017", tekniskTid = LocalDateTime.now())
                            "48526850" -> statistikkTilDVH.copy(behandlingId = "49271210", tekniskTid = LocalDateTime.now())
                            "48418370" -> statistikkTilDVH.copy(behandlingId = "49266801", tekniskTid = LocalDateTime.now())
                            "48529210" -> statistikkTilDVH.copy(behandlingId = "49255944", tekniskTid = LocalDateTime.now())
                            "48288794" -> statistikkTilDVH.copy(behandlingId = "49424668", tekniskTid = LocalDateTime.now())
                            "47288758" -> statistikkTilDVH.copy(behandlingId = "49485867", tekniskTid = LocalDateTime.now())
                            "48429188" -> statistikkTilDVH.copy(behandlingId = "49215367", tekniskTid = LocalDateTime.now())
                            "48402232" -> statistikkTilDVH.copy(behandlingId = "49465604", tekniskTid = LocalDateTime.now())
                            "48608212" -> statistikkTilDVH.copy(behandlingId = "49884624", tekniskTid = LocalDateTime.now())
                            "48609405" -> statistikkTilDVH.copy(behandlingId = "49476918", tekniskTid = LocalDateTime.now())
                            "47999019" -> statistikkTilDVH.copy(behandlingId = "49365843", tekniskTid = LocalDateTime.now())
                            "48408197" -> statistikkTilDVH.copy(behandlingId = "49390240", tekniskTid = LocalDateTime.now())
                            "48496181" -> statistikkTilDVH.copy(behandlingId = "49363169", tekniskTid = LocalDateTime.now())
                            "48329101" -> statistikkTilDVH.copy(behandlingId = "49906209", tekniskTid = LocalDateTime.now())
                            "47216398" -> statistikkTilDVH.copy(behandlingId = "49471306", tekniskTid = LocalDateTime.now())
                            "48367647" -> statistikkTilDVH.copy(behandlingId = "50200737", tekniskTid = LocalDateTime.now())
                            "48443997" -> statistikkTilDVH.copy(behandlingId = "49280748", tekniskTid = LocalDateTime.now())
                            "48632200" -> statistikkTilDVH.copy(behandlingId = "49459881", tekniskTid = LocalDateTime.now())
                            "48632203" -> statistikkTilDVH.copy(behandlingId = "49313488", tekniskTid = LocalDateTime.now())
                            "48402252" -> statistikkTilDVH.copy(behandlingId = "49473997", tekniskTid = LocalDateTime.now())
                            "48642979" -> statistikkTilDVH.copy(behandlingId = "49365435", tekniskTid = LocalDateTime.now())
                            "48817961" -> statistikkTilDVH.copy(behandlingId = "49471244", tekniskTid = LocalDateTime.now())
                            "48534547" -> statistikkTilDVH.copy(behandlingId = "49304552", tekniskTid = LocalDateTime.now())
                            "48178746" -> statistikkTilDVH.copy(behandlingId = "49174896", tekniskTid = LocalDateTime.now())
                            "48630147" -> statistikkTilDVH.copy(behandlingId = "49368647", tekniskTid = LocalDateTime.now())
                            "48245532" -> statistikkTilDVH.copy(behandlingId = "49420108", tekniskTid = LocalDateTime.now())
                            "48830744" -> statistikkTilDVH.copy(behandlingId = "49095006", tekniskTid = LocalDateTime.now())
                            "48421756" -> statistikkTilDVH.copy(behandlingId = "49772208", tekniskTid = LocalDateTime.now())
                            "48478925" -> statistikkTilDVH.copy(behandlingId = "49853044", tekniskTid = LocalDateTime.now())
                            "49066444" -> statistikkTilDVH.copy(behandlingId = "49290728", tekniskTid = LocalDateTime.now())
                            "49074475" -> statistikkTilDVH.copy(behandlingId = "49260426", tekniskTid = LocalDateTime.now())
                            "48287509" -> statistikkTilDVH.copy(behandlingId = "49864813", tekniskTid = LocalDateTime.now())
                            "48455105" -> statistikkTilDVH.copy(behandlingId = "49767751", tekniskTid = LocalDateTime.now())
                            "48462749" -> statistikkTilDVH.copy(behandlingId = "49414999", tekniskTid = LocalDateTime.now())
                            "48818295" -> statistikkTilDVH.copy(behandlingId = "49433104", tekniskTid = LocalDateTime.now())
                            "49090340" -> statistikkTilDVH.copy(behandlingId = "49861032", tekniskTid = LocalDateTime.now())
                            "49074672" -> statistikkTilDVH.copy(behandlingId = "49780534", tekniskTid = LocalDateTime.now())
                            "49089900" -> statistikkTilDVH.copy(behandlingId = "49304737", tekniskTid = LocalDateTime.now())
                            "49095103" -> statistikkTilDVH.copy(behandlingId = "49467254", tekniskTid = LocalDateTime.now())
                            "48818642" -> statistikkTilDVH.copy(behandlingId = "49467280", tekniskTid = LocalDateTime.now())
                            "48823654" -> statistikkTilDVH.copy(behandlingId = "49444845", tekniskTid = LocalDateTime.now())
                            "49072175" -> statistikkTilDVH.copy(behandlingId = "49422510", tekniskTid = LocalDateTime.now())
                            "47989049" -> statistikkTilDVH.copy(behandlingId = "49358648", tekniskTid = LocalDateTime.now())
                            "48469843" -> statistikkTilDVH.copy(behandlingId = "49181445", tekniskTid = LocalDateTime.now())
                            "47195863" -> statistikkTilDVH.copy(behandlingId = "49484111", tekniskTid = LocalDateTime.now())
                            "49092388" -> statistikkTilDVH.copy(behandlingId = "49280393", tekniskTid = LocalDateTime.now())
                            "49063316" -> statistikkTilDVH.copy(behandlingId = "49899550", tekniskTid = LocalDateTime.now())
                            "49128827" -> statistikkTilDVH.copy(behandlingId = "49389240", tekniskTid = LocalDateTime.now())
                            "49077329" -> statistikkTilDVH.copy(behandlingId = "49434855", tekniskTid = LocalDateTime.now())
                            "48489108" -> statistikkTilDVH.copy(behandlingId = "49413817", tekniskTid = LocalDateTime.now())
                            "49111705" -> statistikkTilDVH.copy(behandlingId = "49420022", tekniskTid = LocalDateTime.now())
                            "48390434" -> statistikkTilDVH.copy(behandlingId = "49873682", tekniskTid = LocalDateTime.now())
                            "48431978" -> statistikkTilDVH.copy(behandlingId = "49861012", tekniskTid = LocalDateTime.now())
                            "48638949" -> statistikkTilDVH.copy(behandlingId = "49901803", tekniskTid = LocalDateTime.now())
                            "49074792" -> statistikkTilDVH.copy(behandlingId = "49371140", tekniskTid = LocalDateTime.now())
                            "48494946" -> statistikkTilDVH.copy(behandlingId = "49231239", tekniskTid = LocalDateTime.now())
                            "48823408" -> statistikkTilDVH.copy(behandlingId = "49851804", tekniskTid = LocalDateTime.now())
                            "48453744" -> statistikkTilDVH.copy(behandlingId = "49476251", tekniskTid = LocalDateTime.now())
                            "49164683" -> statistikkTilDVH.copy(behandlingId = "49276575", tekniskTid = LocalDateTime.now())
                            "48425693" -> statistikkTilDVH.copy(behandlingId = "49875869", tekniskTid = LocalDateTime.now())
                            "47204549" -> statistikkTilDVH.copy(behandlingId = "49873664", tekniskTid = LocalDateTime.now())
                            "48816129" -> statistikkTilDVH.copy(behandlingId = "49771665", tekniskTid = LocalDateTime.now())
                            "48455436" -> statistikkTilDVH.copy(behandlingId = "49474102", tekniskTid = LocalDateTime.now())
                            "49067343" -> statistikkTilDVH.copy(behandlingId = "49904875", tekniskTid = LocalDateTime.now())
                            "49180144" -> statistikkTilDVH.copy(behandlingId = "49429805", tekniskTid = LocalDateTime.now())
                            "49165749" -> statistikkTilDVH.copy(behandlingId = "49422775", tekniskTid = LocalDateTime.now())
                            "48632364" -> statistikkTilDVH.copy(behandlingId = "49906048", tekniskTid = LocalDateTime.now())
                            "49079933" -> statistikkTilDVH.copy(behandlingId = "49400551", tekniskTid = LocalDateTime.now())
                            "49100006" -> statistikkTilDVH.copy(behandlingId = "49318123", tekniskTid = LocalDateTime.now())
                            "49074687" -> statistikkTilDVH.copy(behandlingId = "49780699", tekniskTid = LocalDateTime.now())
                            "49170360" -> statistikkTilDVH.copy(behandlingId = "49908351", tekniskTid = LocalDateTime.now())
                            "49089936" -> statistikkTilDVH.copy(behandlingId = "49434399", tekniskTid = LocalDateTime.now())
                            "49106286" -> statistikkTilDVH.copy(behandlingId = "49434808", tekniskTid = LocalDateTime.now())
                            "49080458" -> statistikkTilDVH.copy(behandlingId = "49906019", tekniskTid = LocalDateTime.now())
                            "48828283" -> statistikkTilDVH.copy(behandlingId = "49767613", tekniskTid = LocalDateTime.now())
                            "48815994" -> statistikkTilDVH.copy(behandlingId = "49893939", tekniskTid = LocalDateTime.now())
                            "48824777" -> statistikkTilDVH.copy(behandlingId = "49903520", tekniskTid = LocalDateTime.now())
                            "49076925" -> statistikkTilDVH.copy(behandlingId = "49780761", tekniskTid = LocalDateTime.now())
                            "49215677" -> statistikkTilDVH.copy(behandlingId = "49776013", tekniskTid = LocalDateTime.now())
                            "49230994" -> statistikkTilDVH.copy(behandlingId = "50192713", tekniskTid = LocalDateTime.now())
                            "48400886" -> statistikkTilDVH.copy(behandlingId = "49434797", tekniskTid = LocalDateTime.now())
                            "49256808" -> statistikkTilDVH.copy(behandlingId = "49851697", tekniskTid = LocalDateTime.now())
                            "48815757" -> statistikkTilDVH.copy(behandlingId = "49780600", tekniskTid = LocalDateTime.now())
                            "49090915" -> statistikkTilDVH.copy(behandlingId = "49767685", tekniskTid = LocalDateTime.now())
                            "49293206" -> statistikkTilDVH.copy(behandlingId = "49417944", tekniskTid = LocalDateTime.now())
                            "49110399" -> statistikkTilDVH.copy(behandlingId = "49906200", tekniskTid = LocalDateTime.now())
                            "24030860" -> statistikkTilDVH.copy(behandlingId = "48495213", tekniskTid = LocalDateTime.now())
                            "24585260" -> statistikkTilDVH.copy(behandlingId = "48622478", tekniskTid = LocalDateTime.now())
                            "26229406" -> statistikkTilDVH.copy(behandlingId = "49475099", tekniskTid = LocalDateTime.now())
                            "28857872" -> statistikkTilDVH.copy(behandlingId = "44306254", tekniskTid = LocalDateTime.now())
                            "29858107" -> statistikkTilDVH.copy(behandlingId = "36046250", tekniskTid = LocalDateTime.now())
                            "29885679" -> statistikkTilDVH.copy(behandlingId = "47583286", tekniskTid = LocalDateTime.now())
                            "30577846" -> statistikkTilDVH.copy(behandlingId = "48495072", tekniskTid = LocalDateTime.now())
                            "31516896" -> statistikkTilDVH.copy(behandlingId = "46916920", tekniskTid = LocalDateTime.now())
                            "32598918" -> statistikkTilDVH.copy(behandlingId = "34449374", tekniskTid = LocalDateTime.now())
                            "30499612" -> statistikkTilDVH.copy(behandlingId = "36727088", tekniskTid = LocalDateTime.now())
                            "31502396" -> statistikkTilDVH.copy(behandlingId = "36239442", tekniskTid = LocalDateTime.now())
                            "33886036" -> statistikkTilDVH.copy(behandlingId = "44603036", tekniskTid = LocalDateTime.now())
                            "32430744" -> statistikkTilDVH.copy(behandlingId = "46389938", tekniskTid = LocalDateTime.now())
                            "33627236" -> statistikkTilDVH.copy(behandlingId = "48394350", tekniskTid = LocalDateTime.now())
                            "34246436" -> statistikkTilDVH.copy(behandlingId = "48173256", tekniskTid = LocalDateTime.now())
                            "33809024" -> statistikkTilDVH.copy(behandlingId = "48258549", tekniskTid = LocalDateTime.now())
                            "34282802" -> statistikkTilDVH.copy(behandlingId = "48000261", tekniskTid = LocalDateTime.now())
                            "34562200" -> statistikkTilDVH.copy(behandlingId = "48205212", tekniskTid = LocalDateTime.now())
                            "35086144" -> statistikkTilDVH.copy(behandlingId = "46938006", tekniskTid = LocalDateTime.now())
                            "35222996" -> statistikkTilDVH.copy(behandlingId = "38002037", tekniskTid = LocalDateTime.now())
                            "35044606" -> statistikkTilDVH.copy(behandlingId = "49230454", tekniskTid = LocalDateTime.now())
                            "35856430" -> statistikkTilDVH.copy(behandlingId = "38687565", tekniskTid = LocalDateTime.now())
                            "36055424" -> statistikkTilDVH.copy(behandlingId = "48365343", tekniskTid = LocalDateTime.now())
                            "36042860" -> statistikkTilDVH.copy(behandlingId = "46804296", tekniskTid = LocalDateTime.now())
                            "36033320" -> statistikkTilDVH.copy(behandlingId = "37554452", tekniskTid = LocalDateTime.now())
                            "36208620" -> statistikkTilDVH.copy(behandlingId = "40730095", tekniskTid = LocalDateTime.now())
                            "36294126" -> statistikkTilDVH.copy(behandlingId = "47255004", tekniskTid = LocalDateTime.now())
                            "36285670" -> statistikkTilDVH.copy(behandlingId = "48811952", tekniskTid = LocalDateTime.now())
                            "36149428" -> statistikkTilDVH.copy(behandlingId = "47236218", tekniskTid = LocalDateTime.now())
                            "36461979" -> statistikkTilDVH.copy(behandlingId = "45905369", tekniskTid = LocalDateTime.now())
                            "36327632" -> statistikkTilDVH.copy(behandlingId = "39390764", tekniskTid = LocalDateTime.now())
                            "36944621" -> statistikkTilDVH.copy(behandlingId = "48830720", tekniskTid = LocalDateTime.now())
                            "37553286" -> statistikkTilDVH.copy(behandlingId = "46835743", tekniskTid = LocalDateTime.now())
                            "37596506" -> statistikkTilDVH.copy(behandlingId = "48815699", tekniskTid = LocalDateTime.now())
                            "37583329" -> statistikkTilDVH.copy(behandlingId = "45881146", tekniskTid = LocalDateTime.now())
                            "36956921" -> statistikkTilDVH.copy(behandlingId = "40142968", tekniskTid = LocalDateTime.now())
                            "38298003" -> statistikkTilDVH.copy(behandlingId = "40891656", tekniskTid = LocalDateTime.now())
                            "38318970" -> statistikkTilDVH.copy(behandlingId = "46840644", tekniskTid = LocalDateTime.now())
                            "38511375" -> statistikkTilDVH.copy(behandlingId = "48232955", tekniskTid = LocalDateTime.now())
                            "39060912" -> statistikkTilDVH.copy(behandlingId = "42426654", tekniskTid = LocalDateTime.now())
                            "39080137" -> statistikkTilDVH.copy(behandlingId = "45651737", tekniskTid = LocalDateTime.now())
                            "38709636" -> statistikkTilDVH.copy(behandlingId = "48220097", tekniskTid = LocalDateTime.now())
                            "38685215" -> statistikkTilDVH.copy(behandlingId = "48280515", tekniskTid = LocalDateTime.now())
                            "39081031" -> statistikkTilDVH.copy(behandlingId = "48243251", tekniskTid = LocalDateTime.now())
                            "39539510" -> statistikkTilDVH.copy(behandlingId = "48218644", tekniskTid = LocalDateTime.now())
                            "39409411" -> statistikkTilDVH.copy(behandlingId = "44907156", tekniskTid = LocalDateTime.now())
                            "39252485" -> statistikkTilDVH.copy(behandlingId = "42522803", tekniskTid = LocalDateTime.now())
                            "39841893" -> statistikkTilDVH.copy(behandlingId = "42432231", tekniskTid = LocalDateTime.now())
                            "40392188" -> statistikkTilDVH.copy(behandlingId = "47088886", tekniskTid = LocalDateTime.now())
                            "40553364" -> statistikkTilDVH.copy(behandlingId = "48441912", tekniskTid = LocalDateTime.now())
                            "40571899" -> statistikkTilDVH.copy(behandlingId = "48390755", tekniskTid = LocalDateTime.now())
                            "40199910" -> statistikkTilDVH.copy(behandlingId = "43961598", tekniskTid = LocalDateTime.now())
                            "40295204" -> statistikkTilDVH.copy(behandlingId = "48429191", tekniskTid = LocalDateTime.now())
                            "40742200" -> statistikkTilDVH.copy(behandlingId = "42953836", tekniskTid = LocalDateTime.now())
                            else -> throw RuntimeException("Unknown behandlingId: ${statistikkTilDVH.behandlingId}")
                        }

                        preparedStatement.setString(1, jacksonObjectMapper().writeValueAsString(modifiedVersion))
                        preparedStatement.setObject(2,"IKKE_SENDT")
                        preparedStatement.setObject(3, kafkaEventId)

                        preparedStatement.executeUpdate()
                    }

                }
        }
    }
}