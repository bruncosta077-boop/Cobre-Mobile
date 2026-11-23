package app.cobre.mobile

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private data class AccentChoice(val name:String,val primary:Int,val secondary:Int)
private data class EqPreset(val name:String,val bands:List<Int>)
private val equalizerPresets=listOf(
    EqPreset("Plano",listOf(0,0,0,0,0)),
    EqPreset("Rock",listOf(3,2,0,2,3)),
    EqPreset("Metal",listOf(2,1,-2,2,3)),
    EqPreset("Voz",listOf(-1,0,3,2,0)),
    EqPreset("Jazz",listOf(2,1,0,2,2)),
)
private val desktopAccents=listOf(
    AccentChoice("Cobre",0xFFC9773F.toInt(),0xFFE6A06A.toInt()),
    AccentChoice("Rosa suave",0xFFD99AA9.toInt(),0xFFF1C4CE.toInt()),
    AccentChoice("Dourado",0xFFD2AC67.toInt(),0xFFE8CC8F.toInt()),
    AccentChoice("Azul aço",0xFF6D91B8.toInt(),0xFF91B7C7.toInt()),
    AccentChoice("Terracota",0xFFC97863.toInt(),0xFFDCA779.toInt()),
    AccentChoice("Lavanda",0xFF9B80C2.toInt(),0xFFC698B0.toInt()),
    AccentChoice("Verde sálvia",0xFF77A786.toInt(),0xFFB4C39A.toInt()),
)

@Composable
fun SettingsPage(settings:MobileSettings,folderUri:String?,desktopStatus:String,desktopTrackCount:Int,offlineStorage:OfflineStorageSummary,offlineClearBusy:Boolean,offlineClearMessage:String,backupStatus:String,backupBusy:Boolean,onExportBackup:()->Unit,onImportBackup:()->Unit,updateStatus:String,playDistribution:Boolean,updateBusy:Boolean,updateProgress:Int,updateReady:Boolean,onPickFolder:()->Unit,onClearFolder:()->Unit,onClearOffline:()->Unit,onOpenAccount:()->Unit,onRetryDesktop:()->Unit,onCheckUpdates:()->Unit,onInstallUpdate:()->Unit){
    val context=LocalContext.current
    val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var showReleaseNotes by remember{mutableStateOf(false)}
    var showOutputDetails by remember{mutableStateOf(false)}
    var showAdvancedAudio by remember{mutableStateOf(false)}
    var confirmClearOffline by remember{mutableStateOf(false)}
    var confirmImportBackup by remember{mutableStateOf(false)}
    var volume by remember{mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()*100f/maxOf(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)))}
    val audioPrefs=remember{context.getSharedPreferences(PlaybackService.AUDIO_PREFS,Context.MODE_PRIVATE)}
    val equalizerBands=remember{mutableStateListOf<Float>().apply{repeat(5){add(audioPrefs.getInt("eq_band_$it",0).toFloat())}}}
    var equalizerEnabled by remember{mutableStateOf(audioPrefs.getBoolean("eq_enabled",false))}
    var replayGainEnabled by remember{mutableStateOf(audioPrefs.getBoolean("replaygain_enabled",false))}
    var equalizerAvailable by remember{mutableStateOf(audioPrefs.getBoolean("eq_available",false))}
    var audioSessionReady by remember{mutableStateOf(audioPrefs.getBoolean("audio_session_ready",false))}
    var bassEnabled by remember{mutableStateOf(audioPrefs.getBoolean("bass_enabled",false))}
    var bassAvailable by remember{mutableStateOf(audioPrefs.getBoolean("bass_available",false))}
    var bassStrength by remember{mutableFloatStateOf(audioPrefs.getInt("bass_strength",25).toFloat())}
    var virtualizerEnabled by remember{mutableStateOf(audioPrefs.getBoolean("virtualizer_enabled",false))}
    var virtualizerAvailable by remember{mutableStateOf(audioPrefs.getBoolean("virtualizer_available",false))}
    var virtualizerStrength by remember{mutableFloatStateOf(audioPrefs.getInt("virtualizer_strength",20).toFloat())}
    var outputs by remember{mutableStateOf(audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList())}
    val maxVolume=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    DisposableEffect(audio){
        val callback=object:AudioDeviceCallback(){
            override fun onAudioDevicesAdded(addedDevices:Array<out AudioDeviceInfo>){outputs=audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()}
            override fun onAudioDevicesRemoved(removedDevices:Array<out AudioDeviceInfo>){outputs=audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()}
        }
        audio.registerAudioDeviceCallback(callback,Handler(Looper.getMainLooper()))
        onDispose{audio.unregisterAudioDeviceCallback(callback)}
    }
    DisposableEffect(audioPrefs){
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->when(key){"eq_available"->equalizerAvailable=audioPrefs.getBoolean("eq_available",false);"audio_session_ready"->audioSessionReady=audioPrefs.getBoolean("audio_session_ready",false);"bass_available"->bassAvailable=audioPrefs.getBoolean("bass_available",false);"virtualizer_available"->virtualizerAvailable=audioPrefs.getBoolean("virtualizer_available",false)}}
        audioPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{audioPrefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    LaunchedEffect(settings.portableRestoreRevision){
        equalizerEnabled=audioPrefs.getBoolean("eq_enabled",false)
        replayGainEnabled=audioPrefs.getBoolean("replaygain_enabled",false)
        bassEnabled=audioPrefs.getBoolean("bass_enabled",false);bassStrength=audioPrefs.getInt("bass_strength",25).toFloat()
        virtualizerEnabled=audioPrefs.getBoolean("virtualizer_enabled",false);virtualizerStrength=audioPrefs.getInt("virtualizer_strength",20).toFloat()
        repeat(5){equalizerBands[it]=audioPrefs.getInt("eq_band_$it",0).toFloat()}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("Configurações",color=Tx,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Ajuste a biblioteca, a aparência e o áudio do Cobre no celular.",color=Mu)
        SettingsCard {
            SectionHeading(Icons.Default.Folder,"Biblioteca do celular")
            Text("Pasta de músicas",color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
            Text(folderUri?.let(::folderLabel)?:"Todo o áudio indexado pelo Android",color=Mu,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=4.dp))
            Row(Modifier.padding(top=9.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick=onPickFolder,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary),shape=RoundedCornerShape(22.dp)){Icon(Icons.Default.Folder,null);Spacer(Modifier.width(6.dp));Text("Escolher pasta")}
                if(folderUri!=null)TextButton(onClick=onClearFolder){Text("Mostrar tudo",color=Mu)}
            }
            Text("A biblioteca passa a mostrar somente músicas dentro da pasta escolhida.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=5.dp))
        }
        SettingsCard {
            SectionHeading(Icons.Default.AudioFile,"Downloads offline")
            Text("${offlineStorage.trackCount} faixas · ${formatStorage(offlineStorage.usedBytes)} de ${formatStorage(offlineStorage.limitBytes)}",color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=10.dp))
            LinearProgressIndicator(progress={(offlineStorage.usedBytes.toFloat()/offlineStorage.limitBytes.coerceAtLeast(1L)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().padding(top=9.dp),color=MaterialTheme.colorScheme.primary,trackColor=Line)
            Text("Downloads privados do Cobre. Os arquivos originais no computador não são alterados.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=7.dp))
            if(offlineClearMessage.isNotBlank())Text(offlineClearMessage,color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=5.dp))
            OutlinedButton(onClick={confirmClearOffline=true},enabled=offlineStorage.trackCount>0&&!offlineClearBusy,modifier=Modifier.fillMaxWidth().padding(top=8.dp),shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Line)){
                if(offlineClearBusy)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)else Icon(Icons.Default.DeleteOutline,null,tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(7.dp));Text(if(offlineClearBusy)"Removendo downloads…" else "Remover todos os downloads",color=Tx)
            }
        }
        SettingsCard {
            SectionHeading(Icons.Default.Settings,"Backup de configurações")
            Text("Salva aparência, vinil, cores da capa e ajustes de áudio em um arquivo que você pode guardar e importar depois.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp))
            Text("Por segurança, o backup não inclui Cobre ID, tokens, endereço do computador, pasta autorizada ou músicas baixadas.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=5.dp))
            Row(Modifier.fillMaxWidth().padding(top=9.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick=onExportBackup,enabled=!backupBusy,modifier=Modifier.weight(1f),shape=RoundedCornerShape(22.dp)){Text("Exportar",color=Tx)}
                OutlinedButton(onClick={confirmImportBackup=true},enabled=!backupBusy,modifier=Modifier.weight(1f),shape=RoundedCornerShape(22.dp)){Text("Importar",color=Tx)}
            }
            if(backupBusy)LinearProgressIndicator(modifier=Modifier.fillMaxWidth().padding(top=7.dp),color=MaterialTheme.colorScheme.primary,trackColor=Line)
            if(backupStatus.isNotBlank())Text(backupStatus,color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=5.dp))
        }
        SettingsCard {
            SectionHeading(Icons.Default.Devices,"Conexão")
            val connected=desktopStatus.startsWith("Computador conectado")
            val hasError=desktopStatus.contains("HTTP")||desktopStatus.contains("não foi")||desktopStatus.contains("não alcança")||desktopStatus.contains("expirou")||desktopStatus.contains("não conseguiu")||desktopStatus.contains("indisponível")||desktopStatus.contains("Peça acesso")
            val statusText=when {
                connected -> "$desktopTrackCount faixas disponíveis na biblioteca"
                desktopStatus.startsWith("Conectando") -> "Conectando ao Desktop…"
                desktopStatus.isNotBlank() -> desktopStatus
                settings.desktopUrl.isNullOrBlank() -> "Nenhum computador conectado"
                else -> "Conexão salva · toque para verificar"
            }
            Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(9.dp).clip(CircleShape).background(if(connected)Color(0xFF7EB783)else if(hasError)Color(0xFFE58B78)else Mu))
                Text(statusText,color=if(connected)Color(0xFF7EB783)else Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(start=9.dp).weight(1f),maxLines=4,overflow=TextOverflow.Ellipsis)
            }
            Button(onClick=if(settings.desktopUrl.isNullOrBlank()||hasError)onOpenAccount else onRetryDesktop,modifier=Modifier.fillMaxWidth().padding(top=8.dp),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary),shape=RoundedCornerShape(22.dp)){
                Icon(if(settings.desktopUrl.isNullOrBlank()||hasError)Icons.Default.QrCodeScanner else Icons.Default.Refresh,null);Spacer(Modifier.width(8.dp));Text(when{settings.desktopUrl.isNullOrBlank()->"Conectar pelo QR";hasError->"Refazer conexão pelo QR";else->"Atualizar biblioteca"})
            }
            Text("Use o mesmo Cobre ID do computador. Leia o QR uma vez e aprove no Desktop; endereço e acesso ficam salvos neste celular.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=4.dp))
        }
        SettingsCard {
            SectionHeading(Icons.Default.Palette,"Aparência")
            Text("Cor de destaque",color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
            Text("Paletas ajustadas para manter o contraste no tema escuro.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=3.dp))
            Column(Modifier.padding(top=10.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                desktopAccents.chunked(4).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){row.forEach{choice->
                    val selected=settings.accentPrimary==choice.primary
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable{settings.setAccent(choice.primary,choice.secondary)}.padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally){
                        Box(Modifier.size(35.dp).clip(CircleShape).background(Color(choice.primary.toLong() and 0xFFFFFFFFL)).border(if(selected)2.dp else 1.dp,if(selected)Tx else Color(choice.secondary.toLong() and 0xFFFFFFFFL),CircleShape),contentAlignment=Alignment.Center){Box(Modifier.size(11.dp).clip(CircleShape).background(Color(choice.secondary.toLong() and 0xFFFFFFFFL)))}
                        Text(choice.name,color=if(selected)Tx else Mu,fontSize=9.sp,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center,modifier=Modifier.padding(top=5.dp))
                    }
                };repeat(4-row.size){Spacer(Modifier.weight(1f))}}}
            }
            Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Player de vinil",color=Tx,fontWeight=FontWeight.SemiBold);Text("Capa no centro de um disco animado durante a reprodução.",color=Mu,style=MaterialTheme.typography.bodySmall)};Switch(checked=settings.vinylPlayer,onCheckedChange=settings::saveVinylPlayer)}
            Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Cores da capa no player",color=Tx,fontWeight=FontWeight.SemiBold);Text("Adapta o fundo e o destaque à capa da faixa atual.",color=Mu,style=MaterialTheme.typography.bodySmall)};Switch(checked=settings.dynamicAlbumColors,onCheckedChange=settings::saveDynamicAlbumColors)}
        }
        SettingsCard {
            SectionHeading(Icons.Default.VolumeUp,"Áudio")
            Text("Volume de música · ${volume.toInt()}%",color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
            Slider(value=volume,onValueChange={volume=it;audio.setStreamVolume(AudioManager.STREAM_MUSIC,(it*maxVolume/100f).roundToInt(),0)},valueRange=0f..100f,colors=SliderDefaults.colors(thumbColor=MaterialTheme.colorScheme.primary,activeTrackColor=MaterialTheme.colorScheme.primary))
            val outputRate=audio.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?:"Não informado"
            val buffer=audio.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?:"Não informado"
            Text("Mixer padrão do Android: $outputRate Hz · buffer $buffer quadros",color=Mu,style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={showOutputDetails=!showOutputDetails},modifier=Modifier.fillMaxWidth()){
                Icon(if(showOutputDetails)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null,tint=MaterialTheme.colorScheme.primary)
                Text(if(showOutputDetails)"Ocultar detalhes da saída" else "Saída de áudio e opções",color=Tx)
            }
            if(showOutputDetails){
            Text("Saídas de áudio disponíveis",color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=8.dp))
            if(outputs.isEmpty())Text("Nenhuma saída informada pelo Android.",color=Mu,style=MaterialTheme.typography.bodySmall)
            outputs.forEachIndexed{index,device->
                Column(Modifier.fillMaxWidth().padding(vertical=7.dp)){
                    Text(device.productName?.toString()?.takeIf{it.isNotBlank()}?:outputName(device.type,index),color=Tx,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("${outputName(device.type,index)} · ${device.sampleRates.takeIf{it.isNotEmpty()}?.joinToString(" / "){"$it Hz"}?:"taxa gerenciada pelo Android"} · ${device.channelCounts.size.coerceAtLeast(1)} configuração(ões) de canal",color=Mu,style=MaterialTheme.typography.bodySmall)
                }
            }
            Text("O Android escolhe a rota ativa quando você conecta um fone, Bluetooth ou USB; esta tela mostra as saídas disponíveis.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=4.dp))
            TextButton(onClick={runCatching{context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))}}){Icon(Icons.Default.Settings,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(7.dp));Text("Abrir áudio do Android",color=Tx)}
            }
        }
        SettingsCard {
            SectionHeading(Icons.Default.AudioFile,"Áudio")
            Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Normalizar volume (ReplayGain)",color=Tx,fontWeight=FontWeight.SemiBold);Text("Lê ganho e pico gravados nas tags da faixa. Sem tags, mantém o volume original.",color=Mu,style=MaterialTheme.typography.bodySmall)};Switch(checked=replayGainEnabled,onCheckedChange={replayGainEnabled=it;audioPrefs.edit().putBoolean("replaygain_enabled",it).apply()})}
            TextButton(onClick={showAdvancedAudio=!showAdvancedAudio},modifier=Modifier.fillMaxWidth()){
                Icon(if(showAdvancedAudio)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null,tint=MaterialTheme.colorScheme.primary)
                Text(if(showAdvancedAudio)"Ocultar equalizador e efeitos" else "Equalizador, graves e áudio espacial",color=Tx)
            }
            if(showAdvancedAudio){
            Text("Ajuste o timbre da reprodução local e das faixas recebidas do Desktop.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp))
            Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Equalizador",color=Tx,fontWeight=FontWeight.SemiBold);Text(when{equalizerAvailable->"Aplicado pelo mecanismo de áudio do Android";!audioSessionReady->"Inicie uma faixa para verificar suporte nesta saída";else->"Este aparelho não disponibilizou o efeito de equalização"},color=Mu,style=MaterialTheme.typography.bodySmall)};Switch(checked=equalizerEnabled,onCheckedChange={equalizerEnabled=it;audioPrefs.edit().putBoolean("eq_enabled",it).apply()})}
            if(equalizerEnabled&&equalizerAvailable){
                val labels=listOf("Subgrave","Graves","Médios","Presença","Agudos")
                labels.forEachIndexed{index,label->Column(Modifier.fillMaxWidth().padding(top=3.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=Tx,style=MaterialTheme.typography.bodySmall);Text("${if(equalizerBands[index]>0)"+" else ""}${equalizerBands[index].toInt()} dB",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall)};Slider(value=equalizerBands[index],onValueChange={value->equalizerBands[index]=value;audioPrefs.edit().putInt("eq_band_$index",value.toInt()).apply()},valueRange=-12f..12f,steps=23,colors=SliderDefaults.colors(thumbColor=MaterialTheme.colorScheme.primary,activeTrackColor=MaterialTheme.colorScheme.primary,inactiveTrackColor=Line))}}
            }
            val selectedPreset=equalizerPresets.firstOrNull{preset->equalizerBands.indices.all{equalizerBands[it].toInt()==preset.bands[it]}}?.name?:"Personalizado"
            Text("Perfis de som · $selectedPreset",color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=9.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                equalizerPresets.forEach{preset->
                    val selected=selectedPreset==preset.name
                    OutlinedButton(onClick={
                        preset.bands.forEachIndexed{index,value->equalizerBands[index]=value.toFloat()}
                        audioPrefs.edit().apply{preset.bands.forEachIndexed{index,value->putInt("eq_band_$index",value)}}.apply()
                    },colors=ButtonDefaults.outlinedButtonColors(containerColor=if(selected)MaterialTheme.colorScheme.primary.copy(alpha=.18f)else Color.Transparent,contentColor=if(selected)MaterialTheme.colorScheme.primary else Tx),border=BorderStroke(1.dp,if(selected)MaterialTheme.colorScheme.primary else Line),contentPadding=PaddingValues(horizontal=14.dp,vertical=5.dp)){
                        Text(preset.name)
                    }
                }
            }
            Text("A quantidade e a faixa de frequências disponíveis dependem do aparelho. O equalizador só altera o áudio reproduzido pelo Cobre.",color=Mu,style=MaterialTheme.typography.bodySmall)
            HorizontalDivider(Modifier.padding(vertical=10.dp),color=Line)
            AudioEffectControl(
                title="Graves reforçados", description="Dá mais presença às frequências baixas.", available=bassAvailable, sessionReady=audioSessionReady,
                enabled=bassEnabled, strength=bassStrength, strengthLabel="Intensidade",
                onEnabled={bassEnabled=it;audioPrefs.edit().putBoolean("bass_enabled",it).apply()},
                onStrength={bassStrength=it;audioPrefs.edit().putInt("bass_strength",it.toInt()).apply()},
            )
            HorizontalDivider(Modifier.padding(vertical=10.dp),color=Line)
            AudioEffectControl(
                title="Áudio espacial", description="Amplia a sensação estéreo; o resultado depende da saída.", available=virtualizerAvailable, sessionReady=audioSessionReady,
                enabled=virtualizerEnabled, strength=virtualizerStrength, strengthLabel="Largura",
                onEnabled={virtualizerEnabled=it;audioPrefs.edit().putBoolean("virtualizer_enabled",it).apply()},
                onStrength={virtualizerStrength=it;audioPrefs.edit().putInt("virtualizer_strength",it.toInt()).apply()},
            )
            Text("Os efeitos são vinculados à sessão de áudio. Alguns aparelhos ou saídas podem não oferecer graves reforçados ou áudio espacial; nesses casos o Cobre informa a indisponibilidade.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=5.dp))
            }
        }
        SettingsCard {
            SectionHeading(Icons.Default.Refresh,"Aplicativo")
            Text("Cobre Mobile ${BuildConfig.VERSION_NAME}",color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
            Text("Cobre ID aprova e renova o acesso. As músicas do Desktop são transmitidas diretamente pela rede local.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=4.dp))
            if(playDistribution){
                Text("As atualizações desta edição são entregues pela Google Play Store.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp))
            }else{
                Text(updateStatus,color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp))
                if(updateBusy)LinearProgressIndicator(progress={updateProgress/100f},modifier=Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick=onCheckUpdates,enabled=!updateBusy,modifier=Modifier.weight(1f),shape=RoundedCornerShape(22.dp)){
                        Icon(Icons.Default.Refresh,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(6.dp));Text("Verificar",color=Tx)
                    }
                    Button(onClick=onInstallUpdate,enabled=!updateBusy&&(updateStatus.startsWith("Atualização")||updateReady),modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary),shape=RoundedCornerShape(22.dp)){
                        Text(if(updateBusy)"Aguarde…" else if(updateReady)"Instalar" else "Baixar e instalar")
                    }
                }
            }
            TextButton(onClick={showReleaseNotes=true}){Text("Sobre o Cobre Mobile",color=MaterialTheme.colorScheme.primary)}
            Text("Com contribuição de dilucas00",color=Mu,style=MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
    }
    if(confirmClearOffline)AlertDialog(
        onDismissRequest={confirmClearOffline=false},
        title={Text("Remover downloads offline?")},
        text={Text("Isso apagará ${offlineStorage.trackCount} faixas e liberará ${formatStorage(offlineStorage.usedBytes)} neste celular. A biblioteca e os arquivos originais do computador permanecem intactos.")},
        confirmButton={TextButton(onClick={confirmClearOffline=false;onClearOffline()}){Text("Remover",color=MaterialTheme.colorScheme.primary)}},
        dismissButton={TextButton(onClick={confirmClearOffline=false}){Text("Cancelar",color=Tx)}},
    )
    if(confirmImportBackup)AlertDialog(
        onDismissRequest={confirmImportBackup=false},
        title={Text("Importar configurações?")},
        text={Text("A aparência e os ajustes de áudio atuais serão substituídos. O backup não altera biblioteca, pasta, computador conectado ou conta Cobre ID.")},
        confirmButton={TextButton(onClick={confirmImportBackup=false;onImportBackup()}){Text("Escolher backup",color=MaterialTheme.colorScheme.primary)}},
        dismissButton={TextButton(onClick={confirmImportBackup=false}){Text("Cancelar",color=Tx)}},
    )
    if(showReleaseNotes)AlertDialog(
        onDismissRequest={showReleaseNotes=false},
        title={Text("Cobre Mobile ${BuildConfig.VERSION_NAME}")},
        text={Text("Cobre Mobile reúne a biblioteca do celular e do computador, com autorização Cobre ID, transmissão local, controles de reprodução e volume sempre visível. O Cobre Server aprova o acesso e não recebe os arquivos de áudio.")},
        confirmButton={TextButton(onClick={showReleaseNotes=false}){Text("Entendi")}},
    )
}

@Composable private fun AudioEffectControl(
    title:String,description:String,available:Boolean,sessionReady:Boolean,enabled:Boolean,strength:Float,strengthLabel:String,
    onEnabled:(Boolean)->Unit,onStrength:(Float)->Unit,
){
    Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){
            Text(title,color=Tx,fontWeight=FontWeight.SemiBold)
            Text(when{available->description;!sessionReady->"Inicie uma faixa para verificar suporte nesta saída";else->"Não disponível nesta saída de áudio"},color=Mu,style=MaterialTheme.typography.bodySmall)
        }
        Switch(checked=enabled,onCheckedChange=onEnabled,enabled=available)
    }
    if(enabled&&available){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(strengthLabel,color=Mu,style=MaterialTheme.typography.bodySmall);Text("${strength.toInt()}%",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall)}
        Slider(value=strength,onValueChange=onStrength,valueRange=0f..100f,steps=19,colors=SliderDefaults.colors(thumbColor=MaterialTheme.colorScheme.primary,activeTrackColor=MaterialTheme.colorScheme.primary,inactiveTrackColor=Line))
    }
}

@Composable private fun SettingsCard(content:@Composable ColumnScope.()->Unit){Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(17.dp)){Column(Modifier.fillMaxWidth().padding(16.dp),content=content)}}
@Composable private fun SectionHeading(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String){Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(21.dp));Text(title,color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(start=9.dp))}}
private fun folderLabel(value:String):String=runCatching{val id=DocumentsContract.getTreeDocumentId(Uri.parse(value));id.substringAfter(':',id).trim('/').ifBlank{"Raiz do armazenamento"}}.getOrDefault("Pasta escolhida")
private fun formatStorage(bytes:Long):String=when{bytes>=1024L*1024*1024->"${"%.1f".format(java.util.Locale.ROOT,bytes.toDouble()/(1024.0*1024*1024))} GB";bytes>=1024L*1024->"${"%.0f".format(java.util.Locale.ROOT,bytes.toDouble()/(1024.0*1024))} MB";else->"${bytes/1024} KB"}
private fun outputName(type:Int,index:Int):String=when(type){AudioDeviceInfo.TYPE_BUILTIN_SPEAKER->"Alto-falante do celular";AudioDeviceInfo.TYPE_BUILTIN_EARPIECE->"Alto-falante de chamadas";AudioDeviceInfo.TYPE_WIRED_HEADSET->"Fone com fio e microfone";AudioDeviceInfo.TYPE_WIRED_HEADPHONES->"Fone com fio";AudioDeviceInfo.TYPE_BLUETOOTH_A2DP->"Bluetooth estéreo";AudioDeviceInfo.TYPE_BLUETOOTH_SCO->"Bluetooth de chamadas";AudioDeviceInfo.TYPE_USB_DEVICE,AudioDeviceInfo.TYPE_USB_HEADSET->"Áudio USB";AudioDeviceInfo.TYPE_HDMI,AudioDeviceInfo.TYPE_HDMI_ARC,AudioDeviceInfo.TYPE_HDMI_EARC->"HDMI";else->"Saída de áudio ${index+1}"}
