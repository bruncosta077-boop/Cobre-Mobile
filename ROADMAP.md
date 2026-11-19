# Cobre Mobile — roadmap de paridade com Desktop

## Estado validado

**0.2.0 validado em aparelho pelo autor:** Cobre ID conectado, músicas lidas, reprodução normal e ícone instalado.

**0.3.0 incorporado em 0.4.0:** seletor de pasta, paletas de cor, tela de áudio/configurações.

**0.4.0 em código:** favoritos, histórico recente e playlists locais persistidos em SQLite. O APK ainda precisa de build e teste em aparelho.

## Próximo passo

Compilar e validar o 0.4.0 no aparelho. Depois, avançar a paridade local prioritária:

1. Avaliações e restauração da fila após reiniciar o app.
2. Letras locais e sincronizadas; artista do álbum, disco e total de faixas por tags.
3. Busca e gestão de encartes/metadados.
4. Equalizador, volume prévio, crossfade e efeitos DSP medidos no Android.

## Streaming e sincronização: ordem técnica

Começar pelo **teste de transporte do streaming**: um Desktop e um celular na rede local; medir início, busca dentro da faixa, interrupção e retomada. Em seguida testar outra rede/CGNAT para saber se é necessário WebRTC com relay. Essa etapa tem maior risco de arquitetura e precisa de resposta cedo.

Depois do transporte escolhido, desenvolver autenticação/controle de sessão no servidor para streaming; sincronizar playlists, favoritos, histórico e preferências após os IDs estáveis e as APIs de conflito estarem definidos. Presença de Cobre ID não equivale a streaming.

## Compatibilidade pendente do Desktop

| Recurso do Desktop | Mobile |
|---|---|
| Biblioteca, artistas, álbuns, faixas e reprodução local | Base 0.2 validada; pasta e tema em 0.3 |
| Busca, fila, mini player e player completo | Na base 0.2 |
| Conta e dispositivos | Login/presença integrados; streaming ainda ausente |
| Playlists, favoritos, histórico e estatísticas | Ainda faltam |
| Letras, metadados e créditos de encarte | Ainda faltam |
| Equalizador, crossfade e DSP | Ainda faltam; saída Android precisa de medição |
| Biblioteca Desktop no celular e sincronização | Depende do transporte, backend e IDs estáveis |

## Segurança e plataforma

- Refresh token protegido pelo Android Keystore.
- HTTP aceito apenas para endereços locais privados; acesso pela internet requer HTTPS.
- A tela de áudio lista saídas que o Android anuncia e o volume do sistema. A troca da rota ativa fica sob controle do Android.
- O Cobre Server atual faz contas, sessões, dispositivos e presença; não transmite áudio nem mede velocidade do stream.
