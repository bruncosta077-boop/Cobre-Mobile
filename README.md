# Cobre Mobile

Aplicativo Android do ecossistema Cobre. A interface usa a paleta escura e os detalhes em cobre do Desktop, adaptados para toque e telas pequenas.

## Versão em desenvolvimento: 0.2.0

- Biblioteca local com visualização de álbuns, artistas e faixas, capas e busca.
- Tela do álbum, fila editável, mini player e tela de reprodução com progresso, aleatório e repetição.
- Reprodução em segundo plano por Media3, com integração à notificação e aos controles do Android.
- Ícone Cobre incluído nos recursos do APK.
- Área Cobre ID para login, dispositivos e presença, com refresh token protegido pelo Android Keystore.

A base 0.1.0 já foi instalada e usada no celular do autor. Esta atualização 0.2.0 precisa passar pela compilação do GitHub Actions e por nova validação em aparelho antes de ser considerada estável.

## Cobre ID e conexão

O Mobile fala com as rotas atuais do [Cobre Server](https://github.com/bruncosta077-boop/Cobre-Server) para login, lista de dispositivos e presença. Informe a URL do servidor na área **Cobre ID**. HTTP é aceito somente em endereços privados da rede local; para acessar pela internet o servidor precisa usar HTTPS.

O servidor atual ainda não transmite áudio, não entrega a biblioteca do Desktop ao celular e não sincroniza playlists. Esses recursos serão desenvolvidos em etapas posteriores.

## Gerar APK de teste

1. Abra a aba **Actions** deste repositório.
2. Selecione **Build APK** e clique em **Run workflow**.
3. Baixe o artefato `cobre-mobile-debug-apk` e extraia o APK.

O workflow também roda quando uma alteração chega à branch `main`. O APK é de depuração e não está assinado para publicação na Play Store.

## Compilar localmente

Requer JDK 17 e Gradle 8.9:

```sh
gradle assembleDebug
```

## Estrutura

- `app/` — aplicativo Android em Kotlin, Jetpack Compose e Media3.
- `ROADMAP.md` — etapas seguintes para playlists, letras, áudio e ligação ao Desktop.
- `STATUS.md` — estado da versão e validações feitas.

O projeto não declara uma licença de redistribuição. Consulte o autor antes de reutilizar ou redistribuir o código.