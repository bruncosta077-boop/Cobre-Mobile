# Cobre Mobile — roadmap de paridade com Desktop

## Estado validado

**0.2.0 validado em aparelho pelo autor:** Cobre ID conectado, músicas lidas, reprodução normal e ícone instalado.

**0.3.0 incorporado em 0.4.0:** seletor de pasta, paletas de cor, tela de áudio/configurações.

**0.4.0 incorporado em 0.5.0:** favoritos, histórico recente e playlists locais persistidos em SQLite.

**0.5.0 em código:** agrega a biblioteca LAN do Desktop e envia o áudio diretamente do computador para o Mobile. Ainda requer build e validação pelo autor no aparelho.

## Próximo passo

Compilar e validar o 0.5.0 no aparelho. Depois, avançar a paridade local prioritária:

1. Avaliações e restauração da fila após reiniciar o app.
2. Letras locais e sincronizadas; artista do álbum, disco e total de faixas por tags.
3. Busca e gestão de encartes/metadados.
4. Equalizador, volume prévio, crossfade e efeitos DSP medidos no Android.

## Streaming e sincronização: ordem técnica

O teste local vai usar o endpoint de acesso remoto existente no Desktop, com token e suporte a Range. Para atravessar redes diferentes/CGNAT, será preciso avaliar relay (por exemplo WebRTC) e HTTPS.

Depois, registrar no Cobre Server a disponibilidade do Desktop e desenhar a sincronização de playlists, favoritos, histórico e preferências com IDs estáveis e regras de conflito. Presença de Cobre ID não equivale a streaming.

## Compatibilidade pendente do Desktop

| Recurso do Desktop | Mobile |
|---|---|
| Biblioteca, artistas, álbuns, faixas e reprodução | Base local 0.2 validada; fonte LAN Desktop implementada em 0.5, ainda sem confirmação no aparelho |
| Busca, fila, mini player e player completo | Na base 0.2 |
| Conta e dispositivos | Login/presença integrados; ainda sem relay de internet |
| Playlists, favoritos, histórico e estatísticas | Favoritos, histórico e playlists locais em 0.4; sincronização e estatísticas faltam |
| Letras, metadados e créditos de encarte | Ainda faltam |
| Equalizador, crossfade e DSP | Ainda faltam; saída Android precisa de medição |
| Biblioteca Desktop no celular e sincronização | Depende do transporte, backend e IDs estáveis |

## Segurança e plataforma

- Refresh token protegido pelo Android Keystore.
- HTTP aceito apenas para endereços locais privados; acesso pela internet requer HTTPS.
- A tela de áudio lista saídas que o Android anuncia e o volume do sistema. A troca da rota ativa fica sob controle do Android.
- O Cobre Server atual faz contas, sessões, dispositivos e presença; não transmite áudio nem mede velocidade do stream.
