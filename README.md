# Integração Auttar TEF (CTFClient) - Java via JNA

Este projeto é uma aplicação modelo de Ponto de Venda (PDV) em console construída em **Java**, que realiza a integração direta com a biblioteca nativa da Auttar (`ctfclient.dll` / `libctfclient.so`) utilizando **JNA (Java Native Access)**.

O objetivo deste projeto é fornecer uma arquitetura limpa, robusta e orientada a objetos para o processamento de transações TEF (Transferência Eletrônica de Fundos), gerenciando adequadamente o ciclo de vida da transação, interatividade de tela e comunicação de baixo nível com o servidor CTF em ambientes **Windows e Linux**.

## 🚀 Funcionalidades Suportadas

* **Pagamentos Financeiros:** Crédito (À vista, Parcelado sem juros, Parcelado com juros), Débito e Crédito Digitado.
* **Transações PIX:** Geração e consulta de PIX integrados nativamente.
* **Múltiplos Cartões:** Rotina robusta para pagamento de uma única venda rateada em diversas formas de pagamento (com rollback automático em caso de cancelamento parcial).
* **Cancelamentos e Devoluções:** Cancelamento Genérico (`128`), Cancelamento de Crédito Digitado (`411`) e Devolução PIX (`431`).
* **Funções Administrativas:** Consulta PIX (`423`), Confirmação/Desfazimento por NSU (`280`/`281`), Confirmação/Desfazimento de Transação Pendente (`006`/`007`), Configuração do Terminal (`800`), Autenticação (`801`) e Reimpressão do último comprovante (`012`).
* **Questionamento de Transação Pendente (`7358`):** Suporte ao subcampo `7358` (`1=Sim` / `2=Não`) acionado quando `<Parameter name="confirmardesfazimento">true</Parameter>` está ativo no `configCTFClient.xml`, com resposta automática padrão `DESFAZER` para totens de auto-atendimento ou configurável via `AUTTAR_POLITICA_7358` (`DESFAZER`, `CONFIRMAR` ou `PERGUNTAR`).
* **Auto-Atendimento 24/7 (Timeout de Leitura do Cartão em 2 Fases + Data Fiscal Dinâmica):** Timer estritamente restrito à tela `"INSIRA, PASSE OU APROXIME O CARTAO"` (`30s` de espera inicial + `5s` de aviso `"VOCE AINDA ESTA AI?"` com opção de renovação antes de enviar `Comando 08` de cancelamento), sem impor timeout nas demais etapas da transação, além de captura dinâmica de `data_cli` (`YYYYMMDD`) a cada transação para operação contínua na virada de meia-noite.
* **Tratamento Autônomo (Auto-Responder):** Gerenciamento inteligente de menus de cancelamento e timeout, respondendo automaticamente à DLL da Auttar para manter a fluidez do caixa.

## ⚙️ Pré-requisitos

1. **Java Development Kit (JDK) 8 ou superior.**
    * ⚠️ **MUITO IMPORTANTE:** A arquitetura do seu Java (32-bit ou 64-bit) **DEVE ser exatamente a mesma** da versão da biblioteca (`ctfclient.dll` ou `libctfclient.so`) que você está utilizando. Executar uma lib x86 em uma JVM x64 (ou vice-versa) resultará no erro `UnsatisfiedLinkError`.
2. **Biblioteca JNA (Java Native Access).** * Se usar Maven, adicione a dependência `net.java.dev.jna:jna`.
3. **Ambiente Auttar:**
    * O aplicativo CTFClient deve estar instalado no computador local (ou executado em modo Portátil).

## 🏗️ Arquitetura do Projeto

O código no pacote `com.auttar` segue uma estrutura enxuta e autoexplicativa para facilitar a leitura e manutenção:

* `PdvConsoleApp.java`: Ponto de entrada da aplicação. Configura o console UTF-8 multiplataforma e orquestra os menus do PDV exemplo.
* `CtfClientService.java`: Facade de integração que aplica validações *Fail-Fast*, executa a máquina de estados (`continuaTransacaoCTF`) e controla multitransação.
* `CtfClientLibrary.java`: Interface JNA multiplataforma com as 6 funções nativas exportadas pelo CTFClient.
* `CtfCatalogo.java`: Catálogo tipado de operações, subcampos com nomes legíveis, comandos de tela (`00`-`09`) e códigos de retorno.
* `BufferNativoUtil.java`: Formatação de parâmetros tipo `A`/`N`, limpeza de buffers `byte[]` e leitura C-string com fallback UTF-8/CP850.
* `TransacaoTefRequest.java` / `TransacaoTefResponse.java`: POJOs para dados de requisição e resposta (NSU, código de autorização, cupons e dados PIX).
* `InteracaoTefCallback.java`: Contrato de callback que desacopla o motor TEF da interface do usuário.
* `CtfProcessManager.java`: Gerenciamento multiplataforma (Windows/Linux) do processo em segundo plano do `CTFClient` por PID, encerrando-o junto com o PDV.
* `TefAuditLogger.java`: Logger estruturado (`logs/ctfclient_YYYYMMDD.log`) com `txId`, `duracaoMs` e mascaramento automático de senha (`7918`) e cartão (`7006`).

## 🔧 Configuração e Instalação da Biblioteca Nativa

A interface `CtfClientLibrary.java` localiza a biblioteca nativa automaticamente na seguinte ordem de prioridade:

1. **Via Linha de Comando (Prioridade Máxima):**
   ```bash
   java -Djna.library.path=/caminho/para/sua/pasta/ -jar dll-java-2.0.jar
   ```
2. **Variável de Ambiente `CTFCLIENT_HOME`:** Procura automaticamente em `<CTFCLIENT_HOME>/bin`, `<CTFCLIENT_HOME>/lib` (Linux) ou na raiz informada.
3. **Modo Portátil (Diretório Atual):** Se a biblioteca estiver na mesma pasta onde o `.jar` está sendo executado.
4. **Padrões do Sistema Operacional:**
   * **Windows:** `C:\CTFClient\bin\ctfclient.dll`, `C:\Program Files (x86)\Auttar\CTFClient\bin\ctfclient.dll` (ou `C:\Program Files\...`)
   * **Linux:** `/opt/CTFClient/bin/libctfclient.so` (ou `/opt/CTFClient/lib/libctfclient.so`)

### Variáveis de Ambiente Opcionais

* Inicialização (`iniciaClientCTF`): `AUTTAR_TERMINAL`, `AUTTAR_VERSAO`, `AUTTAR_NOME_AC`, `AUTTAR_NUM_SITES`, `AUTTAR_LISTA_IPS`, `AUTTAR_CRIPTOGRAFIA`, `AUTTAR_LOG`, `AUTTAR_INTERATIVO`, `AUTTAR_PARAMETROS`.
* Autenticação (`801`): `AUTTAR_AUTH_LOGIN`, `AUTTAR_AUTH_SENHA`, `AUTTAR_AUTH_CNPJ`.
* Questionamento de Transação Pendente (`7358`): `AUTTAR_HABILITAR_7358` (`true` por padrão) e `AUTTAR_POLITICA_7358` (`DESFAZER` por padrão, `CONFIRMAR` ou `PERGUNTAR`).
* Timeout de Leitura do Cartão (Auto-Atendimento): `AUTTAR_TIMEOUT_CARTAO_SEGUNDOS` (`30` por padrão; `0` desativa) e `AUTTAR_AVISO_PRESENCA_SEGUNDOS` (`5` por padrão).

## ▶️ Como Compilar e Executar

### 1. Gerar o `.jar` executável e rodar os testes
```powershell
mvn clean package
```
*(Se `mvn` não estiver no `PATH`, utilize o `mvn.cmd` embutido no IntelliJ com `JAVA_HOME` apontando para o seu JDK.)*

### 2. Executar o PDV Console (`target/dll-java-2.0.jar`)
Com o Java 32-bit e o CTFClient configurados, execute:

* **Windows (PowerShell):**
  ```powershell
  $env:CTFCLIENT_HOME="C:\CTFClient"; $env:JAVA_HOME="C:\CTFClient\jre"; & "$env:JAVA_HOME\bin\java.exe" -jar target/dll-java-2.0.jar
  ```
* **Linux:**
  ```bash
  export CTFCLIENT_HOME=/opt/CTFClient
  java -Djna.library.path=$CTFCLIENT_HOME/lib -jar target/dll-java-2.0.jar
  ```

> **Logs e Auditoria:** A execução gera logs detalhados em `logs/ctfclient_YYYYMMDD.log` (com `txId`, `duracaoMs` e mascaramento de `7918` e `7006`).

## 📚 Guias e Tutoriais de Infraestrutura

Dependendo do seu sistema operacional e setup físico, consulte os guias detalhados na pasta `tutoriais/`:

* 🐧 [Configurando CTFClient x86 no Linux (Debian/Ubuntu)](tutoriais/SETUP_LINUX_X86.md)
* 🔌 [Configurando Pinpad Gertec via regras UDEV no Linux](tutoriais/SETUP_PINPAD_LINUX.md)
* 💻 [Compartilhando USB (Pinpad) no WSL 2 do Windows](tutoriais/SETUP_WSL_USB.md)

## 💡 Lições Técnicas (Troubleshooting)

* **Erro 61 em `iniciaClientCTF` (`csisecurity.dll` / `EX99`):** Ocorre quando `JAVA_HOME` aponta para um JDK 64-bit e a DLL auto-inicia o processo interno do `CTFClient` herdando essa JVM 64-bit. Defina `JAVA_HOME=C:\CTFClient\jre` antes de executar.
* **Erro 5331 / CT02 (Erro ao Confirmar Dado):** Ocorre quando há quebra de sincronia. A solução aplicada foi sempre devolver o **próprio comando recebido** como *Acknowledge* (Eco) para a DLL, exceto em comandos de entrada de dados.
* **Sujeira de Memória JNA:** Arrays brutos (`byte[]`) são zerados explicitamente (`Arrays.fill`) antes de cada envio para evitar que o lixo de memória da iteração anterior corrompa a requisição atual.
* **Timeout do PIX e Pinpad:** Não utilizamos timers no Java. A aplicação escuta a pergunta "DESEJA CONSULTA-LO NOVAMENTE?" após a expiração do PIX e aplica a resposta automática `"1"` (Sim) via `CtfClientService.isAutoReconsultaPix`.
