# Histórico de versões — Cobre Mobile

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
