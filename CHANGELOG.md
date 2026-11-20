# Histórico de versões — Cobre Mobile

## 0.12.0 — conexão Cobre ID, áudio remoto e player reorganizado

- Mobile pede aprovação por aparelho ao Desktop através do Cobre ID; o Desktop decide e a permissão do Server vale até 30 dias.
- Biblioteca do Desktop usa as rotas protegidas de catálogo, capa e áudio. O áudio segue direto pela rede local do Desktop para o celular, com `Range` para busca na faixa; o Server autoriza, mas não armazena nem retransmite músicas.
- Tokens de acesso duram poucos minutos, ficam cifrados com Android Keystore e são renovados enquanto a reprodução continua; ao sair da conta, os dados locais da autorização são apagados.
- Depois de revogar o novo acesso Cobre ID, o app não volta silenciosamente ao endereço antigo por chave LAN; o pedido de autorização continua obrigatório após logout ou restauração do backup.
- Controles no Cobre ID permitem ver a faixa do Desktop, pausar/reproduzir, avançar/voltar e ajustar seu volume.
- Tela completa do player foi reorganizada em torno da capa, identificação da origem, progresso, controles, volume visível e acesso à fila; o visual usa a paleta escura cobre do Desktop como referência.
- Configurações agora separam biblioteca do celular, integração com Desktop, aparência e áudio. Equalizador real de cinco bandas aplica ajustes à sessão de reprodução do Android e guarda os valores no backup.
- Cobre ID organiza conta/aparelhos, autorização Desktop e pareamento TV em seções próprias; os controles remotos mostram o estado do player do computador.
- Corrigida a migração de refresh tokens antigos para o armazenamento cifrado; IDs e autorizações do Desktop não entram no backup exportável.
- Requer Cobre Desktop 3.9.0 ou posterior e Cobre Server 0.5.0-beta.9 ou posterior para o novo acesso protegido. A conexão antiga por chave LAN continua disponível como compatibilidade.
- Build compilada localmente em debug. Ainda falta validar a autorização e uma faixa longa no aparelho, publicar a versão com certificado de distribuição estável e confirmar que os controles remotos estão disponíveis na build Desktop instalada.

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
