# Cobre Mobile — roadmap de paridade com Desktop

## 0.20.0 em desenvolvimento — letras, capas e player

- Busca de letras sincronizadas e simples no LRCLIB, cache local e suporte a arquivos `.lrc`/`.txt` ao lado de faixas offline.
- Busca de capas com correspondência exata e escolha manual em Apple Music e MusicBrainz/Cover Art Archive; capas ficam no celular e não modificam a biblioteca do Desktop.
- Modo visual de vinil com giro enquanto toca e preferência persistente.
- Próxima etapa: validar compilação, leitura dos estados vazio/sem internet e comportamento do painel de letras e da capa escolhida.

## 0.19.0 — publicada como pré-release no GitHub

- Exclusão de conta Cobre ID com senha atual, remoção de sessões e permissões no Server e limpeza de tokens no Mobile.
- Downloads de faixas do Desktop para reprodução offline no armazenamento privado do Mobile. Limites atuais: 2 GB de biblioteca, 512 MB por faixa; só aceita o endpoint HTTP local versionado e o host configurado no app.
- Restauração local da fila, faixa atual, posição, aleatório e repetição; a retomada fica pausada até uma ação explícita do usuário.
- Transferência de uma faixa do Desktop do player Mobile para reprodução no computador, com confirmação explícita da substituição da fila. Primeiro fluxo de sessão compartilhada; transferência de fila completa e sincronização contínua continuam como passos separados.
- Build assinado, verificações de APK/AAB e publicação da pré-release concluídos no workflow 45086261665.
- Validação física pendente: transferência Mobile → Desktop, download interrompido/retomado, remoção do arquivo, falta de espaço, fila restaurada e exclusão da conta enquanto toca uma faixa remota.
- Próxima etapa de distribuição: página pública de privacidade e exclusão para a Play Console; configuração Play App Signing e ficha da loja. Requisitos condicionais incluem teste fechado de 12 pessoas por 14 dias para contas pessoais novas.

## Estado validado

**0.2.0 validado em aparelho pelo autor:** Cobre ID conectado, músicas lidas, reprodução normal e ícone instalado.

**0.3.0 incorporado em 0.4.0:** seletor de pasta, paletas de cor, tela de áudio/configurações.

**0.4.0 incorporado em 0.5.0:** favoritos, histórico recente e playlists locais persistidos em SQLite.

**0.5.0 incorporado em 0.6.0:** agrega a biblioteca LAN do Desktop e envia o áudio diretamente do computador para o Mobile.

**0.6.0 validado pelo autor:** conexão e reprodução da biblioteca Desktop funcionaram; a tela mostra também o tempo de resposta do catálogo.

**0.7.0 em código:** separa fontes local e Desktop, deduplica a visão conjunta, expõe o volume no player, adiciona ações de toque longo e refina a apresentação.

## Próximo passo

O foco do Mobile é paridade funcional com o Desktop, sem trabalho de Play Store nesta etapa:

1. Concluir a compilação assinada do 0.20.0 e validar no celular letras (sincronizadas, sem resultado e offline), busca/seleção de capa, rotação do vinil e preservação da reprodução existente.
2. Avançar metadados e organização da biblioteca: créditos/encartes, edição local de capa sem alterar os arquivos e sincronização clara entre fontes Celular e Computador.
3. Completar recursos de reprodução Android que têm efeito real: gapless e crossfade, com limites explícitos do que o sistema e o hardware permitem. O equalizador, o reforço de graves e a espacialização já controlam efeitos da sessão Android quando compatíveis.
4. Consolidar sincronização entre aparelhos: IDs estáveis, playlists/favoritos/histórico e transferência da sessão/fila, mantendo o áudio direto entre Desktop e Mobile.

Cada bloco deve compilar e manter as funções atuais antes de seguir para o próximo; distribuição para a loja permanece fora do escopo imediato.

## Streaming e sincronização: ordem técnica

O teste local vai usar o endpoint de acesso remoto existente no Desktop, com token e suporte a Range. Para atravessar redes diferentes/CGNAT, será preciso avaliar relay (por exemplo WebRTC) e HTTPS.

O 0.8.0 acrescenta observabilidade do trecho LAN (taxa recebida e latência inicial informadas pelo Mobile). Isso não implementa relay; o Cobre Server continua fora do caminho dos bytes de áudio.

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
