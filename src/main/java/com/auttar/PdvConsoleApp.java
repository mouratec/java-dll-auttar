package com.auttar;

import com.auttar.CtfCatalogo.CodigoOperacao;
import com.auttar.CtfCatalogo.Subcampo;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Interface de console (PDV modelo) para integração com a biblioteca CTFClient
 * da Auttar.
 */
public class PdvConsoleApp {

    private static final Scanner SCANNER = new Scanner(System.in, StandardCharsets.UTF_8.name());

    private static final InteracaoTefCallback CONSOLE_CALLBACK = new InteracaoTefCallback() {
        @Override
        public void exibirMensagem(String mensagem, String auxDisplay, boolean isTituloMenu) {
            if (BufferNativoUtil.naoVazio(mensagem)) {
                System.out.printf("INFO DLL: %s%n", mensagem.replace(";", System.lineSeparator()));
            }
        }

        @Override
        public String confirmarOperacao(String mensagemContexto) {
            System.out.print("Confirmar operação? (1=Sim / 2=Não / C=Cancelar): ");
            String resp = SCANNER.nextLine().trim().toUpperCase();
            if (resp.startsWith("C")) {
                return "CANCELAR";
            }
            return ("1".equals(resp) || resp.startsWith("S")) ? "1" : "2";
        }

        @Override
        public String selecionarOpcaoMenu(String opcoesMenu, String tituloContexto, boolean temporizado) {
            if (CtfClientService.isAutoReconsultaPix(opcoesMenu, tituloContexto)) {
                System.out.println("\n[AUTO] Reconsulta PIX detectada: Respondendo '1' (SIM).");
                return "1";
            }
            if (BufferNativoUtil.naoVazio(opcoesMenu)) {
                String formatado = opcoesMenu.replace("\\", System.lineSeparator()).replace(";", " | ");
                System.out.printf("MENU: %s%nSua opção (ou C para cancelar): ", formatado);
            } else if (temporizado) {
                System.out.print("Aguardando opção (Enter para continuar, ou digite a opção / C para cancelar): ");
            }
            String escolha = SCANNER.nextLine().trim();
            return "C".equalsIgnoreCase(escolha) ? "CANCELAR" : escolha;
        }

        @Override
        public boolean aguardarTecla(String mensagemContexto) {
            System.out.print("Pressione [ENTER] para continuar (ou digite C para cancelar): ");
            return !"C".equalsIgnoreCase(SCANNER.nextLine().trim());
        }

        @Override
        public String capturarDado(String numSc, int tamanhoMaximo, boolean aceitaZerosEsquerda,
                String mensagemContexto) {
            if (CtfClientService.isQuestionamentoTransacaoPendente(numSc)) {
                while (true) {
                    System.out.print("Questionamento de transação pendente [7358] (1=Sim / 2=Não / C=Cancelar): ");
                    if (!SCANNER.hasNextLine()) {
                        return null;
                    }
                    String entrada = SCANNER.nextLine().trim();
                    if ("C".equalsIgnoreCase(entrada) || "CANCELAR".equalsIgnoreCase(entrada)) {
                        return null;
                    }
                    String normalizada = CtfClientService.normalizarResposta7358(entrada);
                    if (normalizada != null) {
                        return normalizada;
                    }
                    System.out.println("Opção inválida. Digite 1 (Sim), 2 (Não) ou C (Cancelar).");
                }
            }
            System.out.printf("Informe o dado (subcampo %s, máx %d) [C para cancelar]: ", numSc, tamanhoMaximo);
            String dado = SCANNER.nextLine();
            return "C".equalsIgnoreCase(dado.trim()) ? null : dado;
        }

        @Override
        public boolean confirmarPresencaLeituraCartao() {
            try {
                if (System.in.available() > 0) {
                    if (SCANNER.hasNextLine()) {
                        SCANNER.nextLine();
                    }
                    System.out.println("[SISTEMA] Presença confirmada! Tempo de espera do cartão renovado.");
                    return true;
                }
            } catch (Exception ignored) {
            }
            return false;
        }
    };

    private interface Win32Kernel32 extends Library {
        boolean SetConsoleOutputCP(int wCodePageID);

        boolean SetConsoleCP(int wCodePageID);
    }

    public static void main(String[] args) {
        configurarConsoleUtf8();

        System.out.println("======================================");
        System.out.println("=   Sistema de Integração Auttar     =");
        System.out.println("======================================\n");

        CtfClientService service = new CtfClientService();
        if (!service.inicializarCliente()) {
            System.out.println("\nPressione Enter para sair...");
            if (SCANNER.hasNextLine()) {
                SCANNER.nextLine();
            }
            return;
        }

        try {
            while (executarMenuPrincipal(service)) {
                // Loop principal do PDV
            }
        } finally {
            System.out.println("Encerrando o programa e finalizando processo CTFClient...");
            service.encerrarCliente();
            SCANNER.close();
        }
    }

    static void configurarConsoleUtf8() {
        if (Platform.isWindows()) {
            try {
                Win32Kernel32 kernel32 = Native.load("kernel32", Win32Kernel32.class);
                kernel32.SetConsoleOutputCP(65001);
                kernel32.SetConsoleCP(65001);
            } catch (Throwable ignored) {
                // Ambiente sem console interativo (pipe/serviço/testes)
            }
        }
        try {
            String utf8 = StandardCharsets.UTF_8.name();
            System.setOut(new PrintStream(System.out, true, utf8));
            System.setErr(new PrintStream(System.err, true, utf8));
        } catch (UnsupportedEncodingException ignored) {
            // UTF-8 é garantido pela especificação da JVM
        }
    }

    private static boolean executarMenuPrincipal(CtfClientService service) {
        System.out.println("\n==========================================");
        System.out.println("              MENU PRINCIPAL              ");
        System.out.println("==========================================");
        System.out.println(" 1. Menu de Vendas e Serviços             ");
        System.out.println(" 2. Menu Administrativo                   ");
        System.out.println(" 3. Reimpressão do Último Cupom           ");
        System.out.println(" 4. Configuração CTFClient                ");
        System.out.println(" 5. Autenticação do Terminal              ");
        System.out.println(" 0. Sair                                  ");
        System.out.println("------------------------------------------");
        System.out.print("\nOpção: ");
        if (!SCANNER.hasNextLine()) {
            return false;
        }
        String escolha = SCANNER.nextLine().trim();
        TransacaoTefRequest request = null;

        switch (escolha) {
            case "1":
                executarMenuVendas(service);
                break;
            case "2":
                executarMenuAdministrativo(service);
                break;
            case "3":
                request = criarRequestAdministrativo(CodigoOperacao.ReimpressaoUltimoComprovante,
                        "Reimpressão do Último Comprovante");
                break;
            case "4":
                request = criarRequestAdministrativo(CodigoOperacao.ConfiguracaoCtfClient,
                        "Configuração do CTFClient (800)");
                break;
            case "5":
                request = criarRequestAutenticacao();
                break;
            case "0":
                return false;
            default:
                System.out.println("Opção inválida.");
                break;
        }

        if (request != null) {
            TransacaoTefResponse response = service.executarTransacaoCompleta(
                    request, "01", true, CONSOLE_CALLBACK);
            exibirResultado(request, response, "01");
        }
        return true;
    }

    private static void executarMenuVendas(CtfClientService service) {
        while (true) {
            System.out.println("\n------------------------------------------");
            System.out.println("         MENU DE VENDAS E SERVIÇOS        ");
            System.out.println("------------------------------------------");
            System.out.println(" 1. Crédito (À Vista)                     ");
            System.out.println(" 2. Crédito Parcelado Sem Juros           ");
            System.out.println(" 3. Crédito Parcelado Com Juros           ");
            System.out.println(" 4. Débito                                ");
            System.out.println(" 5. Crédito Digitado                      ");
            System.out.println(" 6. Venda PIX                             ");
            System.out.println(" 7. Crédito Genérico                      ");
            System.out.println(" 8. Débito Genérico                       ");
            System.out.println(" 9. Venda com Múltiplos Cartões           ");
            System.out.println(" 0. Voltar ao Menu Principal              ");
            System.out.print("\nOpção: ");
            if (!SCANNER.hasNextLine()) {
                return;
            }
            String escolha = SCANNER.nextLine().trim();
            TransacaoTefRequest request = null;
            String docFiscal = "";

            switch (escolha) {
                case "1":
                    request = criarRequestPadrao(CodigoOperacao.Credito, docFiscal);
                    break;
                case "2":
                    request = criarRequestParcelado(CodigoOperacao.CreditoParceladoSemJuros, docFiscal);
                    break;
                case "3":
                    request = criarRequestParcelado(CodigoOperacao.CreditoParceladoComJuros, docFiscal);
                    break;
                case "4":
                    request = criarRequestPadrao(CodigoOperacao.Debito, docFiscal);
                    break;
                case "5":
                    request = criarRequestCreditoDigitado(docFiscal, null);
                    break;
                case "6":
                    request = criarRequestPadrao(CodigoOperacao.PagamentoPix, docFiscal);
                    break;
                case "7":
                    request = criarRequestPadrao(CodigoOperacao.CreditoGenerico, docFiscal);
                    break;
                case "8":
                    request = criarRequestPadrao(CodigoOperacao.DebitoGenerico, docFiscal);
                    break;
                case "9":
                    executarVendaMultiplosCartoes(service, docFiscal);
                    break;
                case "0":
                    return;
                default:
                    System.out.println("Opção inválida.");
                    break;
            }

            if (request != null) {
                TransacaoTefResponse response = service.executarTransacaoCompleta(
                        request, "01", true, CONSOLE_CALLBACK);
                exibirResultado(request, response, "01");
            }
        }
    }

    private static void executarMenuAdministrativo(CtfClientService service) {
        while (true) {
            System.out.println("\n------------------------------------------");
            System.out.println("           MENU ADMINISTRATIVO            ");
            System.out.println("------------------------------------------");
            System.out.println(" 1. Cancelamento Genérico (Crédito/Débito)");
            System.out.println(" 2. Cancelamento Crédito Digitado         ");
            System.out.println(" 3. Devolução PIX                         ");
            System.out.println(" 4. Consulta PIX                          ");
            System.out.println(" 5. Confirmação por NSU (280)             ");
            System.out.println(" 6. Desfazimento por NSU (281)            ");
            System.out.println(" 7. Operação 006                          ");
            System.out.println(" 8. Operação 007                          ");
            System.out.println(" 0. Voltar ao Menu Principal              ");
            System.out.print("\nOpção: ");
            if (!SCANNER.hasNextLine()) {
                return;
            }
            String escolha = SCANNER.nextLine().trim();
            TransacaoTefRequest request = null;

            switch (escolha) {
                case "1":
                    request = coletarCancelamentoComNsu(CodigoOperacao.CancelamentoGenerico, "Cancelamento Genérico");
                    break;
                case "2":
                    request = coletarDadosCancelamentoCreditoDigitado();
                    break;
                case "3":
                    request = coletarCancelamentoComNsu(CodigoOperacao.DevolucaoPix, "Devolução PIX");
                    break;
                case "4":
                    request = criarRequestConsultaPix();
                    break;
                case "5":
                    request = coletarConfirmacaoPorNsu();
                    break;
                case "6":
                    request = coletarCancelamentoComNsu(CodigoOperacao.DesfazimentoPorNsu, "Desfazimento por NSU (281)");
                    break;
                case "7":
                    request = criarRequestAdministrativo(CodigoOperacao.Operacao006, "Operação 006");
                    break;
                case "8":
                    request = criarRequestAdministrativo(CodigoOperacao.Operacao007, "Operação 007");
                    break;
                case "0":
                    return;
                default:
                    System.out.println("Opção inválida.");
                    break;
            }

            if (request != null) {
                TransacaoTefResponse response = service.executarTransacaoCompleta(
                        request, "01", true, CONSOLE_CALLBACK);
                exibirResultado(request, response, "01");
            }
        }
    }

    private static void executarVendaMultiplosCartoes(CtfClientService service, String docFiscal) {
        System.out.println("\n--- VENDA COM MÚLTIPLOS CARTÕES ---");
        BigDecimal valorTotal = obterValorDaTransacao();
        BigDecimal valorPendente = valorTotal;
        int numeroTransacao = 1;
        List<TransacaoTefResponse> aprovadas = new ArrayList<>();

        while (valorPendente.compareTo(new BigDecimal("0.005")) > 0) {
            System.out.printf("\nValor Total da Venda...: R$ %,.2f%n", valorTotal);
            System.out.printf("Valor Pendente.........: R$ %,.2f%n", valorPendente);
            System.out.println("-------------------------------------");

            BigDecimal valorPagamento = obterValorParcial(valorPendente);
            TransacaoTefRequest request = escolherPagamentoParcial(valorPagamento, docFiscal);
            if (request == null) {
                break;
            }

            String numTransStr = String.format("%02d", numeroTransacao);
            TransacaoTefResponse response = service.executarTransacaoCompleta(
                    request, numTransStr, false, CONSOLE_CALLBACK);

            if (response != null && response.isAprovada()) {
                exibirResultado(request, response, numTransStr);
                aprovadas.add(response);

                valorPendente = valorPendente.subtract(valorPagamento);
                numeroTransacao++;
            } else {
                System.out.println("\nPagamento NEGADO/ERRO: "
                        + (response != null ? response.getMensagemRetorno() : "Erro desconhecido"));
                System.out.print("Tentar outro pagamento (T) ou Cancelar a venda (C)? ");
                if (SCANNER.nextLine().trim().toUpperCase().startsWith("C")) {
                    if (!aprovadas.isEmpty()) {
                        service.desfazerBlocoTransacoes(aprovadas.size());
                        System.out.println("\nVENDA CANCELADA. TRANSAÇÕES APROVADAS FORAM DESFEITAS.");
                    }
                    return;
                }
            }
        }

        if (valorPendente.compareTo(new BigDecimal("0.005")) < 0 && !aprovadas.isEmpty()) {
            System.out.println("\nVenda totalmente paga. Confirmar todos os pagamentos? (S/N)");
            if ("S".equalsIgnoreCase(SCANNER.nextLine().trim())) {
                if (service.confirmarBlocoTransacoes(aprovadas.size())) {
                    System.out.println("\nVENDA CONFIRMADA COM SUCESSO!");
                } else {
                    System.out.println("\nERRO AO CONFIRMAR LOTE DE TRANSAÇÕES NA DLL.");
                }
            } else {
                service.desfazerBlocoTransacoes(aprovadas.size());
                System.out.println("\nTRANSAÇÕES DESFEITAS.");
            }
        }
    }

    private static TransacaoTefRequest criarRequestPadrao(CodigoOperacao operacao, String docFiscal) {
        System.out.printf("\n--- %s ---%n", operacao.name());
        return new TransacaoTefRequest(operacao, obterValorDaTransacao(), docFiscal);
    }

    private static TransacaoTefRequest criarRequestParcelado(CodigoOperacao operacao, String docFiscal) {
        System.out.printf("\n--- %s ---%n", operacao.name());
        TransacaoTefRequest req = new TransacaoTefRequest(operacao, obterValorDaTransacao(), docFiscal);
        req.setNumeroParcelas(obterNumeroParcelas());
        return req;
    }

    private static TransacaoTefRequest criarRequestCreditoDigitado(String docFiscal, BigDecimal valorFixo) {
        System.out.println("\n--- Crédito Digitado ---");
        BigDecimal valor = valorFixo != null ? valorFixo : obterValorDaTransacao();
        System.out.print("Número do cartão: ");
        String cartao = SCANNER.nextLine();
        System.out.print("Validade (MMAA): ");
        String validade = SCANNER.nextLine();

        TransacaoTefRequest req = new TransacaoTefRequest(CodigoOperacao.CreditoDigitado, valor, docFiscal);
        req.setNumeroCartao(cartao);
        req.setVencimentoCartao(validade);
        return req;
    }

    private static TransacaoTefRequest criarRequestConsultaPix() {
        System.out.println("\n--- Consulta de Transação PIX ---");
        TransacaoTefRequest req = new TransacaoTefRequest(CodigoOperacao.ConsultaPix, obterValorDaTransacao(), "");
        System.out.print("DATA original (DDMMAA): ");
        req.setDataOriginal(SCANNER.nextLine());
        System.out.print("NSU CTF original do PIX: ");
        req.setNsuOriginal(SCANNER.nextLine());
        return req;
    }

    private static TransacaoTefRequest coletarCancelamentoComNsu(CodigoOperacao operacao, String titulo) {
        System.out.println("\n--- " + titulo + " ---");
        TransacaoTefRequest req = new TransacaoTefRequest(operacao, obterValorDaTransacao(), "");
        System.out.print("DATA original (DDMMAA): ");
        req.setDataOriginal(SCANNER.nextLine());
        System.out.print("NSU CTF original: ");
        req.setNsuOriginal(SCANNER.nextLine());
        return req;
    }

    private static TransacaoTefRequest coletarConfirmacaoPorNsu() {
        System.out.println("\n--- Confirmação por NSU (280) ---");
        TransacaoTefRequest req = new TransacaoTefRequest(CodigoOperacao.ConfirmacaoPorNsu, BigDecimal.ZERO, "");
        System.out.print("DATA original (DDMMAA): ");
        req.setDataOriginal(SCANNER.nextLine());
        System.out.print("NSU da transação: ");
        req.setNsuOriginal(SCANNER.nextLine());
        return req;
    }

    private static TransacaoTefRequest coletarDadosCancelamentoCreditoDigitado() {
        TransacaoTefRequest req = coletarCancelamentoComNsu(
                CodigoOperacao.CancelamentoCreditoDigitado, "Cancelamento de Crédito Digitado");
        System.out.print("NÚMERO DO CARTÃO original: ");
        req.setNumeroCartao(SCANNER.nextLine());
        return req;
    }

    private static TransacaoTefRequest criarRequestAutenticacao() {
        System.out.println("\n--- Autenticação do Terminal (801) ---");
        TransacaoTefRequest req = new TransacaoTefRequest(CodigoOperacao.AutenticacaoTerminal, BigDecimal.ZERO, "");
        req.setLoginAutenticacao(lerOuEnv("Login (subcampo 7917)", "AUTTAR_AUTH_LOGIN"));
        req.setSenhaAutenticacao(lerOuEnv("Senha (subcampo 7918)", "AUTTAR_AUTH_SENHA"));
        req.setCnpjAutenticacao(lerOuEnv("CNPJ (subcampo 7919)", "AUTTAR_AUTH_CNPJ"));
        return req;
    }

    private static String lerOuEnv(String prompt, String envKey) {
        String envVal = System.getenv(envKey);
        if (BufferNativoUtil.naoVazio(envVal)) {
            System.out.println(prompt + ": [lido da variável " + envKey + "]");
            return envVal.trim();
        }
        System.out.print(prompt + ": ");
        return SCANNER.nextLine().trim();
    }

    private static TransacaoTefRequest criarRequestAdministrativo(CodigoOperacao operacao, String titulo) {
        System.out.println("\n--- " + titulo + " ---");
        return new TransacaoTefRequest(operacao, BigDecimal.ZERO, "");
    }

    private static BigDecimal obterValorDaTransacao() {
        while (true) {
            System.out.print("\nValor da transação (ex: 19,99): R$ ");
            String input = SCANNER.nextLine().replace(",", ".").trim();
            try {
                BigDecimal valor = new BigDecimal(input);
                if (valor.compareTo(BigDecimal.ZERO) >= 0) {
                    return valor;
                }
            } catch (NumberFormatException ignored) {
                // Repete prompt
            }
            System.out.println("Valor inválido.");
        }
    }

    private static int obterNumeroParcelas() {
        while (true) {
            System.out.print("Número de parcelas (mínimo 2): ");
            String input = SCANNER.nextLine().trim();
            try {
                int parcelas = Integer.parseInt(input);
                if (parcelas >= 2) {
                    return parcelas;
                }
            } catch (NumberFormatException ignored) {
                // Repete prompt
            }
            System.out.println("Número de parcelas inválido (informe 2 ou mais).");
        }
    }

    private static void exibirResultado(TransacaoTefRequest request, TransacaoTefResponse response, String numTrans) {
        System.out.println("\n---------- RESULTADO DA TRANSAÇÃO ----------");
        System.out.println("Status: " + (response.isAprovada() ? "APROVADA" : "NEGADA / ERRO"));
        System.out.println("Mensagem: " + response.getMensagemRetorno());
        if (request != null && request.getValor() != null) {
            System.out.printf("Valor: R$ %,.2f%n", request.getValor());
        }
        if (request != null && request.getNumeroParcelas() != null && request.getNumeroParcelas() > 1) {
            System.out.printf("Parcelas: %dx%n", request.getNumeroParcelas());
        }
        String cartao = response.getCamposRetornados().get(Subcampo.NumeroCartao.codigo());
        System.out.println("Cartão: " + (cartao != null ? TefAuditLogger.mascararSubcampo("7006", cartao) : "N/A"));
        System.out.println("Retorno 7000: " + response.getCodigoRetorno());
        System.out.println("Código de Erro: "
                + (BufferNativoUtil.naoVazio(response.getCodigoErro()) ? response.getCodigoErro() : "N/A"));
        System.out.println(
                "NSU CTF: " + (BufferNativoUtil.naoVazio(response.getNsuCtf()) ? response.getNsuCtf() : "N/A"));
        System.out.println("Cód. Autorização: "
                + (BufferNativoUtil.naoVazio(response.getCodigoAutorizacao()) ? response.getCodigoAutorizacao()
                        : "N/A"));
        System.out.println("Num. Transação: " + numTrans);

        if (!response.getCamposRetornados().isEmpty()) {
            System.out.println("\n--- SUBCAMPOS / RETORNOS DA DLL ---");
            response.getCamposRetornados().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> System.out.printf("  [%s] = %s%n",
                            Subcampo.formatarIdentificador(entry.getKey()),
                            TefAuditLogger.mascararSubcampo(entry.getKey(), entry.getValue())));
        }
        if (BufferNativoUtil.naoVazio(response.getComprovanteCliente())) {
            System.out.println("\n--- COMPROVANTE (VIA CLIENTE) ---");
            System.out.println(response.getComprovanteCliente());
            System.out.println("---------------------------------");
        }
        System.out.println("-------------------------------------------\n");
    }

    private static TransacaoTefRequest escolherPagamentoParcial(BigDecimal valorPagamento, String docFiscal) {
        while (true) {
            System.out.println("\n--- Escolha a forma do pagamento parcial ---");
            System.out.println("1. Crédito");
            System.out.println("2. Débito");
            System.out.println("3. Crédito Digitado");
            System.out.println("0. Cancelar a venda");
            System.out.print("\nOpção: ");
            String escolha = SCANNER.nextLine().trim();

            switch (escolha) {
                case "1":
                    return new TransacaoTefRequest(CodigoOperacao.Credito, valorPagamento, docFiscal);
                case "2":
                    return new TransacaoTefRequest(CodigoOperacao.Debito, valorPagamento, docFiscal);
                case "3":
                    return criarRequestCreditoDigitado(docFiscal, valorPagamento);
                case "0":
                    return null;
                default:
                    System.out.println("Opção inválida. Tente novamente.");
                    break;
            }
        }
    }

    private static BigDecimal obterValorParcial(BigDecimal valorPendente) {
        while (true) {
            System.out.printf("Valor do pagamento (ou Enter para R$ %,.2f): R$ ", valorPendente);
            String input = SCANNER.nextLine().trim();
            if (input.isEmpty()) {
                return valorPendente;
            }
            try {
                BigDecimal valor = new BigDecimal(input.replace(",", "."));
                if (valor.compareTo(BigDecimal.ZERO) > 0
                        && valor.compareTo(valorPendente.add(new BigDecimal("0.005"))) <= 0) {
                    return valor;
                }
            } catch (Exception ignored) {
                // Repete prompt
            }
            System.out.printf("Valor inválido. Insira um valor entre R$ 0,01 e R$ %,.2f.%n", valorPendente);
        }
    }
}
