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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
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
private val desktopAccents=listOf(
    AccentChoice("Cobre",0xFFC9773F.toInt(),0xFFE6A06A.toInt()),
    AccentChoice("Branco e rosa",0xFFF2F2F2.toInt(),0xFFF08CB5.toInt()),
    AccentChoice("Preto e dourado",0xFFB8892B.toInt(),0xFFF4D479.toInt()),
    AccentChoice("Azul e ciano",0xFF4A8DE0.toInt(),0xFF5FD6E0.toInt()),
    AccentChoice("Pôr do sol",0xFFE8604C.toInt(),0xFFF6B45C.toInt()),
    AccentChoice("Roxo e rosa",0xFF8A63D2.toInt(),0xFFE07AB8.toInt()),
    AccentChoice("Verde e limão",0xFF3FA66B.toInt(),0xFFC4E05C.toInt()),
)

@Composable
fun SettingsPage(settings:MobileSettings,folderUri:String?,desktopStatus:String,desktopTrackCount:Int,onPickFolder:()->Unit,onClearFolder:()->Unit,onSaveDesktopUrl:(String)->Unit,onDisconnectDesktop:()->Unit){
    val context=LocalContext.current
    val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var desktopAddress by remember(settings.desktopUrl){mutableStateOf(settings.desktopUrl.orEmpty())}
    var volume by remember{mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()*100f/maxOf(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)))}
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("Configurações",color=Tx,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Ajuste a biblioteca, a aparência e o áudio do Cobre no celular.",color=Mu)
        SettingsCard {
            SectionHeading(Icons.Default.Folder,"Biblioteca")
            Text("Pasta de músicas",color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
            Text(folderUri?.let(::folderLabel)?:"Todo o áudio indexado pelo Android",color=Mu,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=4.dp))
            Row(Modifier.padding(top=9.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick=onPickFolder,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary),shape=RoundedCornerShape(22.dp)){Icon(Icons.Default.Folder,null);Spacer(Modifier.width(6.dp));Text("Escolher pasta")}
                if(folderUri!=null)TextButton(onClick=onClearFolder){Text("Mostrar tudo",color=Mu)}
            }
            Text("A biblioteca passa a mostrar somente músicas dentro da pasta escolhida.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=5.dp))
            Text("Biblioteca do computador",color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=18.dp))
            Text("No Cobre Desktop, abra Acesso remoto e copie o endereço completo. Os dois aparelhos precisam estar na mesma rede Wi-Fi.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=4.dp))
            OutlinedTextField(value=desktopAddress,onValueChange={desktopAddress=it},modifier=Modifier.fillMaxWidth().padding(top=8.dp),label={Text("Endereço do Cobre Desktop")},placeholder={Text("http://192.168.1.20:8787/?k=...")},singleLine=true,colors=fieldColors())
            if(desktopStatus.isNotBlank())Text(desktopStatus,color=if(desktopStatus.startsWith("Computador conectado"))MaterialTheme.colorScheme.primary else Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=5.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=5.dp)){
                Button(onClick={onSaveDesktopUrl(desktopAddress)},enabled=desktopAddress.isNotBlank(),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary),shape=RoundedCornerShape(22.dp)){Icon(Icons.Default.Devices,null);Spacer(Modifier.width(6.dp));Text("Conectar")}
                if(settings.desktopUrl!=null)TextButton(onClick={desktopAddress="";onDisconnectDesktop()}){Text("Desconectar",color=Mu)}
            }
            if(desktopTrackCount>0)Text("As ${desktopTrackCount} faixas do computador aparecem junto com a biblioteca local.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=3.dp))
        }
        SettingsCard {
            SectionHeading(Icons.Default.Palette,"Aparência")
            Text("Cor de destaque",color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
            Text("As mesmas paletas do Cobre Desktop.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=3.dp))
            Column(Modifier.padding(top=10.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                desktopAccents.chunked(4).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){row.forEach{choice->
                    val selected=settings.accentPrimary==choice.primary
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable{settings.setAccent(choice.primary,choice.secondary)}.padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally){
                        Box(Modifier.size(35.dp).clip(CircleShape).background(Color(choice.primary.toLong() and 0xFFFFFFFFL)).border(if(selected)2.dp else 1.dp,if(selected)Tx else Color(choice.secondary.toLong() and 0xFFFFFFFFL),CircleShape),contentAlignment=Alignment.Center){Box(Modifier.size(11.dp).clip(CircleShape).background(Color(choice.secondary.toLong() and 0xFFFFFFFFL)))}
                        Text(choice.name,color=if(selected)Tx else Mu,fontSize=9.sp,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center,modifier=Modifier.padding(top=5.dp))
                    }
                };repeat(4-row.size){Spacer(Modifier.weight(1f))}}}
            }
        }
        SettingsCard {
            SectionHeading(Icons.Default.VolumeUp,"Áudio")
            Text("Volume de música · ${volume.toInt()}%",color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
            Slider(value=volume,onValueChange={volume=it;audio.setStreamVolume(AudioManager.STREAM_MUSIC,(it*maxVolume/100f).roundToInt(),0)},valueRange=0f..100f,colors=SliderDefaults.colors(thumbColor=MaterialTheme.colorScheme.primary,activeTrackColor=MaterialTheme.colorScheme.primary))
            val outputRate=audio.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?:"Não informado"
            val buffer=audio.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?:"Não informado"
            Text("Mixer padrão do Android: $outputRate Hz · buffer $buffer quadros",color=Mu,style=MaterialTheme.typography.bodySmall)
            Text("Saídas de áudio disponíveis",color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
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
        SettingsCard {
            SectionHeading(Icons.Default.AudioFile,"Recursos de áudio")
            Text("Equalizador Cobre",color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
            Text("O equalizador, crossfade e os outros efeitos do Desktop serão portados depois que a saída de áudio do Android estiver medida e validada.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=4.dp))
        }
        SettingsCard {
            SectionHeading(Icons.Default.Refresh,"Aplicativo")
            Text("Cobre Mobile 0.5.0",color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
            Text("A biblioteca do computador é transmitida pela rede local; a conta Cobre ID continua cuidando da presença dos dispositivos.",color=Mu,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=4.dp))
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable private fun SettingsCard(content:@Composable ColumnScope.()->Unit){Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(17.dp)){Column(Modifier.fillMaxWidth().padding(16.dp),content=content)}}
@Composable private fun SectionHeading(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String){Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(21.dp));Text(title,color=Tx,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(start=9.dp))}}
private fun folderLabel(value:String):String=runCatching{val id=DocumentsContract.getTreeDocumentId(Uri.parse(value));id.substringAfter(':',id).trim('/').ifBlank{"Raiz do armazenamento"}}.getOrDefault("Pasta escolhida")
private fun outputName(type:Int,index:Int):String=when(type){AudioDeviceInfo.TYPE_BUILTIN_SPEAKER->"Alto-falante do celular";AudioDeviceInfo.TYPE_BUILTIN_EARPIECE->"Alto-falante de chamadas";AudioDeviceInfo.TYPE_WIRED_HEADSET->"Fone com fio e microfone";AudioDeviceInfo.TYPE_WIRED_HEADPHONES->"Fone com fio";AudioDeviceInfo.TYPE_BLUETOOTH_A2DP->"Bluetooth estéreo";AudioDeviceInfo.TYPE_BLUETOOTH_SCO->"Bluetooth de chamadas";AudioDeviceInfo.TYPE_USB_DEVICE,AudioDeviceInfo.TYPE_USB_HEADSET->"Áudio USB";AudioDeviceInfo.TYPE_HDMI,AudioDeviceInfo.TYPE_HDMI_ARC,AudioDeviceInfo.TYPE_HDMI_EARC->"HDMI";else->"Saída de áudio ${index+1}"}
