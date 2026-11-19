package app.cobre.mobile

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

@Composable
fun AccountPage(repository:CobreIdRepository,state:AccountUi,onState:(AccountUi)->Unit){
    val scope=rememberCoroutineScope();var serverUrl by remember{mutableStateOf(repository.serverUrl)}
    var email by remember{mutableStateOf(state.email.ifBlank{repository.savedEmail})};var password by remember{mutableStateOf("")};var create by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("Cobre ID",color=Color(0xFFECE6DF),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("Conecte sua conta para acompanhar os dispositivos ligados ao ecossistema Cobre.",color=Color(0xFF9B938B))
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF191816)),shape=RoundedCornerShape(16.dp)){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){Icon(if(state.signedIn&&!state.error)Icons.Default.CheckCircle else Icons.Default.CloudOff,null,tint=if(state.signedIn&&!state.error)Color(0xFF7EB783)else Color(0xFF9B938B));Text(if(state.signedIn&&!state.error)"Servidor conectado" else "Conexão do servidor",color=Color(0xFFECE6DF),fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(start=9.dp))}
                OutlinedTextField(value=serverUrl,onValueChange={serverUrl=it},modifier=Modifier.fillMaxWidth(),label={Text("URL do Cobre Server")},placeholder={Text("https://... ou http://192.168.1.20:8080")},singleLine=true,colors=accountFieldColors())
                Text("O servidor atual usa HTTP local. Para acesso pela internet será necessário HTTPS.",color=Color(0xFF9B938B),style=MaterialTheme.typography.bodySmall)
                Button(onClick={scope.launch{onState(state.copy(busy=true,message="Testando servidor…",error=false));runCatching{repository.saveServerUrl(serverUrl);repository.checkServer()}.onSuccess{ok->onState(state.copy(busy=false,serverReady=ok,message=if(ok)"Servidor respondeu." else "O endereço não confirmou a API Cobre.",error=!ok))}.onFailure{onState(state.copy(busy=false,serverReady=false,message=it.message?:"Falha ao testar servidor.",error=true))}}},enabled=!state.busy,colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF302C28)),shape=RoundedCornerShape(24.dp)){
                    if(state.busy)CircularProgressIndicator(Modifier.size(18.dp),color=MaterialTheme.colorScheme.primary,strokeWidth=2.dp)else Icon(Icons.Default.Refresh,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(8.dp));Text("Testar conexão",color=Color(0xFFECE6DF))
                }
            }
        }
        if(state.signedIn){
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF191816)),shape=RoundedCornerShape(16.dp)){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text(state.email,color=Color(0xFFECE6DF),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Text("Dispositivos",color=Color(0xFF9B938B),style=MaterialTheme.typography.labelLarge)
                    if(state.devices.isEmpty())Text("Ainda não há dispositivos para exibir.",color=Color(0xFF9B938B))
                    state.devices.forEach{device->Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Devices,null,tint=if(device.online)Color(0xFF7EB783)else Color(0xFF9B938B),modifier=Modifier.size(21.dp));Column(Modifier.weight(1f).padding(start=10.dp)){Text(device.name+(if(device.current)" · Este celular" else ""),color=Color(0xFFECE6DF),maxLines=1,overflow=TextOverflow.Ellipsis);Text(if(device.online)"Online" else "Offline",color=if(device.online)Color(0xFF7EB783)else Color(0xFF9B938B),style=MaterialTheme.typography.bodySmall)}}}
                    TextButton(onClick={scope.launch{onState(state.copy(busy=true));runCatching{repository.logout()};onState(AccountUi(message="Sessão encerrada."))}},enabled=!state.busy){Text("Sair da conta",color=MaterialTheme.colorScheme.primary)}
                }
            }
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
        if(state.message.isNotBlank())Text(state.message,color=if(state.error)Color(0xFFE58B78)else Color(0xFF7EB783),style=MaterialTheme.typography.bodySmall)
        Text("A conta conecta dispositivos e presença. O servidor atual ainda não transmite músicas nem sincroniza a biblioteca.",color=Color(0xFF9B938B),style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable private fun accountFieldColors()=OutlinedTextFieldDefaults.colors(focusedBorderColor=MaterialTheme.colorScheme.primary,unfocusedBorderColor=Color(0xFF302C28),focusedLabelColor=MaterialTheme.colorScheme.primary,unfocusedLabelColor=Color(0xFF9B938B),focusedTextColor=Color(0xFFECE6DF),unfocusedTextColor=Color(0xFFECE6DF),cursorColor=MaterialTheme.colorScheme.primary)
