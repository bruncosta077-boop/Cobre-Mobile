# Cobre Mobile

Aplicativo Android do ecossistema Cobre. Interface escura com a paleta, o símbolo e a organização visual do Cobre Desktop, adaptados para toque.

## Versão em desenvolvimento: 0.4.0

- Biblioteca local com álbuns, artistas, faixas, capas e busca.
- Pasta de biblioteca configurável pelo seletor de pastas do Android.
- Detalhe do álbum, fila editável, mini player e tela completa com progresso, aleatório e repetição.
- Reprodução em segundo plano pelo Media3 e controles na notificação/tela bloqueada.
- Cobre ID para login, dispositivos e presença; refresh token protegido pelo Android Keystore.
- Aba de configurações com paletas do Desktop, volume de música e detalhes das saídas informadas pelo Android.
- Favoritos, histórico recente e playlists locais persistidos em SQLite.
- Ícone Cobre incluído no APK em formatos legado e adaptativo.

A versão 0.2.0 foi instalada e validada pelo autor: Cobre ID conectou, a biblioteca foi lida, a reprodução funcionou e o ícone apareceu. As versões 0.3.0 e 0.4.0 têm mudanças ainda não confirmadas no aparelho.

## Conexão

O Mobile usa as rotas atuais do [Cobre Server](https://github.com/bruncosta077-boop/Cobre-Server) para login, dispositivos e presença. Informe a URL na área Cobre ID. HTTP só é aceito para endereços privados da rede local; fora dela, use HTTPS.

O servidor atual ainda não transmite músicas do Desktop e não sincroniza biblioteca ou playlists. Os indicadores de presença mostram que o app entrou em contato com o servidor, não que o PC está pronto para enviar áudio.

## Funções do Desktop ainda ausentes no Mobile

Playlists persistentes, favoritos, histórico e estatísticas, letras sincronizadas, edição de metadados, créditos de encarte, equalizador e efeitos DSP, crossfade, streaming do Desktop e sincronização entre dispositivos.

## Gerar APK de teste

1. Abra **Actions** neste repositório.
2. Selecione **Build APK** e clique em **Run workflow**.
3. Baixe o artefato `cobre-mobile-debug-apk` e extraia o APK.

O workflow também roda ao enviar alterações para `main`. O APK é de depuração e não está assinado para a Play Store.

## Compilar localmente

Requer JDK 17 e Gradle 8.9:

```sh
gradle assembleDebug
```

O projeto não declara uma licença de redistribuição. Consulte o autor antes de reutilizar ou redistribuir o código.
