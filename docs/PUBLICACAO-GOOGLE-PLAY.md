# Publicação do Cobre Mobile na Google Play

## Preparação técnica em 0.19.0

- A variante `play` compila para API 36 e remove a permissão `REQUEST_INSTALL_PACKAGES`, que a política da loja proíbe usar para atualizar o próprio app. Essa variante não baixa nem instala APKs externos.
- A variante `direct` preserva o canal atual de APK e o atualizador por GitHub.
- O workflow publica APK direto e AAB Play assinados com a chave estável existente. O Android App Bundle precisa ser validado no GitHub Actions antes de uma tag de release.
- O pacote `app.cobre.mobile` já foi distribuído fora da loja. A chave usada nos APKs diretos precisa ser preservada ao configurar Play App Signing para que os dois canais possam atualizar a mesma instalação.

## Itens obrigatórios antes do envio

1. Validar a variante Play em Android 16, principalmente bordas do sistema, início da reprodução, serviço em segundo plano, notificações, QR, conexão local e restauração de preferências.
2. Criar/usar uma conta Play Console verificada e registrar o app com o nome do pacote e a chave corretos.
3. Publicar política de privacidade acessível dentro e fora do app; preencher a seção Segurança dos dados de acordo com conta Cobre ID, identificador de dispositivo e métricas de conexão enviadas ao Cobre Server. O áudio segue diretamente do Desktop e não passa pelo Server.
4. Confirmar um processo claro de exclusão da conta e dos dados associados. O código atual não foi confirmado como oferecendo esse fluxo.
5. Completar classificação de conteúdo, ficha da loja, screenshots, contato do desenvolvedor, declarações de permissões e credenciais/instruções de revisão para o Cobre ID.
6. Para uma conta pessoal criada depois de 13/11/2023, concluir teste fechado com pelo menos 12 participantes inscritos continuamente por 14 dias antes de pedir acesso à produção.

## Assinatura e atualização

Usar no Play App Signing a chave de assinatura existente do canal direto, em vez de aceitar uma chave diferente sem avaliar a consequência. Uma chave diferente impediria atualizações por cima entre APK direto e versão da Play. Manter a chave privada fora do repositório e dos artefatos públicos.

## Estado

API 36, flavor `play`, remoção de auto-update APK e geração do AAB passaram no workflow [45055015245](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45055015245). O AAB de validação da branch é unsigned; a versão assinada só é gerada em tag de release e ainda não foi criada. A Play Console não foi configurada e nenhum app foi enviado para revisão.
