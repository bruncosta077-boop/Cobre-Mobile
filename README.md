<div align="center">
  <img src="assets/branding/cobre-logo.png" alt="Logo do Cobre" width="150">
  <h1>Cobre Mobile</h1>
  <p>Player Android integrado ao ecossistema Cobre</p>
  <p><strong>Versão 0.8.0 em desenvolvimento</strong></p>
</div>

Desenvolvimento principal: Bruno C S Moraes · Contribuição ao projeto: [@dilucas00](https://github.com/dilucas00).

Aplicativo Android do ecossistema Cobre. Interface escura com a paleta, o símbolo e a organização visual do Cobre Desktop, adaptados para toque.

## Versão em desenvolvimento: 0.8.0

- Biblioteca local com álbuns, artistas, faixas, capas e busca.
- Pasta de biblioteca configurável pelo seletor de pastas do Android.
- Detalhe do álbum, fila editável, mini player e tela completa com progresso, aleatório e repetição.
- Reprodução em segundo plano pelo Media3 e controles na notificação/tela bloqueada.
- Cobre ID para login, dispositivos e presença; refresh token protegido pelo Android Keystore.
- Aba de configurações com paletas do Desktop, volume de música e detalhes das saídas informadas pelo Android.
- Favoritos, histórico recente e playlists locais persistidos em SQLite.
- Biblioteca combinada com faixas do Cobre Desktop e reprodução direta pela rede local.
- Status da conexão Desktop exibe tempo de resposta do catálogo e quantidade de faixas carregadas.
- Filtro entre biblioteca do celular, biblioteca do computador ou visão conjunta sem faixas repetidas.
- Player com volume sempre visível, metadados do álbum e ações por toque longo nos álbuns.
- Ícone Cobre incluído no APK em formatos legado e adaptativo.
- Medição do áudio recebido do Desktop (latência inicial e bytes por segundo) enviada ao painel Cobre Server, sem confundir catálogo com stream.

A versão 0.2.0 foi validada pelo autor. A conexão LAN e a reprodução Desktop do 0.6.0 também foram confirmadas; as mudanças visuais do 0.7.0 e as métricas de stream do 0.8.0 aguardam validação no aparelho.

## Conexão

O Mobile usa o [Cobre Server](https://github.com/bruncosta077-boop/Cobre-Server) para login, dispositivos e presença. Para abrir a biblioteca do Desktop, ative **Acesso remoto** no Cobre Desktop e cole em **Configurações → Biblioteca do computador** o endereço completo exibido pelo Desktop. Os aparelhos precisam estar na mesma rede local.

O áudio da biblioteca do Desktop é entregue pelo próprio computador na rede local, com requisições HTTP por partes para permitir busca na faixa. Essa conexão não passa pelo Cobre Server. Acesso pela internet e sincronização de playlists continuam pendentes; presença do Cobre ID não prova que o computador está alcançável.

## Segurança da conexão

O refresh token do Cobre ID fica protegido pelo Android Keystore. Para conexões HTTP, o app aceita apenas loopback ou endereço IPv4 privado; HTTPS usa a validação padrão de certificados do Android. As requisições de conta e catálogo não seguem redirecionamentos, para evitar encaminhar senhas, tokens ou a chave da biblioteca a outro host. O tráfego HTTP local continua sem criptografia e deve ser usado somente em uma rede Wi-Fi confiável.

## Funções do Desktop ainda ausentes no Mobile

Estatísticas, letras sincronizadas, edição de metadados, créditos de encarte, equalizador e efeitos DSP, crossfade e sincronização de bibliotecas/playlists entre aparelhos.

## Gerar APK de teste

O APK é o instalador padrão do Android. Baixe o arquivo em [Releases](https://github.com/bruncosta077-boop/Cobre-Mobile/releases), abra-o no celular e confirme a instalação. O Android pode pedir permissão para instalar apps baixados pelo navegador.

As versões de desenvolvimento são compiladas pelo GitHub Actions; tags `mobile-v*` publicam o APK diretamente em Releases. O pacote atual é de teste e não é distribuído pela Play Store.

Para gerar uma compilação de teste manualmente, abra **Actions**, selecione **Build APK**, clique em **Run workflow** e baixe o artefato `cobre-mobile-debug-apk`.

O workflow também roda ao enviar alterações para `main`. O APK é de depuração e não está assinado para a Play Store.

## Compilar localmente

Requer JDK 17 e Gradle 8.9:

```sh
gradle assembleDebug
```

O projeto não declara uma licença de redistribuição. Consulte o autor antes de reutilizar ou redistribuir o código.

Atualização normal preserva os dados do app. O backup do Android pode restaurar playlists, favoritos, histórico e cores; por segurança, faça login no Cobre ID e reconecte o Desktop/pasta após migrar para outro aparelho. A restauração depende do backup do Android estar habilitado.
