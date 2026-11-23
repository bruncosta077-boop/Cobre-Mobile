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
private enum class Page { Library, Queue, Collections, Account, Settings }
private enum class LibraryTab { Albums, Artists, Tracks }
private enum class LibrarySource(val label:String) { All("Tudo"), Phone("Celular"), Desktop("Computador"), Downloaded("Baixadas") }
private class IdentityKey(vararg private val values:Any?){
    override fun equals(other:Any?)=other is IdentityKey&&values.size==other.values.size&&values.indices.all{values[it]===other.values[it]}
    override fun hashCode()=values.fold(1){hash,value->31*hash+System.identityHashCode(value)}
}
data class AccountUi(val signedIn:Boolean=false,val email:String="",val devices:List<CobreDevice> = emptyList(),val busy:Boolean=false,val message:String="",val error:Boolean=false,val serverReady:Boolean?=null)

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
    val playback by player.state.collectAsState(); var account by remember{mutableStateOf(AccountUi(email=accountRepo.savedEmail))}; val scope=rememberCoroutineScope()
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
    LaunchedEffect(Unit){if(!BuildConfig.PLAY_DISTRIBUTION)checkForMobileUpdate(false)}
    LaunchedEffect(Unit){
        if(!granted)askAudio.launch(permission)
        if(accountRepo.hasSavedSession&&accountRepo.serverUrl.isNotBlank()){
            account=account.copy(busy=true,message="Reconectando ao Cobre ID…")
            runCatching{accountRepo.restoreSession();sendMeasuredPresence(accountRepo,context,null);accountRepo.loadDevices()}
                .onSuccess{account=AccountUi(true,accountRepo.savedEmail,it,message="Conta conectada.")}
                .onFailure{account=AccountUi(email=accountRepo.savedEmail,message=it.message?:"Sessão expirada.",error=true)}
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
    LaunchedEffect(playback.currentIndex,playback.queue){playback.queue.getOrNull(playback.currentIndex)?.let(collections::recordPlayed)}
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

@Composable private fun Header(page:Page,search:Boolean,query:String,onQuery:(String)->Unit,onSearch:()->Unit,onQueue:()->Unit,onCollection:()->Unit,onRefresh:()->Unit){
    Column {
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){
            Image(painterResource(R.drawable.ic_cobre_logo),"Cobre",Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)))
            Column(Modifier.weight(1f).padding(start=12.dp)){Text("Cobre",color=Tx,fontSize=20.sp,letterSpacing=.2.sp,fontWeight=FontWeight.Bold);Text(when(page){Page.Library->if(search)"Buscar" else "Biblioteca";Page.Queue->"A seguir";Page.Collections->"Minha coleção";Page.Account->"Conexões";Page.Settings->"Configurações"},color=Mu,fontSize=12.sp)}
            if(page==Page.Library&&search)IconButton(onClick=onSearch){Icon(Icons.Default.Close,"Fechar busca",tint=Tx)}
            if(page==Page.Library&&!search){IconButton(onClick=onCollection){Icon(Icons.Default.FavoriteBorder,"Minha coleção",tint=Tx)};IconButton(onClick=onQueue){Icon(Icons.Default.QueueMusic,"Fila",tint=Tx)};IconButton(onClick=onRefresh){Icon(Icons.Default.Refresh,"Atualizar biblioteca",tint=MaterialTheme.colorScheme.primary)}}
        }
        if(page==Page.Library&&search) OutlinedTextField(query,onQuery,Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),placeholder={Text("Buscar faixa, álbum ou artista",color=Mu)},leadingIcon={Icon(Icons.Default.Search,null,tint=MaterialTheme.colorScheme.primary)},singleLine=true,shape=RoundedCornerShape(18.dp),colors=fieldColors())
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}

@Composable private fun LibraryPage(tracks:List<Track>,library:LocalLibrary,collections:LibraryCollections,offlineTracks:List<Track>,downloadMessage:String,downloadProgress:Int,activeDownload:String?,onDownload:(Track)->Unit,onDownloadBatch:(List<Track>,String)->Unit,tab:LibraryTab,query:String,album:Album?,artist:Artist?,source:LibrarySource,sourceCounts:Map<LibrarySource,Int>,desktopStatus:String,onOpenConnections:()->Unit,onSource:(LibrarySource)->Unit,onTab:(LibraryTab)->Unit,onAlbum:(Album)->Unit,onArtist:(Artist)->Unit,onBack:()->Unit,onPlay:(List<Track>,Int)->Unit,onQueue:(Track)->Unit,onSearchCovers:(Album)->Unit,onAlbumInfo:(Album)->Unit){
    if(album!=null){AlbumPage(album,collections,onBack,onPlay,onQueue,onDownloadBatch,{onSearchCovers(album)},{onAlbumInfo(album)});return}
    if(artist!=null){Column{Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar",tint=MaterialTheme.colorScheme.primary)};Column{Text(artist.name,color=Tx,style=MaterialTheme.typography.titleLarge);Text("${artist.albums.size} álbuns · ${artist.tracks.size} faixas",color=Mu,style=MaterialTheme.typography.bodySmall)}};AlbumGrid(artist.albums,onAlbum,{onPlay(it,0)},{albumTracks->albumTracks.forEach(onQueue)})};return}
    val q=query.trim().lowercase()
    val trackSnapshotIdentity=IdentityKey(tracks)
    val filtered by produceState(tracks,trackSnapshotIdentity,q){val snapshot=tracks;value=withContext(Dispatchers.Default){if(q.isBlank())snapshot else snapshot.filter{it.title.lowercase().contains(q)||it.artist.lowercase().contains(q)||it.albumArtist.lowercase().contains(q)||it.album.lowercase().contains(q)}}}
    val filteredIdentity=IdentityKey(filtered)
    val libraryViews by produceState<LibraryViews?>(null,library,filteredIdentity){val snapshot=filtered;value=withContext(Dispatchers.Default){val albums=library.albums(snapshot);LibraryViews(albums,library.artistsFromAlbums(albums))}}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start=14.dp,end=14.dp,top=5.dp,bottom=1.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            LibrarySource.entries.forEach{item->
                val selected=item==source
                val icon=when(item){LibrarySource.All->Icons.Default.LibraryMusic;LibrarySource.Phone->Icons.Default.PhoneAndroid;LibrarySource.Desktop->Icons.Default.Computer;LibrarySource.Downloaded->Icons.Default.Download}
                FilterChip(selected=selected,onClick={onSource(item)},label={Text("${item.label} · ${sourceCounts[item]?:0}",maxLines=1)},leadingIcon={Icon(icon,null,Modifier.size(17.dp))},shape=RoundedCornerShape(22.dp),colors=FilterChipDefaults.filterChipColors(selectedContainerColor=CardBg,selectedLabelColor=Tx,selectedLeadingIconColor=MaterialTheme.colorScheme.primary,labelColor=Mu,iconColor=Mu))
            }
        }
        if(downloadMessage.isNotBlank()) Card(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=3.dp),colors=CardDefaults.cardColors(containerColor=CardBg),shape=RoundedCornerShape(13.dp)){
            Column(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=9.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(downloadMessage,color=Tx,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(activeDownload!=null) LinearProgressIndicator(progress={downloadProgress/100f},modifier=Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.primary,trackColor=Line)
            }
        }
        if(source==LibrarySource.All||source==LibrarySource.Desktop){
            val desktopConnected=desktopStatus.startsWith("Computador conectado")
            val desktopLoading=desktopStatus.startsWith("Conectando")
            val statusLabel=when{desktopConnected->"Computador conectado";desktopLoading->"Conectando ao Desktop";desktopStatus.isBlank()->"Computador ainda não conectado";else->"Biblioteca do computador indisponível"}
            Card(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=3.dp),colors=CardDefaults.cardColors(containerColor=if(desktopConnected)Color(0xFF182019)else CardBg),shape=RoundedCornerShape(13.dp)){
                Row(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if(desktopConnected)Green else if(desktopLoading)MaterialTheme.colorScheme.primary else Mu))
                    Column(Modifier.weight(1f).padding(start=9.dp)){Text(statusLabel,color=Tx,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold);Text(if(desktopConnected)"${sourceCounts[LibrarySource.Desktop]?:0} faixas · duplicatas removidas na visão Tudo" else if(desktopLoading)"Buscando a biblioteca autorizada…" else desktopStatus.ifBlank{"Entre no mesmo Cobre ID e conecte pelo QR do Desktop."},color=Mu,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)}
                    TextButton(onClick=onOpenConnections){Text(if(desktopConnected)"Conexão" else "Conectar",color=MaterialTheme.colorScheme.primary,maxLines=1)}
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){LibraryTab.entries.forEach{item->val selected=item==tab;Text(when(item){LibraryTab.Albums->"Álbuns";LibraryTab.Artists->"Artistas";LibraryTab.Tracks->"Faixas"},color=if(selected)Tx else Mu,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal,modifier=Modifier.clip(CircleShape).background(if(selected)CardBg else Color.Transparent).clickable{onTab(item)}.padding(horizontal=14.dp,vertical=9.dp))};Spacer(Modifier.weight(1f));Text("${filtered.size} faixas",color=Mu,style=MaterialTheme.typography.labelSmall)}
        when{filtered.isEmpty()->EmptyState(Icons.Default.LibraryMusic,if(source==LibrarySource.Downloaded)"Nenhuma faixa baixada" else if(q.isBlank())"Sua biblioteca está vazia" else "Nada encontrado",if(source==LibrarySource.Downloaded)"Na aba Computador, abra Faixas e toque no ícone de download para ouvir sem conexão." else if(q.isBlank()&&desktopStatus.isNotBlank()&&!desktopStatus.startsWith("Computador conectado"))desktopStatus else if(q.isBlank())"Conecte o computador para acessar sua coleção, ou escolha uma pasta local nas configurações." else "Tente outro nome de faixa, álbum ou artista.",actionLabel=if(source==LibrarySource.Downloaded)"" else "Conectar computador",action=if(source==LibrarySource.Downloaded)null else onOpenConnections)
            tab==LibraryTab.Albums->if(libraryViews==null)Loading("Organizando álbuns…")else AlbumGrid(libraryViews!!.albums,onAlbum,{onPlay(it,0)},{albumTracks->albumTracks.forEach(onQueue)})
            tab==LibraryTab.Artists->if(libraryViews==null)Loading("Organizando artistas…")else ArtistList(libraryViews!!.artists,onArtist)
            else->TrackList(filtered,{i->onPlay(filtered,i)},onQueue,collections,offlineTracks,activeDownload,onDownload)}
    }
}

private data class LibraryViews(val albums:List<Album>,val artists:List<Artist>)

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun AlbumGrid(albums:List<Album>,onClick:(Album)->Unit,onPlayAlbum:(List<Track>)->Unit={},onQueueAlbum:(List<Track>)->Unit={}){
    LazyVerticalGrid(GridCells.Fixed(2),contentPadding=PaddingValues(16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        items(albums,key=Album::key){a->
            var menu by remember{mutableStateOf(false)}
            Box {
                Column(Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(onClick={onClick(a)},onLongClick={menu=true})) {
                    Cover(a.tracks.first(),Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)))
                    Text(a.title,color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=8.dp))
                    Text(a.artist,color=Mu,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                    Text("${a.tracks.size} faixas${if(a.year>0)" · ${a.year}" else ""}",color=Mu.copy(alpha=.7f),style=MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    DropdownMenuItem(text={Text("Reproduzir álbum")},onClick={menu=false;onPlayAlbum(a.tracks)})
                    DropdownMenuItem(text={Text("Adicionar à fila")},onClick={menu=false;onQueueAlbum(a.tracks)})
                    DropdownMenuItem(text={Text("Abrir álbum")},onClick={menu=false;onClick(a)})
                }
            }
        }
    }
}

private fun selectLibrarySource(tracks:List<Track>,source:LibrarySource):List<Track>{
    val selected=when(source){LibrarySource.All->tracks;LibrarySource.Phone->tracks.filter{it.uri.scheme!="http"&&it.uri.scheme!="file"};LibrarySource.Desktop->tracks.filter{it.uri.scheme=="http"};LibrarySource.Downloaded->tracks.filter{it.uri.scheme=="file"}}
    if(source!=LibrarySource.All)return selected
    return selected.distinctBy(Track::libraryIdentity)
}

@Composable private fun ArtistList(artists:List<Artist>,onClick:(Artist)->Unit){
    LazyColumn(contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp)){items(artists,key={it.name.lowercase()}){a->Row(Modifier.fillMaxWidth().clickable{onClick(a)}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(54.dp).clip(CircleShape).background(CardBg),contentAlignment=Alignment.Center){Text(a.name.take(1).uppercase(),color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,fontSize=20.sp)};Column(Modifier.weight(1f).padding(start=14.dp)){Text(a.name,color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium);Text("${a.albums.size} álbuns · ${a.tracks.size} faixas",color=Mu,style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.Album,null,tint=Mu,modifier=Modifier.size(18.dp))};Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line))}}
}

@Composable private fun TrackList(tracks:List<Track>,onPlay:(Int)->Unit,onQueue:(Track)->Unit,collections:LibraryCollections?=null,offlineTracks:List<Track> = emptyList(),activeDownload:String?=null,onDownload:(Track)->Unit={}){
    val context=LocalContext.current
    val favoriteIdentity=IdentityKey(collections?.favorites)
    val favoriteUris=remember(favoriteIdentity){collections?.favorites?.mapTo(hashSetOf()){it.uri.toString()}.orEmpty()}
    val offlineIdentity=IdentityKey(offlineTracks)
    val offlineIdentities=remember(offlineIdentity){offlineTracks.mapTo(hashSetOf()){it.libraryIdentity()}}
    LazyColumn(contentPadding=PaddingValues(start=16.dp,end=10.dp,bottom=16.dp)){
        itemsIndexed(tracks,key={_,track->"${track.id}-${track.uri}"}){index,t->
            var menu by remember(t.uri){mutableStateOf(false)}
            val canDownload=t.uri.scheme=="file"||(t.uri.scheme=="http"&&t.uri.path.orEmpty().matches(Regex("/v1/tracks/\\d+/stream")))
            val saved=t.uri.scheme=="file"||t.libraryIdentity() in offlineIdentities
            Row(Modifier.fillMaxWidth().heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.MusicNote,null,tint=Mu,modifier=Modifier.size(22.dp))
                Column(Modifier.weight(1f).clickable{onPlay(index)}.padding(horizontal=12.dp,vertical=10.dp)){
                    Text(t.title,color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("${t.artist} · ${t.album}",color=Mu,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Text(time(t.durationMs),color=Mu,style=MaterialTheme.typography.labelSmall)
                Box{
                    if(activeDownload==t.uri.toString())CircularProgressIndicator(Modifier.padding(10.dp).size(20.dp),strokeWidth=2.dp,color=MaterialTheme.colorScheme.primary)
                    else IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Ações da faixa",tint=Mu)}
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                        DropdownMenuItem(text={Text("Adicionar à fila")},leadingIcon={Icon(Icons.Default.QueueMusic,null)},onClick={menu=false;onQueue(t)})
                        if(collections!=null)DropdownMenuItem(text={Text(if(t.uri.toString() in favoriteUris)"Remover dos favoritos" else "Adicionar aos favoritos")},leadingIcon={Icon(if(t.uri.toString() in favoriteUris)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null)},onClick={menu=false;collections.toggleFavorite(t)})
                        if(canDownload)DropdownMenuItem(text={Text(if(saved)"Remover download offline" else "Baixar para ouvir offline")},leadingIcon={Icon(if(saved)Icons.Default.DeleteOutline else Icons.Default.Download,null)},onClick={menu=false;onDownload(t)})
                        if(t.uri.scheme=="content")DropdownMenuItem(text={Text("Compartilhar")},leadingIcon={Icon(Icons.Default.Share,null)},onClick={menu=false;runCatching{Intent(Intent.ACTION_SEND).apply{type="audio/*";putExtra(Intent.EXTRA_STREAM,t.uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}.let{context.startActivity(Intent.createChooser(it,"Compartilhar faixa"))}}})
                    }
                }
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha=.7f)))
        }
    }
}
@Composable private fun AlbumPage(album:Album,collections:LibraryCollections,onBack:()->Unit,onPlay:(List<Track>,Int)->Unit,onQueue:(Track)->Unit,onDownloadBatch:(List<Track>,String)->Unit,onSearchCovers:()->Unit,onAlbumInfo:()->Unit){
    val rating=collections.rating(album)
    LazyColumn(contentPadding=PaddingValues(bottom=18.dp)){item{Column(Modifier.padding(horizontal=18.dp)){TextButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(6.dp));Text("Biblioteca",color=MaterialTheme.colorScheme.primary)};Cover(album.tracks.first(),Modifier.fillMaxWidth().aspectRatio(1.05f).clip(RoundedCornerShape(14.dp)));Text(album.title,color=Tx,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=14.dp));Text(album.artist,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium);Text("${if(album.year>0)"${album.year} · " else ""}${album.tracks.size} faixas",color=Mu,style=MaterialTheme.typography.bodySmall);Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=6.dp)){Text("Sua avaliação",color=Mu,style=MaterialTheme.typography.bodySmall);Spacer(Modifier.width(7.dp));(1..5).forEach{star->IconButton(onClick={collections.setRating(album,if(rating==star)0 else star)},modifier=Modifier.size(30.dp)){Icon(if(star<=rating)Icons.Default.Star else Icons.Default.StarBorder,"${star} estrelas",tint=if(star<=rating)MaterialTheme.colorScheme.primary else Mu,modifier=Modifier.size(20.dp))}}};Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically){Button(onClick={onPlay(album.tracks,0)},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary),shape=RoundedCornerShape(24.dp)){Icon(Icons.Default.PlayArrow,null);Text("Reproduzir")};TextButton(onClick={onDownloadBatch(album.tracks,"álbum ${album.title}")},enabled=album.tracks.any{it.uri.scheme=="http"}){Icon(Icons.Default.Download,null,tint=MaterialTheme.colorScheme.primary);Text("Offline",color=Tx)};TextButton(onClick={album.tracks.forEach(onQueue)}){Icon(Icons.Default.QueueMusic,null,tint=MaterialTheme.colorScheme.primary);Text("Fila",color=Tx)};TextButton(onClick=onSearchCovers){Icon(Icons.Default.Search,null,tint=MaterialTheme.colorScheme.primary);Text("Capa",color=Tx)};TextButton(onClick=onAlbumInfo){Icon(Icons.Default.AutoStories,null,tint=MaterialTheme.colorScheme.primary);Text("Encarte",color=Tx)}}}}
        itemsIndexed(album.tracks,key={_,track->track.uri.toString()}){index,t->
            var menu by remember(t.uri){mutableStateOf(false)}
            Row(Modifier.fillMaxWidth().clickable{onPlay(album.tracks,index)}.padding(start=22.dp,end=12.dp,top=7.dp,bottom=7.dp),verticalAlignment=Alignment.CenterVertically){
                Text(if(t.trackNumber>0)t.trackNumber.toString() else "•",color=Mu,modifier=Modifier.width(30.dp))
                Column(Modifier.weight(1f)){Text(t.title,color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis);if(t.artist!=album.artist)Text(t.artist,color=Mu,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)}
                Text(time(t.durationMs),color=Mu,style=MaterialTheme.typography.labelSmall)
                Box{
                    IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Ações da faixa",tint=Mu)}
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                        DropdownMenuItem(text={Text("Adicionar à fila")},leadingIcon={Icon(Icons.Default.QueueMusic,null)},onClick={menu=false;onQueue(t)})
                        DropdownMenuItem(text={Text(if(collections.isFavorite(t))"Remover dos favoritos" else "Adicionar aos favoritos")},leadingIcon={Icon(if(collections.isFavorite(t))Icons.Default.Favorite else Icons.Default.FavoriteBorder,null)},onClick={menu=false;collections.toggleFavorite(t)})
                    }
                }
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line))
        }
    }
}

@Composable private fun QueuePage(playback:NowPlaying,player:PlayerController,collections:LibraryCollections){
    var askSave by remember{mutableStateOf(false)};var playlistName by remember{mutableStateOf("")};var message by remember{mutableStateOf("")}
    if(askSave)AlertDialog(onDismissRequest={askSave=false},title={Text("Salvar como playlist")},text={OutlinedTextField(playlistName,{playlistName=it},singleLine=true,label={Text("Nome da playlist")},colors=fieldColors())},confirmButton={TextButton(onClick={if(collections.savePlaylist(playlistName,playback.queue)){message="Playlist salva.";askSave=false;playlistName=""}else message="Escolha um nome novo e uma fila com faixas."}){Text("Salvar")}},dismissButton={TextButton(onClick={askSave=false}){Text("Cancelar")}})
    Column(Modifier.fillMaxSize()){Row(Modifier.fillMaxWidth().padding(start=20.dp,end=12.dp,top=16.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("A seguir",color=Tx,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("${playback.queue.size} faixas na fila",color=Mu,style=MaterialTheme.typography.bodySmall)};TextButton(onClick={askSave},enabled=playback.queue.isNotEmpty()){Icon(Icons.Default.PlaylistAdd,null,tint=MaterialTheme.colorScheme.primary);Text("Salvar",color=Tx)};TextButton(onClick=player::clearQueue,enabled=playback.queue.isNotEmpty()){Icon(Icons.Default.DeleteOutline,null,tint=MaterialTheme.colorScheme.primary);Text("Limpar",color=Tx)}}
        if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(horizontal=20.dp,vertical=4.dp))
        if(playback.queue.isEmpty())EmptyState(Icons.Default.QueueMusic,"Sua fila está vazia","Escolha um álbum ou faixa e adicione à fila.")else LazyColumn(contentPadding=PaddingValues(bottom=12.dp)){items(playback.queue.size,key={i->"${playback.queue[i].id}-$i"}){i->val t=playback.queue[i];val current=i==playback.currentIndex;Row(Modifier.fillMaxWidth().background(if(current)CardBg else Color.Transparent).padding(start=16.dp,end=4.dp,top=3.dp,bottom=3.dp),verticalAlignment=Alignment.CenterVertically){Cover(t,Modifier.size(46.dp).clip(RoundedCornerShape(7.dp)));Column(Modifier.weight(1f).clickable{player.playAt(i)}.padding(horizontal=10.dp,vertical=8.dp)){Text(t.title,color=if(current)MaterialTheme.colorScheme.primary else Tx,maxLines=1,overflow=TextOverflow.Ellipsis);Text(t.artist,color=Mu,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)};IconButton(onClick={player.moveInQueue(i,i-1)},enabled=i>0){Icon(Icons.Default.KeyboardArrowUp,"Mover para cima",tint=Mu)};IconButton(onClick={player.moveInQueue(i,i+1)},enabled=i<playback.queue.lastIndex){Icon(Icons.Default.KeyboardArrowDown,"Mover para baixo",tint=Mu)};IconButton(onClick={player.removeFromQueue(i)}){Icon(Icons.Default.Close,"Remover",tint=Mu)}}}}
    }
}

@Composable private fun CollectionsPage(collections:LibraryCollections,onPlay:(List<Track>)->Unit,onQueue:(Track)->Unit,onDownloadBatch:(List<Track>,String)->Unit){
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=12.dp,bottom=20.dp)){
        item{Text("Minha coleção",color=Tx,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=16.dp,bottom=12.dp))}
        item{Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text("Favoritas",color=Tx,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text("${collections.favorites.size}",color=Mu);TextButton(onClick={onPlay(collections.favorites)},enabled=collections.favorites.isNotEmpty()){Icon(Icons.Default.PlayArrow,null);Text("Tocar")}}}
        if(collections.favorites.isEmpty())item{Text("Suas músicas favoritas ficam guardadas aqui.",color=Mu,modifier=Modifier.padding(bottom=12.dp))}
        items(collections.favorites,key={"favorite:${it.uri}"}){track->CollectionTrackRow(track,collections,onQueue)}
        item{Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text("Tocadas recentemente",color=Tx,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text("${collections.history.size}",color=Mu)}}
        if(collections.history.isEmpty())item{Text("As faixas reproduzidas aparecerão aqui.",color=Mu,modifier=Modifier.padding(bottom=12.dp))}
        items(collections.history,key={"history:${it.uri}"}){track->CollectionTrackRow(track,collections,onQueue)}
        item{Spacer(Modifier.height(14.dp));Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text("Playlists",color=Tx,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text("${collections.playlists.size}",color=Mu)}}
        if(collections.playlists.isEmpty())item{Text("Monte uma fila e salve como playlist na aba Fila.",color=Mu)}
        items(collections.playlists,key={"playlist:${it.name}"}){playlist->
            var menu by remember(playlist.name){mutableStateOf(false)}
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp).clip(RoundedCornerShape(12.dp)).background(Panel).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.QueueMusic,null,tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(playlist.name,color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium);Text("${playlist.tracks.size} faixas",color=Mu,style=MaterialTheme.typography.bodySmall)}
                Box{
                    IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Ações da playlist",tint=Mu)}
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                        DropdownMenuItem(text={Text("Adicionar à fila")},leadingIcon={Icon(Icons.Default.Add,null)},onClick={menu=false;playlist.tracks.forEach(onQueue)})
                        DropdownMenuItem(text={Text("Baixar para ouvir offline")},leadingIcon={Icon(Icons.Default.Download,null)},enabled=playlist.tracks.any{it.uri.scheme=="http"},onClick={menu=false;onDownloadBatch(playlist.tracks,"playlist ${playlist.name}")})
                        DropdownMenuItem(text={Text("Excluir playlist")},leadingIcon={Icon(Icons.Default.DeleteOutline,null)},onClick={menu=false;collections.deletePlaylist(playlist.name)})
                    }
                }
                IconButton(onClick={onPlay(playlist.tracks)},enabled=playlist.tracks.isNotEmpty()){Icon(Icons.Default.PlayArrow,"Reproduzir playlist",tint=MaterialTheme.colorScheme.primary)}
            }
        }
    }
}

@Composable private fun CollectionTrackRow(track:Track,collections:LibraryCollections,onQueue:(Track)->Unit){
    var menu by remember(track.uri){mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().heightIn(min=60.dp),verticalAlignment=Alignment.CenterVertically){
        Cover(track,Modifier.size(42.dp).clip(RoundedCornerShape(7.dp)))
        Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(track.title,color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium);Text("${track.artist} · ${track.album}",color=Mu,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)}
        Box{
            IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Ações da faixa",tint=Mu)}
            DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                DropdownMenuItem(text={Text("Adicionar à fila")},leadingIcon={Icon(Icons.Default.QueueMusic,null)},onClick={menu=false;onQueue(track)})
                DropdownMenuItem(text={Text(if(collections.isFavorite(track))"Remover dos favoritos" else "Adicionar aos favoritos")},leadingIcon={Icon(if(collections.isFavorite(track))Icons.Default.Favorite else Icons.Default.FavoriteBorder,null)},onClick={menu=false;collections.toggleFavorite(track)})
            }
        }
    }
}

@Composable private fun MiniPlayer(s:NowPlaying,player:PlayerController,onOpen:()->Unit){
    Column(Modifier.fillMaxWidth().background(Panel)){
        Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal=11.dp),verticalAlignment=Alignment.CenterVertically){
            Cover(s.queue.getOrNull(s.currentIndex),Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick=onOpen))
            Column(Modifier.weight(1f).clickable(onClick=onOpen).padding(horizontal=10.dp)){
                Text(s.title.ifBlank{"Reproduzindo"},color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                Text(s.artist,color=Mu,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            IconButton(onClick=player::toggle){Icon(if(s.playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Reproduzir ou pausar",tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(28.dp))}
        }
        val progress=if(s.durationMs>0)(s.positionMs.toFloat()/s.durationMs).coerceIn(0f,1f)else 0f
        Box(Modifier.fillMaxWidth().height(2.dp).background(Line)){Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary))}
    }
}

@Composable private fun PlayerPage(s:NowPlaying,player:PlayerController,desktopOrigin:String,hasDesktopGrant:Boolean,vinylEnabled:Boolean,dynamicAlbumColors:Boolean,artworkRepository:CoverArtworkRepository,grantToken:String?,onVinylToggle:(Boolean)->Unit,onClose:()->Unit,onQueue:()->Unit,onPlayOnDesktop:(Int)->Unit,onChangeCover:(Track)->Unit,collections:LibraryCollections,onAlbumInfo:(Album)->Unit){
    val context=LocalContext.current
    val audio=context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    val maxVolume=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var volume by remember{mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()/maxVolume)}
    var scrubbing by remember{mutableStateOf(false)};var preview by remember{mutableFloatStateOf(0f)}
    val current=s.queue.getOrNull(s.currentIndex)
    val currentAlbum=current?.let{Album(it.albumArtist.ifBlank{it.artist},it.album,it.year,listOf(it))}
    var moreActionsExpanded by remember(current?.uri){mutableStateOf(false)}
    var showPlaylistDialog by remember(current?.uri){mutableStateOf(false)}
    var newPlaylistName by remember(current?.uri){mutableStateOf("")}
    val audioPrefs=remember{context.getSharedPreferences(PlaybackService.AUDIO_PREFS,android.content.Context.MODE_PRIVATE)}
    var audioSessionId by remember{mutableIntStateOf(audioPrefs.getInt("audio_session_id",0))}
    DisposableEffect(audioPrefs){
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{prefs,key->if(key=="audio_session_id")audioSessionId=prefs.getInt("audio_session_id",0)}
        audioPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{audioPrefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val coverSourceRepository=remember{DesktopLibraryRepository()}
    val remoteCover=current?.let(coverSourceRepository::coverUri)
    val artworkRevision by CoverArtworkEvents.revision.collectAsState()
    val defaultAccent=MaterialTheme.colorScheme.primary
    val albumAccent by produceState(defaultAccent,current?.uri,dynamicAlbumColors,remoteCover,grantToken,artworkRevision){
        value=if(dynamicAlbumColors&&current!=null) artworkRepository.dominantColor(current,remoteCover,grantToken)?.let(::Color)?:defaultAccent else defaultAccent
    }
    var showLyrics by remember(current?.uri){mutableStateOf(false)};var lyricsRequested by remember(current?.uri){mutableStateOf(false)};var lyricsLoading by remember(current?.uri){mutableStateOf(false)};var lyrics by remember(current?.uri){mutableStateOf<TrackLyrics?>(null)}
    val lyricsRepo=remember{LyricsRepository(context)};val lyricScope=rememberCoroutineScope()
    fun requestLyrics(){
        showLyrics=true
        val track=current?:return
        if(!lyricsRequested){lyricsRequested=true;lyricsLoading=true;lyricScope.launch{lyrics=runCatching{lyricsRepo.load(track)}.getOrNull();lyricsLoading=false}}
    }
    val desktopRepository=remember{DesktopLibraryRepository()}
    val desktopTrackId=current?.let{desktopRepository.desktopTrackIdForRemotePlayback(it,desktopOrigin)}
    var confirmDesktopPlayback by remember(current?.uri){mutableStateOf(false)}
    val source=if(current?.uri?.path?.startsWith("/v1/tracks/")==true||current?.uri?.path?.startsWith("/stream/")==true)"COMPUTADOR · REDE LOCAL" else "BIBLIOTECA DO CELULAR"
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(if(dynamicAlbumColors)albumAccent.copy(alpha=.24f)else Color(0xFF35261E),Bg.copy(alpha=.98f),Bg))).navigationBarsPadding()){
        Column(Modifier.fillMaxSize()){
            Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onClose){Icon(Icons.Default.KeyboardArrowDown,"Minimizar player",tint=Tx)}
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Text("TOCANDO AGORA",color=Tx,letterSpacing=1.8.sp,style=MaterialTheme.typography.labelMedium);Text(source,color=Mu,letterSpacing=1.sp,style=MaterialTheme.typography.labelSmall)}
                IconButton(onClick=onQueue){Icon(Icons.Default.QueueMusic,"Abrir fila",tint=MaterialTheme.colorScheme.primary)}
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=26.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(.86f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(CardBg),contentAlignment=Alignment.Center){
                    if(vinylEnabled)VinylRecord(current,s.playing,Modifier.fillMaxSize())else Cover(current,Modifier.fillMaxSize())
                    if(current!=null)IconButton(onClick={onChangeCover(current)},modifier=Modifier.align(Alignment.TopEnd).padding(9.dp).size(42.dp).background(Color.Black.copy(alpha=.68f),CircleShape)){
                        Icon(Icons.Default.Edit,"Alterar capa",tint=Color.White,modifier=Modifier.size(19.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(s.title,color=Tx,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(s.artist,color=Mu,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Text(buildString{append(current?.album?:"Álbum desconhecido");if((current?.year?:0)>0)append(" · ${current?.year}");if((current?.trackNumber?:0)>0)append(" · Faixa ${current?.trackNumber}")},color=Mu.copy(alpha=.8f),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=3.dp))}
                    Box{
                        IconButton(onClick={moreActionsExpanded=true}){Icon(Icons.Default.MoreVert,"Mais ações da faixa",tint=Mu)}
                        DropdownMenu(expanded=moreActionsExpanded,onDismissRequest={moreActionsExpanded=false}){
                            current?.let{track->
                                DropdownMenuItem(text={Text(if(collections.isFavorite(track))"Remover dos favoritos" else "Adicionar aos favoritos")},leadingIcon={Icon(if(collections.isFavorite(track))Icons.Default.Favorite else Icons.Default.FavoriteBorder,null)},onClick={moreActionsExpanded=false;collections.toggleFavorite(track)})
                                DropdownMenuItem(text={Text("Adicionar à playlist")},leadingIcon={Icon(Icons.Default.PlaylistAdd,null)},onClick={moreActionsExpanded=false;showPlaylistDialog=true})
                                DropdownMenuItem(text={Text("Buscar ou trocar capa")},leadingIcon={Icon(Icons.Default.Edit,null)},onClick={moreActionsExpanded=false;onChangeCover(track)})
                            }
                            currentAlbum?.let{item->DropdownMenuItem(text={Text("Créditos e encarte")},leadingIcon={Icon(Icons.Default.Info,null)},onClick={moreActionsExpanded=false;onAlbumInfo(item)})}
                            DropdownMenuItem(text={Text("Abrir letra")},leadingIcon={Icon(Icons.Default.MusicNote,null)},onClick={moreActionsExpanded=false;requestLyrics()})
                            DropdownMenuItem(text={Text(if(vinylEnabled)"Mostrar capa do álbum" else "Ativar visual de vinil")},leadingIcon={Icon(Icons.Default.Album,null)},onClick={moreActionsExpanded=false;onVinylToggle(!vinylEnabled)})
                            if(current?.uri?.scheme=="content")DropdownMenuItem(text={Text("Compartilhar arquivo de áudio")},leadingIcon={Icon(Icons.Default.Share,null)},onClick={
                                moreActionsExpanded=false
                                runCatching{Intent(Intent.ACTION_SEND).apply{type="audio/*";putExtra(Intent.EXTRA_STREAM,current.uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}.let{context.startActivity(Intent.createChooser(it,"Compartilhar faixa"))}}
                                    .onFailure{Toast.makeText(context,"Não foi possível compartilhar este arquivo.",Toast.LENGTH_SHORT).show()}
                            })
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                val p=if(scrubbing)preview else s.positionMs.toFloat()
                Slider(value=p.coerceIn(0f,s.durationMs.coerceAtLeast(1).toFloat()),onValueChange={preview=it;scrubbing=true},onValueChangeFinished={player.seekTo(preview.toLong());scrubbing=false},valueRange=0f..s.durationMs.coerceAtLeast(1).toFloat(),colors=SliderDefaults.colors(thumbColor=albumAccent,activeTrackColor=albumAccent,inactiveTrackColor=Line))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(time(p.toLong()),color=Mu,style=MaterialTheme.typography.labelSmall);Text("-${time((s.durationMs-p.toLong()).coerceAtLeast(0))}",color=Mu,style=MaterialTheme.typography.labelSmall)}
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick=player::previous,modifier=Modifier.size(52.dp)){Icon(Icons.Default.SkipPrevious,"Anterior",tint=Tx,modifier=Modifier.size(31.dp))}
                    Spacer(Modifier.width(22.dp))
                    Box(Modifier.size(66.dp).clip(CircleShape).background(albumAccent).clickable{player.toggle()},contentAlignment=Alignment.Center){Icon(if(s.playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Reproduzir ou pausar",tint=Color.White,modifier=Modifier.size(34.dp))}
                    Spacer(Modifier.width(22.dp))
                    IconButton(onClick=player::next,modifier=Modifier.size(52.dp)){Icon(Icons.Default.SkipNext,"Próxima",tint=Tx,modifier=Modifier.size(31.dp))}
                }
                Row(Modifier.fillMaxWidth().padding(top=5.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
                    FilterChip(selected=s.shuffleEnabled,onClick=player::toggleShuffle,label={Text("Aleatório")})
                    FilterChip(selected=s.repeatMode!=androidx.media3.common.Player.REPEAT_MODE_OFF,onClick=player::cycleRepeat,label={Text(when(s.repeatMode){androidx.media3.common.Player.REPEAT_MODE_ONE->"Repetir faixa";androidx.media3.common.Player.REPEAT_MODE_ALL->"Repetir fila";else->"Repetir"})})
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha=.055f)).padding(horizontal=13.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.VolumeDown,"Volume",tint=Mu,modifier=Modifier.size(20.dp));Slider(value=volume,onValueChange={volume=it;audio.setStreamVolume(AudioManager.STREAM_MUSIC,(it*maxVolume).toInt().coerceIn(0,maxVolume),0)},modifier=Modifier.weight(1f).padding(horizontal=10.dp),valueRange=0f..1f,colors=SliderDefaults.colors(thumbColor=albumAccent,activeTrackColor=albumAccent,inactiveTrackColor=Line));Text("${(volume*100).toInt()}%",color=Mu,style=MaterialTheme.typography.labelSmall,modifier=Modifier.width(38.dp),textAlign=TextAlign.End)}
                AudioSpectrumVisualizer(audioSessionId,s.playing)
                Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically){
                    TextButton(onClick=::requestLyrics){Icon(Icons.Default.MusicNote,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(5.dp));Text("Letra",color=Tx)}
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick={onVinylToggle(!vinylEnabled)}){Icon(Icons.Default.Album,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(5.dp));Text(if(vinylEnabled)"Mostrar capa" else "Ativar vinil",color=Tx)}
                }
                if(hasDesktopGrant&&desktopTrackId!=null){
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick={confirmDesktopPlayback=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
                        Icon(Icons.Default.Cast,null,tint=MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Tocar esta faixa no computador",color=Tx)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable(onClick=onQueue).padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.QueueMusic,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(19.dp));Column(Modifier.weight(1f).padding(start=10.dp)){Text("A seguir",color=Tx,style=MaterialTheme.typography.labelLarge);Text("${s.queue.size} faixas · faixa ${if(s.queue.isEmpty())0 else s.currentIndex+1}",color=Mu,style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.KeyboardArrowRight,null,tint=Mu)}
                Spacer(Modifier.height(14.dp))
            }
        }
    }
    if(confirmDesktopPlayback&&desktopTrackId!=null)AlertDialog(
        onDismissRequest={confirmDesktopPlayback=false},
        title={Text("Tocar no Cobre Desktop?")},
        text={Text("O computador vai tocar esta faixa e substituir a fila atual dele. O celular será pausado quando o Desktop aceitar o comando.",color=Mu)},
        confirmButton={TextButton(onClick={confirmDesktopPlayback=false;onPlayOnDesktop(desktopTrackId)}){Text("Tocar no computador",color=MaterialTheme.colorScheme.primary)}},
        dismissButton={TextButton(onClick={confirmDesktopPlayback=false}){Text("Cancelar",color=Mu)}},
    )
    if(showPlaylistDialog&&current!=null)AlertDialog(
        onDismissRequest={showPlaylistDialog=false},
        title={Text("Adicionar à playlist")},
        text={
            Column(Modifier.fillMaxWidth().heightIn(max=380.dp).verticalScroll(rememberScrollState())){
                if(collections.playlists.isEmpty())Text("Crie uma playlist para guardar esta faixa.",color=Mu)
                collections.playlists.forEach{playlist->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable{
                        val alreadyThere=playlist.tracks.any{it.uri==current.uri}
                        val added=collections.addTrackToPlaylist(playlist.name,current)
                        showPlaylistDialog=false
                        Toast.makeText(context,when{added->"Faixa adicionada a ${playlist.name}.";alreadyThere->"A faixa já está nesta playlist.";else->"Não consegui atualizar a playlist."},Toast.LENGTH_SHORT).show()
                    }.padding(horizontal=10.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.QueueMusic,null,tint=MaterialTheme.colorScheme.primary)
                        Text(playlist.name,color=Tx,modifier=Modifier.padding(start=10.dp))
                    }
                }
                OutlinedTextField(value=newPlaylistName,onValueChange={newPlaylistName=it.take(60)},label={Text("Criar nova playlist")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(top=10.dp))
            }
        },
        confirmButton={TextButton(onClick={
            val name=newPlaylistName.trim()
            if(name.isBlank()){showPlaylistDialog=false;return@TextButton}
            val created=collections.savePlaylist(name,listOf(current))
            if(created){newPlaylistName="";showPlaylistDialog=false;Toast.makeText(context,"Playlist criada com esta faixa.",Toast.LENGTH_SHORT).show()}
            else Toast.makeText(context,"Nome já usado ou não foi possível criar a playlist.",Toast.LENGTH_SHORT).show()
        }){Text("Criar",color=MaterialTheme.colorScheme.primary)}},
        dismissButton={TextButton(onClick={showPlaylistDialog=false}){Text("Cancelar",color=Mu)}}
    )
    if(showLyrics) LyricsSheet(current?.title.orEmpty(),current?.artist.orEmpty(),lyrics,lyricsLoading,s.positionMs,{showLyrics=false})
}

@Composable private fun VinylRecord(track:Track?,playing:Boolean,modifier:Modifier=Modifier){
    val rotation=remember{Animatable(0f)}
    LaunchedEffect(playing){if(playing){while(isActive){rotation.animateTo(rotation.value+360f,tween(durationMillis=8_000,easing=LinearEasing))}}else rotation.stop()}
    Box(modifier.graphicsLayer{rotationZ=rotation.value},contentAlignment=Alignment.Center){
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(5.dp)){
            val radius=size.minDimension/2f
            val center=androidx.compose.ui.geometry.Offset(size.width/2f,size.height/2f)
            drawCircle(brush=Brush.radialGradient(listOf(Color(0xFF4B4947),Color(0xFF201F1E),Color(0xFF090909)),center=center,radius=radius),radius=radius,center=center)
            for(index in 1..15){val ring=radius*(.39f+index*.037f);drawCircle(color=Color.White.copy(alpha=if(index%3==0).12f else .055f),radius=ring,center=center,style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.dp.toPx()))}
            drawArc(color=Color.White.copy(alpha=.13f),startAngle=210f,sweepAngle=42f,useCenter=false,topLeft=androidx.compose.ui.geometry.Offset(center.x-radius*.82f,center.y-radius*.82f),size=androidx.compose.ui.geometry.Size(radius*1.64f,radius*1.64f),style=androidx.compose.ui.graphics.drawscope.Stroke(width=radius*.035f))
            drawCircle(color=Color(0xFF090909),radius=radius*.34f,center=center)
        }
        Cover(track,Modifier.fillMaxSize(.48f).clip(CircleShape))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun LyricsSheet(title:String,artist:String,lyrics:TrackLyrics?,loading:Boolean,positionMs:Long,onDismiss:()->Unit){
    val state=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    val active=lyrics?.activeLine(positionMs)?:-1
    val listState=androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(active,lyrics?.synchronized){if(lyrics?.synchronized==true&&active>=0)runCatching{listState.animateScrollToItem(active)}}
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=state,containerColor=Panel,contentColor=Tx){
        Column(Modifier.fillMaxWidth().heightIn(min=300.dp,max=610.dp).padding(horizontal=20.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("LETRA",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium,letterSpacing=1.8.sp);Text(title,color=Tx,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(artist,color=Mu,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)};IconButton(onClick=onDismiss){Icon(Icons.Default.Close,"Fechar letra",tint=Mu)}}
            when{
                loading->Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){CircularProgressIndicator(color=MaterialTheme.colorScheme.primary)}
                lyrics==null->Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){Text("Não encontramos a letra desta faixa. Conecte-se à internet e tente novamente.",color=Mu,textAlign=TextAlign.Center)}
                lyrics.instrumental->Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){Text("Faixa instrumental",color=Mu,style=MaterialTheme.typography.titleMedium)}
                lyrics.synchronized->LazyColumn(state=listState,modifier=Modifier.weight(1f),contentPadding=PaddingValues(top=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){itemsIndexed(lyrics.lines){index,line->Text(line.text,color=if(index==active)Tx else Mu.copy(alpha=.8f),fontSize=if(index==active)22.sp else 19.sp,fontWeight=if(index==active)FontWeight.Bold else FontWeight.Medium,modifier=Modifier.fillMaxWidth())}}
                else->Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){lyrics.lines.forEach{Text(it.text,color=Tx,fontSize=18.sp,lineHeight=27.sp)}}
            }
        }
    }
}

@Composable private fun Cover(track:Track?,modifier:Modifier=Modifier){
    val context=LocalContext.current;val desktop=remember{DesktopLibraryRepository()};val artwork=remember{CoverArtworkRepository(context)};val revision by CoverArtworkEvents.revision.collectAsState();val customFile=remember(track,revision){track?.let(artwork::coverFile)};val coverUri=track?.let(desktop::coverUri)
    val grantToken=CobreIdRepository(context).currentDesktopGrantToken()
    val bitmap by produceState<Bitmap?>(null,track?.uri,coverUri,grantToken,customFile,revision){
        val item=track
        value=if(item==null)null else withContext(Dispatchers.IO){
            val key=customFile?.let{"custom:${it.absolutePath}"}?:coverUri?.let{"desktop:$it"}?:"local:${item.uri}"
            CoverBitmapCache.get(key)?:runCatching{
                val loaded=when{
                    customFile!=null->decodeSampledBitmap(customFile.absolutePath)
                    coverUri==null->context.contentResolver.loadThumbnail(item.uri,Size(360,360),null)
                    else->{
                        val connection=URL(coverUri.toString()).openConnection() as HttpURLConnection
                        try{
                            connection.instanceFollowRedirects=false;connection.connectTimeout=4_000;connection.readTimeout=6_000
                            if(coverUri.path?.startsWith("/v1/")==true&&!grantToken.isNullOrBlank())connection.setRequestProperty("Authorization","Bearer $grantToken")
                            if(connection.responseCode==HttpURLConnection.HTTP_OK)connection.inputStream.use(::decodeSampledBitmap) else null
                        }finally{connection.disconnect()}
                    }
                }
                loaded?.also{CoverBitmapCache.put(key,it)}
            }.getOrNull()
        }
    }
    Box(modifier.background(CardBg),contentAlignment=Alignment.Center){if(bitmap!=null)Image(bitmap!!.asImageBitmap(),track?.album,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Icon(Icons.Default.MusicNote,null,tint=MaterialTheme.colorScheme.primary.copy(alpha=.75f),modifier=Modifier.size(36.dp))}
}

private object CoverBitmapCache {
    private val cache=object:android.util.LruCache<String,Bitmap>(12*1024){override fun sizeOf(key:String,value:Bitmap)=value.byteCount/1024}
    fun get(key:String)=cache.get(key)
    fun put(key:String,bitmap:Bitmap){cache.put(key,bitmap)}
}

private fun decodeSampledBitmap(path:String):Bitmap?{
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeFile(path,bounds)
    bounds.inSampleSize=sampleSize(bounds.outWidth,bounds.outHeight)
    bounds.inJustDecodeBounds=false
    return BitmapFactory.decodeFile(path,bounds)
}

private fun decodeSampledBitmap(stream:java.io.InputStream):Bitmap?{
    val buffered=java.io.BufferedInputStream(stream)
    buffered.mark(16*1024*1024)
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeStream(buffered,null,bounds)
    buffered.reset()
    bounds.inSampleSize=sampleSize(bounds.outWidth,bounds.outHeight)
    bounds.inJustDecodeBounds=false
    return BitmapFactory.decodeStream(buffered,null,bounds)
}

private fun sampleSize(width:Int,height:Int):Int{
    var sample=1
    while(width/sample>720||height/sample>720)sample*=2
    return sample
}

@Composable private fun EmptyState(icon:ImageVector,title:String,message:String,actionLabel:String="Permitir acesso",action:(()->Unit)?=null){Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(42.dp));Spacer(Modifier.height(14.dp));Text(title,color=Tx,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(6.dp));Text(message,color=Mu,textAlign=TextAlign.Center);if(action!=null){Spacer(Modifier.height(16.dp));Button(onClick=action,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary),shape=RoundedCornerShape(24.dp)){Text(actionLabel)}}}}
@Composable private fun Loading(message:String="Lendo sua biblioteca…"){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator(color=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(14.dp));Text(message,color=Mu)}}}
@Composable private fun navColors()=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=MaterialTheme.colorScheme.primary,indicatorColor=MaterialTheme.colorScheme.primary.copy(alpha=.15f),unselectedIconColor=Mu,unselectedTextColor=Mu)
@Composable internal fun fieldColors()=OutlinedTextFieldDefaults.colors(focusedBorderColor=MaterialTheme.colorScheme.primary,unfocusedBorderColor=Line,focusedLabelColor=MaterialTheme.colorScheme.primary,unfocusedLabelColor=Mu,focusedTextColor=Tx,unfocusedTextColor=Tx,cursorColor=MaterialTheme.colorScheme.primary)
private fun time(ms:Long):String{val s=ms.coerceAtLeast(0)/1000;return "${s/60}:${(s%60).toString().padStart(2,'0')}"}
