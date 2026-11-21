# Histórico de versões — Cobre Mobile
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
