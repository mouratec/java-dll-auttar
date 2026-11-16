package com.auttar;

import java.util.HashMap;
import java.util.Map;

/**
 * Catálogo centralizado de operações, subcampos, comandos de tela e códigos de retorno
 * utilizados na comunicação com o CTFClient.
 * Evita números mágicos espalhados pelo código e facilita a leitura dos logs.
 */
public final class CtfCatalogo {

    private CtfCatalogo() {
    }

    /** Códigos de operação enviados em {@code iniciaTransacaoCTF} / {@code iniciaTransacaoCTFext}. */
    public enum CodigoOperacao {
        /** Confirmação de transação pendente (006). */
        Operacao006(6, false, false),
        /** Desfazimento de transação pendente (007). */
        Operacao007(7, false, false),
        ReimpressaoUltimoComprovante(12, false, false),
        Debito(101, true, true),
        Credito(112, true, true),
        CreditoParceladoSemJuros(113, true, true),
        CreditoParceladoComJuros(114, true, true),
        CreditoDigitado(120, true, true),
        CancelamentoGenerico(128, true, true),
        CreditoGenerico(223, true, true),
        DebitoGenerico(224, true, true),
        ConfirmacaoPorNsu(280, false, false),
        DesfazimentoPorNsu(281, false, false),
        CancelamentoCreditoDigitado(411, true, true),
        PagamentoPix(422, true, true),
        ConsultaPix(423, false, false),
        DevolucaoPix(431, false, false),
        ConfiguracaoCtfClient(800, false, false),
        AutenticacaoTerminal(801, false, false);

        public final int valor;
        private final boolean exigeConfirmacao;
        private final boolean permiteDesfazimento;

        CodigoOperacao(int valor, boolean exigeConfirmacao, boolean permiteDesfazimento) {
            this.valor = valor;
            this.exigeConfirmacao = exigeConfirmacao;
            this.permiteDesfazimento = permiteDesfazimento;
        }

        public String codigoFormatado() {
            return String.format("%03d", valor);
        }

        public boolean exigeConfirmacao() {
            return exigeConfirmacao;
        }

        public boolean permiteDesfazimento() {
            return permiteDesfazimento;
        }
    }

    /** Subcampos enviados e recebidos durante a transação TEF (ex.: NSU, cupom, bandeira, erros). */
    public enum Subcampo {
        CodigoRetornoTransacao("7000", "CodRetornoTransacao", false),
        CodigoTransacao("7001", "CodigoTransacao", false),
        ValorTransacao("7005", "ValorTransacao", false),
        NumeroCartao("7006", "CartaoMascaradoOuPan", false),
        NumeroParcelas("7008", "NumeroParcelas", false),
        VencimentoCartao("7010", "VencimentoCartao", false),
        CodigoAutorizadora("7011", "CodigoAutorizadora", false),
        NsuCtfOriginal("7012", "NsuCtfOriginal", false),
        CodigoResposta("7015", "CodRespostaAutorizadora", false),
        NumeroDocumento("7020", "NumeroDocumento", false),
        DadosComplementares("7029", "DadosComplementares", false),
        NsuCtf("7031", "NsuCtf", false),
        ModoEntradaCartao("7081", "ModoEntradaCartao", false),
        NsuRedeAdquirente("7095", "NsuRedeAdquirente", false),
        DataTransacaoOriginal("7161", "DataTransacaoOriginal", false),
        CodigoErro("7300", "CodigoErroCtf", false),
        DescricaoTransacao("7301", "DescricaoTransacao", false),
        CupomCliente("7302", "CupomCliente", false),
        CupomLojista("7303", "CupomLojista", false),
        DataHoraComprovante("7306", "DataHoraComprovante", false),
        ValorAprovado("7308", "ValorAprovado", false),
        DataTransacaoCtf("7311", "DataTransacaoCtf", false),
        HoraTransacaoCtf("7312", "HoraTransacaoCtf", false),
        CodigoEstabelecimento("7315", "CodigoEstabelecimento", false),
        QuestionamentoTransacaoPendente("7358", "QuestionamentoTransacaoPendente", false),
        NumeroLogicoTerminal("7381", "NumeroLogicoTerminal", false),
        AidCartaoChip("7384", "AidCartaoChip", false),
        MensagemDisplay("7385", "MensagemDisplay", false),
        ViaComprovante("7388", "ViaComprovante", false),
        NomeBandeira("7389", "NomeBandeira", false),
        IdentificadorConsulta("7390", "IdentificadorConsulta", false),
        FlagConsulta("7391", "FlagConsulta", false),
        IdentificadorPix("7516", "IdentificadorPix", false),
        PspPix("7517", "PspPix", false),
        CodigoAutorizacao("7564", "CodigoAutorizacao", false),
        CodigoTerminal("7900", "CodigoTerminal", false),
        LoginAutenticacao("7917", "LoginAutenticacao", false),
        SenhaAutenticacao("7918", "SenhaAutenticacao", false),
        CnpjAutenticacao("7919", "CnpjAutenticacao", false),
        IdentificadorMultiEc("7926", "IdentificadorMultiEc", false),
        CupomReduzido("7939", "CupomReduzido", false),
        NomeRedeAdquirente("7942", "NomeRedeAdquirente", false),
        ModalidadePagamento("7943", "ModalidadePagamento", false),
        CnpjAdquirente("7947", "CnpjAdquirente", false),
        NomeTransacao("7948", "NomeTransacao", false),
        TipoFinanciamento("7949", "TipoFinanciamento", false),
        HashComprovante("7963", "HashComprovante", false),
        IdTransacaoAdquirente("7964", "IdTransacaoAdquirente", false),
        CodigoBandeira("7965", "CodigoBandeira", false),
        CodigoRede("7966", "CodigoRede", false),
        ModoCaptura("7967", "ModoCaptura", false),
        AidEmv("7968", "AidEmv", false),
        ReferenciaTerminal("7969", "ReferenciaTerminal", false);

        private static final Map<String, Subcampo> INDICE_POR_CODIGO = new HashMap<>();

        static {
            for (Subcampo sub : values()) {
                INDICE_POR_CODIGO.put(sub.codigo, sub);
            }
        }

        private final String codigo;
        private final String nomeAuditavel;
        private final boolean sensivel;

        Subcampo(String codigo, String nomeAuditavel, boolean sensivel) {
            this.codigo = codigo;
            this.nomeAuditavel = nomeAuditavel;
            this.sensivel = sensivel;
        }

        public String codigo() {
            return codigo;
        }

        public String getNomeAuditavel() {
            return nomeAuditavel;
        }

        public boolean isSensivel() {
            return sensivel;
        }

        public static Subcampo porCodigo(String codigo) {
            if (codigo == null) {
                return null;
            }
            return INDICE_POR_CODIGO.get(codigo.trim());
        }

        public static String formatarIdentificador(String numSc) {
            if (numSc == null || numSc.trim().isEmpty()) {
                return "0000";
            }
            String cod = numSc.trim();
            if ("0000".equals(cod)) {
                return "0000";
            }
            Subcampo sub = porCodigo(cod);
            return sub != null ? cod + ":" + sub.nomeAuditavel : cod;
        }
    }

    /** Comandos interativos que a DLL envia em {@code continuaTransacaoCTF} para o PDV exibir ou capturar dados. */
    public enum ComandoInterativo {
        RetornoSubcampo(0),
        ExibirMensagem(1),
        ExibirTituloMenu(2),
        LimparDisplay(3),
        ConfirmarSimNao(4),
        MenuOpcoes(5),
        AguardarTecla(6),
        CapturarDado(7),
        VerificarCancelamento(8),
        MenuOpcoesTemporizado(9);

        public final int valor;

        ComandoInterativo(int valor) {
            this.valor = valor;
        }

        public String codigoFormatado() {
            return String.format("%02d", valor);
        }

        public static ComandoInterativo porCodigo(int codigo) {
            for (ComandoInterativo c : values()) {
                if (c.valor == codigo) {
                    return c;
                }
            }
            return null;
        }
    }

    /** Códigos de retorno imediato (parâmetro {@code resultado}) das chamadas à biblioteca nativa. */
    public static final class CodigoRetornoBiblioteca {

        public static final int SUCESSO = 0;
        public static final int CONFIRMACAO_PARCIAL_OUTRAS_PENDENTES = 13;
        public static final int SEM_TRANSACAO_PARA_CONFIRMAR = 18;
        public static final int AGUARDANDO_CONTINUACAO = 99;

        private CodigoRetornoBiblioteca() {
        }

        public static boolean isConfirmacaoSucesso(int codigo) {
            return codigo == SUCESSO || codigo == CONFIRMACAO_PARCIAL_OUTRAS_PENDENTES;
        }

        public static boolean isSemTransacaoParaConfirmar(int codigo) {
            return codigo == SEM_TRANSACAO_PARA_CONFIRMAR;
        }

        public static String descrever(int codigo) {
            switch (codigo) {
                case 0:  return "Execução bem-sucedida da função.";
                case 2:  return "Erro ao receber a resposta do comando 09 na função continuaTransacaoCTF.";
                case 3:  return "Parâmetro tam_sc inválido do comando 09 da função continuaTransacaoCTF.";
                case 4:  return "Erro nos parâmetros de iniciaTransacaoCTF ou comando de continuaTransacaoCTF.";
                case 5:  return "Erro no parâmetro num_sc da função continuaTransacaoCTF.";
                case 6:  return "Erro no parâmetro p_sc da função continuaTransacaoCTF.";
                case 7:  return "Erro no parâmetro tam_sc da função continuaTransacaoCTF.";
                case 8:  return "Erro no parâmetro aux da função continuaTransacaoCTF.";
                case 9:  return "Parâmetro comando da função continuaTransacaoCTF não numérico.";
                case 10: return "Parâmetro comando inválido da função continuaTransacaoCTF: deveria ser 00.";
                case 11: return "Parâmetro tam_sc inválido do comando 04 da função continuaTransacaoCTF.";
                case 12: return "Parâmetro tam_sc inválido do comando 05 da função continuaTransacaoCTF.";
                case 13: return "Parâmetro tam_sc inválido do comando 07 da função continuaTransacaoCTF (ou confirmação parcial com outras pendentes).";
                case 14: return "Parâmetro comando inválido da função continuaTransacaoCTF.";
                case 15: return "Parâmetro comando inválido na primeira chamada: deveria ser 00.";
                case 16: return "Resposta do CTFClient inválida na função continuaTransacaoCTF.";
                case 17: return "Erro no parâmetro confirmar da função finalizaTransacaoCTF.";
                case 18: return "Erro no parâmetro num_trans da função finalizaTransacaoCTF (ou não há transação a confirmar).";
                case 19: return "Erro no parâmetro data_cli da função finalizaTransacaoCTF.";
                case 20: return "Parâmetro confirmar inválido: deveria ser 0 ou 1.";
                case 21: return "Parâmetro num_trans inválido na função finalizaTransacaoCTF: deveria ser numérico.";
                case 22: return "Parâmetro data_cli inválido na função finalizaTransacaoCTF: deveria ser numérico.";
                case 23: return "Algum parâmetro de iniciaClientCTF está com formato inválido.";
                case 24: return "Parâmetro criptografia inválido: deveria ser 0 ou 1.";
                case 25: return "Parâmetro log inválido: deveria ser 0 ou 1.";
                case 26: return "Parâmetro interativo inválido: deveria ser 0 ou 1.";
                case 27: return "Parâmetro lista_ips inválido em iniciaClientCTF.";
                case 30: return "Parâmetro operacao inválido em iniciaTransacaoCTF.";
                case 31: return "Parâmetro valor inválido em iniciaTransacaoCTF.";
                case 32: return "Parâmetro num_doc inválido em iniciaTransacaoCTF.";
                case 33: return "Parâmetro data_cli inválido em iniciaTransacaoCTF.";
                case 34: return "Parâmetro num_trans inválido em iniciaTransacaoCTF.";
                case 35: return "Parâmetro dados inválido.";
                case 36: return "Parâmetro operacao não numérico.";
                case 37: return "Parâmetro valor não numérico.";
                case 38: return "Parâmetro data_cli não numérico.";
                case 39: return "Parâmetro num_trans não numérico.";
                case 50: return "Biblioteca não foi inicializada na função continuaTransacaoCTF.";
                case 51: return "Erro interno na função finalizaTransacaoCTF.";
                case 52: return "Erro ao carregar a biblioteca auttar_ctf em iniciaClientCTF.";
                case 53: return "Erro ao carregar bibliotecas de criptografia em iniciaClientCTF.";
                case 54: return "Erro ao estabelecer conexão em iniciaClientCTF.";
                case 55: return "Erro ao enviar comando 01 em continuaTransacaoCTF.";
                case 56: return "Erro ao receber resposta em finalizaTransacaoCTF.";
                case 57: return "Erro ao receber resposta do comando 01.";
                case 59: return "Variável de ambiente CTFCLIENT_HOME não configurada ou inválida.";
                case 60: return "Porta de conexão com o CTFClient não definida.";
                case 61: return "Erro ao configurar o CTFClient em iniciaClientCTF.";
                case 62: return "Erro ao receber resposta do comando 02.";
                case 63: return "Erro ao receber resposta do comando 03.";
                case 64: return "Erro ao receber resposta do comando 04.";
                case 65: return "Erro ao receber resposta do comando 05.";
                case 66: return "Erro ao receber resposta do comando 06.";
                case 67: return "Erro ao receber resposta do comando 07.";
                case 68: return "Erro ao receber resposta do comando 08.";
                case 69: return "Erro ao receber comando inválido em continuaTransacaoCTF.";
                case 70: return "Erro ao receber os dados finais da transação.";
                case 71: return "Erro ao receber dados inválidos do CTFClient em continuaTransacaoCTF.";
                case 72: return "Erro ao receber dados inválidos do CTFClient em finalizaTransacaoCTF.";
                case 73: return "Erro ao receber os dados finais em finalizaTransacaoCTF.";
                case 74: return "Erro ao enviar comando 02.";
                case 75: return "Erro ao enviar comando 03.";
                case 76: return "Erro ao enviar comando 04.";
                case 77: return "Erro ao enviar comando 05.";
                case 78: return "Erro ao enviar comando 06.";
                case 79: return "Erro ao enviar comando 07.";
                case 80: return "Erro ao enviar comando 08.";
                case 81: return "Erro ao enviar comando 00.";
                case 82: return "Erro ao enviar dados em finalizaTransacaoCTF.";
                case 83: return "Erro ao enviar dados em iniciaTransacaoCTF.";
                case 84: return "Biblioteca não inicializada ao executar finalizaTransacaoCTF.";
                case 85: return "Biblioteca não inicializada ao executar iniciaTransacaoCTF.";
                case 87: return "Erro interno em iniciaTransacaoCTF.";
                case 88: return "Erro ao estabelecer conexão em iniciaTransacaoCTF.";
                case 90: return "Estado da biblioteca incorreto em continuaTransacaoCTF.";
                case 92: return "Não existe conexão estabelecida em continuaTransacaoCTF.";
                case 95: return "Não foi possível iniciar a biblioteca CTFClientC.";
                case 97: return "Erro ao enviar comando 09.";
                case 98: return "Erro interno da biblioteca.";
                case 99: return "Aguardando execução do comando pela Automação Comercial.";
                default: return "Código de retorno não catalogado (" + codigo + ").";
            }
        }
    }

    /** Resultado final do processamento da transação devolvido no subcampo {@code 7000}. */
    public static final class CodigoRetornoTransacao {

        private CodigoRetornoTransacao() {
        }

        public static boolean isAprovado(String codigo7000) {
            if (codigo7000 == null) {
                return false;
            }
            String cod = codigo7000.trim();
            return "00".equals(cod) || "13".equals(cod);
        }

        public static String descrever(String codigo7000) {
            if (codigo7000 == null || codigo7000.trim().isEmpty()) {
                return "Código de retorno 7000 ausente.";
            }
            switch (codigo7000.trim()) {
                case "00": return "Execução bem-sucedida.";
                case "01": return "Timeout da transação.";
                case "02": return "Apitef não inicializada.";
                case "04": return "Erro nos parâmetros ou erro de integração (ver subcampo 7300).";
                case "05": return "Transação não autorizada pela credenciadora (ver subcampo 7015).";
                case "10": return "Erro de comunicação da Apitef.";
                case "11": return "Erro no CTF Server.";
                case "12": return "Erro na camada de Intertef da Apitef.";
                case "13": return "Transação confirmada, mas ainda existem outras transações a confirmar.";
                case "16": return "Erro de criptografia da mensagem.";
                case "17": return "Erro de formatação da mensagem.";
                case "18": return "Não há transação para ser confirmada.";
                case "20": return "Dados de entrada inválidos (ver subcampo 7300).";
                case "29": return "Erro na camada de JNI.";
                default:   return "Código de retorno transacional não catalogado (" + codigo7000.trim() + ").";
            }
        }
    }
}
