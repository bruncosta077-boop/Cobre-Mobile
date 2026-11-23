# Histórico de versões — Cobre Mobile

## 0.41.0-rc.1 — gravações locais consistentes

- Gravações de favoritos, avaliações, playlists, histórico e reassociação do catálogo agora compartilham uma trava assíncrona; ações simultâneas não leem estado intermediário e se sobrescrevem.
- Remover favorito e impedir faixas repetidas em playlists usam a URL normalizada sem query nem fragmento, igual à chave salva no banco.
- Build, assinatura e publicação pendentes no GitHub Actions.

## 0.40.0-rc.1 — leitura eficiente das coleções

- Leitura de playlists agora usa um `LEFT JOIN` ordenado em uma consulta, eliminando uma consulta extra por playlist e preservando playlists vazias.
- Migração de banco v4 cria índices de ordenação para favoritos, histórico e playlists sem remover registros existentes.
- Build, assinatura estável do APK/AAB e publicação com checksums SHA-256 passaram no [GitHub Actions](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45276795425). Pacote 42 / versionCode 42. A migração do banco ainda não foi exercitada num aparelho nesta rodada.

## 0.39.0-rc.1 — atualização direta mais confiável

- A edição de instalação direta volta a mostrar a atualização já encontrada se o app for fechado/reaberto dentro do intervalo de consulta, incluindo as notas da release.
- A consulta automática ocorre ao abrir e uma vez por hora enquanto o app permanece aberto; a janela de cache caiu de seis para uma hora.
- APKs baixados continuam sujeitos à verificação de hash quando informado, nome do pacote, versão superior e assinatura igual à instalada antes de chamar o instalador Android.
- Build, assinatura estável do APK/AAB e publicação com checksums SHA-256 passaram no [GitHub Actions](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45275413913). Pacote 41 / versionCode 41. A instalação em aparelho ainda não foi validada nesta rodada.

## 0.38.0-rc.1 — coleções carregadas sem bloquear a interface

- A primeira leitura do banco de favoritos, histórico, playlists e avaliações foi movida para I/O; os dados prontos são publicados em estado do app.
- Salvar favoritos, editar playlists, avaliar álbuns, associar faixas remotas e registrar histórico também rodam fora da thread da interface.
- Ações aguardam a inicialização única das coleções antes de gravar, protegendo os dados existentes no início do app.
- Build, assinatura estável do APK/AAB e publicação com checksums SHA-256 passaram no [GitHub Actions](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45273969194). Pacote 40 / versionCode 40. A instalação em aparelho ainda não foi validada nesta rodada.

## 0.37.0-rc.1 — histórico sem travar a reprodução

- Ao começar outra faixa, gravar a reprodução, limitar o histórico e atualizar a lista de recentes agora ocorre em I/O, sem consultas SQLite na thread da interface.
- Build, assinatura estável do APK/AAB e publicação com checksums SHA-256 passaram no [GitHub Actions](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45272086863). Pacote 39 / versionCode 39. A instalação em aparelho ainda não foi validada nesta rodada.

## 0.36.0-rc.1 — downloads offline sem bloquear a interface

- A lista e o resumo de armazenamento offline são carregados do banco e do sistema de arquivos em segundo plano durante a abertura do app.
- Consultas para saber se uma faixa já está baixada e operações de remover cópias offline também rodam fora da thread da interface.
- Enquanto a biblioteca offline inicializa, ações de download em lote informam que os dados ainda estão carregando, evitando decidir com uma lista incompleta.
- Build, assinatura estável do APK/AAB e publicação com checksums SHA-256 passaram no [GitHub Actions](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45270069446). Pacote 38 / versionCode 38. A avaliação em aparelho ainda não foi feita.

## 0.35.0-rc.1 — reconciliação das coleções do Desktop

- Após o catálogo do computador carregar, favoritos, histórico e playlists são associados às URLs atuais do Desktop usando a identidade da faixa (artista, álbum, título e posição).
- Isso recupera faixas salvas com a rota antiga depois da limpeza da chave LAN e não grava o token na coleção.
- O remapeamento do banco ocorre em segundo plano e mantém faixas sem correspondência, sem removê-las.
- CI compilou, verificou as assinaturas do APK/AAB e publicou os pacotes com checksums em 23/11/2026.

## 0.34.0-rc.1 — limpeza de chaves LAN antigas

- O banco de favoritos, histórico e playlists remove chaves de acesso antigas que estavam embutidas nas URLs do Desktop; novas gravações já descartam query e fragmento das URLs de rede.
- A preferência de endereço do Desktop também é sanitizada na inicialização e os endereços legados passam a exigir novo pareamento pelo Cobre ID.
- A migração do banco roda uma vez durante a atualização, antes de os dados voltarem a ser usados; os arquivos do usuário continuam no backup Android sem copiar tokens de acesso nas URLs.
- CI compilou, verificou as assinaturas do APK/AAB e publicou os pacotes com checksums em 23/11/2026.

## 0.33.0-rc.1 — backup de conexão sem segredos

- O backup portátil agora também guarda e-mail da conta e endereços do Cobre Server e do Desktop para acelerar a configuração após reinstalação.
- Chaves antigas que apareciam em endereços do Desktop são removidas; tokens, senha, concessões, permissões da pasta e músicas baixadas continuam fora do arquivo.
- Importar outro e-mail/servidor encerra a sessão e as autorizações antigas; quando a identidade permanece igual, o login atual é mantido.
- A importação valida os endereços antes de alterar as preferências e segue aceitando backups de aparência/áudio da versão 0.32.
- CI compilou o app, verificou as assinaturas do APK/AAB e publicou os pacotes com checksums em 23/11/2026.

## 0.32.0-rc.1 — backup portátil de configurações

- Configurações agora permitem exportar e importar cor, player de vinil, cores dinâmicas da capa, equalizador, ReplayGain, graves e áudio espacial.
- O formato tem versão e valida os campos antes de gravar; importar exige confirmação e atualiza os controles do app sem reiniciar.
- O arquivo não inclui senha, tokens, endereço do Desktop, permissão da pasta ou downloads offline. A versão 0.33 passa a preservar somente dados de conexão não secretos.
- CI compilou o app, verificou a assinatura do APK/AAB e publicou os pacotes com checksums em 23/11/2026.

## 0.31.0-rc.1 — biblioteca do Desktop resiliente

- O catálogo de faixas do computador fica salvo no banco privado do app, sem áudio ou tokens, e reaparece quando uma queda temporária impede a conexão.
- O cache é associado ao endereço local normalizado e à concessão Cobre ID do aparelho; uma autorização recusada (401/403) apaga o catálogo salvo correspondente.
- Quando a rede retorna, o Mobile substitui os metadados salvos pelo catálogo atual do Desktop. A reprodução remota continua dependendo de conexão ativa e autorização válida.
- Build, assinatura do APK/AAB e publicação passaram no workflow [45258493140](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45258493140). Artefatos na [release 0.31.0-rc.1](https://github.com/bruncosta077-boop/Cobre-Mobile/releases/tag/mobile-v0.31.0-rc.1).

## 0.30.0-rc.1 — controle de espaço offline

- Configurações agora mostram quantas faixas offline estão salvas, o espaço usado e o limite de 2 GB.
- Adicionada remoção total de downloads com confirmação clara; ela apaga somente cópias privadas do Cobre no celular e não altera os originais do Desktop.
- A limpeza também remove arquivos temporários de downloads interrompidos.
- Faixas baixadas removidas da biblioteca offline são retiradas da fila; se a faixa atual for apagada, o player é pausado antes da remoção.
- Build, assinatura do APK/AAB e publicação passaram no workflow [45256146633](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45256146633). Artefatos na [release 0.30.0-rc.1](https://github.com/bruncosta077-boop/Cobre-Mobile/releases/tag/mobile-v0.30.0-rc.1).

## 0.29.0-rc.1 — downloads offline de álbuns e playlists

- Álbuns e playlists do Desktop podem ser baixados em lote para ouvir offline, sem baixar faixa por faixa manualmente.
- O download é sequencial para limitar uso de memória e rede, mostra andamento geral e da faixa atual e pode ser cancelado.
- Faixas já disponíveis offline são ignoradas; se um download falhar ou for cancelado, os itens concluídos são mantidos e uma nova tentativa continua do que falta.
- Downloads continuam no espaço privado do aplicativo e respeitam o limite total de 2 GB e o limite de 512 MB por faixa existentes.
- Em playlists, as ações de fila e exclusão foram agrupadas no menu contextual para reduzir botões permanentes.
- Build, assinatura e publicação passaram no workflow [45254471818](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45254471818). APK e AAB assinados com a chave estável do app estão disponíveis na [release 0.29.0-rc.1](https://github.com/bruncosta077-boop/Cobre-Mobile/releases/tag/mobile-v0.29.0-rc.1).

## 0.28.0-rc.1 — navegação enxuta e biblioteca mais leve

- A barra inferior agora tem três áreas: Início, Buscar e Ajustes. Fila e Minha coleção ficam acessíveis por atalhos no cabeçalho da biblioteca; conexões Cobre ID continuam dentro de Ajustes.
- Busca unificada continua encontrando faixa, álbum e artista, com o filtro e a organização de álbuns/artistas executados fora da thread de interface.
- As listas de faixas e de favoritos/recente agora mostram título e artista com um único menu de ações para fila, favoritos, download offline e compartilhamento quando disponível. As ações de fila seguem também no player e em sua aba própria.
- Mini player reduzido a capa, faixa, progresso e reproduzir/pausar. A fila permanece no botão específico da tela completa.
- Equalizador, graves e espacialização ficam recolhidos por padrão nas configurações; ReplayGain permanece visível como ajuste simples de volume.
- Toque em Buscar para abrir a pesquisa; atalhos de fila e coleção ficam no Início. Agrupamento e filtragem são calculados em segundo plano e as linhas usam seus índices diretos, evitando buscas repetidas ao tocar em faixas.
- Build [45250835205](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45250835205), build assinado e publicação [45251861919](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45251861919) passaram. APK conferido: SHA-256, assinatura estável, package `app.cobre.mobile`, versionCode 30, minSdk 29 e targetSdk 36 corretos. A suavidade precisa ser observada em diferentes aparelhos.

## 0.27.0-rc.1 — ações úteis no player e ReplayGain

- O menu de três pontos agora reúne ações da faixa: favoritos, adicionar/criar playlist, buscar ou trocar capa, créditos/encarte, letra, visual de vinil e compartilhamento do arquivo local quando o Android permite. A fila permanece nos controles próprios de fila.
- Adicionar faixa a uma playlist existente evita duplicatas e preserva a ordem; criação de playlist continua local ao celular.
- Adicionada opção desligada por padrão para normalização ReplayGain. Quando o arquivo contém tags ReplayGain legíveis pelo Media3, aplica o ganho de faixa (ou de álbum) e limita o pico para reduzir clipping; sem tags reconhecidas, mantém volume original.
- Atualizada a base Media3 para 1.11.1 para dar suporte aos metadados ReplayGain de MP3. Esta versão não adiciona decodificadores para DSD, MQA, APE, WMA ou WavPack; compatibilidade de formatos continua dependendo do Android e de codecs incorporados.
- Atualizado o compilador Kotlin para a compatibilidade binária exigida pela nova base Media3.
- A leitura de capas passou a reutilizar imagens em cache de memória, decodificar capas grandes em tamanho reduzido e usar tempos limite menores para capas servidas pelo Desktop, reduzindo trabalho durante rolagem e troca de tela.
- Build e assinatura passaram no workflow [45217722021](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45217722021). APK verificado: versionCode 29, package correto e certificado de distribuição estável. Falta teste auditivo em aparelho com arquivos que contenham tags ReplayGain.

## 0.26.0-rc.1 — vinil contínuo e controles mais claros

- Corrigido o vinil: a animação agora continua girando a cada volta enquanto a faixa toca e mantém a posição ao pausar.
- Incluído no player um botão sobre a arte para buscar uma capa ou escolher uma imagem do celular, sem sair da tela Tocando agora.
- O player agora permite alternar entre capa e visual de vinil diretamente na tela.
- Aleatório e repetição ganharam rótulos visíveis; a repetição informa se está desligada, na fila ou em uma faixa.
- Workflow [45207850024](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45207850024) passou. APK conferido: SHA-256, certificado estável, pacote `app.cobre.mobile`, versionCode 28, minSdk 29 e targetSdk 36 corretos. A validação visual e o teste no celular ainda dependem do usuário.

## 0.25.0-rc.1 — atualização protegida contra falta de espaço

- Antes e durante o download do APK, o app verifica se há espaço para o arquivo, para a cópia de instalação do Android e para uma reserva operacional. Se não houver, informa quantos MB precisam ser liberados.
- Downloads temporários e APKs antigos do atualizador são removidos antes de iniciar uma nova transferência, evitando que versões incompletas ocupem armazenamento.
- O espaço é verificado novamente antes de abrir o instalador, pois pode mudar durante o download.
- A checagem não substitui a validação do checksum, pacote e certificado, que continua obrigatória.
- O workflow 45103243315 compilou e assinou o APK/AAB. O APK da release foi baixado e verificado: checksum SHA-256, certificado estável, pacote `app.cobre.mobile`, versionCode 27 e SDK mínimo 29 conferem. Falta o teste de instalação no aparelho do usuário.

## 0.24.0-rc.1 — visualizador de espectro opcional

- Adicionado visualizador de espectro ligado à sessão de áudio do Cobre, com atualização leve e controles no player completo.
- A permissão Android de áudio só é solicitada quando o usuário tenta ativar o visualizador. A captura fica no aparelho e não é gravada nem enviada; desligar o recurso libera a sessão do Visualizer.
- O Android pode não oferecer o efeito em certos aparelhos, sessões ou rotas Bluetooth/USB; nesse caso o app mostra indisponibilidade em vez de afetar a reprodução.
- Corrigido o atualizador direto para reconhecer releases `-alpha`, `-beta` e `-rc` e comparar corretamente prévias com versões estáveis. A partir desta versão, novas prévias publicadas podem aparecer dentro do app.
- Falta validação física em diferentes aparelhos/saídas e conferência do fluxo de permissão Android antes de tratar o recurso como estável.

## 0.23.0-rc.1 — créditos e encarte por edição

- A página do álbum agora permite consultar edições correspondentes no MusicBrainz e escolher qual edição consultar. O app exige correspondência exata do álbum e do artista antes de apresentar dados, e mostra ano, país, formato e gravadora quando disponíveis.
- Os créditos por faixa são exibidos separadamente e as imagens aprovadas de contracapa, encarte e mídia disponíveis no Cover Art Archive podem ser abertas no app.
- As consultas ao MusicBrainz compartilham limitação de frequência; imagens e respostas têm limites de tamanho/dimensão. As fontes comunitárias podem não ter créditos ou encarte para todas as edições.
- Ainda precisa de validação física do autor e conferência em álbuns com vários relançamentos, álbuns duplos, créditos de artistas convidados e imagens de encarte grandes.

## 0.22.0-rc.1 — capas, avaliações e cor dinâmica

- A página do álbum permite escolher uma imagem do celular como capa e restaurar a arte original. A imagem é validada, reduzida e guardada no espaço privado do Cobre Mobile; os arquivos de música e o Desktop não são alterados.
- Adicionada avaliação local de álbuns de uma a cinco estrelas, com remoção ao tocar novamente na nota atual. Uma migração SQLite aditiva preserva favoritos, histórico e playlists existentes.
- Player pode adaptar o fundo e os controles à cor dominante da capa atual. A opção fica desligada por padrão e pode ser ligada em Ajustes → Aparência.
- O 0.21.0-rc.1 permanece disponível para teste enquanto esta próxima etapa é validada.
- Falta validação pelo autor em aparelho físico, especialmente seleção de imagem de diferentes provedores Android, migração do banco existente e troca de cores em faixa local e transmitida do Desktop.

## 0.21.0-rc.1 — pacote de teste de áudio e visual do player

- Pré-lançamento instalável que reúne letras, busca de capas, player de vinil, perfis de equalização, reforço de graves e espacialização.
- Mantém a biblioteca local e do Desktop, Cobre ID, favoritos, histórico, playlists locais, downloads offline e atualização direta já existentes.
- Build e assinatura são verificados pelo workflow de publicação. Teste em aparelho de equalização e efeitos de áudio segue necessário; a disponibilidade varia entre celulares e saídas.

## 0.21.0 — controles de áudio e perfis do equalizador (em desenvolvimento)

- Adicionados perfis de equalização Plano, Rock, Metal, Voz e Jazz, aplicados às cinco bandas do mecanismo Android e preservados entre sessões.
- Incluídos reforço de graves e espacialização estéreo com intensidade ajustável. Cada efeito usa a sessão real de reprodução e a tela mostra quando ainda não há faixa ativa ou quando o aparelho não oferece suporte.
- A disponibilidade é reavaliada quando a sessão muda; os efeitos antigos são liberados para evitar manter recursos de áudio nativos presos.
- Incluída a permissão normal do Android para os controles de áudio. Não exige confirmação adicional do usuário.
- O workflow deve compilar as variantes antes de disponibilizar o APK assinado para teste no aparelho.

## 0.20.0 — letras, busca de capas e player de vinil (em desenvolvimento)

- O player completo pode buscar letras sincronizadas ou simples no LRCLIB e lê arquivos `.lrc`/`.txt` ao lado de faixas offline. Letras sincronizadas acompanham a posição da música; as letras encontradas ficam em cache privado por 12 horas.
- A página de álbum permite procurar capas por correspondência exata no Apple Music e no MusicBrainz/Cover Art Archive. A imagem escolhida é reduzida, validada e salva no armazenamento privado do celular; ela não altera tags nem o Desktop.
- Adicionado modo visual de vinil, configurável e persistente. O disco gira enquanto a faixa toca e pausa junto com a reprodução; a capa ocupa o selo central.
- A validação desta versão ainda vai cobrir busca sem rede, metadados com caracteres acentuados, LRC, capa local/da rede e restauração da preferência de vinil.

## 0.19.0 — downloads offline, retomada e controle do Desktop (pré-release)

- No player, uma faixa da biblioteca autorizada do Desktop pode ser enviada para tocar no computador. O app confirma antes, informa que a ação substitui a fila do Desktop e pausa o celular somente depois da aceitação; faixa local ou de outro host não oferece essa ação.
- Incluído fluxo de exclusão permanente da conta Cobre ID com confirmação de senha no Mobile; o Server remove dispositivos, sessões e permissões vinculados. O app apaga tokens, retira a autorização mantida pelo serviço de reprodução e pausa a faixa remota atual. Músicas e preferências locais permanecem no aparelho.
- Adicionada a opção de baixar faixas do Desktop para reprodução offline, com progresso e remoção pelo mesmo controle. Os downloads ficam no armazenamento privado do app, usam uma autorização curta no cabeçalho e só aceitam a origem Desktop configurada; limite de 2 GB no total e 512 MB por faixa.
- A fila agora é salva no aparelho com a faixa atual, posição, modo aleatório e repetição. Ao reabrir o Mobile, ela volta pausada e só toca quando o usuário iniciar a reprodução.
- Atualizados compile/target SDK para API 36, exigida para novos apps e atualizações da Google Play desde 31/08/2026.
- Criada variante `play` que remove `REQUEST_INSTALL_PACKAGES`, desativa o atualizador de APK externo e orienta a receber atualizações pela loja. A edição `direct` mantém o atualizador GitHub existente.
- O workflow publicou APK direto e AAB Play assinados, mais checksums SHA-256, na [pré-release do GitHub `mobile-v0.19.0`](https://github.com/bruncosta077-boop/Cobre-Mobile/releases/tag/mobile-v0.19.0). O [workflow 45086261665](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45086261665) compilou e verificou os pacotes.
- A transferência de reprodução, os downloads offline e a restauração da fila ainda não foram exercitados em aparelho físico nesta versão. A publicação no GitHub não é publicação na Google Play.
- Para a Play Store ainda faltam configuração da Play Console e Play App Signing, política pública de privacidade, URL externa de exclusão da conta, ficha e declarações da loja, teste físico e, se a conta pessoal se enquadrar na regra, teste fechado antes do acesso à produção. Veja [PUBLICACAO-GOOGLE-PLAY.md](docs/PUBLICACAO-GOOGLE-PLAY.md).
## 0.18.0 — evitar conflito ao renovar sessão Cobre ID

- A renovação do refresh token agora é serializada no processo Android, pois a tela principal e o serviço de reprodução têm instâncias independentes do repositório e compartilham o mesmo token rotativo.
- Cada renovação lê o token mais recente já dentro da trava; isso evita que uma segunda chamada concorrente reutilize um refresh token consumido e encerre a sessão por detecção de replay.
- Mantida a rotação de refresh token e a resposta de segurança do Server a reuso real; nenhum comportamento de autorização ou escopo foi afrouxado.
- Validação local concluída: `:app:assembleDebug`, `:app:lintDebug`, `:app:assembleRelease` e `:app:lintVitalRelease` passaram. APK assinado e checksum publicados na [release 0.18.0](https://github.com/bruncosta077-boop/Cobre-Mobile/releases/tag/mobile-v0.18.0); build e assinatura passaram no [GitHub Actions](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45052837954).

## 0.17.0 — renovar acesso expirado sem refazer o QR

- Antes de carregar a biblioteca, o Mobile verifica a validade da concessão e pede uma nova ao Cobre Server quando ela está perto de expirar.
- Se o Desktop rejeitar o token antigo durante a leitura do catálogo, o Mobile renova uma vez e tenta novamente. O fluxo não pede novo QR enquanto o consentimento do Desktop continua válido.
- Controles remotos usam a mesma renovação e tentam novamente somente após uma resposta 401. Respostas 403, problemas de rede e falhas do Cobre Server continuam explícitos e não são mascarados.
- A restauração inicial da sessão Cobre ID termina antes de uma renovação da concessão, evitando concorrência no refresh token ao abrir o app.
- Validação local concluída: `:app:assembleDebug`, `:app:lintDebug`, `:app:assembleRelease` e `:app:lintVitalRelease` passaram. O lint reporta 24 avisos não bloqueantes já presentes no app, principalmente APIs Android/ícones depreciados; o APK local de release é unsigned.
- APK assinado com a chave estável, validação de assinatura e publicação passaram no [GitHub Actions](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/44951035326); APK e checksum estão na [release 0.17.0](https://github.com/bruncosta077-boop/Cobre-Mobile/releases/tag/mobile-v0.17.0).

## 0.16.0 — medições recentes de áudio

- A presença Cobre ID agora informa a idade da última amostra do stream, permitindo ao Cobre Server distinguir uma medição recente de uma taxa antiga.
- Métricas continuam limitadas a latência inicial e bytes por segundo recebidos diretamente do Desktop; nenhum nome, caminho ou conteúdo de faixa é enviado.
- Validação local concluída: `:app:assembleDebug`, `:app:lintDebug`, `:app:assembleRelease` e `:app:lintVitalRelease` passaram. APK assinado, verificação da assinatura e publicação passaram no [GitHub Actions](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/44949187555); pacote e checksum estão na [release 0.16.0](https://github.com/bruncosta077-boop/Cobre-Mobile/releases/tag/mobile-v0.16.0).

## 0.15.0 — encontrar o Desktop na rede local

- A tela Conexões pode localizar automaticamente o Cobre Desktop na mesma rede, sem digitar seu endereço ou escanear QR.
- A descoberta anuncia apenas a identidade do Desktop e as versões de protocolo; não carrega autorização. O Mobile ainda pede acesso pelo Cobre ID e o Desktop precisa aprovar.
- QR e endereço manual continuam como alternativas. O token curto segue protegido no dispositivo e é enviado no cabeçalho ao Desktop; o áudio continua direto pela rede local.
- `:app:assembleDebug`, `:app:lintDebug`, `:app:assembleRelease` e `:app:lintVitalRelease` passaram localmente. O APK local não foi assinado com a chave de distribuição. A descoberta ainda requer validação física em várias redes/roteadores; o Android NSD reporta depreciações de API compatíveis, sem falhas de lint.
- APK assinado e checksum SHA-256 publicados em [Cobre Mobile 0.15.0](https://github.com/bruncosta077-boop/Cobre-Mobile/releases/tag/mobile-v0.15.0); build e publicação passaram no [GitHub Actions](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/44946606790). A descoberta ainda precisa ser validada pelo autor em celular físico e em diferentes redes.

## 0.14.0 — bibliotecas mais claras no uso diário

- Os filtros **Tudo**, **Celular** e **Computador** agora mostram a quantidade de faixas e usam controles maiores e mais claros para toque.
- A biblioteca combinada continua removendo duplicatas e deixa essa regra visível na própria tela.
- O estado do Desktop aparece junto do filtro de origem, com um atalho para Conexões quando é preciso conectar ou conferir o acesso.
- Mantidos o fluxo Cobre ID/QR, as permissões, a reprodução direta na rede local e o comportamento do player.
- Build local de release compilada e aprovada na análise `lintVitalRelease`. GitHub Actions compilou, verificou a assinatura e publicou o APK com checksum SHA-256 em [Cobre Mobile 0.14.0](https://github.com/bruncosta077-boop/Cobre-Mobile/releases/tag/mobile-v0.14.0); falta validação desta versão no aparelho pelo autor.

## 0.13.1 — diagnóstico de conexão com o Cobre Server

- Falhas de tempo limite, conexão recusada e endereço inválido agora mostram instruções claras, sem a mensagem bruta do Android.
- A tela informa que o Server deve permanecer aberto ou minimizado na área de notificação, e orienta a conferir a mesma rede Wi-Fi e o Firewall do Windows.

## 0.13.0 — conexão simplificada e interface reorganizada

- A conexão com o Desktop agora tem um único caminho: entrar no Cobre ID, ler o QR no computador e aprovar o pedido no próprio Desktop.
- Removida a configuração manual duplicada do endereço do Desktop; o endereço local e a autorização são salvos após o pareamento.
- A biblioteca mostra os erros de autorização separadamente: acesso vencido/revogado, permissão sem escopo e Cobre Server indisponível deixam de aparecer como o mesmo erro genérico.
- A tela de Conexões reúne estado da conta, biblioteca do computador, pareamento QR, dispositivos e controles remotos numa sequência única.
- Configurações e cabeçalho foram reorganizados com a paleta cobre, hierarquia visual e linguagem mais próximas do Desktop atual.
- A tela da biblioteca e as configurações agora direcionam para o mesmo pareamento QR quando a biblioteca Desktop está ausente ou precisa de nova autorização.
- O app verifica novas versões ao abrir, permite busca manual, baixa o APK assinado e valida pacote, versão e certificado antes de abrir o instalador Android.
- As notas da versão aparecem na oferta e, após a atualização instalada pelo app, na primeira abertura. O Android ainda exige confirmar a instalação.
- Requer Cobre Desktop 3.12.0 ou posterior para diagnóstico detalhado de erros de autorização; Cobre Server atualizado continua necessário para emitir e renovar a permissão.
- APK assinado publicado como pré-release: [Cobre-Mobile-0.13.0.apk](https://github.com/bruncosta077-boop/Cobre-Mobile/releases/tag/mobile-v0.13.0), acompanhado por checksum SHA-256.
- GitHub Actions concluiu o build, verificou a assinatura com apksigner e publicou o release no [run 44924814319](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/44924814319). A compilação local foi unsigned; o APK público usa a chave estável do canal de distribuição.

## 0.12.1 — espera de aprovação ajustada

- O prazo de espera acompanha o `expiresIn` informado pelo Cobre Server, em vez de parar após 84 segundos.
- O fluxo manual e o pareamento por QR indicam exatamente onde aprovar: Cobre Desktop → Configurações → Conta Cobre → Permitir.
- A solicitação fica válida por até cinco minutos no Server, dando tempo para abrir o Desktop e aprovar; após isso, o app informa que é necessário tentar novamente.
- O tempo de espera vem do prazo retornado pelo Server; requer Cobre Server 0.5.0-beta.12 ou posterior para usar o prazo ampliado de cinco minutos.

## 0.12.0 — conexão Cobre ID, áudio remoto e player reorganizado

- Mobile pede aprovação por aparelho ao Desktop através do Cobre ID; o Desktop decide e a permissão do Server vale até 30 dias.
- Biblioteca do Desktop usa as rotas protegidas de catálogo, capa e áudio. O áudio segue direto pela rede local do Desktop para o celular, com `Range` para busca na faixa; o Server autoriza, mas não armazena nem retransmite músicas.
- Tokens de acesso duram poucos minutos, ficam cifrados com Android Keystore e são renovados enquanto a reprodução continua; ao sair da conta, os dados locais da autorização são apagados.
- Depois de revogar o novo acesso Cobre ID, o app não volta silenciosamente ao endereço antigo por chave LAN; o pedido de autorização continua obrigatório após logout ou restauração do backup.
- Controles no Cobre ID permitem ver a faixa do Desktop, pausar/reproduzir, avançar/voltar e ajustar seu volume.
- Tela completa do player foi reorganizada em torno da capa, identificação da origem, progresso, controles, volume visível e acesso à fila; o visual usa a paleta escura cobre do Desktop como referência.
- Configurações agora separam biblioteca do celular, integração com Desktop, aparência e áudio. Equalizador real de cinco bandas aplica ajustes à sessão de reprodução do Android e guarda os valores no backup.
- Cobre ID organiza conta/aparelhos, autorização Desktop e pareamento TV em seções próprias; os controles remotos mostram o estado do player do computador.
- Pareamento do Desktop por QR: o celular lê endereço local e identidade do host, pede autorização Cobre ID e conecta a biblioteca após a aprovação no computador. O QR não carrega credenciais e expira em cinco minutos; o endereço manual segue disponível como alternativa.
- Scanner Google Code Scanner lê QR sem pedir permissão de câmera ao app; aparelhos sem suporte aos serviços Google continuam podendo configurar o endereço manualmente e pedir acesso pelo Cobre ID.
- Corrigida a migração de refresh tokens antigos para o armazenamento cifrado; IDs e autorizações do Desktop não entram no backup exportável.
- Requer Cobre Desktop 3.9.0 ou posterior e Cobre Server 0.5.0-beta.9 ou posterior para o acesso protegido; QR requer Desktop 3.10.0. A conexão antiga por chave LAN continua disponível como compatibilidade para instalações sem migração.
- APK assinado `Cobre-Mobile-0.12.0.apk` publicado em [Releases](https://github.com/bruncosta077-boop/Cobre-Mobile/releases/tag/mobile-v0.12.0). O GitHub Actions compilou o release e aprovou `apksigner verify` no [run 44796230905](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/44796230905).
- Esta é a primeira versão assinada pela chave estável de distribuição; APKs antigos de debug podem exigir exportar o backup, desinstalar a versão antiga e fazer uma migração única antes da instalação.
- A versão é pré-release e ainda requer validação de reprodução longa e controles no aparelho com Desktop 3.10.0 e Cobre Server atualizado.

## 0.11.0 — recuperação de configurações

- Backup do Android passa a incluir endereço do Cobre Server, email da conta, pasta escolhida, endereço do Desktop, cores e dados locais (playlists, favoritos e histórico).
- Preferências exportáveis e token cifrado agora ficam em arquivos separados; tokens antigos são migrados automaticamente sem encerrar a sessão na atualização, e o arquivo legado misto fica excluído do backup.
- Uma restauração em outro aparelho pode exigir login novamente e nova permissão para a pasta, pois credenciais e concessões SAF não podem ser restauradas como texto.
- A atualização sobre uma instalação preserva os dados quando `applicationId` e certificado de assinatura permanecem iguais. O APK de release ainda aguarda uma chave estável no GitHub; esta versão não deve ser instalada desinstalando um APK com dados importantes.
- `assembleDebug` passou no GitHub Actions [44746930132](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/44746930132); ainda falta validar backup/restauração em aparelho físico.
- A versão 0.11.0 não chegou a ser publicada; suas mudanças estão incluídas na 0.12.0.

## 0.10.0 — preparação da assinatura estável

- Adiciona à conta Cobre ID a aprovação de códigos de pareamento de TV; o código é enviado autenticado ao Cobre Server e, após aprovação, o Mobile atualiza a lista de dispositivos vinculados.
- Valida o formato curto do código antes do envio e apresenta erros/expiração sem criar registros locais falsos.
- O workflow passou a separar APKs debug (testes) de APKs de release, que exigem uma chave Cobre estável por segredos do GitHub Actions.
- Quando a chave for configurada e o APK assinado for validado, instalações com a mesma assinatura poderão atualizar por cima, preservando configurações, sessão e dados locais.
- A troca inicial de APK debug para a chave estável pode exigir migração única. Faça uma cópia dos dados e confirme a instalação antes de remover qualquer versão que contenha dados importantes.
- A publicação da 0.10.0 continua pendente até configurar a chave nos segredos do repositório e validar o APK assinado.
- O backup seletivo continua sem exportar credenciais Cobre ID nem tokens.

## 0.9.0 — backup seletivo do aparelho

- Android pode restaurar banco local (playlists, favoritos e histórico) e cores de aparência pelo backup do sistema ou transferência entre aparelhos.
- Migra preferências antigas e separa aparência das configurações de acesso local.
- Endereço do Desktop, permissão de pasta e credenciais Cobre ID ficam fora do backup; podem precisar ser reconectados após reinstalação ou troca de aparelho.
- Em atualização sobre o app instalado, o Android mantém os dados quando o certificado de assinatura é o mesmo. Esta build debug de CI tem certificado diferente do APK 0.8.0; não tente contornar removendo o app se precisar dos dados locais.
- O backup do Android depende de estar habilitado; a assinatura estável de distribuição continua pendente.

## 0.8.0 — em validação

- A reprodução de faixas do Desktop mede latência até o primeiro bloco e taxa de bytes recebidos no próprio Mobile.
- O Mobile envia medições recentes no heartbeat Cobre ID; o painel do Server passa a exibir taxa/latência reais, separadas da latência do catálogo.
- As medições expiram após dois minutos sem atividade de stream e não incluem faixas locais.
- O GitHub Actions prepara um APK instalável e, em tags `mobile-v*`, publica o arquivo diretamente em Releases.
- O áudio ainda é entregue diretamente pelo Desktop na mesma rede local; Cobre ID não retransmite mídia nem habilita acesso pela internet.
- Requer build e validação no aparelho; métricas precisam ser conferidas durante uma faixa do Desktop.

## 0.7.0 — em validação

- Visão de biblioteca separa músicas do celular e do Desktop, com deduplicação na visão conjunta.
- Player destaca volume e metadados e a tela de álbuns oferece ações por toque longo.
- Paletas e navegação refinadas para aproximar o app do Desktop em telas de toque.
- Esta revisão precisa ser instalada e validada no aparelho antes de ser marcada como estável.
- Conexões HTTP do Cobre ID ficam limitadas a loopback/endereço IPv4 privado, e os clientes não seguem redirecionamentos que poderiam encaminhar senhas ou tokens a outro host.

## 0.6.0 — validada pelo autor

- Biblioteca LAN do Desktop e reprodução de áudio direta pela rede local funcionaram no aparelho.
- A resposta do catálogo é medida em milissegundos; esse valor não representa a velocidade contínua do stream.

## 0.5.0

- Fontes de áudio locais e Desktop foram conectadas; o Desktop atende o stream diretamente via HTTP Range.

## 0.4.0

- Favoritos, histórico e playlists locais persistidos em SQLite.

## 0.3.0

- Seletor de pasta, paletas e configurações de áudio do Android.

## 0.2.0 — validada pelo autor

- Cobre ID, biblioteca local, reprodução normal e ícone do aplicativo confirmados no aparelho.

## Créditos

Bruno C S Moraes, com contribuição de [@dilucas00](https://github.com/dilucas00).
