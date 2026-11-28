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

// Telas da biblioteca: cabeçalho, álbuns, artistas, faixas, fila e coleções.
@Composable internal fun Header(page:Page,search:Boolean,query:String,onQuery:(String)->Unit,onSearch:()->Unit,onQueue:()->Unit,onCollection:()->Unit,onRefresh:()->Unit){
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

@Composable internal fun LibraryPage(tracks:List<Track>,library:LocalLibrary,collections:LibraryCollections,offlineTracks:List<Track>,downloadMessage:String,downloadProgress:Int,activeDownload:String?,onDownload:(Track)->Unit,onDownloadBatch:(List<Track>,String)->Unit,tab:LibraryTab,query:String,album:Album?,artist:Artist?,source:LibrarySource,sourceCounts:Map<LibrarySource,Int>,desktopStatus:String,onOpenConnections:()->Unit,onSource:(LibrarySource)->Unit,onTab:(LibraryTab)->Unit,onAlbum:(Album)->Unit,onArtist:(Artist)->Unit,onBack:()->Unit,onPlay:(List<Track>,Int)->Unit,onQueue:(Track)->Unit,onSearchCovers:(Album)->Unit,onAlbumInfo:(Album)->Unit){
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

internal data class LibraryViews(val albums:List<Album>,val artists:List<Artist>)

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun AlbumGrid(albums:List<Album>,onClick:(Album)->Unit,onPlayAlbum:(List<Track>)->Unit={},onQueueAlbum:(List<Track>)->Unit={}){
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

internal fun selectLibrarySource(tracks:List<Track>,source:LibrarySource):List<Track>{
    val selected=when(source){LibrarySource.All->tracks;LibrarySource.Phone->tracks.filter{it.uri.scheme!="http"&&it.uri.scheme!="file"};LibrarySource.Desktop->tracks.filter{it.uri.scheme=="http"};LibrarySource.Downloaded->tracks.filter{it.uri.scheme=="file"}}
    if(source!=LibrarySource.All)return selected
    return selected.distinctBy(Track::libraryIdentity)
}

@Composable internal fun ArtistList(artists:List<Artist>,onClick:(Artist)->Unit){
    LazyColumn(contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp)){items(artists,key={it.name.lowercase()}){a->Row(Modifier.fillMaxWidth().clickable{onClick(a)}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(54.dp).clip(CircleShape).background(CardBg),contentAlignment=Alignment.Center){Text(a.name.take(1).uppercase(),color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,fontSize=20.sp)};Column(Modifier.weight(1f).padding(start=14.dp)){Text(a.name,color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium);Text("${a.albums.size} álbuns · ${a.tracks.size} faixas",color=Mu,style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.Album,null,tint=Mu,modifier=Modifier.size(18.dp))};Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line))}}
}

@Composable internal fun TrackList(tracks:List<Track>,onPlay:(Int)->Unit,onQueue:(Track)->Unit,collections:LibraryCollections?=null,offlineTracks:List<Track> = emptyList(),activeDownload:String?=null,onDownload:(Track)->Unit={}){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
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
                        if(collections!=null)DropdownMenuItem(text={Text(if(t.uri.toString() in favoriteUris)"Remover dos favoritos" else "Adicionar aos favoritos")},leadingIcon={Icon(if(t.uri.toString() in favoriteUris)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null)},onClick={menu=false;scope.launch{collections.toggleFavorite(t)}})
                        if(canDownload)DropdownMenuItem(text={Text(if(saved)"Remover download offline" else "Baixar para ouvir offline")},leadingIcon={Icon(if(saved)Icons.Default.DeleteOutline else Icons.Default.Download,null)},onClick={menu=false;onDownload(t)})
                        if(t.uri.scheme=="content")DropdownMenuItem(text={Text("Compartilhar")},leadingIcon={Icon(Icons.Default.Share,null)},onClick={menu=false;runCatching{Intent(Intent.ACTION_SEND).apply{type="audio/*";putExtra(Intent.EXTRA_STREAM,t.uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}.let{context.startActivity(Intent.createChooser(it,"Compartilhar faixa"))}}})
                    }
                }
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha=.7f)))
        }
    }
}
@Composable internal fun AlbumPage(album:Album,collections:LibraryCollections,onBack:()->Unit,onPlay:(List<Track>,Int)->Unit,onQueue:(Track)->Unit,onDownloadBatch:(List<Track>,String)->Unit,onSearchCovers:()->Unit,onAlbumInfo:()->Unit){
    val scope=rememberCoroutineScope()
    val rating=collections.rating(album)
    LazyColumn(contentPadding=PaddingValues(bottom=18.dp)){item{Column(Modifier.padding(horizontal=18.dp)){TextButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(6.dp));Text("Biblioteca",color=MaterialTheme.colorScheme.primary)};Cover(album.tracks.first(),Modifier.fillMaxWidth().aspectRatio(1.05f).clip(RoundedCornerShape(14.dp)));Text(album.title,color=Tx,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=14.dp));Text(album.artist,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium);Text("${if(album.year>0)"${album.year} · " else ""}${album.tracks.size} faixas",color=Mu,style=MaterialTheme.typography.bodySmall);Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=6.dp)){Text("Sua avaliação",color=Mu,style=MaterialTheme.typography.bodySmall);Spacer(Modifier.width(7.dp));(1..5).forEach{star->IconButton(onClick={scope.launch{collections.setRating(album,if(rating==star)0 else star)}},modifier=Modifier.size(30.dp)){Icon(if(star<=rating)Icons.Default.Star else Icons.Default.StarBorder,"${star} estrelas",tint=if(star<=rating)MaterialTheme.colorScheme.primary else Mu,modifier=Modifier.size(20.dp))}}};Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically){Button(onClick={onPlay(album.tracks,0)},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary),shape=RoundedCornerShape(24.dp)){Icon(Icons.Default.PlayArrow,null);Text("Reproduzir")};TextButton(onClick={onDownloadBatch(album.tracks,"álbum ${album.title}")},enabled=album.tracks.any{it.uri.scheme=="http"}){Icon(Icons.Default.Download,null,tint=MaterialTheme.colorScheme.primary);Text("Offline",color=Tx)};TextButton(onClick={album.tracks.forEach(onQueue)}){Icon(Icons.Default.QueueMusic,null,tint=MaterialTheme.colorScheme.primary);Text("Fila",color=Tx)};TextButton(onClick=onSearchCovers){Icon(Icons.Default.Search,null,tint=MaterialTheme.colorScheme.primary);Text("Capa",color=Tx)};TextButton(onClick=onAlbumInfo){Icon(Icons.Default.AutoStories,null,tint=MaterialTheme.colorScheme.primary);Text("Encarte",color=Tx)}}}}
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
                        DropdownMenuItem(text={Text(if(collections.isFavorite(t))"Remover dos favoritos" else "Adicionar aos favoritos")},leadingIcon={Icon(if(collections.isFavorite(t))Icons.Default.Favorite else Icons.Default.FavoriteBorder,null)},onClick={menu=false;scope.launch{collections.toggleFavorite(t)}})
                    }
                }
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(Line))
        }
    }
}

@Composable internal fun QueuePage(playback:NowPlaying,player:PlayerController,collections:LibraryCollections){
    val scope=rememberCoroutineScope()
    var askSave by remember{mutableStateOf(false)};var playlistName by remember{mutableStateOf("")};var message by remember{mutableStateOf("")}
    if(askSave)AlertDialog(onDismissRequest={askSave=false},title={Text("Salvar como playlist")},text={OutlinedTextField(playlistName,{playlistName=it},singleLine=true,label={Text("Nome da playlist")},colors=fieldColors())},confirmButton={TextButton(onClick={scope.launch{if(collections.savePlaylist(playlistName,playback.queue)){message="Playlist salva.";askSave=false;playlistName=""}else message="Escolha um nome novo e uma fila com faixas."}}){Text("Salvar")}},dismissButton={TextButton(onClick={askSave=false}){Text("Cancelar")}})
    Column(Modifier.fillMaxSize()){Row(Modifier.fillMaxWidth().padding(start=20.dp,end=12.dp,top=16.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("A seguir",color=Tx,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("${playback.queue.size} faixas na fila",color=Mu,style=MaterialTheme.typography.bodySmall)};TextButton(onClick={askSave},enabled=playback.queue.isNotEmpty()){Icon(Icons.Default.PlaylistAdd,null,tint=MaterialTheme.colorScheme.primary);Text("Salvar",color=Tx)};TextButton(onClick=player::clearQueue,enabled=playback.queue.isNotEmpty()){Icon(Icons.Default.DeleteOutline,null,tint=MaterialTheme.colorScheme.primary);Text("Limpar",color=Tx)}}
        if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(horizontal=20.dp,vertical=4.dp))
        if(playback.queue.isEmpty())EmptyState(Icons.Default.QueueMusic,"Sua fila está vazia","Escolha um álbum ou faixa e adicione à fila.")else LazyColumn(contentPadding=PaddingValues(bottom=12.dp)){items(playback.queue.size,key={i->"${playback.queue[i].id}-$i"}){i->val t=playback.queue[i];val current=i==playback.currentIndex;Row(Modifier.fillMaxWidth().background(if(current)CardBg else Color.Transparent).padding(start=16.dp,end=4.dp,top=3.dp,bottom=3.dp),verticalAlignment=Alignment.CenterVertically){Cover(t,Modifier.size(46.dp).clip(RoundedCornerShape(7.dp)));Column(Modifier.weight(1f).clickable{player.playAt(i)}.padding(horizontal=10.dp,vertical=8.dp)){Text(t.title,color=if(current)MaterialTheme.colorScheme.primary else Tx,maxLines=1,overflow=TextOverflow.Ellipsis);Text(t.artist,color=Mu,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)};IconButton(onClick={player.moveInQueue(i,i-1)},enabled=i>0){Icon(Icons.Default.KeyboardArrowUp,"Mover para cima",tint=Mu)};IconButton(onClick={player.moveInQueue(i,i+1)},enabled=i<playback.queue.lastIndex){Icon(Icons.Default.KeyboardArrowDown,"Mover para baixo",tint=Mu)};IconButton(onClick={player.removeFromQueue(i)}){Icon(Icons.Default.Close,"Remover",tint=Mu)}}}}
    }
}

@Composable internal fun CollectionsPage(collections:LibraryCollections,onPlay:(List<Track>)->Unit,onQueue:(Track)->Unit,onDownloadBatch:(List<Track>,String)->Unit){
    val scope=rememberCoroutineScope()
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
                        DropdownMenuItem(text={Text("Excluir playlist")},leadingIcon={Icon(Icons.Default.DeleteOutline,null)},onClick={menu=false;scope.launch{collections.deletePlaylist(playlist.name)}})
                    }
                }
                IconButton(onClick={onPlay(playlist.tracks)},enabled=playlist.tracks.isNotEmpty()){Icon(Icons.Default.PlayArrow,"Reproduzir playlist",tint=MaterialTheme.colorScheme.primary)}
            }
        }
    }
}

@Composable internal fun CollectionTrackRow(track:Track,collections:LibraryCollections,onQueue:(Track)->Unit){
    val scope=rememberCoroutineScope()
    var menu by remember(track.uri){mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().heightIn(min=60.dp),verticalAlignment=Alignment.CenterVertically){
        Cover(track,Modifier.size(42.dp).clip(RoundedCornerShape(7.dp)))
        Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(track.title,color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium);Text("${track.artist} · ${track.album}",color=Mu,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)}
        Box{
            IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Ações da faixa",tint=Mu)}
            DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                DropdownMenuItem(text={Text("Adicionar à fila")},leadingIcon={Icon(Icons.Default.QueueMusic,null)},onClick={menu=false;onQueue(track)})
                DropdownMenuItem(text={Text(if(collections.isFavorite(track))"Remover dos favoritos" else "Adicionar aos favoritos")},leadingIcon={Icon(if(collections.isFavorite(track))Icons.Default.Favorite else Icons.Default.FavoriteBorder,null)},onClick={menu=false;scope.launch{collections.toggleFavorite(track)}})
            }
        }
    }
}
