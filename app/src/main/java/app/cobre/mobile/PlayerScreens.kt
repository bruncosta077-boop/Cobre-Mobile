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

// Mini player, player completo, vinil, letras e capas.
@Composable internal fun MiniPlayer(s:NowPlaying,player:PlayerController,onOpen:()->Unit){
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

@Composable internal fun PlayerPage(s:NowPlaying,player:PlayerController,desktopOrigin:String,hasDesktopGrant:Boolean,vinylEnabled:Boolean,dynamicAlbumColors:Boolean,artworkRepository:CoverArtworkRepository,grantToken:String?,onVinylToggle:(Boolean)->Unit,onClose:()->Unit,onQueue:()->Unit,onPlayOnDesktop:(Int)->Unit,onChangeCover:(Track)->Unit,collections:LibraryCollections,onAlbumInfo:(Album)->Unit){
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
                    Column(Modifier.weight(1f)){
                        Text(s.title,color=Tx,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(s.artist,color=Mu,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(buildString{append(current?.album?:"Álbum desconhecido");if((current?.year?:0)>0)append(" · ${current?.year}");if((current?.trackNumber?:0)>0)append(" · Faixa ${current?.trackNumber}")},color=Mu.copy(alpha=.8f),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=3.dp))
                        if(s.formatLabel.isNotBlank()) Text(s.formatLabel,color=albumAccent,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=6.dp))
                    }
                    Box{
                        IconButton(onClick={moreActionsExpanded=true}){Icon(Icons.Default.MoreVert,"Mais ações da faixa",tint=Mu)}
                        DropdownMenu(expanded=moreActionsExpanded,onDismissRequest={moreActionsExpanded=false}){
                            current?.let{track->
                                DropdownMenuItem(text={Text(if(collections.isFavorite(track))"Remover dos favoritos" else "Adicionar aos favoritos")},leadingIcon={Icon(if(collections.isFavorite(track))Icons.Default.Favorite else Icons.Default.FavoriteBorder,null)},onClick={moreActionsExpanded=false;lyricScope.launch{collections.toggleFavorite(track)}})
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
                Row(Modifier.fillMaxWidth().padding(top=2.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick=player::toggleShuffle,modifier=Modifier.size(42.dp)) {
                        Icon(Icons.Default.Shuffle,contentDescription=if(s.shuffleEnabled)"Desativar aleatório" else "Ativar aleatório",tint=if(s.shuffleEnabled)albumAccent else Mu,modifier=Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(18.dp))
                    IconButton(onClick=player::cycleRepeat,modifier=Modifier.size(42.dp)) {
                        Icon(if(s.repeatMode==androidx.media3.common.Player.REPEAT_MODE_ONE)Icons.Default.RepeatOne else Icons.Default.Repeat,contentDescription=when(s.repeatMode){androidx.media3.common.Player.REPEAT_MODE_ONE->"Repetir faixa";androidx.media3.common.Player.REPEAT_MODE_ALL->"Repetir fila";else->"Repetir desligado"},tint=if(s.repeatMode!=androidx.media3.common.Player.REPEAT_MODE_OFF)albumAccent else Mu,modifier=Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha=.055f)).padding(horizontal=13.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.VolumeDown,"Volume",tint=Mu,modifier=Modifier.size(20.dp));Slider(value=volume,onValueChange={volume=it;audio.setStreamVolume(AudioManager.STREAM_MUSIC,(it*maxVolume).toInt().coerceIn(0,maxVolume),0)},modifier=Modifier.weight(1f).padding(horizontal=10.dp),valueRange=0f..1f,colors=SliderDefaults.colors(thumbColor=albumAccent,activeTrackColor=albumAccent,inactiveTrackColor=Line));Text("${(volume*100).toInt()}%",color=Mu,style=MaterialTheme.typography.labelSmall,modifier=Modifier.width(38.dp),textAlign=TextAlign.End)}
                AudioSpectrumVisualizer(s.playing)
                if(hasDesktopGrant&&desktopTrackId!=null){
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick={confirmDesktopPlayback=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
                        Icon(Icons.Default.Cast,null,tint=MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Tocar esta faixa no computador",color=Tx)
                    }
                }
                Spacer(Modifier.height(12.dp))
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
                        lyricScope.launch{
                            val added=collections.addTrackToPlaylist(playlist.name,current)
                            showPlaylistDialog=false
                            Toast.makeText(context,when{added->"Faixa adicionada a ${playlist.name}.";alreadyThere->"A faixa já está nesta playlist.";else->"Não consegui atualizar a playlist."},Toast.LENGTH_SHORT).show()
                        }
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
            lyricScope.launch{
                val created=collections.savePlaylist(name,listOf(current))
                if(created){newPlaylistName="";showPlaylistDialog=false;Toast.makeText(context,"Playlist criada com esta faixa.",Toast.LENGTH_SHORT).show()}
                else Toast.makeText(context,"Nome já usado ou não foi possível criar a playlist.",Toast.LENGTH_SHORT).show()
            }
        }){Text("Criar",color=MaterialTheme.colorScheme.primary)}},
        dismissButton={TextButton(onClick={showPlaylistDialog=false}){Text("Cancelar",color=Mu)}}
    )
    if(showLyrics) LyricsSheet(current?.title.orEmpty(),current?.artist.orEmpty(),lyrics,lyricsLoading,s.positionMs,{showLyrics=false})
}

@Composable internal fun VinylRecord(track:Track?,playing:Boolean,modifier:Modifier=Modifier){
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
@Composable internal fun LyricsSheet(title:String,artist:String,lyrics:TrackLyrics?,loading:Boolean,positionMs:Long,onDismiss:()->Unit){
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

@Composable internal fun Cover(track:Track?,modifier:Modifier=Modifier){
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

internal object CoverBitmapCache {
    private val cache=object:android.util.LruCache<String,Bitmap>(12*1024){override fun sizeOf(key:String,value:Bitmap)=value.byteCount/1024}
    fun get(key:String)=cache.get(key)
    fun put(key:String,bitmap:Bitmap){cache.put(key,bitmap)}
}

internal fun decodeSampledBitmap(path:String):Bitmap?{
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeFile(path,bounds)
    bounds.inSampleSize=sampleSize(bounds.outWidth,bounds.outHeight)
    bounds.inJustDecodeBounds=false
    return BitmapFactory.decodeFile(path,bounds)
}

internal fun decodeSampledBitmap(stream:java.io.InputStream):Bitmap?{
    val buffered=java.io.BufferedInputStream(stream)
    buffered.mark(16*1024*1024)
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeStream(buffered,null,bounds)
    buffered.reset()
    bounds.inSampleSize=sampleSize(bounds.outWidth,bounds.outHeight)
    bounds.inJustDecodeBounds=false
    return BitmapFactory.decodeStream(buffered,null,bounds)
}

internal fun sampleSize(width:Int,height:Int):Int{
    var sample=1
    while(width/sample>720||height/sample>720)sample*=2
    return sample
}
