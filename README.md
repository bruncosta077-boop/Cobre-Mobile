<div align="center">
  <img src="assets/branding/cobre-logo.png" alt="Logo do Cobre" width="150">
  <h1>Cobre Mobile</h1>
  <p>Player Android integrado ao ecossistema Cobre</p>
  <p><strong>Versão 0.12.0 — conexão protegida com o Desktop e player reorganizado</strong></p>
</div>

Desenvolvimento principal: Bruno C S Moraes · Contribuição ao projeto: [@dilucas00](https://github.com/dilucas00).

Aplicativo Android do ecossistema Cobre. Interface escura com a paleta, o símbolo e a organização visual do Cobre Desktop, adaptados para toque.

## Versão em preparação: 0.12.0 — ainda não publicada

- Biblioteca local com álbuns, artistas, faixas, capas e busca.
- Pasta de biblioteca configurável pelo seletor de pastas do Android.
- Detalhe do álbum, fila editável, mini player e tela completa com progresso, aleatório e repetição.
- Reprodução em segundo plano pelo Media3 e controles na notificação/tela bloqueada.
- Cobre ID para login, dispositivos e presença; refresh token protegido pelo Android Keystore.
- Permite aprovar pela conta Cobre ID o código de pareamento de um cliente TV compatível e acompanha o dispositivo vinculado.
- Aba de configurações com paletas do Desktop, volume de música e detalhes das saídas informadas pelo Android.
- Favoritos, histórico recente e playlists locais persistidos em SQLite.
- Biblioteca combinada com faixas do Cobre Desktop e reprodução direta pela rede local.
- Status da conexão Desktop exibe tempo de resposta do catálogo e quantidade de faixas carregadas.
- Filtro entre biblioteca do celular, biblioteca do computador ou visão conjunta sem faixas repetidas.
- Player com volume sempre visível, metadados do álbum e ações por toque longo nos álbuns.
- Ícone Cobre incluído no APK em formatos legado e adaptativo.
- Medição do áudio recebido do Desktop (latência inicial e bytes por segundo) enviada ao painel Cobre Server, sem confundir catálogo com stream.
- Backup Android seletivo de playlists, favoritos, histórico, cores, endereço do Server, email, endereço do Desktop e URI da pasta escolhida; tokens não são incluídos, e o Android pode exigir conceder a permissão da pasta novamente após restaurar.
- O workflow está preparado para usar assinatura estável protegida nos segredos do GitHub Actions; o cadastro e a primeira validação do APK ainda estão pendentes.
- O Mobile pede permissão ao Desktop através do Cobre ID, acessa biblioteca/capas/áudio protegidos e renova tokens curtos enquanto toca. A permissão é aprovada no Desktop e vale até 30 dias.
- Após revogar a permissão ou sair da conta, o endereço antigo com chave LAN não pode reabrir o acesso em silêncio; a autorização Cobre ID será exigida novamente.
- Controles do Cobre ID podem pausar, trocar faixa, ajustar o volume e consultar a faixa em reprodução no Desktop.
- O player foi reorganizado com capa em destaque, origem da faixa, progresso e volume sempre visível; configurações de biblioteca, aparência e áudio continuam agrupadas em cartões por finalidade.
- O equalizador de cinco bandas altera a sessão de reprodução do Android; suas preferências ficam incluídas no backup do app.
- A aba Cobre ID separa estado da conta/dispositivos, autorização do Desktop e pareamento da TV; a tela também lê o estado atual do player Desktop para sincronizar os controles.
- A transmissão de áudio continua direta na rede local entre Desktop e celular; o Cobre Server controla acesso e não retransmite nem armazena arquivos de música.

A versão 0.2.0 foi validada pelo autor. A conexão LAN e a reprodução Desktop do 0.6.0 também foram confirmadas. A versão 0.9.0 foi compilada e publicada pelo GitHub Actions. A versão 0.11.0 não foi publicada; suas alterações estão incluídas na 0.12.0, que foi compilada localmente em debug e ainda requer teste no aparelho.

**Assinatura e atualizações:** o workflow está preparado para assinar releases com uma chave estável protegida por segredos do GitHub Actions. A chave ainda precisa ser cadastrada e o primeiro APK assinado testado; até isso acontecer, a 0.12.0 não está publicada. Atualizações por cima preservam os dados se `applicationId` e certificado forem os mesmos. APKs debug antigos podem exigir uma migração única; faça backup e não remova o app com dados importantes sem validar a cópia.

## Conexão

O Mobile usa o [Cobre Server](https://github.com/bruncosta077-boop/Cobre-Server/releases/tag/v0.5.0-beta.9) para login, dispositivos e presença. Para o novo acesso protegido, entre na mesma conta Cobre ID no Desktop e no Mobile, peça acesso na aba **Cobre ID** e aprove no Desktop. Em **Configurações → Biblioteca do computador**, informe o endereço local do Desktop. Os aparelhos precisam estar na mesma rede local.

O [Cobre TV](https://github.com/bruncosta077-boop/Cobre-TV) segue como protótipo separado para Android TV/Google TV: já solicita e resgata código de pareamento Cobre ID, mas ainda não carrega biblioteca real nem reproduz áudio.

O áudio da biblioteca do Desktop é entregue pelo próprio computador na rede local, com autorização curta renovável e requisições por partes para permitir busca na faixa. Essa conexão não passa pelo Cobre Server. Acesso pela internet e sincronização de playlists continuam pendentes; presença do Cobre ID não prova que o computador está alcançável. A integração protegida requer Desktop 3.9.0 e Server beta.9 ou posterior.

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
