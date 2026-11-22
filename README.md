# Children Location

App Android (Kotlin + Jetpack Compose) para responsáveis saberem a localização do filho em tempo real.
Um único app: ao criar a conta a pessoa escolhe **responsável** ou **filho(a)**.

## Como funciona

- **Filho(a):** serviço em primeiro plano com notificação permanente (sempre transparente). O Fused Location Provider combina GPS, Wi-Fi e rede celular. A localização vai para o Firestore a cada ~15 s.
- **Responsável:** vê o filho num mapa OpenStreetMap (sem chave de API), com bateria, precisão e hora da última atualização.
- **Pareamento:** iniciado pelo celular **do filho**. Ele gera um código de 8 dígitos (10 min, uso único) e o responsável digita. Sem acesso ao aparelho do filho, ninguém consegue vincular.
- O filho vê quem acompanha e pode **remover** qualquer vínculo a qualquer momento.

## Configurar

1. Crie um projeto no [Firebase Console](https://console.firebase.google.com), adicione um app Android com o pacote `com.natantec.childrenlocation` e baixe o `google-services.json` para `app/`.
2. Ative **Authentication → E-mail/senha** e crie o banco **Firestore**.
3. Cole o conteúdo de `firestore.rules` em Firestore → Regras → Publicar.
4. Abra a pasta no Android Studio, deixe sincronizar o Gradle e rode em dois celulares (um como filho, outro como responsável).

## Segurança — o que já existe e o que falta

Já existe (nas regras do Firestore):
- O papel é definido uma única vez e não pode ser alterado depois.
- Só a criança escreve a própria localização; só responsáveis vinculados leem.
- O vínculo só é criado com um código válido gerado pelo celular da criança.

Falta fazer (por ordem de importância):
1. **Impedir que uma criança se cadastre como responsável** (hoje o papel é autodeclarado): verificação de maioridade/identidade do responsável, por exemplo CPF + validação, ou convite via conta já verificada.
2. **Limitar tentativas de código** (força bruta) com Cloud Functions + App Check.
3. Firebase **App Check** (Play Integrity) para aceitar só o app original.
4. Verificação de e-mail obrigatória antes de escolher o papel.
5. Aviso periódico ao filho de quem está vendo a localização, e histórico de acessos.
6. Política de privacidade e adequação à **LGPD** (dados de menores) antes de publicar na Play Store.

## Próximas funcionalidades

Cercas virtuais (chegou/saiu da escola), histórico de trajeto, botão SOS, bateria baixa.
