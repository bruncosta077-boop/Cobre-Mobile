package app.cobre.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg=Color(0xFF10100F); private val Panel=Color(0xFF191816); private val CardBg=Color(0xFF211F1D)
private val Line=Color(0xFF302C28); private val Tx=Color(0xFFECE6DF); private val Mu=Color(0xFF9B938B)
private val Green=Color(0xFF7EB783)
private enum class Page { Library, Queue, Account, Settings }
private enum class LibraryTab { Albums, Artists, Tracks }
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
    val context=LocalContext.current; val library=remember{LocalLibrary(context)}; val accountRepo=remember{CobreIdRepository(context)}
    val permission=if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED)}
    val askAudio=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted=it}
    val askNotifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    var folderUri by remember{mutableStateOf(settings.folderUri)}
    val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null){runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)};settings.setFolder(uri.toString());folderUri=uri.toString()}}
    var tracks by remember{mutableStateOf<List<Track>?>(null)}; var search by remember{mutableStateOf(false)}; var query by remember{mutableStateOf("")}
    var page by remember{mutableStateOf(Page.Library)}; var tab by remember{mutableStateOf(LibraryTab.Albums)}
    var album by remember{mutableStateOf<Album?>(null)}; var artist by remember{mutableStateOf<Artist?>(null)}; var fullPlayer by remember{mutableStateOf(false)}
    val playback by player.state.collectAsState(); var account by remember{mutableStateOf(AccountUi(email=accountRepo.savedEmail))}; val scope=rememberCoroutineScope()
    LaunchedEffect(Unit){if(!granted)askAudio.launch(permission); if(accountRepo.hasSavedSession&&accountRepo.serverUrl.isNotBlank()){
        account=account.copy(busy=true,message="Reconectando ao Cobre ID…")
        runCatching{accountRepo.restoreSession();accountRepo.sendPresence();accountRepo.loadDevices()}.onSuccess{account=AccountUi(true,accountRepo.savedEmail,it,message="Conta conectada.")}.onFailure{account=AccountUi(email=accountRepo.savedEmail,message=it.message?:"Sessão expirada.",error=true)}
    }}
    LaunchedEffect(granted,folderUri){tracks=if(granted)runCatching{library.load(folderUri?.let{Uri.parse(it)})}.getOrDefault(emptyList()) else null}
    LaunchedEffect(account.signedIn){while(account.signedIn){delay(45_000);runCatching{accountRepo.sendPresence();accountRepo.loadDevices()}.onSuccess{account=account.copy(devices=it,message="Conexão ativa.",error=false)}.onFailure{account=account.copy(message=it.message?:"Conexão interrompida.",error=true)}}}
    LaunchedEffect(playback.playing,fullPlayer){while(playback.playing&&fullPlayer){delay(450);player.refreshPosition()}}
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()){
        if(fullPlayer&&playback.hasItem) PlayerPage(playback,player,{fullPlayer=false},{fullPlayer=false;page=Page.Queue})
        else {
            Header(page,search,query,{query=it},{search=!search;query="";tab=LibraryTab.Tracks;album=null;artist=null},{scope.launch{granted=ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED;if(granted)tracks=runCatching{library.load(folderUri?.let{Uri.parse(it)})}.getOrDefault(emptyList())}})
            Box(Modifier.weight(1f).fillMaxWidth()){
                when(page){
                    Page.Library->when{
                        !granted->EmptyState(Icons.Default.LibraryMusic,"Sua música começa aqui","Permita o acesso ao áudio para montar sua biblioteca neste aparelho."){askAudio.launch(permission)}
                        tracks==null->Loading()
                        else->LibraryPage(tracks.orEmpty(),library,tab,query,album,artist,{tab=it;album=null;artist=null},{album=it},{artist=it},{album=null;artist=null},{list,index->
                            if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            player.play(list,index)
                        },player::addToQueue)
                    }
                    Page.Queue->QueuePage(playback,player)
                    Page.Account->AccountPage(accountRepo,account){account=it}
                    Page.Settings->SettingsPage(settings,folderUri,{folderPicker.launch(folderUri?.let{Uri.parse(it)})},{folderUri?.let{old->runCatching{context.contentResolver.releasePersistableUriPermission(Uri.parse(old),Intent.FLAG_GRANT_READ_URI_PERMISSION)}};settings.setFolder(null);folderUri=null})
                }
            }
            if(playback.hasItem) MiniPlayer(playback,player){fullPlayer=true}
            NavigationBar(containerColor=Panel){
                NavigationBarItem(page==Page.Library,{page=Page.Library;search=false;query=""},{Icon(Icons.Default.LibraryMusic,null)},label={Text("Biblioteca")},colors=navColors())
                NavigationBarItem(page==Page.Queue,{page=Page.Queue;search=false},{Icon(Icons.Default.QueueMusic,null)},label={Text("Fila")},colors=navColors())
                NavigationBarItem(page==Page.Account,{page=Page.Account;search=false},{Icon(Icons.Default.Person,null)},label={Text("Cobre ID")},colors=navColors())
                NavigationBarItem(page==Page.Settings,{page=Page.Settings;search=false},{Icon(Icons.Default.Settings,null)},label={Text("Ajustes")},colors=navColors())
            }
        }
    }
}

@Composable private fun Header(page:Page,search:Boolean,query:String,onQuery:(String)->Unit,onSearch:()->Unit,onRefresh:()->Unit){
    Column {
        Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){
            Image(painterResource(R.drawable.ic_cobre_logo),"Cobre",Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)))
            Column(Modifier.weight(1f).padding(start=10.dp)){Text("COBRE",color=Tx,fontSize=18.sp,letterSpacing=3.sp,fontWeight=FontWeight.Bold);Text(when(page){Page.Library->"BIBLIOTECA";Page.Queue->"A SEGUIR";Page.Account->"COBRE ID";Page.Settings->"CONFIGURAÇÕES"},color=Mu,fontSize=10.sp,letterSpacing=1.5.sp)}
            if(page==Page.Library){IconButton(onClick=onSearch){Icon(if(search)Icons.Default.Close else Icons.Default.Search,"Buscar",tint=Tx)};IconButton(onClick=onRefresh){Icon(Icons.Default.Refresh,"Atualizar biblioteca",tint=Mu)}}
        }
        if(page==Page.Library&&search) OutlinedTextField(query,onQuery,Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),placeholder={Text("Buscar faixa, álbum ou artista",color=Mu)},leadingIcon={Icon(Icons.Default.Search,null,tint=MaterialTheme.colorScheme.primary)},singleLine=true,shape=RoundedCornerShape(18.dp),colors=fieldColors())
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}

@Composable private fun LibraryPage(tracks:List<Track>,library:LocalLibrary,tab:LibraryTab,query:String,album:Album?,artist:Artist?,onTab:(LibraryTab)->Unit,onAlbum:(Album)->Unit,onArtist:(Artist)->Unit,onBack:()->Unit,onPlay:(List<Track>,Int)->Unit,onQueue:(Track)->Unit){
    if(album!=null){AlbumPage(album,onBack,onPlay,onQueue);return}
    if(artist!=null){Column{Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Voltar",tint=MaterialTheme.colorScheme.primary)};Column{Text(artist.name,color=Tx,style=MaterialTheme.typography.titleLarge);Text("${artist.albums.size} álbuns · ${artist.tracks.size} faixas",color=Mu,style=MaterialTheme.typography.bodySmall)}};AlbumGrid(artist.albums,onAlbum)};return}
    val q=query.trim().lowercase();val filtered=remember(tracks,q){if(q.isBlank())tracks else tracks.filter{it.title.lowercase().contains(q)||it.artist.lowercase().contains(q)||it.albumArtist.lowercase().contains(q)||it.album.lowercase().contains(q)}}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically){LibraryTab.entries.forEach{item->val selected=item==tab;Text(when(item){LibraryTab.Albums->"Álbuns";LibraryTab.Artists->"Artistas";LibraryTab.Tracks->"Faixas"},color=if(selected)Tx else Mu,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal,modifier=Modifier.clip(CircleShape).background(if(selected)CardBg else Color.Transparent).clickable{onTab(item)}.padding(horizontal=14.dp,vertical=9.dp))};Spacer(Modifier.weight(1f));Text("${filtered.size} faixas",color=Mu,style=MaterialTheme.typography.labelSmall)}
        when{filtered.isEmpty()->EmptyState(Icons.Default.LibraryMusic,if(q.isBlank())"Sua biblioteca está vazia" else "Nada encontrado",if(q.isBlank())"Adicione músicas ao aparelho e atualize a biblioteca." else "Tente outro nome de faixa, álbum ou artista.")
            tab==LibraryTab.Albums->AlbumGrid(library.albums(filtered),onAlbum)
            tab==LibraryTab.Artists->ArtistList(library.artists(filtered),onArtist)
            else->TrackList(filtered,{i->onPlay(filtered,i)},onQueue)}
    }
}

@Composable private fun AlbumGrid(albums:List<Album>,onClick:(Album)->Unit){
    LazyVerticalGrid(GridCells.Fixed(2),contentPadding=PaddingValues(16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        items(albums,key=Album::key){a->Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable{onClick(a)}){Cover(a.tracks.first(),Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)));Text(a.title,color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=8.dp));Text(a.artist,color=Mu,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall);Text("${a.tracks.size} faixas${if(a.year>0)" · ${a.year}" else ""}",color=Mu.copy(alpha=.7f),style=MaterialTheme.typography.labelSmall)} }
    }
}

@Composable private fun ArtistList(artists:List<Artist>,onClick:(Artist)->Unit){
    LazyColumn(contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp)){items(artists,key={it.name.lowercase()}){a->Row(Modifier.fillMaxWidth().clickable{onClick(a)}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(54.dp).clip(CircleShape).background(CardBg),contentAlignment=Alignment.Center){Text(a.name.take(1).uppercase(),color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,fontSize=20.sp)};Column(Modifier.weight(1f).padding(start=14.dp)){Text(a.name,color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium);Text("${a.albums.size} álbuns · ${a.tracks.size} faixas",color=Mu,style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.Album,null,tint=Mu,modifier=Modifier.size(18.dp))};Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line))}}
}

@Composable private fun TrackList(tracks:List<Track>,onPlay:(Int)->Unit,onQueue:(Track)->Unit){
    LazyColumn(contentPadding=PaddingValues(start=16.dp,end=10.dp,bottom=16.dp)){items(tracks,key={"${it.id}-${it.uri}"}){t->Row(Modifier.fillMaxWidth().heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.MusicNote,null,tint=Mu,modifier=Modifier.size(22.dp));Column(Modifier.weight(1f).clickable{onPlay(tracks.indexOf(t))}.padding(horizontal=12.dp,vertical=10.dp)){Text(t.title,color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${t.artist} · ${t.album}",color=Mu,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)};Text(time(t.durationMs),color=Mu,style=MaterialTheme.typography.labelSmall);IconButton(onClick={onQueue(t)}){Icon(Icons.Default.Add,"Adicionar à fila",tint=MaterialTheme.colorScheme.primary)}};Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha=.7f)))}}
}

@Composable private fun AlbumPage(album:Album,onBack:()->Unit,onPlay:(List<Track>,Int)->Unit,onQueue:(Track)->Unit){
    LazyColumn(contentPadding=PaddingValues(bottom=18.dp)){item{Column(Modifier.padding(horizontal=18.dp)){TextButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(6.dp));Text("Biblioteca",color=MaterialTheme.colorScheme.primary)};Cover(album.tracks.first(),Modifier.fillMaxWidth().aspectRatio(1.05f).clip(RoundedCornerShape(14.dp)));Text(album.title,color=Tx,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=14.dp));Text(album.artist,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium);Text("${if(album.year>0)"${album.year} · " else ""}${album.tracks.size} faixas",color=Mu,style=MaterialTheme.typography.bodySmall);Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.padding(vertical=12.dp)){Button(onClick={onPlay(album.tracks,0)},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary),shape=RoundedCornerShape(24.dp)){Icon(Icons.Default.PlayArrow,null);Text("Reproduzir")};TextButton(onClick={album.tracks.forEach(onQueue)}){Icon(Icons.Default.QueueMusic,null,tint=MaterialTheme.colorScheme.primary);Text("Fila",color=Tx)}}}}
        items(album.tracks,key={it.id}){t->Row(Modifier.fillMaxWidth().clickable{onPlay(album.tracks,album.tracks.indexOf(t))}.padding(horizontal=22.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Text(if(t.trackNumber>0)t.trackNumber.toString() else "•",color=Mu,modifier=Modifier.width(30.dp));Column(Modifier.weight(1f)){Text(t.title,color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis);if(t.artist!=album.artist)Text(t.artist,color=Mu,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)};Text(time(t.durationMs),color=Mu,style=MaterialTheme.typography.labelSmall);IconButton(onClick={onQueue(t)}){Icon(Icons.Default.Add,"Adicionar à fila",tint=Mu)}};Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line))}
    }
}

@Composable private fun QueuePage(playback:NowPlaying,player:PlayerController){
    Column(Modifier.fillMaxSize()){Row(Modifier.fillMaxWidth().padding(start=20.dp,end=12.dp,top=16.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("A seguir",color=Tx,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("${playback.queue.size} faixas na fila",color=Mu,style=MaterialTheme.typography.bodySmall)};TextButton(onClick=player::clearQueue,enabled=playback.queue.isNotEmpty()){Icon(Icons.Default.DeleteOutline,null,tint=MaterialTheme.colorScheme.primary);Text("Limpar",color=Tx)}}
        if(playback.queue.isEmpty())EmptyState(Icons.Default.QueueMusic,"Sua fila está vazia","Escolha um álbum ou faixa e adicione à fila.")else LazyColumn(contentPadding=PaddingValues(bottom=12.dp)){items(playback.queue.size,key={i->"${playback.queue[i].id}-$i"}){i->val t=playback.queue[i];val current=i==playback.currentIndex;Row(Modifier.fillMaxWidth().background(if(current)CardBg else Color.Transparent).padding(start=16.dp,end=4.dp,top=3.dp,bottom=3.dp),verticalAlignment=Alignment.CenterVertically){Cover(t,Modifier.size(46.dp).clip(RoundedCornerShape(7.dp)));Column(Modifier.weight(1f).clickable{player.playAt(i)}.padding(horizontal=10.dp,vertical=8.dp)){Text(t.title,color=if(current)MaterialTheme.colorScheme.primary else Tx,maxLines=1,overflow=TextOverflow.Ellipsis);Text(t.artist,color=Mu,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)};IconButton(onClick={player.moveInQueue(i,i-1)},enabled=i>0){Icon(Icons.Default.KeyboardArrowUp,"Mover para cima",tint=Mu)};IconButton(onClick={player.moveInQueue(i,i+1)},enabled=i<playback.queue.lastIndex){Icon(Icons.Default.KeyboardArrowDown,"Mover para baixo",tint=Mu)};IconButton(onClick={player.removeFromQueue(i)}){Icon(Icons.Default.Close,"Remover",tint=Mu)}}}}
    }
}

@Composable private fun MiniPlayer(s:NowPlaying,player:PlayerController,onOpen:()->Unit){
    Column(Modifier.fillMaxWidth().background(Panel)){Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal=11.dp),verticalAlignment=Alignment.CenterVertically){Cover(s.queue.getOrNull(s.currentIndex),Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick=onOpen));Column(Modifier.weight(1f).clickable(onClick=onOpen).padding(horizontal=10.dp)){Text(s.title.ifBlank{"Reproduzindo"},color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium);Text(s.artist,color=Mu,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)};IconButton(onClick=player::previous){Icon(Icons.Default.SkipPrevious,"Anterior",tint=Tx,modifier=Modifier.size(21.dp))};IconButton(onClick=player::toggle){Icon(if(s.playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Reproduzir ou pausar",tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(27.dp))};IconButton(onClick=player::next){Icon(Icons.Default.SkipNext,"Próxima",tint=Tx,modifier=Modifier.size(21.dp))}};val progress=if(s.durationMs>0)(s.positionMs.toFloat()/s.durationMs).coerceIn(0f,1f)else 0f;Box(Modifier.fillMaxWidth().height(2.dp).background(Line)){Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary))}}
}

@Composable private fun PlayerPage(s:NowPlaying,player:PlayerController,onClose:()->Unit,onQueue:()->Unit){
    var scrubbing by remember{mutableStateOf(false)};var preview by remember{mutableFloatStateOf(0f)}
    Column(Modifier.fillMaxSize().navigationBarsPadding()){
        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onClose){Icon(Icons.Default.KeyboardArrowDown,"Minimizar",tint=Tx)};Text("TOCANDO AGORA",color=Mu,letterSpacing=2.sp,style=MaterialTheme.typography.labelMedium,modifier=Modifier.weight(1f),textAlign=TextAlign.Center);IconButton(onClick=onQueue){Icon(Icons.Default.QueueMusic,"Fila",tint=MaterialTheme.colorScheme.primary)}}
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=28.dp),horizontalAlignment=Alignment.CenterHorizontally){Spacer(Modifier.height(8.dp));Cover(s.queue.getOrNull(s.currentIndex),Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)));Spacer(Modifier.height(24.dp));Column(Modifier.fillMaxWidth()){Text(s.title,color=Tx,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(s.artist,color=Mu,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)}
            val p=if(scrubbing)preview else s.positionMs.toFloat();Slider(value=p.coerceIn(0f,s.durationMs.coerceAtLeast(1).toFloat()),onValueChange={preview=it;scrubbing=true},onValueChangeFinished={player.seekTo(preview.toLong());scrubbing=false},valueRange=0f..s.durationMs.coerceAtLeast(1).toFloat(),colors=SliderDefaults.colors(thumbColor=MaterialTheme.colorScheme.primary,activeTrackColor=MaterialTheme.colorScheme.primary,inactiveTrackColor=Line));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(time(p.toLong()),color=Mu,style=MaterialTheme.typography.labelSmall);Text("-${time((s.durationMs-p.toLong()).coerceAtLeast(0))}",color=Mu,style=MaterialTheme.typography.labelSmall)}
            Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){IconButton(onClick=player::toggleShuffle){Icon(Icons.Default.Shuffle,"Aleatório",tint=if(s.shuffleEnabled)MaterialTheme.colorScheme.primary else Mu)};IconButton(onClick=player::previous,modifier=Modifier.size(54.dp)){Icon(Icons.Default.SkipPrevious,"Anterior",tint=Tx,modifier=Modifier.size(34.dp))};Box(Modifier.size(70.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable{player.toggle()},contentAlignment=Alignment.Center){Icon(if(s.playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Reproduzir ou pausar",tint=Color.White,modifier=Modifier.size(38.dp))};IconButton(onClick=player::next,modifier=Modifier.size(54.dp)){Icon(Icons.Default.SkipNext,"Próxima",tint=Tx,modifier=Modifier.size(34.dp))};IconButton(onClick=player::cycleRepeat){Icon(if(s.repeatMode==androidx.media3.common.Player.REPEAT_MODE_ONE)Icons.Default.RepeatOne else Icons.Default.Repeat,"Repetir",tint=if(s.repeatMode==androidx.media3.common.Player.REPEAT_MODE_OFF)Mu else MaterialTheme.colorScheme.primary)}}
            Row(Modifier.fillMaxWidth().padding(top=18.dp).clip(RoundedCornerShape(14.dp)).background(Panel).clickable(onClick=onQueue).padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.QueueMusic,null,tint=MaterialTheme.colorScheme.primary);Column(Modifier.weight(1f).padding(start=12.dp)){Text("Fila de reprodução",color=Tx,fontWeight=FontWeight.Medium);Text("${s.queue.size} faixas",color=Mu,style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.KeyboardArrowDown,null,tint=Mu)};Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun Cover(track:Track?,modifier:Modifier=Modifier){
    val context=LocalContext.current;val bitmap by produceState<Bitmap?>(null,track?.id){value=if(track==null)null else withContext(Dispatchers.IO){runCatching{context.contentResolver.loadThumbnail(track.uri,Size(480,480),null)}.getOrNull()}}
    Box(modifier.background(CardBg),contentAlignment=Alignment.Center){if(bitmap!=null)Image(bitmap!!.asImageBitmap(),track?.album,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Icon(Icons.Default.MusicNote,null,tint=MaterialTheme.colorScheme.primary.copy(alpha=.75f),modifier=Modifier.size(36.dp))}
}

@Composable private fun EmptyState(icon:ImageVector,title:String,message:String,action:(()->Unit)?=null){Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(42.dp));Spacer(Modifier.height(14.dp));Text(title,color=Tx,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(6.dp));Text(message,color=Mu,textAlign=TextAlign.Center);if(action!=null){Spacer(Modifier.height(16.dp));Button(onClick=action,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary),shape=RoundedCornerShape(24.dp)){Text("Permitir acesso")}}}}
@Composable private fun Loading(){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator(color=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(14.dp));Text("Lendo sua biblioteca…",color=Mu)}}}
@Composable private fun navColors()=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=MaterialTheme.colorScheme.primary,indicatorColor=MaterialTheme.colorScheme.primary.copy(alpha=.15f),unselectedIconColor=Mu,unselectedTextColor=Mu)
@Composable private fun fieldColors()=OutlinedTextFieldDefaults.colors(focusedBorderColor=MaterialTheme.colorScheme.primary,unfocusedBorderColor=Line,focusedLabelColor=MaterialTheme.colorScheme.primary,unfocusedLabelColor=Mu,focusedTextColor=Tx,unfocusedTextColor=Tx,cursorColor=MaterialTheme.colorScheme.primary)
private fun time(ms:Long):String{val s=ms.coerceAtLeast(0)/1000;return "${s/60}:${(s%60).toString().padStart(2,'0')}"}
