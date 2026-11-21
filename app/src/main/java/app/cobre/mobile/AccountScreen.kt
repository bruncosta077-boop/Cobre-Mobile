package app.cobre.mobile

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun AccountPage(repository:CobreIdRepository,state:AccountUi,desktopOrigin:String,onState:(AccountUi)->Unit,onDesktopGrantChanged:()->Unit={},onScanDesktop:()->Unit={}){
    val scope=rememberCoroutineScope();var serverUrl by remember{mutableStateOf(repository.serverUrl)};var tvCode by remember{mutableStateOf("")}
    var desktopNowPlaying by remember(desktopOrigin){mutableStateOf("")};var desktopVolume by remember(desktopOrigin){mutableFloatStateOf(50f)};var desktopPlaying by remember(desktopOrigin){mutableStateOf(false)}
    var email by remember{mutableStateOf(state.email.ifBlank{repository.savedEmail})};var password by remember{mutableStateOf("")};var create by remember{mutableStateOf(false)}
    LaunchedEffect(repository.desktopGrantId(),desktopOrigin){
        if(repository.desktopGrantId()!=null&&desktopOrigin.isNotBlank())runCatching{repository.readDesktopNowPlaying(desktopOrigin)}.onSuccess{desktopNowPlaying=listOf(it.optString("title"),it.optString("artist")).filter(String::isNotBlank).joinToString(" · ").ifBlank{"Nenhuma faixa informada"};desktopVolume=it.optDouble("volume",50.0).toFloat();desktopPlaying=it.optBoolean("playing")}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("Cobre ID",color=Color(0xFFECE6DF),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Conecte sua conta para acompanhar os dispositivos ligados ao ecossistema Cobre.",color=Color(0xFF9B938B))
        if(state.message.isNotBlank())Text(state.message,color=if(state.error)Color(0xFFE58B78)else Color(0xFF7EB783),style=MaterialTheme.typography.bodySmall)
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF191816)),shape=RoundedCornerShape(16.dp)){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){Icon(if(state.signedIn&&!state.error)Icons.Default.CheckCircle else Icons.Default.CloudOff,null,tint=if(state.signedIn&&!state.error)Color(0xFF7EB783)else Color(0xFF9B938B));Text(if(state.signedIn&&!state.error)"Servidor conectado" else "Conexão do servidor",color=Color(0xFFECE6DF),fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(start=9.dp))}
                OutlinedTextField(value=serverUrl,onValueChange={serverUrl=it},modifier=Modifier.fillMaxWidth(),label={Text("URL do Cobre Server")},placeholder={Text("https://... ou http://192.168.1.20:8080")},singleLine=true,colors=accountFieldColors())
                Text("Use HTTPS em um servidor acessível pela internet. HTTP fica restrito à rede local.",color=Color(0xFF9B938B),style=MaterialTheme.typography.bodySmall)
                Button(onClick={scope.launch{onState(state.copy(busy=true,message="Testando servidor…",error=false));runCatching{repository.saveServerUrl(serverUrl);repository.checkServer()}.onSuccess{ok->onState(state.copy(busy=false,serverReady=ok,message=if(ok)"Servidor respondeu." else "O endereço não confirmou a API Cobre.",error=!ok))}.onFailure{onState(state.copy(busy=false,serverReady=false,message=it.message?:"Falha ao testar servidor.",error=true))}}},enabled=!state.busy,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF302C28)),shape=RoundedCornerShape(24.dp)){
                    if(state.busy)CircularProgressIndicator(Modifier.size(18.dp),color=MaterialTheme.colorScheme.primary,strokeWidth=2.dp)else Icon(Icons.Default.Refresh,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(8.dp));Text("Testar conexão",color=Color(0xFFECE6DF))
                }
            }
        }
        if(state.signedIn){
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF191816)),shape=RoundedCornerShape(16.dp)){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("Conta e dispositivos",color=Color(0xFFECE6DF),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Text(state.email,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium)
                    Text("Dispositivos",color=Color(0xFF9B938B),style=MaterialTheme.typography.labelLarge)
                    if(state.devices.isEmpty())Text("Ainda não há dispositivos para exibir.",color=Color(0xFF9B938B))
                    state.devices.forEach{device->Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Devices,null,tint=if(device.online)Color(0xFF7EB783)else Color(0xFF9B938B),modifier=Modifier.size(21.dp));Column(Modifier.weight(1f).padding(start=10.dp)){Text(device.name+(if(device.current)" · Este celular" else ""),color=Color(0xFFECE6DF),maxLines=1,overflow=TextOverflow.Ellipsis);Text(if(device.online)"Online" else "Offline",color=if(device.online)Color(0xFF7EB783)else Color(0xFF9B938B),style=MaterialTheme.typography.bodySmall)}}}
                }
            }
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF191816)),shape=RoundedCornerShape(16.dp)){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("Biblioteca do computador",color=Color(0xFFECE6DF),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                    Text("Peça acesso no Cobre ID. Depois, o Desktop precisa aprovar a solicitação; o áudio continua indo diretamente pela rede local.",color=Color(0xFF9B938B),style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick=onScanDesktop,enabled=!state.busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp)){
                        Icon(Icons.Default.Devices,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(8.dp));Text("Encontrar Desktop pelo QR",color=Color(0xFFECE6DF))
                    }
                    val desktopDevices=state.devices.filter{it.type.equals("desktop",ignoreCase=true)}
                    if(desktopDevices.isEmpty())Text("Nenhum Desktop vinculado a esta conta ainda.",color=Color(0xFF9B938B),style=MaterialTheme.typography.bodySmall)
                    desktopDevices.forEach{desktop->
                        Button(onClick={scope.launch{
                            onState(state.copy(busy=true,message="Solicitando aprovação ao Desktop…",error=false))
                            runCatching {
                                val request=repository.requestDesktopAccess(desktop.id)
                                var approved:DesktopAccessStatus.Approved?=null
                                val deadline=SystemClock.elapsedRealtime()+request.expiresInSeconds*1_000L
                                while(SystemClock.elapsedRealtime()<deadline){
                                    delay(2_000)
                                    when(val result=repository.pollDesktopAccess(desktop.id,request.requestId)){
                                        DesktopAccessStatus.Pending->onState(state.copy(busy=true,message="No Desktop: Configurações → Conta Cobre → Permitir. Pedido válido por até ${request.expiresInSeconds/60} min.",error=false))
                                        is DesktopAccessStatus.Approved->{approved=result;break}
                                    }
                                }
                                approved?:throw CobreIdException("O pedido expirou. No Desktop, abra Configurações → Conta Cobre e aprove dentro do prazo; depois tente novamente.")
                            }.onSuccess{access->onDesktopGrantChanged();onState(state.copy(busy=false,message="Acesso aprovado. Permissão renovável por até 30 dias; o token é atualizado automaticamente.",error=false))}
                                .onFailure{onState(state.copy(busy=false,message=it.message?:"Não foi possível pedir acesso.",error=true))}
                        }},enabled=!state.busy&&desktop.online,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF302C28)),shape=RoundedCornerShape(24.dp)){
                            if(state.busy)CircularProgressIndicator(Modifier.size(18.dp),color=MaterialTheme.colorScheme.primary,strokeWidth=2.dp)else Icon(Icons.Default.Devices,null,tint=MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp));Text("Pedir acesso a ${desktop.name}",color=Color(0xFFECE6DF),maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                    if(repository.desktopGrantId()!=null){
                        Text("Este celular tem autorização para acessar a biblioteca do Desktop.",color=Color(0xFF7EB783),style=MaterialTheme.typography.bodySmall)
                        Text("Controles do Desktop",color=Color(0xFFECE6DF),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                        val canControlDesktop=desktopOrigin.isNotBlank()
                        if(!canControlDesktop)Text("Informe o endereço local do Desktop em Configurações para habilitar os controles.",color=Color(0xFF9B938B),style=MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
                            IconButton(enabled=canControlDesktop,onClick={scope.launch{runCatching{repository.sendDesktopCommand(desktopOrigin,"previous")}.onFailure{onState(state.copy(message=it.message?:"Falha no controle remoto.",error=true))}}}){Icon(Icons.Default.SkipPrevious,"Faixa anterior",tint=if(canControlDesktop)Color(0xFFECE6DF)else Color(0xFF6A625A))}
                            IconButton(enabled=canControlDesktop,onClick={scope.launch{runCatching{repository.sendDesktopCommand(desktopOrigin,"toggle")}.onSuccess{desktopPlaying=!desktopPlaying}.onFailure{onState(state.copy(message=it.message?:"Falha no controle remoto.",error=true))}}}){Icon(if(desktopPlaying)Icons.Default.Pause else Icons.Default.PlayArrow,"Reproduzir ou pausar no Desktop",tint=if(canControlDesktop)MaterialTheme.colorScheme.primary else Color(0xFF6A625A))}
                            IconButton(enabled=canControlDesktop,onClick={scope.launch{runCatching{repository.sendDesktopCommand(desktopOrigin,"next")}.onFailure{onState(state.copy(message=it.message?:"Falha no controle remoto.",error=true))}}}){Icon(Icons.Default.SkipNext,"Próxima faixa",tint=if(canControlDesktop)Color(0xFFECE6DF)else Color(0xFF6A625A))}
                            IconButton(enabled=canControlDesktop,onClick={scope.launch{runCatching{repository.readDesktopNowPlaying(desktopOrigin)}.onSuccess{desktopNowPlaying=listOf(it.optString("title"),it.optString("artist")).filter(String::isNotBlank).joinToString(" · ").ifBlank{"Nenhuma faixa informada"};desktopVolume=it.optDouble("volume",50.0).toFloat();desktopPlaying=it.optBoolean("playing")}.onFailure{onState(state.copy(message=it.message?:"Não foi possível consultar o Desktop.",error=true))}}}){Icon(Icons.Default.Refresh,"Atualizar estado",tint=if(canControlDesktop)Color(0xFF9B938B)else Color(0xFF6A625A))}
                        }
                        if(desktopNowPlaying.isNotBlank())Text("No computador: $desktopNowPlaying",color=Color(0xFF9B938B),style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.VolumeUp,null,tint=Color(0xFF9B938B));Slider(enabled=canControlDesktop,value=desktopVolume,onValueChange={desktopVolume=it},onValueChangeFinished={scope.launch{runCatching{repository.sendDesktopCommand(desktopOrigin,"volume",desktopVolume.toDouble())}.onFailure{onState(state.copy(message=it.message?:"Falha ao ajustar volume remoto.",error=true))}}},valueRange=0f..100f,modifier=Modifier.weight(1f));Text("${desktopVolume.toInt()}%",color=Color(0xFF9B938B),style=MaterialTheme.typography.labelSmall)}
                        Text("Os controles atuam no player do Desktop; a reprodução de faixas do computador no celular continua na Biblioteca.",color=Color(0xFF9B938B),style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={scope.launch{onState(state.copy(busy=true,message="Removendo autorização…",error=false));runCatching{repository.revokeDesktopGrant()}.onSuccess{onDesktopGrantChanged();onState(state.copy(busy=false,message="Acesso do celular revogado.",error=false))}.onFailure{onState(state.copy(busy=false,message=it.message?:"Não foi possível revogar.",error=true))}}},enabled=!state.busy){Text("Revogar acesso deste celular",color=Color(0xFFE58B78))}
                    }
                }
            }
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF191816)),shape=RoundedCornerShape(16.dp)){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("Autorizar Cobre TV",color=Color(0xFFECE6DF),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                    Text("Na TV, inicie o pareamento e digite aqui o código exibido. Ele expira em poucos minutos.",color=Color(0xFF9B938B),style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value=tvCode,
                        onValueChange={value->tvCode=value.uppercase().filter{it.isLetterOrDigit()||it=='-'}.take(9)},
                        modifier=Modifier.fillMaxWidth(),label={Text("Código da TV")},placeholder={Text("ABCD-EFGH")},singleLine=true,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii),colors=accountFieldColors(),
                    )
                    Button(onClick={scope.launch{
                        onState(state.copy(busy=true,message="Autorizando TV…",error=false))
                        runCatching{val device=repository.authorizeTv(tvCode);val devices=repository.loadDevices();device to devices}
                            .onSuccess{(device,devices)->tvCode="";onState(state.copy(busy=false,devices=devices,message="${device.name} autorizada na sua conta.",error=false))}
                            .onFailure{onState(state.copy(busy=false,message=it.message?:"Não foi possível autorizar a TV.",error=true))}
                    }},enabled=!state.busy&&tvCode.matches(Regex("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}")),modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary,contentColor=Color.White),shape=RoundedCornerShape(24.dp)){
                        if(state.busy)CircularProgressIndicator(Modifier.size(18.dp),color=Color.White,strokeWidth=2.dp)else Text("Autorizar dispositivo")
                    }
                }
            }
            TextButton(onClick={scope.launch{onState(state.copy(busy=true));runCatching{repository.logout()};onDesktopGrantChanged();onState(AccountUi(message="Sessão encerrada."))}},enabled=!state.busy){Text("Sair da conta",color=Color(0xFFE58B78))}
        }else{
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF191816)),shape=RoundedCornerShape(16.dp)){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text(if(create)"Criar Cobre ID" else "Entrar",color=Color(0xFFECE6DF),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    OutlinedTextField(email,{email=it},modifier=Modifier.fillMaxWidth(),label={Text("E-mail")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),colors=accountFieldColors())
                    OutlinedTextField(password,{password=it},modifier=Modifier.fillMaxWidth(),label={Text("Senha")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),colors=accountFieldColors())
                    Button(onClick={scope.launch{onState(state.copy(busy=true,message="Conectando…",error=false));runCatching{repository.login(email,password,create);repository.sendPresence();repository.loadDevices()}.onSuccess{devices->password="";onState(AccountUi(true,email.trim(),devices,message="Conta conectada."))}.onFailure{onState(state.copy(busy=false,message=it.message?:"Não foi possível entrar.",error=true))}}},enabled=!state.busy&&email.isNotBlank()&&password.length>=8,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary,contentColor=Color.White),shape=RoundedCornerShape(24.dp)){
                        if(state.busy)CircularProgressIndicator(Modifier.size(18.dp),color=Color.White,strokeWidth=2.dp)else Text(if(create)"Criar conta" else "Entrar")
                    }
                    TextButton(onClick={create=!create}){Text(if(create)"Já tenho uma conta" else "Criar uma conta Cobre ID",color=MaterialTheme.colorScheme.primary)}
                }
            }
        }
        Text("O Cobre Server identifica os aparelhos e autoriza o acesso. O áudio e a biblioteca passam diretamente entre Desktop e celular na mesma rede local.",color=Color(0xFF9B938B),style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable private fun accountFieldColors()=OutlinedTextFieldDefaults.colors(focusedBorderColor=MaterialTheme.colorScheme.primary,unfocusedBorderColor=Color(0xFF302C28),focusedLabelColor=MaterialTheme.colorScheme.primary,unfocusedLabelColor=Color(0xFF9B938B),focusedTextColor=Color(0xFFECE6DF),unfocusedTextColor=Color(0xFFECE6DF),cursorColor=MaterialTheme.colorScheme.primary)
