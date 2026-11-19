# Cobre Mobile

Aplicativo Android do ecossistema Cobre. Interface escura com a paleta, o símbolo e a organização visual do Cobre Desktop, adaptados para toque.

## Versão em desenvolvimento: 0.5.0

- Biblioteca local com álbuns, artistas, faixas, capas e busca.
- Pasta de biblioteca configurável pelo seletor de pastas do Android.
- Detalhe do álbum, fila editável, mini player e tela completa com progresso, aleatório e repetição.
- Reprodução em segundo plano pelo Media3 e controles na notificação/tela bloqueada.
- Cobre ID para login, dispositivos e presença; refresh token protegido pelo Android Keystore.
- Aba de configurações com paletas do Desktop, volume de música e detalhes das saídas informadas pelo Android.
- Favoritos, histórico recente e playlists locais persistidos em SQLite.
- Biblioteca combinada com faixas do Cobre Desktop e reprodução direta pela rede local.
- Ícone Cobre incluído no APK em formatos legado e adaptativo.

A versão 0.2.0 foi instalada e validada pelo autor: Cobre ID conectou, a biblioteca foi lida, a reprodução funcionou e o ícone apareceu. As versões 0.3.0 a 0.5.0 contêm mudanças ainda não confirmadas no aparelho.

## Conexão

O Mobile usa o [Cobre Server](https://github.com/bruncosta077-boop/Cobre-Server) para login, dispositivos e presença. Para abrir a biblioteca do Desktop, ative **Acesso remoto** no Cobre Desktop e cole em **Configurações → Biblioteca do computador** o endereço completo exibido pelo Desktop. Os aparelhos precisam estar na mesma rede local.

O áudio da biblioteca do Desktop é entregue pelo próprio computador na rede local, com requisições HTTP por partes para permitir busca na faixa. Essa conexão não passa pelo Cobre Server. Acesso pela internet e sincronização de playlists continuam pendentes; presença do Cobre ID não prova que o computador está alcançável.

## Funções do Desktop ainda ausentes no Mobile

Estatísticas, letras sincronizadas, edição de metadados, créditos de encarte, equalizador e efeitos DSP, crossfade e sincronização de bibliotecas/playlists entre aparelhos.

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
