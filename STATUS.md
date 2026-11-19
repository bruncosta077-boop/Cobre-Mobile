# Cobre Mobile — STATUS (Etapa 4, fatia 1)

> Plano completo e histórico: ver `ROADMAP.md`.

## Feito (escrito, NÃO compilado nem testado em aparelho)
- Projeto Gradle/Compose com Media3 (ExoPlayer + MediaSession).
- Biblioteca local via MediaStore, agrupada por artista|álbum (igual ao Desktop 3.1).
- Grade de álbuns, tela do álbum, mini player, capas via loadThumbnail.
- Serviço de reprodução em segundo plano com notificação e tela de bloqueio (MediaSessionService).
- Tema com a paleta do Desktop.

## Não verificado (preciso abrir no Android Studio)
- Compilação: versões em build.gradle.kts são as que conheço; atualizar ao abrir o projeto.
- Permissões em Android 13+ e 14+, notificação de mídia, retomada após matar o app.
- Capas de arquivos sem arte; desempenho com biblioteca grande (grade sem paginação).

## Limitações conhecidas / decisões
- minSdk 29 (Android 10): simplifica capas e armazenamento. Decisão revisável.
- Sem artista do álbum nem disco (MediaStore); mesma limitação do Desktop. Sairá da Etapa 2a.
- Sem busca, playlists, fila, letras, DSP, login. Próximas fatias.
- Sem IDs estáveis ainda: usa o _ID do MediaStore (local ao aparelho).

## Como gerar o APK (sem Android Studio)
1. Crie um repositório no GitHub e envie o conteúdo desta pasta (a raiz do repositório é esta pasta).
2. Aba Actions > "Build APK" > Run workflow.
3. Ao terminar (alguns minutos), baixe o artefato "cobre-mobile-debug-apk" (um .zip com o .apk dentro).
4. No celular, instale o .apk (permita "instalar apps desconhecidos"). É APK de depuração, assinado com chave de debug.
Não testado: se o build falhar, o log do Actions mostra o erro e eu corrijo a partir dele.
