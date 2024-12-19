package db.migration

import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.time.LocalDateTime
import java.util.*

class V172__dvh_pesys_fix_migrated_anker_part2 : BaseJavaMigration() {
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
 '58290427', 
 '59678358',
 '59840378',
 '59899331',
 '59912089',
 '59919397',
 '59980086',
 '60001412',
 '60005998',
 '60006072',
 '60007696',
 '60015842',
 '60131670',
 '61748885',
 '61750894',
 '61778586',
 '61778671',
 '61778700',
 '61786532',
 '61801989',
 '61812699',
 '61827567',
 '61827949',
 '61830781',
 '61841600',
 '61850422',
 '61855849',
 '61860290',
 '61864471',
 '61866056',
 '61885865',
 '61892739',
 '61897878',
 '61919647',
 '61932749',
 '61940510',
 '61940572',
 '61983223',
 '61986711',
 '62001478',
 '62019893',
 '62021520',
 '62023541',
 '62054649',
 '62055983',
 '62062015',
 '62695513',
 '62696116',
 '62701249',
 '62708458',
 '62712334',
 '62724709',
 '62726792',
 '62732855',
 '62748398',
 '62748480',
 '62751203',
 '62753009',
 '62753715',
 '62759570',
 '62763503',
 '62766704',
 '62768456',
 '62771904',
 '62774749',
 '62779431',
 '62781243',
 '62783578',
 '62785620',
 '62787622',
 '62794965',
 '62804004',
 '63240321',
 '63240546',
 '63241696',
 '63245485',
 '63249590',
 '63250213',
 '63252018',
 '63254593',
 '63254971',
 '63256949',
 '63257456',
 '63258321',
 '63259865',
 '63260236',
 '63260556',
 '63261877',
 '63262797',
 '63263556',
 '63263734',
 '63265357',
 '63267973',
 '63269231',
 '63272330',
 '63278702',
 '63282626',
 '63283534',
 '63284070',
 '63285048',
 '63285260',
 '63287693',
 '63287722',
 '63288994',
 '63290428',
 '63291543',
 '63294301',
 '63295840',
 '63298861',
 '63299755',
 '63300329',
 '63301335',
 '63301617',
 '63301701',
 '63303325',
 '63305354',
 '63315713',
 '63316603',
 '63317241',
 '63317646',
 '63318095',
 '63320817',
 '63322152',
 '63322758',
 '63323045',
 '63323342',
 '63325032',
 '63327966',
 '63330641',
 '63331453',
 '63331573',
 '63333494',
 '63335355',
 '63337067',
 '63337573', 
 '63338523',
 '64935754',
 '64937625',
 '64948265',
 '64950451',
 '64951332',
 '64951688',
 '64956059',
 '64956325',
 '64960228',
 '64963927',
 '64967819',
 '64972094',
 '64973422',
 '64976194',
 '64976411',
 '64976744',
 '64977747',
 '64977824',
 '64978803',
 '64979121',
 '64991665',
 '65000970',
 '65007620',
 '65014301',
 '65014796',
 '65021561',
 '65025940',
 '65036472',
 '65036643',
 '65038524',
 '65039247',
 '65039261',
 '65041769',
 '65042055',
 '65042373',
 '65042651',
 '65047668',
 '65048878',
 '65052797',
 '65053201',
 '65054465',
 '65065936',
 '65066320',
 '65066328',
 '65068618',
 '65070226',
 '65070252',
 '65070376',
 '65071461',
 '65072132',
 '65072607',
 '65076729',
 '65077533',
 '65078434',
 '65079971',
 '65081126',
 '65081297',
 '65082878',
 '65082915',
 '65083007',
 '65084314',
 '65084512',
 '65085044',
 '65085376',
 '65085786',
 '65086692',
 '65087899',
 '65088093',
 '65089011',
 '65089080',
 '65089148',
 '65089286',
 '65089393',
 '65090623',
 '65091527',
 '65091590',
 '65091912',
 '65092120',
 '65092150',
 '65092260',
 '65092416',
 '65092437',
 '65092445',
 '65092559',
 '65092623',
 '65092926',
 '65093260',
 '65093364',
 '65093413',
 '65093545',
 '65093674',
 '65093916',
 '65094156',
 '65094506',
 '65094891',
 '65095059',
 '65095191',
 '65095229',
 '65095374',
 '65095840',
 '65096263',
 '65096413',
 '65098544',
 '65098600',
 '65104577',
 '65104995',
 '65105282',
 '65105392',
 '65105803',
 '65105886',
 '65106040',
 '65106655',
 '65107783',
 '65108024',
 '65108546',
 '65109102',
 '65109106',
 '65109277',
 '65109281',
 '65109455',
 '65111248',
 '65111297',
 '65111489',
 '65112063',
 '65112638',
 '65112716',
 '65113251',
 '65113576',
 '65118328',
 '65118369',
 '65126214',
 '65126261',
 '65126273',
 '65127438',
 '65127468',
 '65128068',
 '65128156',
 '65130208',
 '65130296',
 '65131008',
 '65131045',
 '65132837',
 '65132891',
 '65133205',
 '65133349',
 '65133970',
 '65134156',
 '65134578',
 '65134591',
 '65134782',
 '65135506',
 '65135828',
 '65139145',
 '65147347',
 '65147857',
 '65148027',
 '65186579',
 '65187673',
 '65187710',
 '65188364',
 '65188523',
 '65188541',
 '65188678',
 '65188679',
 '65189110',
 '65189220',
 '65189384',
 '65190505',
 '65191209',
 '65193581',
 '65194129',
 '65195190',
 '65195257',
 '65195321',
 '65196731',
 '65196811',
 '65200300',
 '65200405',
 '65204929',
 '65205323',
 '65205831',
 '65207571',
 '65207770',
 '65207904',
 '65208033',
 '65210846',
 '65211056',
 '65211856',
 '65212727',
 '65213184',
 '65213423',
 '65214902',
 '65215167',
 '65216148',
 '65216653',
 '65219534',
 '65220479',
 '65220743',
 '65221006',
 '65221510',
 '65222375',
 '65223252',
 '65223940',
 '65224123',
 '65224501',
 '65224730',
 '65225239',
 '65225351',
 '65225393',
 '65226275',
 '65226955',
 '65227054',
 '65227471',
 '65227480',
 '65227521',
 '65227709',
 '65227987',
 '65228055',
 '65228203',
 '65229290',
 '65249763',
 '65249903',
 '65250244',
 '65250363',
 '65253676',
 '65254388',
 '65254726',
 '65256459',
 '65256562',
 '65257759',
 '65258351',
 '65511458',
 '65512329',
 '65513560',
 '65513794',
 '65514321',
 '65514725',
 '65514899',
 '65514982',
 '65515129',
 '65515132',
 '65515283',
 '65515300',
 '65515346',
 '65515743',
 '65515987',
 '65516316',
 '65517147',
 '65517843',
 '65518166',
 '65518486',
 '65519420',
 '65519822',
 '65519847',
 '65520482',
 '65520515',
 '65520805',
 '65521403',
 '65521448',
 '65526614',
 '65527776',
 '65528318',
 '65528409',
 '65530262',
 '65530294',
 '65530861',
 '65531306',
 '65531455',
 '65531646',
 '65531655',
 '65531671',
 '65531679',
 '65531697',
 '65531711',
 '65532165',
 '65532477',
 '65586033', 
 '65587508',
 '65592403',
 '65592466',
 '65592489',
 '65592493',
 '65592509',
 '65592671',
 '65593105',
 '65593196',
 '65593493',
 '65593826',
 '65664970',
 '65666174',
 '65666750',
 '65667035',
 '65676955',
 '65677674',
 '65692068',
 '65692541',
 '65693294',
 '65693986',
 '65695741',
 '65696454',
 '65696582',
 '65697950',
 '65698040',
 '65698314',
 '65698453',
 '65698637',
 '65716566',
 '65716886',
 '65717010',
 '65717190',
 '65717948',
 '65882333',
 '65883584',
 '65883870',
 '65883970',
 '65884443',
 '65884772',
 '65885387',
 '65887874',
 '65892260',
 '65894508',
 '66012245', 
 '66014404',
 '66015412',
 '66015531',
 '66016206',
 '66016287',
 '66016511',
 '66037952',
 '66038364',
 '66046060',
 '66046109',
 '66046857',
 '66050844',
 '66051530',
 '66052816',
 '66053427',
 '66054442',
 '66055637',
 '66069571',
 '66072476',
 '66072488',
 '66072671',
 '66076920',
 '66077229',
 '66078066',
 '66078172',
 '66078264',
 '66080158',
 '66082242',
 '66090219', 
 '66094109', 
 '66095943',
 '66096141',
 '66096931',
 '66097263',
 '66097369',
 '66097538',
 '66100468',
 '66101413',
 '66103801',
 '66104603',
 '66104859',
 '66104949',
 '66104994',
 '66105727',
 '66106414',
 '66106738',
 '66106760',
 '66108919',
 '66109471',
 '66109734',
 '66109852',
 '66110762',
 '66115632',
 '66115850',
 '66116995',
 '66117068',
 '66119094',
 '66119139',
 '66123123',
 '66124117',
 '66126771',
 '66127044',
 '66133906',
 '66134133',
 '66135245', 
 '66137607',
 '66138695',
 '66140752',
 '66141095',
 '66141449',
 '66141835',
 '66144176',
 '66146012',
 '66147405',
 '66147417',
 '66147484',
 '66150716',
 '66161237',
 '66161575',
 '66163882',
 '66171204',
 '66171789',
 '66173205',
 '66173290',
 '66175267',
 '66176759',
 '66177005',
 '66178606',
 '66190577',
 '66192477',
 '66194094',
 '66196360',
 '66198387',
 '66202741',
 '66203294',
 '66215264',
 '66220265',
 '66233172',
 '66240203',
 '67886831',
 '67889634',
 '67908958',
 '68074613',
 '68079386',
 '68083544',
 '68308428',
 '68310332',
 '68311312',
 '68342413',
 '68379786')
                    """
            )
                .use { rows ->
                    while (rows.next()) {
                        val kafkaEventId = rows.getObject(1, UUID::class.java)
                        val jsonPayload = rows.getString(2)

                        val statistikkTilDVH =
                            ourJacksonObjectMapper().readValue(jsonPayload, StatistikkTilDVH::class.java)

                        val modifiedVersion = when (statistikkTilDVH.behandlingId) {
                            "41056205" -> statistikkTilDVH.copy(behandlingId = "43548904", tekniskTid = LocalDateTime.now())
                            "40130646" -> statistikkTilDVH.copy(behandlingId = "48451047", tekniskTid = LocalDateTime.now())
                            "42429359" -> statistikkTilDVH.copy(behandlingId = "48381593", tekniskTid = LocalDateTime.now())
                            "40279855" -> statistikkTilDVH.copy(behandlingId = "44306161", tekniskTid = LocalDateTime.now())
                            "42715950" -> statistikkTilDVH.copy(behandlingId = "43652505", tekniskTid = LocalDateTime.now())
                            "42531341" -> statistikkTilDVH.copy(behandlingId = "44892707", tekniskTid = LocalDateTime.now())
                            "43072143" -> statistikkTilDVH.copy(behandlingId = "49176655", tekniskTid = LocalDateTime.now())
                            "42757727" -> statistikkTilDVH.copy(behandlingId = "46438601", tekniskTid = LocalDateTime.now())
                            "42816873" -> statistikkTilDVH.copy(behandlingId = "44906943", tekniskTid = LocalDateTime.now())
                            "43136608" -> statistikkTilDVH.copy(behandlingId = "48495522", tekniskTid = LocalDateTime.now())
                            "43011489" -> statistikkTilDVH.copy(behandlingId = "48275156", tekniskTid = LocalDateTime.now())
                            "43417112" -> statistikkTilDVH.copy(behandlingId = "45270277", tekniskTid = LocalDateTime.now())
                            "43101803" -> statistikkTilDVH.copy(behandlingId = "46837399", tekniskTid = LocalDateTime.now())
                            "42139056" -> statistikkTilDVH.copy(behandlingId = "44727906", tekniskTid = LocalDateTime.now())
                            "43500734" -> statistikkTilDVH.copy(behandlingId = "44861049", tekniskTid = LocalDateTime.now())
                            "43111753" -> statistikkTilDVH.copy(behandlingId = "46423808", tekniskTid = LocalDateTime.now())
                            "42848791" -> statistikkTilDVH.copy(behandlingId = "48401344", tekniskTid = LocalDateTime.now())
                            "43541869" -> statistikkTilDVH.copy(behandlingId = "45612472", tekniskTid = LocalDateTime.now())
                            "44166846" -> statistikkTilDVH.copy(behandlingId = "48520429", tekniskTid = LocalDateTime.now())
                            "43548002" -> statistikkTilDVH.copy(behandlingId = "45617251", tekniskTid = LocalDateTime.now())
                            "43465650" -> statistikkTilDVH.copy(behandlingId = "46947997", tekniskTid = LocalDateTime.now())
                            "43883318" -> statistikkTilDVH.copy(behandlingId = "44877597", tekniskTid = LocalDateTime.now())
                            "42885144" -> statistikkTilDVH.copy(behandlingId = "45732463", tekniskTid = LocalDateTime.now())
                            "44252667" -> statistikkTilDVH.copy(behandlingId = "47035348", tekniskTid = LocalDateTime.now())
                            "43481500" -> statistikkTilDVH.copy(behandlingId = "45657267", tekniskTid = LocalDateTime.now())
                            "44179696" -> statistikkTilDVH.copy(behandlingId = "49081544", tekniskTid = LocalDateTime.now())
                            "44401831" -> statistikkTilDVH.copy(behandlingId = "44861068", tekniskTid = LocalDateTime.now())
                            "44356666" -> statistikkTilDVH.copy(behandlingId = "45792465", tekniskTid = LocalDateTime.now())
                            "44416485" -> statistikkTilDVH.copy(behandlingId = "46488598", tekniskTid = LocalDateTime.now())
                            "44254891" -> statistikkTilDVH.copy(behandlingId = "46465444", tekniskTid = LocalDateTime.now())
                            "44501272" -> statistikkTilDVH.copy(behandlingId = "48381616", tekniskTid = LocalDateTime.now())
                            "44210804" -> statistikkTilDVH.copy(behandlingId = "49255064", tekniskTid = LocalDateTime.now())
                            "44227323" -> statistikkTilDVH.copy(behandlingId = "45895815", tekniskTid = LocalDateTime.now())
                            "44597628" -> statistikkTilDVH.copy(behandlingId = "48348896", tekniskTid = LocalDateTime.now())
                            "44638320" -> statistikkTilDVH.copy(behandlingId = "46519447", tekniskTid = LocalDateTime.now())
                            "44305802" -> statistikkTilDVH.copy(behandlingId = "46409044", tekniskTid = LocalDateTime.now())
                            "38559223" -> statistikkTilDVH.copy(behandlingId = "46530374", tekniskTid = LocalDateTime.now())
                            "44567836" -> statistikkTilDVH.copy(behandlingId = "48242844", tekniskTid = LocalDateTime.now())
                            "44730146" -> statistikkTilDVH.copy(behandlingId = "46943543", tekniskTid = LocalDateTime.now())
                            "44769197" -> statistikkTilDVH.copy(behandlingId = "46413250", tekniskTid = LocalDateTime.now())
                            "44321278" -> statistikkTilDVH.copy(behandlingId = "49064982", tekniskTid = LocalDateTime.now())
                            "44843202" -> statistikkTilDVH.copy(behandlingId = "47037966", tekniskTid = LocalDateTime.now())
                            "44833768" -> statistikkTilDVH.copy(behandlingId = "46472848", tekniskTid = LocalDateTime.now())
                            "44822150" -> statistikkTilDVH.copy(behandlingId = "46468849", tekniskTid = LocalDateTime.now())
                            "44492812" -> statistikkTilDVH.copy(behandlingId = "46352487", tekniskTid = LocalDateTime.now())
                            "44915121" -> statistikkTilDVH.copy(behandlingId = "45627220", tekniskTid = LocalDateTime.now())
                            "44440571" -> statistikkTilDVH.copy(behandlingId = "46575296", tekniskTid = LocalDateTime.now())
                            "42745576" -> statistikkTilDVH.copy(behandlingId = "46579854", tekniskTid = LocalDateTime.now())
                            "43142047" -> statistikkTilDVH.copy(behandlingId = "46578843", tekniskTid = LocalDateTime.now())
                            "42951416" -> statistikkTilDVH.copy(behandlingId = "46742128", tekniskTid = LocalDateTime.now())
                            "44811703" -> statistikkTilDVH.copy(behandlingId = "46579462", tekniskTid = LocalDateTime.now())
                            "45651943" -> statistikkTilDVH.copy(behandlingId = "48643019", tekniskTid = LocalDateTime.now())
                            "44835271" -> statistikkTilDVH.copy(behandlingId = "46708927", tekniskTid = LocalDateTime.now())
                            "45683045" -> statistikkTilDVH.copy(behandlingId = "46763282", tekniskTid = LocalDateTime.now())
                            "44827102" -> statistikkTilDVH.copy(behandlingId = "47822797", tekniskTid = LocalDateTime.now())
                            "45295459" -> statistikkTilDVH.copy(behandlingId = "46752895", tekniskTid = LocalDateTime.now())
                            "44204138" -> statistikkTilDVH.copy(behandlingId = "46566344", tekniskTid = LocalDateTime.now())
                            "44562969" -> statistikkTilDVH.copy(behandlingId = "48529198", tekniskTid = LocalDateTime.now())
                            "45723093" -> statistikkTilDVH.copy(behandlingId = "46661958", tekniskTid = LocalDateTime.now())
                            "44064545" -> statistikkTilDVH.copy(behandlingId = "46947560", tekniskTid = LocalDateTime.now())
                            "44769641" -> statistikkTilDVH.copy(behandlingId = "46758856", tekniskTid = LocalDateTime.now())
                            "44257700" -> statistikkTilDVH.copy(behandlingId = "46777946", tekniskTid = LocalDateTime.now())
                            "44848744" -> statistikkTilDVH.copy(behandlingId = "46801404", tekniskTid = LocalDateTime.now())
                            "45262701" -> statistikkTilDVH.copy(behandlingId = "48623450", tekniskTid = LocalDateTime.now())
                            "44188531" -> statistikkTilDVH.copy(behandlingId = "46848221", tekniskTid = LocalDateTime.now())
                            "45309850" -> statistikkTilDVH.copy(behandlingId = "47553268", tekniskTid = LocalDateTime.now())
                            "45621595" -> statistikkTilDVH.copy(behandlingId = "46806016", tekniskTid = LocalDateTime.now())
                            "45693913" -> statistikkTilDVH.copy(behandlingId = "46772998", tekniskTid = LocalDateTime.now())
                            "44728581" -> statistikkTilDVH.copy(behandlingId = "46760044", tekniskTid = LocalDateTime.now())
                            "45795144" -> statistikkTilDVH.copy(behandlingId = "48360928", tekniskTid = LocalDateTime.now())
                            "45288930" -> statistikkTilDVH.copy(behandlingId = "46857709", tekniskTid = LocalDateTime.now())
                            "44887156" -> statistikkTilDVH.copy(behandlingId = "47989165", tekniskTid = LocalDateTime.now())
                            "45866794" -> statistikkTilDVH.copy(behandlingId = "47798301", tekniskTid = LocalDateTime.now())
                            "44631424" -> statistikkTilDVH.copy(behandlingId = "49083052", tekniskTid = LocalDateTime.now())
                            "44654615" -> statistikkTilDVH.copy(behandlingId = "48814568", tekniskTid = LocalDateTime.now())
                            "44848774" -> statistikkTilDVH.copy(behandlingId = "47223196", tekniskTid = LocalDateTime.now())
                            "45857643" -> statistikkTilDVH.copy(behandlingId = "47296658", tekniskTid = LocalDateTime.now())
                            "44647822" -> statistikkTilDVH.copy(behandlingId = "46996338", tekniskTid = LocalDateTime.now())
                            "45607812" -> statistikkTilDVH.copy(behandlingId = "47173706", tekniskTid = LocalDateTime.now())
                            "45638115" -> statistikkTilDVH.copy(behandlingId = "47085701", tekniskTid = LocalDateTime.now())
                            "44788298" -> statistikkTilDVH.copy(behandlingId = "46881805", tekniskTid = LocalDateTime.now())
                            "45246788" -> statistikkTilDVH.copy(behandlingId = "48152406", tekniskTid = LocalDateTime.now())
                            "45180614" -> statistikkTilDVH.copy(behandlingId = "47224608", tekniskTid = LocalDateTime.now())
                            "45665426" -> statistikkTilDVH.copy(behandlingId = "47236176", tekniskTid = LocalDateTime.now())
                            "45740390" -> statistikkTilDVH.copy(behandlingId = "47245293", tekniskTid = LocalDateTime.now())
                            "45639449" -> statistikkTilDVH.copy(behandlingId = "46814121", tekniskTid = LocalDateTime.now())
                            "45638925" -> statistikkTilDVH.copy(behandlingId = "47197645", tekniskTid = LocalDateTime.now())
                            "45666007" -> statistikkTilDVH.copy(behandlingId = "47578627", tekniskTid = LocalDateTime.now())
                            "45719714" -> statistikkTilDVH.copy(behandlingId = "47076176", tekniskTid = LocalDateTime.now())
                            "46408145" -> statistikkTilDVH.copy(behandlingId = "47213944", tekniskTid = LocalDateTime.now())
                            "46397794" -> statistikkTilDVH.copy(behandlingId = "47989734", tekniskTid = LocalDateTime.now())
                            "45881604" -> statistikkTilDVH.copy(behandlingId = "46854041", tekniskTid = LocalDateTime.now())
                            "45751193" -> statistikkTilDVH.copy(behandlingId = "47801137", tekniskTid = LocalDateTime.now())
                            "45308570" -> statistikkTilDVH.copy(behandlingId = "47674694", tekniskTid = LocalDateTime.now())
                            "46433104" -> statistikkTilDVH.copy(behandlingId = "47989597", tekniskTid = LocalDateTime.now())
                            "45622503" -> statistikkTilDVH.copy(behandlingId = "47251745", tekniskTid = LocalDateTime.now())
                            "45849992" -> statistikkTilDVH.copy(behandlingId = "46780895", tekniskTid = LocalDateTime.now())
                            "44926183" -> statistikkTilDVH.copy(behandlingId = "47992569", tekniskTid = LocalDateTime.now())
                            "45679854" -> statistikkTilDVH.copy(behandlingId = "46877496", tekniskTid = LocalDateTime.now())
                            "45689427" -> statistikkTilDVH.copy(behandlingId = "46705279", tekniskTid = LocalDateTime.now())
                            "45721612" -> statistikkTilDVH.copy(behandlingId = "48282999", tekniskTid = LocalDateTime.now())
                            "46383719" -> statistikkTilDVH.copy(behandlingId = "47989762", tekniskTid = LocalDateTime.now())
                            "45620865" -> statistikkTilDVH.copy(behandlingId = "47675449", tekniskTid = LocalDateTime.now())
                            "45625488" -> statistikkTilDVH.copy(behandlingId = "48005287", tekniskTid = LocalDateTime.now())
                            "45655100" -> statistikkTilDVH.copy(behandlingId = "47212649", tekniskTid = LocalDateTime.now())
                            "45703822" -> statistikkTilDVH.copy(behandlingId = "48639648", tekniskTid = LocalDateTime.now())
                            "45657379" -> statistikkTilDVH.copy(behandlingId = "47994895", tekniskTid = LocalDateTime.now())
                            "43016804" -> statistikkTilDVH.copy(behandlingId = "47995121", tekniskTid = LocalDateTime.now())
                            "45717785" -> statistikkTilDVH.copy(behandlingId = "47039511", tekniskTid = LocalDateTime.now())
                            "45848082" -> statistikkTilDVH.copy(behandlingId = "47796593", tekniskTid = LocalDateTime.now())
                            "45760246" -> statistikkTilDVH.copy(behandlingId = "47680416", tekniskTid = LocalDateTime.now())
                            "46484194" -> statistikkTilDVH.copy(behandlingId = "48134991", tekniskTid = LocalDateTime.now())
                            "45812713" -> statistikkTilDVH.copy(behandlingId = "46856951", tekniskTid = LocalDateTime.now())
                            "45807143" -> statistikkTilDVH.copy(behandlingId = "47786993", tekniskTid = LocalDateTime.now())
                            "46428806" -> statistikkTilDVH.copy(behandlingId = "47557595", tekniskTid = LocalDateTime.now())
                            "45857998" -> statistikkTilDVH.copy(behandlingId = "48143421", tekniskTid = LocalDateTime.now())
                            "46364843" -> statistikkTilDVH.copy(behandlingId = "47218446", tekniskTid = LocalDateTime.now())
                            "45881178" -> statistikkTilDVH.copy(behandlingId = "46938143", tekniskTid = LocalDateTime.now())
                            "46509281" -> statistikkTilDVH.copy(behandlingId = "48211628", tekniskTid = LocalDateTime.now())
                            "46347495" -> statistikkTilDVH.copy(behandlingId = "47681356", tekniskTid = LocalDateTime.now())
                            "46531245" -> statistikkTilDVH.copy(behandlingId = "48204259", tekniskTid = LocalDateTime.now())
                            "46544203" -> statistikkTilDVH.copy(behandlingId = "49090209", tekniskTid = LocalDateTime.now())
                            "46555296" -> statistikkTilDVH.copy(behandlingId = "47675698", tekniskTid = LocalDateTime.now())
                            "45837096" -> statistikkTilDVH.copy(behandlingId = "48001393", tekniskTid = LocalDateTime.now())
                            "45881143" -> statistikkTilDVH.copy(behandlingId = "47254752", tekniskTid = LocalDateTime.now())
                            "44812556" -> statistikkTilDVH.copy(behandlingId = "48496353", tekniskTid = LocalDateTime.now())
                            "46371554" -> statistikkTilDVH.copy(behandlingId = "47024098", tekniskTid = LocalDateTime.now())
                            "46438443" -> statistikkTilDVH.copy(behandlingId = "47562324", tekniskTid = LocalDateTime.now())
                            "46497855" -> statistikkTilDVH.copy(behandlingId = "48168168", tekniskTid = LocalDateTime.now())
                            "45863119" -> statistikkTilDVH.copy(behandlingId = "48372453", tekniskTid = LocalDateTime.now())
                            "46567601" -> statistikkTilDVH.copy(behandlingId = "48009502", tekniskTid = LocalDateTime.now())
                            "46500604" -> statistikkTilDVH.copy(behandlingId = "48187506", tekniskTid = LocalDateTime.now())
                            "46389909" -> statistikkTilDVH.copy(behandlingId = "47554749", tekniskTid = LocalDateTime.now())
                            "46497396" -> statistikkTilDVH.copy(behandlingId = "47690897", tekniskTid = LocalDateTime.now())
                            "46377051" -> statistikkTilDVH.copy(behandlingId = "47995351", tekniskTid = LocalDateTime.now())
                            "46408849" -> statistikkTilDVH.copy(behandlingId = "48328022", tekniskTid = LocalDateTime.now())
                            "46527125" -> statistikkTilDVH.copy(behandlingId = "47213693", tekniskTid = LocalDateTime.now())
                            "46413747" -> statistikkTilDVH.copy(behandlingId = "48194331", tekniskTid = LocalDateTime.now())
                            "46391201" -> statistikkTilDVH.copy(behandlingId = "48447160", tekniskTid = LocalDateTime.now())
                            "46677593" -> statistikkTilDVH.copy(behandlingId = "48830256", tekniskTid = LocalDateTime.now())
                            "46590694" -> statistikkTilDVH.copy(behandlingId = "47605754", tekniskTid = LocalDateTime.now())
                            "46468645" -> statistikkTilDVH.copy(behandlingId = "48183052", tekniskTid = LocalDateTime.now())
                            "46550813" -> statistikkTilDVH.copy(behandlingId = "48187546", tekniskTid = LocalDateTime.now())
                            "44025696" -> statistikkTilDVH.copy(behandlingId = "49085720", tekniskTid = LocalDateTime.now())
                            "44921829" -> statistikkTilDVH.copy(behandlingId = "49147860", tekniskTid = LocalDateTime.now())
                            "46652099" -> statistikkTilDVH.copy(behandlingId = "48388270", tekniskTid = LocalDateTime.now())
                            "46722124" -> statistikkTilDVH.copy(behandlingId = "48384636", tekniskTid = LocalDateTime.now())
                            "46751894" -> statistikkTilDVH.copy(behandlingId = "48621718", tekniskTid = LocalDateTime.now())
                            "46456633" -> statistikkTilDVH.copy(behandlingId = "48211602", tekniskTid = LocalDateTime.now())
                            "46742609" -> statistikkTilDVH.copy(behandlingId = "48273067", tekniskTid = LocalDateTime.now())
                            "46686942" -> statistikkTilDVH.copy(behandlingId = "48270721", tekniskTid = LocalDateTime.now())
                            "46746955" -> statistikkTilDVH.copy(behandlingId = "48384355", tekniskTid = LocalDateTime.now())
                            "46545265" -> statistikkTilDVH.copy(behandlingId = "47823714", tekniskTid = LocalDateTime.now())
                            "46557729" -> statistikkTilDVH.copy(behandlingId = "48182915", tekniskTid = LocalDateTime.now())
                            "46760302" -> statistikkTilDVH.copy(behandlingId = "48339049", tekniskTid = LocalDateTime.now())
                            "46472069" -> statistikkTilDVH.copy(behandlingId = "47788400", tekniskTid = LocalDateTime.now())
                            "46463749" -> statistikkTilDVH.copy(behandlingId = "48231102", tekniskTid = LocalDateTime.now())
                            "46733199" -> statistikkTilDVH.copy(behandlingId = "48201773", tekniskTid = LocalDateTime.now())
                            "46749738" -> statistikkTilDVH.copy(behandlingId = "47562447", tekniskTid = LocalDateTime.now())
                            "46441140" -> statistikkTilDVH.copy(behandlingId = "48203186", tekniskTid = LocalDateTime.now())
                            "46530380" -> statistikkTilDVH.copy(behandlingId = "48022769", tekniskTid = LocalDateTime.now())
                            "46659124" -> statistikkTilDVH.copy(behandlingId = "48245484", tekniskTid = LocalDateTime.now())
                            "46592269" -> statistikkTilDVH.copy(behandlingId = "48824861", tekniskTid = LocalDateTime.now())
                            "46415810" -> statistikkTilDVH.copy(behandlingId = "48276076", tekniskTid = LocalDateTime.now())
                            "45648015" -> statistikkTilDVH.copy(behandlingId = "48314022", tekniskTid = LocalDateTime.now())
                            "46579147" -> statistikkTilDVH.copy(behandlingId = "48629003", tekniskTid = LocalDateTime.now())
                            "46864649" -> statistikkTilDVH.copy(behandlingId = "48272744", tekniskTid = LocalDateTime.now())
                            "46872693" -> statistikkTilDVH.copy(behandlingId = "48318114", tekniskTid = LocalDateTime.now())
                            "46875811" -> statistikkTilDVH.copy(behandlingId = "48324383", tekniskTid = LocalDateTime.now())
                            "46573698" -> statistikkTilDVH.copy(behandlingId = "48452285", tekniskTid = LocalDateTime.now())
                            "46485095" -> statistikkTilDVH.copy(behandlingId = "48319676", tekniskTid = LocalDateTime.now())
                            "46411452" -> statistikkTilDVH.copy(behandlingId = "48244551", tekniskTid = LocalDateTime.now())
                            "46808263" -> statistikkTilDVH.copy(behandlingId = "48286279", tekniskTid = LocalDateTime.now())
                            "46895254" -> statistikkTilDVH.copy(behandlingId = "48282907", tekniskTid = LocalDateTime.now())
                            "46551072" -> statistikkTilDVH.copy(behandlingId = "48349120", tekniskTid = LocalDateTime.now())
                            "46900143" -> statistikkTilDVH.copy(behandlingId = "48295398", tekniskTid = LocalDateTime.now())
                            "46433698" -> statistikkTilDVH.copy(behandlingId = "48323671", tekniskTid = LocalDateTime.now())
                            "46921847" -> statistikkTilDVH.copy(behandlingId = "48427269", tekniskTid = LocalDateTime.now())
                            "46462309" -> statistikkTilDVH.copy(behandlingId = "48815390", tekniskTid = LocalDateTime.now())
                            "46467944" -> statistikkTilDVH.copy(behandlingId = "48441972", tekniskTid = LocalDateTime.now())
                            "46475271" -> statistikkTilDVH.copy(behandlingId = "48410394", tekniskTid = LocalDateTime.now())
                            "46745997" -> statistikkTilDVH.copy(behandlingId = "48393802", tekniskTid = LocalDateTime.now())
                            "46752239" -> statistikkTilDVH.copy(behandlingId = "48349021", tekniskTid = LocalDateTime.now())
                            "46777021" -> statistikkTilDVH.copy(behandlingId = "48299414", tekniskTid = LocalDateTime.now())
                            "46928344" -> statistikkTilDVH.copy(behandlingId = "48492821", tekniskTid = LocalDateTime.now())
                            "46928696" -> statistikkTilDVH.copy(behandlingId = "48644215", tekniskTid = LocalDateTime.now())
                            "46924161" -> statistikkTilDVH.copy(behandlingId = "48346642", tekniskTid = LocalDateTime.now())
                            "46498895" -> statistikkTilDVH.copy(behandlingId = "48328129", tekniskTid = LocalDateTime.now())
                            "46522744" -> statistikkTilDVH.copy(behandlingId = "48323649", tekniskTid = LocalDateTime.now())
                            "46497863" -> statistikkTilDVH.copy(behandlingId = "48453141", tekniskTid = LocalDateTime.now())
                            "46684856" -> statistikkTilDVH.copy(behandlingId = "48463168", tekniskTid = LocalDateTime.now())
                            "46779185" -> statistikkTilDVH.copy(behandlingId = "48376797", tekniskTid = LocalDateTime.now())
                            "46788052" -> statistikkTilDVH.copy(behandlingId = "48375286", tekniskTid = LocalDateTime.now())
                            "46722760" -> statistikkTilDVH.copy(behandlingId = "48384218", tekniskTid = LocalDateTime.now())
                            "46528912" -> statistikkTilDVH.copy(behandlingId = "48349418", tekniskTid = LocalDateTime.now())
                            "46469557" -> statistikkTilDVH.copy(behandlingId = "48387421", tekniskTid = LocalDateTime.now())
                            "50207416" -> statistikkTilDVH.copy(behandlingId = "48387421", tekniskTid = LocalDateTime.now())
                            "46720470" -> statistikkTilDVH.copy(behandlingId = "48264729", tekniskTid = LocalDateTime.now())
                            "46564404" -> statistikkTilDVH.copy(behandlingId = "48352912", tekniskTid = LocalDateTime.now())
                            "46755697" -> statistikkTilDVH.copy(behandlingId = "48229906", tekniskTid = LocalDateTime.now())
                            "46672505" -> statistikkTilDVH.copy(behandlingId = "48376179", tekniskTid = LocalDateTime.now())
                            "46657747" -> statistikkTilDVH.copy(behandlingId = "48340715", tekniskTid = LocalDateTime.now())
                            "46708313" -> statistikkTilDVH.copy(behandlingId = "48429194", tekniskTid = LocalDateTime.now())
                            "46758244" -> statistikkTilDVH.copy(behandlingId = "48351508", tekniskTid = LocalDateTime.now())
                            "46720318" -> statistikkTilDVH.copy(behandlingId = "48387427", tekniskTid = LocalDateTime.now())
                            "46892245" -> statistikkTilDVH.copy(behandlingId = "49119146", tekniskTid = LocalDateTime.now())
                            "46732995" -> statistikkTilDVH.copy(behandlingId = "48331272", tekniskTid = LocalDateTime.now())
                            "46733096" -> statistikkTilDVH.copy(behandlingId = "48351929", tekniskTid = LocalDateTime.now())
                            "46723668" -> statistikkTilDVH.copy(behandlingId = "48351909", tekniskTid = LocalDateTime.now())
                            "46564144" -> statistikkTilDVH.copy(behandlingId = "48337510", tekniskTid = LocalDateTime.now())
                            "46770194" -> statistikkTilDVH.copy(behandlingId = "48381588", tekniskTid = LocalDateTime.now())
                            "46559948" -> statistikkTilDVH.copy(behandlingId = "48307492", tekniskTid = LocalDateTime.now())
                            "46754655" -> statistikkTilDVH.copy(behandlingId = "48390548", tekniskTid = LocalDateTime.now())
                            "46503148" -> statistikkTilDVH.copy(behandlingId = "48388413", tekniskTid = LocalDateTime.now())
                            "46508593" -> statistikkTilDVH.copy(behandlingId = "48375469", tekniskTid = LocalDateTime.now())
                            "46529112" -> statistikkTilDVH.copy(behandlingId = "48427483", tekniskTid = LocalDateTime.now())
                            "46652368" -> statistikkTilDVH.copy(behandlingId = "48376278", tekniskTid = LocalDateTime.now())
                            "46550274" -> statistikkTilDVH.copy(behandlingId = "48396090", tekniskTid = LocalDateTime.now())
                            "46584947" -> statistikkTilDVH.copy(behandlingId = "48332873", tekniskTid = LocalDateTime.now())
                            "46588969" -> statistikkTilDVH.copy(behandlingId = "48266770", tekniskTid = LocalDateTime.now())
                            "46579161" -> statistikkTilDVH.copy(behandlingId = "48337572", tekniskTid = LocalDateTime.now())
                            "46582558" -> statistikkTilDVH.copy(behandlingId = "48307606", tekniskTid = LocalDateTime.now())
                            "46507315" -> statistikkTilDVH.copy(behandlingId = "48376562", tekniskTid = LocalDateTime.now())
                            "46707085" -> statistikkTilDVH.copy(behandlingId = "48399394", tekniskTid = LocalDateTime.now())
                            "46744452" -> statistikkTilDVH.copy(behandlingId = "48434389", tekniskTid = LocalDateTime.now())
                            "46761799" -> statistikkTilDVH.copy(behandlingId = "48392499", tekniskTid = LocalDateTime.now())
                            "46742261" -> statistikkTilDVH.copy(behandlingId = "48380247", tekniskTid = LocalDateTime.now())
                            "45633446" -> statistikkTilDVH.copy(behandlingId = "48320625", tekniskTid = LocalDateTime.now())
                            "46771478" -> statistikkTilDVH.copy(behandlingId = "48286643", tekniskTid = LocalDateTime.now())
                            "46769914" -> statistikkTilDVH.copy(behandlingId = "48267844", tekniskTid = LocalDateTime.now())
                            "46789547" -> statistikkTilDVH.copy(behandlingId = "48370230", tekniskTid = LocalDateTime.now())
                            "46779446" -> statistikkTilDVH.copy(behandlingId = "48447416", tekniskTid = LocalDateTime.now())
                            "46593410" -> statistikkTilDVH.copy(behandlingId = "48390516", tekniskTid = LocalDateTime.now())
                            "46788858" -> statistikkTilDVH.copy(behandlingId = "48428922", tekniskTid = LocalDateTime.now())
                            "46796600" -> statistikkTilDVH.copy(behandlingId = "48465093", tekniskTid = LocalDateTime.now())
                            "46784922" -> statistikkTilDVH.copy(behandlingId = "48320485", tekniskTid = LocalDateTime.now())
                            "46801350" -> statistikkTilDVH.copy(behandlingId = "48392374", tekniskTid = LocalDateTime.now())
                            "46854149" -> statistikkTilDVH.copy(behandlingId = "48370167", tekniskTid = LocalDateTime.now())
                            "46825905" -> statistikkTilDVH.copy(behandlingId = "48360930", tekniskTid = LocalDateTime.now())
                            "46451608" -> statistikkTilDVH.copy(behandlingId = "48324516", tekniskTid = LocalDateTime.now())
                            "46460525" -> statistikkTilDVH.copy(behandlingId = "48332746", tekniskTid = LocalDateTime.now())
                            "46548210" -> statistikkTilDVH.copy(behandlingId = "48352345", tekniskTid = LocalDateTime.now())
                            "47016335" -> statistikkTilDVH.copy(behandlingId = "48830295", tekniskTid = LocalDateTime.now())
                            "45828371" -> statistikkTilDVH.copy(behandlingId = "48482245", tekniskTid = LocalDateTime.now())
                            "46518270" -> statistikkTilDVH.copy(behandlingId = "48365306", tekniskTid = LocalDateTime.now())
                            "46502500" -> statistikkTilDVH.copy(behandlingId = "49056191", tekniskTid = LocalDateTime.now())
                            "46711260" -> statistikkTilDVH.copy(behandlingId = "48329150", tekniskTid = LocalDateTime.now())
                            "46545914" -> statistikkTilDVH.copy(behandlingId = "48393760", tekniskTid = LocalDateTime.now())
                            "46577545" -> statistikkTilDVH.copy(behandlingId = "48322484", tekniskTid = LocalDateTime.now())
                            "46778923" -> statistikkTilDVH.copy(behandlingId = "48309698", tekniskTid = LocalDateTime.now())
                            "46750369" -> statistikkTilDVH.copy(behandlingId = "48400809", tekniskTid = LocalDateTime.now())
                            "46686395" -> statistikkTilDVH.copy(behandlingId = "48387404", tekniskTid = LocalDateTime.now())
                            "46743151" -> statistikkTilDVH.copy(behandlingId = "48384325", tekniskTid = LocalDateTime.now())
                            "46754407" -> statistikkTilDVH.copy(behandlingId = "48384290", tekniskTid = LocalDateTime.now())
                            "46700936" -> statistikkTilDVH.copy(behandlingId = "48423196", tekniskTid = LocalDateTime.now())
                            "46748798" -> statistikkTilDVH.copy(behandlingId = "48400734", tekniskTid = LocalDateTime.now())
                            "46805455" -> statistikkTilDVH.copy(behandlingId = "48396203", tekniskTid = LocalDateTime.now())
                            "46902603" -> statistikkTilDVH.copy(behandlingId = "48519695", tekniskTid = LocalDateTime.now())
                            "47035719" -> statistikkTilDVH.copy(behandlingId = "48424293", tekniskTid = LocalDateTime.now())
                            "47043764" -> statistikkTilDVH.copy(behandlingId = "48474609", tekniskTid = LocalDateTime.now())
                            "46708932" -> statistikkTilDVH.copy(behandlingId = "48427547", tekniskTid = LocalDateTime.now())
                            "46720589" -> statistikkTilDVH.copy(behandlingId = "48312235", tekniskTid = LocalDateTime.now())
                            "46783773" -> statistikkTilDVH.copy(behandlingId = "48519711", tekniskTid = LocalDateTime.now())
                            "46773086" -> statistikkTilDVH.copy(behandlingId = "48387514", tekniskTid = LocalDateTime.now())
                            "46720308" -> statistikkTilDVH.copy(behandlingId = "48350025", tekniskTid = LocalDateTime.now())
                            "46782912" -> statistikkTilDVH.copy(behandlingId = "48390197", tekniskTid = LocalDateTime.now())
                            "46704596" -> statistikkTilDVH.copy(behandlingId = "48427125", tekniskTid = LocalDateTime.now())
                            "46858248" -> statistikkTilDVH.copy(behandlingId = "48392799", tekniskTid = LocalDateTime.now())
                            "46859023" -> statistikkTilDVH.copy(behandlingId = "48402447", tekniskTid = LocalDateTime.now())
                            "46802184" -> statistikkTilDVH.copy(behandlingId = "48455396", tekniskTid = LocalDateTime.now())
                            "46850302" -> statistikkTilDVH.copy(behandlingId = "48815958", tekniskTid = LocalDateTime.now())
                            "47068776" -> statistikkTilDVH.copy(behandlingId = "48475011", tekniskTid = LocalDateTime.now())
                            "46861295" -> statistikkTilDVH.copy(behandlingId = "48448870", tekniskTid = LocalDateTime.now())
                            "46859088" -> statistikkTilDVH.copy(behandlingId = "49094924", tekniskTid = LocalDateTime.now())
                            "46456959" -> statistikkTilDVH.copy(behandlingId = "48428839", tekniskTid = LocalDateTime.now())
                            "46505568" -> statistikkTilDVH.copy(behandlingId = "48438293", tekniskTid = LocalDateTime.now())
                            "46513648" -> statistikkTilDVH.copy(behandlingId = "48349409", tekniskTid = LocalDateTime.now())
                            "46980646" -> statistikkTilDVH.copy(behandlingId = "48386853", tekniskTid = LocalDateTime.now())
                            "46594149" -> statistikkTilDVH.copy(behandlingId = "48384162", tekniskTid = LocalDateTime.now())
                            "46720894" -> statistikkTilDVH.copy(behandlingId = "48318320", tekniskTid = LocalDateTime.now())
                            "47084271" -> statistikkTilDVH.copy(behandlingId = "48492995", tekniskTid = LocalDateTime.now())
                            "46683007" -> statistikkTilDVH.copy(behandlingId = "48388508", tekniskTid = LocalDateTime.now())
                            "46863249" -> statistikkTilDVH.copy(behandlingId = "48367194", tekniskTid = LocalDateTime.now())
                            "46723210" -> statistikkTilDVH.copy(behandlingId = "48612863", tekniskTid = LocalDateTime.now())
                            "46783551" -> statistikkTilDVH.copy(behandlingId = "48402455", tekniskTid = LocalDateTime.now())
                            "46671094" -> statistikkTilDVH.copy(behandlingId = "48387281", tekniskTid = LocalDateTime.now())
                            "46901957" -> statistikkTilDVH.copy(behandlingId = "48435543", tekniskTid = LocalDateTime.now())
                            "46811111" -> statistikkTilDVH.copy(behandlingId = "48458302", tekniskTid = LocalDateTime.now())
                            "46807985" -> statistikkTilDVH.copy(behandlingId = "48455471", tekniskTid = LocalDateTime.now())
                            "46889374" -> statistikkTilDVH.copy(behandlingId = "48402421", tekniskTid = LocalDateTime.now())
                            "47100617" -> statistikkTilDVH.copy(behandlingId = "48524800", tekniskTid = LocalDateTime.now())
                            "46838500" -> statistikkTilDVH.copy(behandlingId = "48402303", tekniskTid = LocalDateTime.now())
                            "46731794" -> statistikkTilDVH.copy(behandlingId = "48433436", tekniskTid = LocalDateTime.now())
                            "46801505" -> statistikkTilDVH.copy(behandlingId = "48467860", tekniskTid = LocalDateTime.now())
                            "46770301" -> statistikkTilDVH.copy(behandlingId = "48423467", tekniskTid = LocalDateTime.now())
                            "46813316" -> statistikkTilDVH.copy(behandlingId = "48610262", tekniskTid = LocalDateTime.now())
                            "46845343" -> statistikkTilDVH.copy(behandlingId = "48380530", tekniskTid = LocalDateTime.now())
                            "46801090" -> statistikkTilDVH.copy(behandlingId = "48470498", tekniskTid = LocalDateTime.now())
                            "46843699" -> statistikkTilDVH.copy(behandlingId = "48441299", tekniskTid = LocalDateTime.now())
                            "46783259" -> statistikkTilDVH.copy(behandlingId = "48433897", tekniskTid = LocalDateTime.now())
                            "46864243" -> statistikkTilDVH.copy(behandlingId = "48399323", tekniskTid = LocalDateTime.now())
                            "46772947" -> statistikkTilDVH.copy(behandlingId = "48427140", tekniskTid = LocalDateTime.now())
                            "46795204" -> statistikkTilDVH.copy(behandlingId = "48414285", tekniskTid = LocalDateTime.now())
                            "46800657" -> statistikkTilDVH.copy(behandlingId = "48383308", tekniskTid = LocalDateTime.now())
                            "46814081" -> statistikkTilDVH.copy(behandlingId = "48520324", tekniskTid = LocalDateTime.now())
                            "46716350" -> statistikkTilDVH.copy(behandlingId = "48612157", tekniskTid = LocalDateTime.now())
                            "45750659" -> statistikkTilDVH.copy(behandlingId = "48363371", tekniskTid = LocalDateTime.now())
                            "46881997" -> statistikkTilDVH.copy(behandlingId = "48453112", tekniskTid = LocalDateTime.now())
                            "46947356" -> statistikkTilDVH.copy(behandlingId = "49095944", tekniskTid = LocalDateTime.now())
                            "46899832" -> statistikkTilDVH.copy(behandlingId = "48365949", tekniskTid = LocalDateTime.now())
                            "46489578" -> statistikkTilDVH.copy(behandlingId = "48441009", tekniskTid = LocalDateTime.now())
                            "46938974" -> statistikkTilDVH.copy(behandlingId = "48427351", tekniskTid = LocalDateTime.now())
                            "46962994" -> statistikkTilDVH.copy(behandlingId = "48420757", tekniskTid = LocalDateTime.now())
                            "46797303" -> statistikkTilDVH.copy(behandlingId = "48489991", tekniskTid = LocalDateTime.now())
                            "46797431" -> statistikkTilDVH.copy(behandlingId = "48196626", tekniskTid = LocalDateTime.now())
                            "46806355" -> statistikkTilDVH.copy(behandlingId = "48512483", tekniskTid = LocalDateTime.now())
                            "46985056" -> statistikkTilDVH.copy(behandlingId = "48396241", tekniskTid = LocalDateTime.now())
                            "46802594" -> statistikkTilDVH.copy(behandlingId = "48400675", tekniskTid = LocalDateTime.now())
                            "46816674" -> statistikkTilDVH.copy(behandlingId = "48392461", tekniskTid = LocalDateTime.now())
                            "46810575" -> statistikkTilDVH.copy(behandlingId = "48422643", tekniskTid = LocalDateTime.now())
                            "46855748" -> statistikkTilDVH.copy(behandlingId = "48523799", tekniskTid = LocalDateTime.now())
                            "46830395" -> statistikkTilDVH.copy(behandlingId = "48433899", tekniskTid = LocalDateTime.now())
                            "46849248" -> statistikkTilDVH.copy(behandlingId = "48492174", tekniskTid = LocalDateTime.now())
                            "46912114" -> statistikkTilDVH.copy(behandlingId = "48453294", tekniskTid = LocalDateTime.now())
                            "46901997" -> statistikkTilDVH.copy(behandlingId = "48512350", tekniskTid = LocalDateTime.now())
                            "46948526" -> statistikkTilDVH.copy(behandlingId = "48504639", tekniskTid = LocalDateTime.now())
                            "47168597" -> statistikkTilDVH.copy(behandlingId = "48482993", tekniskTid = LocalDateTime.now())
                            "46922544" -> statistikkTilDVH.copy(behandlingId = "48610234", tekniskTid = LocalDateTime.now())
                            "46920394" -> statistikkTilDVH.copy(behandlingId = "48526819", tekniskTid = LocalDateTime.now())
                            "46959405" -> statistikkTilDVH.copy(behandlingId = "48432034", tekniskTid = LocalDateTime.now())
                            "47003843" -> statistikkTilDVH.copy(behandlingId = "48529161", tekniskTid = LocalDateTime.now())
                            "46838446" -> statistikkTilDVH.copy(behandlingId = "48824597", tekniskTid = LocalDateTime.now())
                            "46835612" -> statistikkTilDVH.copy(behandlingId = "48402496", tekniskTid = LocalDateTime.now())
                            "46886094" -> statistikkTilDVH.copy(behandlingId = "48522264", tekniskTid = LocalDateTime.now())
                            "46910919" -> statistikkTilDVH.copy(behandlingId = "48421453", tekniskTid = LocalDateTime.now())
                            "46858003" -> statistikkTilDVH.copy(behandlingId = "48466496", tekniskTid = LocalDateTime.now())
                            "46975248" -> statistikkTilDVH.copy(behandlingId = "48497532", tekniskTid = LocalDateTime.now())
                            "46938865" -> statistikkTilDVH.copy(behandlingId = "48468478", tekniskTid = LocalDateTime.now())
                            "47239998" -> statistikkTilDVH.copy(behandlingId = "48495157", tekniskTid = LocalDateTime.now())
                            "47040920" -> statistikkTilDVH.copy(behandlingId = "48644683", tekniskTid = LocalDateTime.now())
                            "46798218" -> statistikkTilDVH.copy(behandlingId = "48492860", tekniskTid = LocalDateTime.now())
                            "46932448" -> statistikkTilDVH.copy(behandlingId = "48463076", tekniskTid = LocalDateTime.now())
                            "47027845" -> statistikkTilDVH.copy(behandlingId = "48448855", tekniskTid = LocalDateTime.now())
                            "47043396" -> statistikkTilDVH.copy(behandlingId = "48440271", tekniskTid = LocalDateTime.now())
                            "47244847" -> statistikkTilDVH.copy(behandlingId = "48399681", tekniskTid = LocalDateTime.now())
                            "47034447" -> statistikkTilDVH.copy(behandlingId = "48453357", tekniskTid = LocalDateTime.now())
                            "47102181" -> statistikkTilDVH.copy(behandlingId = "48013585", tekniskTid = LocalDateTime.now())
                            "46848155" -> statistikkTilDVH.copy(behandlingId = "48421284", tekniskTid = LocalDateTime.now())
                            "46794370" -> statistikkTilDVH.copy(behandlingId = "48526856", tekniskTid = LocalDateTime.now())
                            "46946249" -> statistikkTilDVH.copy(behandlingId = "48523053", tekniskTid = LocalDateTime.now())
                            "47256599" -> statistikkTilDVH.copy(behandlingId = "48529169", tekniskTid = LocalDateTime.now())
                            "46946065" -> statistikkTilDVH.copy(behandlingId = "48512716", tekniskTid = LocalDateTime.now())
                            "46938875" -> statistikkTilDVH.copy(behandlingId = "48623445", tekniskTid = LocalDateTime.now())
                            "46836416" -> statistikkTilDVH.copy(behandlingId = "48429723", tekniskTid = LocalDateTime.now())
                            "46899608" -> statistikkTilDVH.copy(behandlingId = "48458558", tekniskTid = LocalDateTime.now())
                            "46952772" -> statistikkTilDVH.copy(behandlingId = "48492217", tekniskTid = LocalDateTime.now())
                            "47259578" -> statistikkTilDVH.copy(behandlingId = "49083228", tekniskTid = LocalDateTime.now())
                            "47173498" -> statistikkTilDVH.copy(behandlingId = "48619929", tekniskTid = LocalDateTime.now())
                            "46887744" -> statistikkTilDVH.copy(behandlingId = "48535797", tekniskTid = LocalDateTime.now())
                            "46895280" -> statistikkTilDVH.copy(behandlingId = "48653900", tekniskTid = LocalDateTime.now())
                            "46816727" -> statistikkTilDVH.copy(behandlingId = "48646442", tekniskTid = LocalDateTime.now())
                            "46943003" -> statistikkTilDVH.copy(behandlingId = "48640954", tekniskTid = LocalDateTime.now())
                            "46995557" -> statistikkTilDVH.copy(behandlingId = "48524847", tekniskTid = LocalDateTime.now())
                            "47297369" -> statistikkTilDVH.copy(behandlingId = "49085812", tekniskTid = LocalDateTime.now())
                            "46923758" -> statistikkTilDVH.copy(behandlingId = "48513294", tekniskTid = LocalDateTime.now())
                            "47221545" -> statistikkTilDVH.copy(behandlingId = "49087838", tekniskTid = LocalDateTime.now())
                            "46989951" -> statistikkTilDVH.copy(behandlingId = "48402328", tekniskTid = LocalDateTime.now())
                            "46968343" -> statistikkTilDVH.copy(behandlingId = "48620989", tekniskTid = LocalDateTime.now())
                            "46885801" -> statistikkTilDVH.copy(behandlingId = "48610328", tekniskTid = LocalDateTime.now())
                            "46886660" -> statistikkTilDVH.copy(behandlingId = "48656926", tekniskTid = LocalDateTime.now())
                            "46896100" -> statistikkTilDVH.copy(behandlingId = "48801152", tekniskTid = LocalDateTime.now())
                            "46898645" -> statistikkTilDVH.copy(behandlingId = "48375460", tekniskTid = LocalDateTime.now())
                            "46899099" -> statistikkTilDVH.copy(behandlingId = "48535330", tekniskTid = LocalDateTime.now())
                            "46950671" -> statistikkTilDVH.copy(behandlingId = "48644773", tekniskTid = LocalDateTime.now())
                            "47559498" -> statistikkTilDVH.copy(behandlingId = "48499847", tekniskTid = LocalDateTime.now())
                            "47566709" -> statistikkTilDVH.copy(behandlingId = "49092441", tekniskTid = LocalDateTime.now())
                            "47165754" -> statistikkTilDVH.copy(behandlingId = "48482900", tekniskTid = LocalDateTime.now())
                            "47201093" -> statistikkTilDVH.copy(behandlingId = "48621594", tekniskTid = LocalDateTime.now())
                            "46928144" -> statistikkTilDVH.copy(behandlingId = "48506680", tekniskTid = LocalDateTime.now())
                            "47558821" -> statistikkTilDVH.copy(behandlingId = "48619495", tekniskTid = LocalDateTime.now())
                            "46996547" -> statistikkTilDVH.copy(behandlingId = "48644625", tekniskTid = LocalDateTime.now())
                            "46943562" -> statistikkTilDVH.copy(behandlingId = "49071946", tekniskTid = LocalDateTime.now())
                            "47044394" -> statistikkTilDVH.copy(behandlingId = "48612248", tekniskTid = LocalDateTime.now())
                            "46938785" -> statistikkTilDVH.copy(behandlingId = "48452162", tekniskTid = LocalDateTime.now())
                            "46358499" -> statistikkTilDVH.copy(behandlingId = "48811968", tekniskTid = LocalDateTime.now())
                            "47289753" -> statistikkTilDVH.copy(behandlingId = "48520309", tekniskTid = LocalDateTime.now())
                            "47572395" -> statistikkTilDVH.copy(behandlingId = "48448952", tekniskTid = LocalDateTime.now())
                            "46962743" -> statistikkTilDVH.copy(behandlingId = "48825498", tekniskTid = LocalDateTime.now())
                            "46977997" -> statistikkTilDVH.copy(behandlingId = "48375596", tekniskTid = LocalDateTime.now())
                            "46986070" -> statistikkTilDVH.copy(behandlingId = "48654063", tekniskTid = LocalDateTime.now())
                            "46986044" -> statistikkTilDVH.copy(behandlingId = "48536093", tekniskTid = LocalDateTime.now())
                            "47577387" -> statistikkTilDVH.copy(behandlingId = "48611952", tekniskTid = LocalDateTime.now())
                            "47011700" -> statistikkTilDVH.copy(behandlingId = "48491852", tekniskTid = LocalDateTime.now())
                            "47008261" -> statistikkTilDVH.copy(behandlingId = "48535281", tekniskTid = LocalDateTime.now())
                            "47022247" -> statistikkTilDVH.copy(behandlingId = "48628462", tekniskTid = LocalDateTime.now())
                            "47032543" -> statistikkTilDVH.copy(behandlingId = "48408251", tekniskTid = LocalDateTime.now())
                            "47242888" -> statistikkTilDVH.copy(behandlingId = "48638345", tekniskTid = LocalDateTime.now())
                            "47559193" -> statistikkTilDVH.copy(behandlingId = "48458206", tekniskTid = LocalDateTime.now())
                            "46969106" -> statistikkTilDVH.copy(behandlingId = "48512806", tekniskTid = LocalDateTime.now())
                            "47049747" -> statistikkTilDVH.copy(behandlingId = "49085742", tekniskTid = LocalDateTime.now())
                            "47160647" -> statistikkTilDVH.copy(behandlingId = "48445443", tekniskTid = LocalDateTime.now())
                            "47016007" -> statistikkTilDVH.copy(behandlingId = "48612237", tekniskTid = LocalDateTime.now())
                            "47001418" -> statistikkTilDVH.copy(behandlingId = "48638156", tekniskTid = LocalDateTime.now())
                            "47031180" -> statistikkTilDVH.copy(behandlingId = "48519703", tekniskTid = LocalDateTime.now())
                            "47553570" -> statistikkTilDVH.copy(behandlingId = "49058714", tekniskTid = LocalDateTime.now())
                            "47041218" -> statistikkTilDVH.copy(behandlingId = "48308146", tekniskTid = LocalDateTime.now())
                            "47258549" -> statistikkTilDVH.copy(behandlingId = "48308243", tekniskTid = LocalDateTime.now())
                            "47248348" -> statistikkTilDVH.copy(behandlingId = "48308345", tekniskTid = LocalDateTime.now())
                            "47258702" -> statistikkTilDVH.copy(behandlingId = "48310751", tekniskTid = LocalDateTime.now())
                            "47255134" -> statistikkTilDVH.copy(behandlingId = "48644749", tekniskTid = LocalDateTime.now())
                            "47045093" -> statistikkTilDVH.copy(behandlingId = "48830407", tekniskTid = LocalDateTime.now())
                            "46937867" -> statistikkTilDVH.copy(behandlingId = "49184083", tekniskTid = LocalDateTime.now())
                            "46934845" -> statistikkTilDVH.copy(behandlingId = "48628839", tekniskTid = LocalDateTime.now())
                            "47073445" -> statistikkTilDVH.copy(behandlingId = "48529177", tekniskTid = LocalDateTime.now())
                            "47605794" -> statistikkTilDVH.copy(behandlingId = "48400542", tekniskTid = LocalDateTime.now())
                            "46990844" -> statistikkTilDVH.copy(behandlingId = "48632450", tekniskTid = LocalDateTime.now())
                            "47670295" -> statistikkTilDVH.copy(behandlingId = "48461117", tekniskTid = LocalDateTime.now())
                            "47066500" -> statistikkTilDVH.copy(behandlingId = "48644676", tekniskTid = LocalDateTime.now())
                            "47241293" -> statistikkTilDVH.copy(behandlingId = "48644656", tekniskTid = LocalDateTime.now())
                            "46987398" -> statistikkTilDVH.copy(behandlingId = "48629417", tekniskTid = LocalDateTime.now())
                            "46989974" -> statistikkTilDVH.copy(behandlingId = "48496128", tekniskTid = LocalDateTime.now())
                            "47251750" -> statistikkTilDVH.copy(behandlingId = "49197943", tekniskTid = LocalDateTime.now())
                            "46746167" -> statistikkTilDVH.copy(behandlingId = "48830353", tekniskTid = LocalDateTime.now())
                            "47562394" -> statistikkTilDVH.copy(behandlingId = "49065261", tekniskTid = LocalDateTime.now())
                            "47087969" -> statistikkTilDVH.copy(behandlingId = "49120867", tekniskTid = LocalDateTime.now())
                            "47071084" -> statistikkTilDVH.copy(behandlingId = "48622066", tekniskTid = LocalDateTime.now())
                            "47085167" -> statistikkTilDVH.copy(behandlingId = "48520356", tekniskTid = LocalDateTime.now())
                            "47081957" -> statistikkTilDVH.copy(behandlingId = "49087829", tekniskTid = LocalDateTime.now())
                            "46977194" -> statistikkTilDVH.copy(behandlingId = "48431354", tekniskTid = LocalDateTime.now())
                            "47043443" -> statistikkTilDVH.copy(behandlingId = "48418998", tekniskTid = LocalDateTime.now())
                            "47016267" -> statistikkTilDVH.copy(behandlingId = "49088953", tekniskTid = LocalDateTime.now())
                            "47256145" -> statistikkTilDVH.copy(behandlingId = "48610214", tekniskTid = LocalDateTime.now())
                            "47038598" -> statistikkTilDVH.copy(behandlingId = "48455354", tekniskTid = LocalDateTime.now())
                            "47295746" -> statistikkTilDVH.copy(behandlingId = "48482875", tekniskTid = LocalDateTime.now())
                            "47568701" -> statistikkTilDVH.copy(behandlingId = "49079790", tekniskTid = LocalDateTime.now())
                            "47244946" -> statistikkTilDVH.copy(behandlingId = "49094684", tekniskTid = LocalDateTime.now())
                            "47160250" -> statistikkTilDVH.copy(behandlingId = "48453550", tekniskTid = LocalDateTime.now())
                            "47167043" -> statistikkTilDVH.copy(behandlingId = "49085905", tekniskTid = LocalDateTime.now())
                            "47602197" -> statistikkTilDVH.copy(behandlingId = "49079733", tekniskTid = LocalDateTime.now())
                            "47601338" -> statistikkTilDVH.copy(behandlingId = "48424301", tekniskTid = LocalDateTime.now())
                            "47017995" -> statistikkTilDVH.copy(behandlingId = "49091521", tekniskTid = LocalDateTime.now())
                            "47801333" -> statistikkTilDVH.copy(behandlingId = "48431753", tekniskTid = LocalDateTime.now())
                            "47088560" -> statistikkTilDVH.copy(behandlingId = "48463173", tekniskTid = LocalDateTime.now())
                            "47692106" -> statistikkTilDVH.copy(behandlingId = "48814573", tekniskTid = LocalDateTime.now())
                            "47562451" -> statistikkTilDVH.copy(behandlingId = "49053932", tekniskTid = LocalDateTime.now())
                            "47152250" -> statistikkTilDVH.copy(behandlingId = "48816048", tekniskTid = LocalDateTime.now())
                            "47570849" -> statistikkTilDVH.copy(behandlingId = "48617901", tekniskTid = LocalDateTime.now())
                            "47601392" -> statistikkTilDVH.copy(behandlingId = "48472627", tekniskTid = LocalDateTime.now())
                            "46918446" -> statistikkTilDVH.copy(behandlingId = "49075910", tekniskTid = LocalDateTime.now())
                            "47220532" -> statistikkTilDVH.copy(behandlingId = "48524936", tekniskTid = LocalDateTime.now())
                            "47154746" -> statistikkTilDVH.copy(behandlingId = "48489500", tekniskTid = LocalDateTime.now())
                            "47195531" -> statistikkTilDVH.copy(behandlingId = "48438404", tekniskTid = LocalDateTime.now())
                            "47044261" -> statistikkTilDVH.copy(behandlingId = "48830240", tekniskTid = LocalDateTime.now())
                            "47819233" -> statistikkTilDVH.copy(behandlingId = "48482052", tekniskTid = LocalDateTime.now())
                            "47046150" -> statistikkTilDVH.copy(behandlingId = "49084613", tekniskTid = LocalDateTime.now())
                            "47993335" -> statistikkTilDVH.copy(behandlingId = "48629368", tekniskTid = LocalDateTime.now())
                            "47289907" -> statistikkTilDVH.copy(behandlingId = "48827256", tekniskTid = LocalDateTime.now())
                            "47979939" -> statistikkTilDVH.copy(behandlingId = "48823407", tekniskTid = LocalDateTime.now())
                            "47166043" -> statistikkTilDVH.copy(behandlingId = "49072667", tekniskTid = LocalDateTime.now())
                            "47054645" -> statistikkTilDVH.copy(behandlingId = "49106545", tekniskTid = LocalDateTime.now())
                            "47586521" -> statistikkTilDVH.copy(behandlingId = "48297797", tekniskTid = LocalDateTime.now())
                            "47201694" -> statistikkTilDVH.copy(behandlingId = "48463084", tekniskTid = LocalDateTime.now())
                            "47787282" -> statistikkTilDVH.copy(behandlingId = "49120937", tekniskTid = LocalDateTime.now())
                            "48008908" -> statistikkTilDVH.copy(behandlingId = "48634058", tekniskTid = LocalDateTime.now())
                            "47241695" -> statistikkTilDVH.copy(behandlingId = "49090918", tekniskTid = LocalDateTime.now())
                            "47989602" -> statistikkTilDVH.copy(behandlingId = "49094981", tekniskTid = LocalDateTime.now())
                            "47098606" -> statistikkTilDVH.copy(behandlingId = "48645047", tekniskTid = LocalDateTime.now())
                            "47152532" -> statistikkTilDVH.copy(behandlingId = "48647147", tekniskTid = LocalDateTime.now())
                            "47216960" -> statistikkTilDVH.copy(behandlingId = "48827470", tekniskTid = LocalDateTime.now())
                            "47191146" -> statistikkTilDVH.copy(behandlingId = "49089800", tekniskTid = LocalDateTime.now())
                            "48189961" -> statistikkTilDVH.copy(behandlingId = "49122314", tekniskTid = LocalDateTime.now())
                            "47158669" -> statistikkTilDVH.copy(behandlingId = "48537454", tekniskTid = LocalDateTime.now())
                            "48001326" -> statistikkTilDVH.copy(behandlingId = "48353115", tekniskTid = LocalDateTime.now())
                            "47586744" -> statistikkTilDVH.copy(behandlingId = "48474966", tekniskTid = LocalDateTime.now())
                            "47218893" -> statistikkTilDVH.copy(behandlingId = "48465119", tekniskTid = LocalDateTime.now())
                            "47664657" -> statistikkTilDVH.copy(behandlingId = "49265894", tekniskTid = LocalDateTime.now())
                            "47248356" -> statistikkTilDVH.copy(behandlingId = "49235025", tekniskTid = LocalDateTime.now())
                            "47294648" -> statistikkTilDVH.copy(behandlingId = "48612266", tekniskTid = LocalDateTime.now())
                            "47284317" -> statistikkTilDVH.copy(behandlingId = "49199199", tekniskTid = LocalDateTime.now())
                            "48227833" -> statistikkTilDVH.copy(behandlingId = "48490651", tekniskTid = LocalDateTime.now())
                            "48196700" -> statistikkTilDVH.copy(behandlingId = "49080181", tekniskTid = LocalDateTime.now())
                            "48241280" -> statistikkTilDVH.copy(behandlingId = "48815917", tekniskTid = LocalDateTime.now())
                            "48233545" -> statistikkTilDVH.copy(behandlingId = "49089757", tekniskTid = LocalDateTime.now())
                            "47240016" -> statistikkTilDVH.copy(behandlingId = "48529674", tekniskTid = LocalDateTime.now())
                            "48244008" -> statistikkTilDVH.copy(behandlingId = "49091568", tekniskTid = LocalDateTime.now())
                            "48191399" -> statistikkTilDVH.copy(behandlingId = "49131910", tekniskTid = LocalDateTime.now())
                            "48188636" -> statistikkTilDVH.copy(behandlingId = "48482817", tekniskTid = LocalDateTime.now())
                            "48263052" -> statistikkTilDVH.copy(behandlingId = "49126461", tekniskTid = LocalDateTime.now())
                            "48182545" -> statistikkTilDVH.copy(behandlingId = "49076497", tekniskTid = LocalDateTime.now())
                            "47786550" -> statistikkTilDVH.copy(behandlingId = "49113755", tekniskTid = LocalDateTime.now())
                            "48227901" -> statistikkTilDVH.copy(behandlingId = "49193075", tekniskTid = LocalDateTime.now())
                            "48214353" -> statistikkTilDVH.copy(behandlingId = "48654219", tekniskTid = LocalDateTime.now())
                            "46950703" -> statistikkTilDVH.copy(behandlingId = "49080258", tekniskTid = LocalDateTime.now())
                            "48258243" -> statistikkTilDVH.copy(behandlingId = "48612658", tekniskTid = LocalDateTime.now())
                            "48270474" -> statistikkTilDVH.copy(behandlingId = "48529661", tekniskTid = LocalDateTime.now())
                            "47801360" -> statistikkTilDVH.copy(behandlingId = "49121103", tekniskTid = LocalDateTime.now())
                            "48196942" -> statistikkTilDVH.copy(behandlingId = "49057209", tekniskTid = LocalDateTime.now())
                            "48279492" -> statistikkTilDVH.copy(behandlingId = "49153695", tekniskTid = LocalDateTime.now())
                            "48269131" -> statistikkTilDVH.copy(behandlingId = "48420902", tekniskTid = LocalDateTime.now())
                            "48168188" -> statistikkTilDVH.copy(behandlingId = "48452433", tekniskTid = LocalDateTime.now())
                            "48193697" -> statistikkTilDVH.copy(behandlingId = "48512474", tekniskTid = LocalDateTime.now())
                            "47608043" -> statistikkTilDVH.copy(behandlingId = "48818706", tekniskTid = LocalDateTime.now())
                            "48281917" -> statistikkTilDVH.copy(behandlingId = "48429200", tekniskTid = LocalDateTime.now())
                            "47294366" -> statistikkTilDVH.copy(behandlingId = "49071361", tekniskTid = LocalDateTime.now())
                            "48288254" -> statistikkTilDVH.copy(behandlingId = "48430954", tekniskTid = LocalDateTime.now())
                            "48288469" -> statistikkTilDVH.copy(behandlingId = "49222875", tekniskTid = LocalDateTime.now())
                            "48226158" -> statistikkTilDVH.copy(behandlingId = "49132841", tekniskTid = LocalDateTime.now())
                            "48176859" -> statistikkTilDVH.copy(behandlingId = "49155293", tekniskTid = LocalDateTime.now())
                            "48274803" -> statistikkTilDVH.copy(behandlingId = "49107233", tekniskTid = LocalDateTime.now())
                            "48253593" -> statistikkTilDVH.copy(behandlingId = "48825968", tekniskTid = LocalDateTime.now())
                            "48004357" -> statistikkTilDVH.copy(behandlingId = "49091512", tekniskTid = LocalDateTime.now())
                            "46919444" -> statistikkTilDVH.copy(behandlingId = "49094733", tekniskTid = LocalDateTime.now())
                            "48230643" -> statistikkTilDVH.copy(behandlingId = "49269298", tekniskTid = LocalDateTime.now())
                            "48226598" -> statistikkTilDVH.copy(behandlingId = "48610854", tekniskTid = LocalDateTime.now())
                            "47795696" -> statistikkTilDVH.copy(behandlingId = "48434679", tekniskTid = LocalDateTime.now())
                            "48312447" -> statistikkTilDVH.copy(behandlingId = "48416308", tekniskTid = LocalDateTime.now())
                            "48262947" -> statistikkTilDVH.copy(behandlingId = "49222840", tekniskTid = LocalDateTime.now())
                            "48302376" -> statistikkTilDVH.copy(behandlingId = "48470054", tekniskTid = LocalDateTime.now())
                            "48233656" -> statistikkTilDVH.copy(behandlingId = "49057164", tekniskTid = LocalDateTime.now())
                            "47801636" -> statistikkTilDVH.copy(behandlingId = "48619399", tekniskTid = LocalDateTime.now())
                            "48276042" -> statistikkTilDVH.copy(behandlingId = "48656644", tekniskTid = LocalDateTime.now())
                            "48303043" -> statistikkTilDVH.copy(behandlingId = "48470525", tekniskTid = LocalDateTime.now())
                            "48336832" -> statistikkTilDVH.copy(behandlingId = "48499853", tekniskTid = LocalDateTime.now())
                            "48339240" -> statistikkTilDVH.copy(behandlingId = "49421206", tekniskTid = LocalDateTime.now())
                            "48238494" -> statistikkTilDVH.copy(behandlingId = "49081629", tekniskTid = LocalDateTime.now())
                            "48005240" -> statistikkTilDVH.copy(behandlingId = "49088600", tekniskTid = LocalDateTime.now())
                            "48363701" -> statistikkTilDVH.copy(behandlingId = "49198068", tekniskTid = LocalDateTime.now())
                            "48349557" -> statistikkTilDVH.copy(behandlingId = "48619939", tekniskTid = LocalDateTime.now())
                            "48354050" -> statistikkTilDVH.copy(behandlingId = "48497572", tekniskTid = LocalDateTime.now())
                            "48353476" -> statistikkTilDVH.copy(behandlingId = "48810561", tekniskTid = LocalDateTime.now())
                            "48365305" -> statistikkTilDVH.copy(behandlingId = "48499788", tekniskTid = LocalDateTime.now())
                            "48357543" -> statistikkTilDVH.copy(behandlingId = "48465147", tekniskTid = LocalDateTime.now())
                            "48360752" -> statistikkTilDVH.copy(behandlingId = "49106051", tekniskTid = LocalDateTime.now())
                            "48287563" -> statistikkTilDVH.copy(behandlingId = "49313573", tekniskTid = LocalDateTime.now())
                            "48360071" -> statistikkTilDVH.copy(behandlingId = "48644402", tekniskTid = LocalDateTime.now())
                            "48375336" -> statistikkTilDVH.copy(behandlingId = "49091981", tekniskTid = LocalDateTime.now())
                            "48387288" -> statistikkTilDVH.copy(behandlingId = "49080877", tekniskTid = LocalDateTime.now())
                            "48388240" -> statistikkTilDVH.copy(behandlingId = "48537577", tekniskTid = LocalDateTime.now())
                            "48394373" -> statistikkTilDVH.copy(behandlingId = "48495102", tekniskTid = LocalDateTime.now())
                            "48383101" -> statistikkTilDVH.copy(behandlingId = "48526844", tekniskTid = LocalDateTime.now())
                            "48396081" -> statistikkTilDVH.copy(behandlingId = "48818548", tekniskTid = LocalDateTime.now())
                            "48337096" -> statistikkTilDVH.copy(behandlingId = "49083523", tekniskTid = LocalDateTime.now())
                            "48396748" -> statistikkTilDVH.copy(behandlingId = "49093039", tekniskTid = LocalDateTime.now())
                            "48349762" -> statistikkTilDVH.copy(behandlingId = "48622051", tekniskTid = LocalDateTime.now())
                            "48401360" -> statistikkTilDVH.copy(behandlingId = "48653847", tekniskTid = LocalDateTime.now())
                            "48339454" -> statistikkTilDVH.copy(behandlingId = "49120103", tekniskTid = LocalDateTime.now())
                            "48414972" -> statistikkTilDVH.copy(behandlingId = "48823425", tekniskTid = LocalDateTime.now())
                            "48438758" -> statistikkTilDVH.copy(behandlingId = "49250143", tekniskTid = LocalDateTime.now())
                            "48230093" -> statistikkTilDVH.copy(behandlingId = "49230450", tekniskTid = LocalDateTime.now())
                            "48444011" -> statistikkTilDVH.copy(behandlingId = "49188193", tekniskTid = LocalDateTime.now())
                            "48396065" -> statistikkTilDVH.copy(behandlingId = "48627596", tekniskTid = LocalDateTime.now())
                            "48280570" -> statistikkTilDVH.copy(behandlingId = "49139746", tekniskTid = LocalDateTime.now())
                            "48468466" -> statistikkTilDVH.copy(behandlingId = "49121055", tekniskTid = LocalDateTime.now())
                            "48167629" -> statistikkTilDVH.copy(behandlingId = "48627783", tekniskTid = LocalDateTime.now())
                            "48469299" -> statistikkTilDVH.copy(behandlingId = "49393998", tekniskTid = LocalDateTime.now())
                            "48458593" -> statistikkTilDVH.copy(behandlingId = "48819834", tekniskTid = LocalDateTime.now())
                            "48494125" -> statistikkTilDVH.copy(behandlingId = "49171510", tekniskTid = LocalDateTime.now())
                            "48420299" -> statistikkTilDVH.copy(behandlingId = "49311244", tekniskTid = LocalDateTime.now())
                            "48617203" -> statistikkTilDVH.copy(behandlingId = "48644826", tekniskTid = LocalDateTime.now())
                            "48497743" -> statistikkTilDVH.copy(behandlingId = "49228334", tekniskTid = LocalDateTime.now())
                            "48632232" -> statistikkTilDVH.copy(behandlingId = "49221848", tekniskTid = LocalDateTime.now())
                            "48402350" -> statistikkTilDVH.copy(behandlingId = "49064939", tekniskTid = LocalDateTime.now())
                            "48827169" -> statistikkTilDVH.copy(behandlingId = "49177713", tekniskTid = LocalDateTime.now())
                            "48830398" -> statistikkTilDVH.copy(behandlingId = "49420031", tekniskTid = LocalDateTime.now())
                            "49052810" -> statistikkTilDVH.copy(behandlingId = "49300100", tekniskTid = LocalDateTime.now())
                            "48654051" -> statistikkTilDVH.copy(behandlingId = "49230396", tekniskTid = LocalDateTime.now())
                            "49065397" -> statistikkTilDVH.copy(behandlingId = "49147343", tekniskTid = LocalDateTime.now())
                            "48642794" -> statistikkTilDVH.copy(behandlingId = "49443799", tekniskTid = LocalDateTime.now())
                            "48440076" -> statistikkTilDVH.copy(behandlingId = "49483934", tekniskTid = LocalDateTime.now())
                            else -> throw RuntimeException("Unknown behandlingId: ${statistikkTilDVH.behandlingId}")
                        }

                        preparedStatement.setString(1, ourJacksonObjectMapper().writeValueAsString(modifiedVersion))
                        preparedStatement.setObject(2,"IKKE_SENDT")
                        preparedStatement.setObject(3, kafkaEventId)

                        preparedStatement.executeUpdate()
                    }

                }
        }
    }
}