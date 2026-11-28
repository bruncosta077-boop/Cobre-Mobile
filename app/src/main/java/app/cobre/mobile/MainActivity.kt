package app.cobre.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.media.AudioManager
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

internal val Bg=Color(0xFF10100F); internal val Panel=Color(0xFF191816); internal val CardBg=Color(0xFF211F1D)
internal val Line=Color(0xFF302C28); internal val Tx=Color(0xFFECE6DF); internal val Mu=Color(0xFF9B938B)
internal val Green=Color(0xFF7EB783)
internal enum class Page { Library, Queue, Collections, Account, Settings }
internal enum class LibraryTab { Albums, Artists, Tracks }
internal enum class LibrarySource(val label:String) { All("Tudo"), Phone("Celular"), Desktop("Computador"), Downloaded("Baixadas") }
internal class IdentityKey(vararg private val values:Any?){
    override fun equals(other:Any?)=other is IdentityKey&&values.size==other.values.size&&values.indices.all{values[it]===other.values[it]}
    override fun hashCode()=values.fold(1){hash,value->31*hash+System.identityHashCode(value)}
}
data class AccountUi(val signedIn:Boolean=false,val email:String="",val devices:List<CobreDevice> = emptyList(),val busy:Boolean=false,val message:String="",val error:Boolean=false,val serverReady:Boolean?=null,val sessionSaved:Boolean=false)

class MainActivity:ComponentActivity(){
    private lateinit var player:PlayerController
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState); player=PlayerController(applicationContext)
        setContent {
            val settings=remember{MobileSettings(applicationContext)}
            MaterialTheme(colorScheme=darkColorScheme(primary=Color(settings.accentPrimary.toLong() and 0xFFFFFFFFL),onPrimary=Color.White,secondary=Color(settings.accentSecondary.toLong() and 0xFFFFFFFFL),background=Bg,onBackground=Tx,surface=Panel,onSurface=Tx,surfaceVariant=CardBg,onSurfaceVariant=Mu)){
                Surface(Modifier.fillMaxSize(),color=Bg){App(player,settings)}
            }
        }
    }
    override fun onDestroy(){player.release();super.onDestroy()}
}

@Composable private fun App(player:PlayerController,settings:MobileSettings){
     val context=LocalContext.current; val library=remember{LocalLibrary(context)}; val collections=remember{LibraryCollections(context)}; val offlineStore=remember{OfflineTrackStore(context)}; val accountRepo=remember{CobreIdRepository(context)};val coverArtwork=remember{CoverArtworkRepository(context)}
    val permission=if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED)}
    val askAudio=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted=it}
    val askNotifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    var folderUri by remember{mutableStateOf(settings.folderUri)}
    val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null){runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)};settings.setFolder(uri.toString());folderUri=uri.toString()}}
    var localTracks by remember{mutableStateOf<List<Track>?>(null)}; var desktopTracks by remember{mutableStateOf<List<Track>>(emptyList())}; var desktopStatus by remember{mutableStateOf("")}; var desktopCatalogResponseMs by remember{mutableStateOf<Long?>(null)}
    var desktopRefreshKey by remember{mutableIntStateOf(0)}
    var desktopAuthorizationRevision by remember{mutableIntStateOf(0)}
    var librarySource by remember{mutableStateOf(LibrarySource.All)}
    val offlineTracks=offlineStore.tracks
    var offlineReady by remember{mutableStateOf(false)}
    var offlineStorage by remember{mutableStateOf(OfflineStorageSummary(0,0,2L*1024*1024*1024))}
    LaunchedEffect(offlineStore){offlineStorage=offlineStore.load();offlineReady=true}
    LaunchedEffect(offlineTracks){if(offlineReady)offlineStorage=offlineStore.storageSummary()}
    LaunchedEffect(collections){collections.load()}
    val catalogIdentity=IdentityKey(localTracks,offlineTracks,desktopTracks)
    val allTracks=remember(catalogIdentity){localTracks?.plus(offlineTracks)?.plus(desktopTracks)}
    LaunchedEffect(catalogIdentity){allTracks?.takeIf{it.isNotEmpty()}?.let{collections.rebindRemoteTracks(it)}}
    val allTracksIdentity=IdentityKey(allTracks)
    val tracks by produceState<List<Track>?>(null,allTracksIdentity,librarySource){val snapshot=allTracks;value=snapshot?.let{withContext(Dispatchers.Default){selectLibrarySource(it,librarySource)}}}
    val sourceCounts by produceState<Map<LibrarySource,Int>>(emptyMap(),catalogIdentity){value=withContext(Dispatchers.Default){val combined=localTracks.orEmpty()+offlineTracks+desktopTracks;mapOf(LibrarySource.All to selectLibrarySource(combined,LibrarySource.All).size,LibrarySource.Phone to combined.count{it.uri.scheme!="http"&&it.uri.scheme!="file"},LibrarySource.Desktop to combined.count{it.uri.scheme=="http"},LibrarySource.Downloaded to offlineTracks.size)}}
    val desktopLibrary=remember{DesktopLibraryRepository()}
    val desktopCatalogCache=remember{DesktopCatalogCache(context)}
    var search by remember{mutableStateOf(false)}; var query by remember{mutableStateOf("")}
    var page by remember{mutableStateOf(Page.Library)}; var tab by remember{mutableStateOf(LibraryTab.Albums)}
     var album by remember{mutableStateOf<Album?>(null)}; var artist by remember{mutableStateOf<Artist?>(null)}; var fullPlayer by remember{mutableStateOf(false)};var coverSearchAlbum by remember{mutableStateOf<Album?>(null)};var albumInfoAlbum by remember{mutableStateOf<Album?>(null)}
    val albumInfoRepository=remember{AlbumInfoRepository()}
    val playback by player.state.collectAsState(); var account by remember{mutableStateOf(AccountUi(email=accountRepo.savedEmail,sessionSaved=accountRepo.hasSavedSession))}; val scope=rememberCoroutineScope()
    var activeDownload by remember{mutableStateOf<String?>(null)}; var downloadProgress by remember{mutableIntStateOf(0)}; var downloadMessage by remember{mutableStateOf("")}
    var activeBatchLabel by remember{mutableStateOf<String?>(null)};var batchTotal by remember{mutableIntStateOf(0)};var batchFinished by remember{mutableIntStateOf(0)};var batchSucceeded by remember{mutableIntStateOf(0)};var downloadBatchJob by remember{mutableStateOf<Job?>(null)}
    var batchResult by remember{mutableStateOf<String?>(null)}
    var offlineClearBusy by remember{mutableStateOf(false)};var offlineClearMessage by remember{mutableStateOf("")}
    var backupBusy by remember{mutableStateOf(false)};var backupStatus by remember{mutableStateOf("")}
    val exportSettingsBackup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        if(uri!=null){
            val contents=runCatching{MobileSettingsBackup.export(settings,accountRepo,context)}.getOrElse{backupStatus=it.message?:"Não foi possível preparar o backup.";return@rememberLauncherForActivityResult}
            scope.launch{backupBusy=true;backupStatus="";runCatching{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter(Charsets.UTF_8)?.use{it.write(contents)}?:error("Não foi possível gravar o arquivo de backup.")}}.onSuccess{backupStatus="Backup de configurações salvo."}.onFailure{backupStatus=it.message?:"Não foi possível salvar o backup."};backupBusy=false}
        }
    }
    val importSettingsBackup=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)scope.launch{
            backupBusy=true;backupStatus=""
            runCatching{
                val contents=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{input->
                    val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                    while(true){val count=input.read(buffer);if(count<0)break;require(output.size()+count<=64*1024){"O arquivo de backup é maior que o permitido."};output.write(buffer,0,count)}
                    output.toString(Charsets.UTF_8.name())
                }?:error("Não foi possível abrir o arquivo de backup.")}
                val previousGrant=accountRepo.desktopGrantId()
                val sessionReset=MobileSettingsBackup.import(contents,settings,accountRepo,context)
                sessionReset to (previousGrant!=accountRepo.desktopGrantId())
            }.onSuccess{(sessionReset,desktopGrantReset)->
                account=if(sessionReset)AccountUi(email=accountRepo.savedEmail,message="Entre no Cobre ID para continuar.") else if(!account.signedIn)account.copy(email=accountRepo.savedEmail) else account
                if(desktopGrantReset){desktopStatus="Acesso ao Desktop precisa ser autorizado novamente.";desktopRefreshKey++;desktopAuthorizationRevision++}
                backupStatus="Preferências restauradas. Entre no Cobre ID se solicitado e autorize novamente a pasta, se necessário."
            }.onFailure{backupStatus=it.message?:"Não foi possível importar o backup."}
            backupBusy=false
        }
    }
    val mainHandler=remember{android.os.Handler(android.os.Looper.getMainLooper())}
    fun downloadTrack(track:Track){
        if(activeDownload!=null||activeBatchLabel!=null)return
        activeDownload=track.uri.toString();downloadProgress=0;downloadMessage="Preparando ${track.title}…"
        scope.launch{
            runCatching{
                check(offlineReady){"Os downloads offline ainda estão sendo carregados. Tente novamente em instantes."}
                if(track.uri.scheme=="file"||offlineStore.contains(track)){
                    offlineStore.remove(track)
                    null
                }else{
                    downloadMessage="Baixando ${track.title}…"
                    val token=accountRepo.ensureDesktopGrantToken()
                    offlineStore.download(track,token,settings.desktopUrl.orEmpty()){percent->mainHandler.post{downloadProgress=percent}}
                }
            }.onSuccess{downloadMessage=it?.let{"${it.title} disponível offline no celular."}?:"Download removido do celular."}
                .onFailure{downloadMessage=it.message?:"Não foi possível baixar a faixa."}
            activeDownload=null
        }
    }
    fun downloadTracksBatch(tracks:List<Track>,label:String){
        if(activeDownload!=null||activeBatchLabel!=null)return
        if(!offlineReady){downloadMessage="Os downloads offline ainda estão sendo carregados. Tente novamente em instantes.";return}
        val alreadyOffline=offlineStore.tracks.mapTo(hashSetOf()){it.libraryIdentity()}
        val pending=tracks.distinctBy{it.libraryIdentity()}.filter{it.uri.scheme=="http"&&it.libraryIdentity() !in alreadyOffline}
        if(pending.isEmpty()){
            downloadMessage=if(tracks.isEmpty())"Este conjunto não tem faixas para baixar." else "Todas as faixas compatíveis já estão disponíveis offline."
            return
        }
        activeBatchLabel=label;batchResult=null;batchTotal=pending.size;batchFinished=0;batchSucceeded=0;downloadMessage=""
        downloadBatchJob=scope.launch{
            var stopReason:String?=null
            try{
                for(track in pending){
                    activeDownload=track.uri.toString();downloadProgress=0
                    try{
                        val token=accountRepo.ensureDesktopGrantToken()
                        offlineStore.download(track,token,settings.desktopUrl.orEmpty()){percent->mainHandler.post{downloadProgress=percent}}
                        batchSucceeded++
                    }catch(cancelled:CancellationException){throw cancelled}
                    catch(error:Exception){stopReason=error.message?:"Falha ao baixar ${track.title}.";break}
                    batchFinished++
                }
                downloadMessage=if(stopReason==null)"$batchSucceeded faixas baixadas e disponíveis offline." else "$batchSucceeded faixas baixadas; restante interrompido. $stopReason"
                batchResult=downloadMessage
            }catch(cancelled:CancellationException){
                downloadMessage="Download interrompido · $batchSucceeded faixas ficaram disponíveis offline."
                batchResult=downloadMessage
                throw cancelled
            }catch(error:Exception){downloadMessage=error.message?:"Não foi possível iniciar os downloads.";batchResult=downloadMessage}
            finally{activeDownload=null;activeBatchLabel=null;downloadBatchJob=null}
        }
    }
    var accountSessionReady by remember{mutableStateOf(!accountRepo.hasSavedSession||accountRepo.serverUrl.isBlank())}
    val updateManager=remember{AppUpdateManager(context)}
    var updateInfo by remember{mutableStateOf<MobileUpdate?>(null)}
    var updateFile by remember{mutableStateOf<File?>(null)}
    var updateStatus by remember{mutableStateOf(if(BuildConfig.PLAY_DISTRIBUTION)"Atualizações gerenciadas pela Play Store." else "Verificando atualizações automaticamente…")}
    var updateBusy by remember{mutableStateOf(false)}
    var updateProgress by remember{mutableIntStateOf(0)}
    var showUpdatePrompt by remember{mutableStateOf(false)}
    var installedReleaseNotes by remember{mutableStateOf(updateManager.takeNotesForCurrentVersion())}
    val installPermissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){
        val apk=updateFile
        if(updateManager.canInstallPackages()&&apk!=null)runCatching{context.startActivity(updateManager.installerIntent(apk))}.onFailure{updateStatus=it.message?:"Não foi possível abrir o instalador do Android."}
        else updateStatus="Permita instalações do Cobre nas configurações do Android e toque em Instalar novamente."
    }
    fun installUpdate(){
        if(BuildConfig.PLAY_DISTRIBUTION){updateStatus="Atualizações gerenciadas pela Play Store.";return}
        val apk=updateFile ?: return
        if(!updateManager.canInstallPackages())installPermissionLauncher.launch(updateManager.permissionSettingsIntent())
        else runCatching{context.startActivity(updateManager.installerIntent(apk))}.onFailure{
            updateStatus=if(it is ActivityNotFoundException)"Não há instalador de pacotes disponível neste aparelho." else it.message?:"Não foi possível abrir o instalador do Android."
        }
    }
    fun checkForMobileUpdate(force:Boolean){
        if(BuildConfig.PLAY_DISTRIBUTION){updateStatus="Atualizações gerenciadas pela Play Store.";return}
        scope.launch{
            updateBusy=true
            updateStatus="Verificando atualizações…"
            runCatching{updateManager.checkForUpdate(force)}.onSuccess{latest->
                updateInfo=latest
                updateStatus=if(latest==null)"Cobre Mobile está atualizado." else "Versão ${latest.version} disponível."
                if(latest!=null)showUpdatePrompt=true
            }.onFailure{updateStatus=it.message?:"Não foi possível verificar atualizações."}
            updateBusy=false
        }
    }
    fun downloadOrInstallUpdate(){
        if(BuildConfig.PLAY_DISTRIBUTION){updateStatus="Atualizações gerenciadas pela Play Store.";return}
        val latest=updateInfo?:return
        val existing=updateFile
        if(existing!=null){installUpdate();return}
        scope.launch{
            updateBusy=true;updateProgress=0;updateStatus="Baixando atualização…"
            runCatching{updateManager.downloadAndVerify(latest){updateProgress=it}}.onSuccess{apk->
                updateFile=apk
                updateManager.savePendingNotes(latest)
                updateStatus="Download verificado. Toque em Instalar para concluir."
            }.onFailure{updateStatus=it.message?:"Não foi possível baixar a atualização."}
            updateBusy=false
        }
    }
    LaunchedEffect(Unit){if(!BuildConfig.PLAY_DISTRIBUTION){checkForMobileUpdate(false);while(isActive){delay(60*60*1000L);checkForMobileUpdate(false)}}}
    LaunchedEffect(Unit){
        if(!granted)askAudio.launch(permission)
        if(accountRepo.hasSavedSession&&accountRepo.serverUrl.isNotBlank()){
            account=account.copy(busy=true,message="Reconectando ao Cobre ID…")
            runCatching{accountRepo.restoreSession();sendMeasuredPresence(accountRepo,context,null);accountRepo.loadDevices()}
                .onSuccess{account=AccountUi(true,accountRepo.savedEmail,it,message="Conta conectada.",sessionSaved=true)}
                .onFailure{account=AccountUi(email=accountRepo.savedEmail,message=it.message?:"Sem conexão com o Cobre Server.",error=true,sessionSaved=accountRepo.hasSavedSession)}
        }
        accountSessionReady=true
    }
    LaunchedEffect(granted,folderUri){localTracks=if(granted)runCatching{library.load(folderUri?.let{Uri.parse(it)})}.getOrDefault(emptyList()) else emptyList()}
    LaunchedEffect(settings.desktopUrl,desktopRefreshKey,desktopAuthorizationRevision,accountSessionReady){
        if(!accountSessionReady)return@LaunchedEffect
        val address=settings.desktopUrl
        if(address.isNullOrBlank()){desktopTracks=emptyList();desktopCatalogResponseMs=null;desktopStatus=""}
        else{
            val grantToken=accountRepo.currentDesktopGrantToken()
            if(settings.secureDesktopAccessRequired&&grantToken.isNullOrBlank()){
                desktopTracks=emptyList();desktopCatalogResponseMs=null;desktopStatus="Peça acesso Cobre ID ao Desktop para reconectar esta biblioteca."
            }else{
                val origin=runCatching{desktopLibrary.cacheOrigin(address)}.getOrNull()
                val grantId=accountRepo.desktopGrantId()
                val cacheKey=if(origin!=null&&!grantId.isNullOrBlank())DesktopCatalogCache.key(origin,grantId)else null
                desktopStatus="Conectando ao Cobre Desktop…"
                try{
                    val validToken=if(accountRepo.desktopGrantId()!=null)accountRepo.ensureDesktopGrantToken()else grantToken
                    val remote=try{desktopLibrary.load(address,validToken)}catch(error:DesktopLibraryException){
                        if(error.statusCode!=401||accountRepo.desktopGrantId()==null)throw error
                        val renewed=accountRepo.refreshDesktopGrant()
                        desktopLibrary.loadAuthorized(address,renewed.token)
                    }
                    desktopTracks=remote.tracks;desktopCatalogResponseMs=remote.catalogResponseMs;desktopStatus="Computador conectado · ${remote.tracks.size} faixas · catálogo respondeu em ${remote.catalogResponseMs} ms"
                    if(cacheKey!=null&&origin!=null)runCatching{desktopCatalogCache.replace(cacheKey,origin,remote.tracks)}
                }catch(error:Exception){
                    desktopCatalogResponseMs=null
                    val authorizationRejected=(error is DesktopLibraryException&&(error.statusCode==401||error.statusCode==403))||(error is CobreIdException&&(error.statusCode==401||error.statusCode==403))
                    if(authorizationRejected){cacheKey?.let{runCatching{desktopCatalogCache.clear(it)}};desktopTracks=emptyList();desktopStatus=error.message?:"A autorização do computador foi recusada. Reconecte pelo QR."}
                    else{
                        val cached=cacheKey?.let{runCatching{desktopCatalogCache.read(it)}.getOrNull()}.orEmpty()
                        if(cached.isNotEmpty()){desktopTracks=cached;desktopStatus="Computador indisponível · catálogo salvo com ${cached.size} faixas. A reprodução pela rede retorna quando ele reconectar."}
                        else{desktopTracks=emptyList();desktopStatus=error.message?:"Não foi possível conectar ao computador."}
                    }
                }
            }
        }
    }
    LaunchedEffect(account.signedIn,desktopCatalogResponseMs){if(account.signedIn&&desktopCatalogResponseMs!=null)runCatching{sendMeasuredPresence(accountRepo,context,desktopCatalogResponseMs)}}
    LaunchedEffect(account.signedIn,desktopCatalogResponseMs){while(account.signedIn){delay(45_000);runCatching{sendMeasuredPresence(accountRepo,context,desktopCatalogResponseMs);accountRepo.loadDevices()}.onSuccess{account=account.copy(devices=it,message="Conexão ativa.",error=false)}.onFailure{account=account.copy(message=it.message?:"Conexão interrompida.",error=true)}}}
    LaunchedEffect(playback.playing,fullPlayer){while(playback.playing&&fullPlayer){delay(800);player.refreshPosition()}}
    LaunchedEffect(playback.currentIndex,playback.queue){val track=playback.queue.getOrNull(playback.currentIndex);if(track!=null)collections.recordPlayed(track)}
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()){
         if(fullPlayer&&playback.hasItem) PlayerPage(playback,player,settings.desktopUrl.orEmpty(),accountRepo.desktopGrantId()!=null,settings.vinylPlayer,settings.dynamicAlbumColors,coverArtwork,accountRepo.currentDesktopGrantToken(),settings::saveVinylPlayer,{fullPlayer=false},{fullPlayer=false;page=Page.Queue},{trackId->
            scope.launch {
                runCatching { accountRepo.sendDesktopCommand(settings.desktopUrl.orEmpty(),"play",trackId.toDouble()) }
                    .onSuccess {
                        if(playback.playing)player.toggle()
                        Toast.makeText(context,"Faixa enviada ao Cobre Desktop.",Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { Toast.makeText(context,it.message?:"Não foi possível enviar a faixa ao Desktop.",Toast.LENGTH_LONG).show() }
            }
        },{track->coverSearchAlbum=Album(track.albumArtist.ifBlank{track.artist},track.album,track.year,listOf(track))},collections,{selectedAlbum->albumInfoAlbum=selectedAlbum})
        else {
            Header(page,search,query,{query=it},{search=!search;query="";tab=LibraryTab.Tracks;album=null;artist=null},{page=Page.Queue;search=false},{page=Page.Collections;search=false},{scope.launch{granted=ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED;localTracks=if(granted)runCatching{library.load(folderUri?.let{Uri.parse(it)})}.getOrDefault(emptyList()) else emptyList();desktopRefreshKey++}})
            if(activeBatchLabel!=null||batchResult!=null){
                val progress=((batchFinished+downloadProgress/100f)/batchTotal.coerceAtLeast(1)).coerceIn(0f,1f)
                Card(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=4.dp),colors=CardDefaults.cardColors(containerColor=CardBg),shape=RoundedCornerShape(13.dp)){
                    Column(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=7.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){Text(activeBatchLabel?.let{"Baixando $it · ${batchFinished+1}/$batchTotal"}?:batchResult.orEmpty(),color=Tx,style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis);if(activeBatchLabel!=null)TextButton(onClick={downloadBatchJob?.cancel()}){Text("Cancelar",color=Mu)}else TextButton(onClick={batchResult=null}){Text("Fechar",color=Mu)}}
                        if(activeBatchLabel!=null)LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.primary,trackColor=Line)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()){
                when(page){
                    Page.Library->when{
                        localTracks==null->Loading()
                        tracks==null->Loading("Organizando sua biblioteca…")
                        tracks.isNullOrEmpty()&&desktopStatus.startsWith("Conectando")->Loading("Conectando ao Cobre Desktop…")
                        tracks.isNullOrEmpty()&&!granted->EmptyState(Icons.Default.LibraryMusic,"Sua música começa aqui","Conecte ao computador ou permita acesso ao áudio para usar a biblioteca deste aparelho."){askAudio.launch(permission)}
                        else->LibraryPage(tracks.orEmpty(),library,collections,offlineTracks,downloadMessage,downloadProgress,activeDownload,::downloadTrack,{list,label->downloadTracksBatch(list,label)},tab,query,album,artist,librarySource,sourceCounts,desktopStatus,{page=Page.Account;search=false},{librarySource=it},{tab=it;album=null;artist=null},{album=it},{artist=it},{album=null;artist=null},{list,index->
                            if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            player.play(list,index)
                         },player::addToQueue,{selectedAlbum->coverSearchAlbum=selectedAlbum},{selectedAlbum->albumInfoAlbum=selectedAlbum})
                    }
                    Page.Queue->QueuePage(playback,player,collections)
                    Page.Collections->CollectionsPage(collections,{list->player.play(list,0)},{track->player.addToQueue(track)},{list,name->downloadTracksBatch(list,name)})
                    Page.Account->AccountPage(accountRepo,account,settings.desktopUrl.orEmpty(),desktopStatus,desktopTracks.size,{account=it},{settings.requireSecureDesktopAccess();desktopAuthorizationRevision++},onAccountDeleted={
                        if(playback.playing&&playback.queue.getOrNull(playback.currentIndex)?.uri?.scheme=="http")player.toggle()
                    },onScanDesktop={
                        if(!account.signedIn){account=account.copy(message="Entre no Cobre ID antes de parear o Desktop.",error=true)}
                        else {
                            val options=GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                            GmsBarcodeScanning.getClient(context,options).startScan()
                                .addOnSuccessListener { barcode ->
                                    val raw=barcode.rawValue
                                    if(raw.isNullOrBlank())account=account.copy(message="O QR não contém dados de pareamento.",error=true)
                                    else scope.launch {
                                        account=account.copy(busy=true,message="Validando QR e solicitando aprovação…",error=false)
                                        runCatching {
                                            val pairing=parseDesktopPairingQr(raw)
                                            val request=accountRepo.requestDesktopAccess(pairing.hostId)
                                            var approved:DesktopAccessStatus.Approved?=null
                                            val deadline=SystemClock.elapsedRealtime()+request.expiresInSeconds*1_000L
                                            while(SystemClock.elapsedRealtime()<deadline){
                                                delay(2_000)
                                                when(val result=accountRepo.pollDesktopAccess(pairing.hostId,request.requestId)){
                                                    DesktopAccessStatus.Pending->account=account.copy(busy=true,message="No Desktop: Configurações → Conta Cobre → Permitir. Pedido válido por até ${request.expiresInSeconds/60} min.",error=false)
                                                    is DesktopAccessStatus.Approved->{approved=result;break}
                                                }
                                            }
                                            approved?:throw CobreIdException("O pedido expirou. Aprove no Desktop dentro do prazo e leia o QR novamente.")
                                            pairing
                                        }.onSuccess { pairing ->
                                            settings.saveDesktopUrl(pairing.origin);settings.requireSecureDesktopAccess();desktopRefreshKey++;desktopAuthorizationRevision++
                                            account=account.copy(busy=false,message="Desktop pareado e biblioteca conectando. A permissão pode ser renovada por até 30 dias.",error=false)
                                        }.onFailure { error -> account=account.copy(busy=false,message=error.message?:"Não foi possível parear o Desktop.",error=true) }
                                    }
                                }
                                .addOnFailureListener { error -> account=account.copy(message=error.message?:"Não foi possível ler o QR.",error=true) }
                        }
                    },onDiscoveredDesktop={desktop->
                        if(!account.signedIn){account=account.copy(message="Entre no Cobre ID antes de conectar o computador.",error=true)}
                        else scope.launch {
                            account=account.copy(busy=true,message="Solicitando acesso a ${desktop.name}…",error=false)
                            runCatching {
                                val request=accountRepo.requestDesktopAccess(desktop.hostId)
                                var approved:DesktopAccessStatus.Approved?=null
                                val deadline=SystemClock.elapsedRealtime()+request.expiresInSeconds*1_000L
                                while(SystemClock.elapsedRealtime()<deadline){
                                    delay(2_000)
                                    when(val result=accountRepo.pollDesktopAccess(desktop.hostId,request.requestId)){
                                        DesktopAccessStatus.Pending->account=account.copy(busy=true,message="No Desktop: abra Conta Cobre e aprove o pedido desta rede.",error=false)
                                        is DesktopAccessStatus.Approved->{approved=result;break}
                                    }
                                }
                                approved?:throw CobreIdException("O pedido expirou. Tente encontrar o computador novamente.")
                            }.onSuccess {
                                settings.saveDesktopUrl(desktop.origin);settings.requireSecureDesktopAccess();desktopRefreshKey++;desktopAuthorizationRevision++
                                account=account.copy(busy=false,message="${desktop.name} pareado. A biblioteca está conectando.",error=false)
                            }.onFailure { error->account=account.copy(busy=false,message=error.message?:"Não foi possível conectar ao computador.",error=true) }
                        }
                    })
                    Page.Settings->SettingsPage(settings,folderUri,desktopStatus,desktopTracks.size,offlineStorage,offlineClearBusy,offlineClearMessage,backupStatus,backupBusy,{exportSettingsBackup.launch("cobre-mobile-configuracoes.json")},{importSettingsBackup.launch(arrayOf("application/json","text/json"))},updateInfo?.let{"Atualização ${it.version} disponível."}?:updateStatus,BuildConfig.PLAY_DISTRIBUTION,updateBusy,updateProgress,updateFile!=null,{folderPicker.launch(folderUri?.let{Uri.parse(it)})},{folderUri?.let{old->runCatching{context.contentResolver.releasePersistableUriPermission(Uri.parse(old),Intent.FLAG_GRANT_READ_URI_PERMISSION)}};settings.setFolder(null);folderUri=null},{
                        if(!offlineClearBusy){
                            val offlineUris=offlineTracks.mapTo(hashSetOf()){it.uri}
                            if(playback.playing&&playback.queue.getOrNull(playback.currentIndex)?.uri?.let{it in offlineUris}==true)player.toggle()
                            playback.queue.indices.reversed().filter{playback.queue[it].uri in offlineUris}.forEach(player::removeFromQueue)
                            offlineClearBusy=true;offlineClearMessage=""
                            scope.launch{runCatching{offlineStore.clearAll()}.onSuccess{count->offlineClearMessage=if(count==0)"Nenhum download estava salvo." else "$count faixas removidas e espaço liberado."}.onFailure{offlineClearMessage=it.message?:"Não foi possível remover os downloads."};offlineClearBusy=false}
                        }
                    },{page=Page.Account;search=false},{desktopRefreshKey++},{checkForMobileUpdate(true)},{downloadOrInstallUpdate()})
                }
            }
            if(playback.hasItem) MiniPlayer(playback,player){fullPlayer=true}
            NavigationBar(containerColor=Panel){
                NavigationBarItem(page==Page.Library&&!search||page==Page.Queue||page==Page.Collections,{page=Page.Library;search=false;query=""},{Icon(Icons.Default.LibraryMusic,null)},label={Text("Início")},colors=navColors())
                NavigationBarItem(page==Page.Library&&search,{page=Page.Library;search=true;query="";tab=LibraryTab.Tracks;album=null;artist=null},{Icon(Icons.Default.Search,null)},label={Text("Buscar")},colors=navColors())
                NavigationBarItem(page==Page.Settings||page==Page.Account,{page=Page.Settings;search=false},{Icon(Icons.Default.Settings,null)},label={Text("Ajustes")},colors=navColors())
            }
        }
    }
    coverSearchAlbum?.let{selected->CoverSearchDialog(selected,coverArtwork,{coverSearchAlbum=null})}
    albumInfoAlbum?.let{selected->AlbumInfoDialog(selected,albumInfoRepository){albumInfoAlbum=null}}
    if(showUpdatePrompt&&updateInfo!=null){
        val latest=updateInfo!!
        AlertDialog(
            onDismissRequest={if(!updateBusy)showUpdatePrompt=false},
            title={Text("Atualização disponível · ${latest.version}")},
            text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text(latest.notes.ifBlank{"Há uma nova versão do Cobre Mobile."},color=Tx,maxLines=12,overflow=TextOverflow.Ellipsis)
                if(updateBusy&&updateFile==null){LinearProgressIndicator(progress={updateProgress/100f},modifier=Modifier.fillMaxWidth());Text("Baixando e validando o APK… $updateProgress%",color=Mu,style=MaterialTheme.typography.bodySmall)}
                Text(updateStatus,color=Mu,style=MaterialTheme.typography.bodySmall)
                Text("O Android pedirá confirmação antes de instalar. O Cobre verifica a assinatura e mantém os dados do app.",color=Mu,style=MaterialTheme.typography.bodySmall)
            }},
            confirmButton={TextButton(onClick={if(updateFile==null)downloadOrInstallUpdate()else installUpdate()},enabled=!updateBusy){Text(if(updateFile==null)"Baixar atualização" else "Instalar",color=MaterialTheme.colorScheme.primary)}},
            dismissButton={TextButton(onClick={showUpdatePrompt=false},enabled=!updateBusy){Text("Mais tarde",color=Mu)}},
        )
    }
    installedReleaseNotes?.let{notes->
        AlertDialog(
            onDismissRequest={installedReleaseNotes=null},
            title={Text("Cobre Mobile atualizado · ${BuildConfig.VERSION_NAME}")},
            text={Text(notes,color=Tx,maxLines=16,overflow=TextOverflow.Ellipsis)},
            confirmButton={TextButton(onClick={installedReleaseNotes=null}){Text("Entendi",color=MaterialTheme.colorScheme.primary)}},
        )
    }
}

private suspend fun sendMeasuredPresence(repository:CobreIdRepository,context:android.content.Context,catalogResponseMs:Long?){
    val metrics=StreamMetrics.recent(context)
    repository.sendPresence(catalogResponseMs,metrics?.latencyMs,metrics?.bytesPerSecond,metrics?.ageMs)
}
