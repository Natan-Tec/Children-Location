# Children Location

App Android (Kotlin + Jetpack Compose) para responsáveis acompanharem a localização dos filhos em tempo real.

## Como funciona (beta 0.2)

- **Só adultos têm conta** (e-mail e senha, ou Google). A **criança não cria conta**: instala o app, toca em "Sou criança: escanear QR code" e lê o QR gerado no celular dos pais.
- **Família:** o primeiro responsável cria a família, cadastra as crianças (nome e figura) e convida outros adultos (mãe, avó...) pelo WhatsApp ou e-mail do próprio celular. O convidado cria conta com aquele e-mail e toca em "Aceitar".
- **Aprovação de adultos (só na beta):** a conta de adulto nasce "pendente" e só cria família depois de aprovada no **painel de administração**. Adultos convidados por um responsável já aprovado entram sem aprovação.
- **Criança:** vê o mapa com a própria posição. Não tem configurações, nem botão de sair ou parar. Só os pais desvinculam o aparelho.
- **Localização:** serviço em primeiro plano com notificação permanente. O Fused Location Provider combina GPS, Wi-Fi e rede celular. Intervalo adaptativo (mais espaçado quando parada) para poupar bateria e a cota gratuita do Firebase. Volta sozinho após reiniciar o celular.
- **Pareamento:** QR com token aleatório de 24 caracteres, uso único, válido por 15 minutos. Se o aparelho for desvinculado, ele percebe e volta para a tela inicial.

## Painel de administração

Página web (GitHub Pages) em `https://natan-tec.github.io/Children-Location/`. Entra-se com o Google do administrador (`tecnologiconatan@gmail.com`), vê os pedidos pendentes e aprova ou bloqueia. Também lista famílias, crianças e a última localização. Funciona no celular e pode ser adicionada à tela inicial.

A configuração do Firebase da página é gerada pelo workflow **Painel (GitHub Pages)** a partir do mesmo secret do APK. Não é segredo (qualquer app carrega essa chave); quem protege os dados são as regras do Firestore.

## Configurar o Firebase

1. Projeto no [Firebase Console](https://console.firebase.google.com) com um app Android de pacote `com.natantec.childrenlocation`.
2. **Authentication:** ativar **E-mail/senha**, **Google** e **Anônimo** (usado pelo aparelho da criança). Em *Configurações > Domínios autorizados*, adicionar `natan-tec.github.io`.
3. **Google:** em *Configurações do projeto > Seus apps*, adicionar o SHA-1 da chave fixa do app (abaixo) e **baixar de novo o `google-services.json`**.
4. **Firestore:** criar o banco e colar o `firestore.rules` na aba Regras > Publicar.
5. **GitHub:** salvar o `google-services.json` em base64 no secret `GOOGLE_SERVICES_JSON` (Settings > Secrets and variables > Actions). Em Settings > Pages, escolher **GitHub Actions** como origem.

SHA-1 da chave fixa: `1E:45:28:CC:23:39:38:57:83:A7:DC:75:B2:92:C0:C0:D6:54:C1:C7`

## Compilar

Pelo GitHub Actions: **Build APK** gera o APK a cada push; rodando manualmente (Actions > Build APK > Run workflow) ele também publica um pré-lançamento em Releases. A chave de assinatura é fixa (`app/debug.keystore`, versionada de propósito e sem segredo), então cada APK atualiza o anterior sem desinstalar. Para a versão pública, trocar por uma chave de release privada.

## Segurança

Já existe (regras do Firestore):
- Só adulto aprovado cria família; o aprovado não pode ser alterado por ninguém além do administrador.
- A criança (login anônimo) só consegue assumir um perfil com um QR válido de uso único e só escreve a localização do próprio perfil.
- Só membros da família leem a localização. O convite só vale para o e-mail verificado do convidado.

Limites conhecidos:
- Nenhum sistema impede 100% que uma criança crie conta de adulto sem verificar identidade. Na beta isso é contido pela aprovação manual. Para a versão pública: sinais de idade da Google (Play Age Signals, a confirmar fora da Play Store) ou verificação de documento.
- O APK da beta é baixável por qualquer pessoa. Contas novas ficam pendentes até a aprovação.
- Sem o plano pago do Firebase não há servidor: o SOS (beta 0.3) usará escuta no app dos pais e SMS de reserva.
- LGPD, política de privacidade e Play Store ficam fora da beta fechada e voltam antes de abrir para outras famílias.

## Próximas betas

- **0.3:** SOS tocando como chamada nos pais, ligar 190/192/193 pelo app, contatos de acesso rápido, mensagens de texto e voz para texto.
- **0.4:** lugares e trajetos pré-definidos, histórico gravado, alertas de saída e SMS de reserva.
