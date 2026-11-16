package com.auttar;

import com.sun.jna.Platform;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gerenciador multiplataforma (Windows e Linux) do ciclo de vida do processo
 * em segundo plano do CTFClient ({@code ctf-client-desktop} / {@code ClientCTF}).
 *
 * <p>Identifica o PID do processo TEF iniciado pela biblioteca nativa (via porta TCP
 * configurada em {@code configCTFClient.xml} e assinatura de linha de comando) e garante
 * seu encerramento limpo ao fechar o PDV (normalmente ou via {@code ShutdownHook}).</p>
 */
public final class CtfProcessManager {

    public static final int PORTA_PADRAO_CTFCLIENT = 2500;

    private static final Pattern PADRAO_PORTA_XML = Pattern.compile(
            "<Parameter\\s+name=\"portclient\"\\s*>\\s*(\\d+)\\s*</Parameter>",
            Pattern.CASE_INSENSITIVE);

    private static final long MEU_PID = extrairMeuPid();
    private static final Set<Long> PIDS_MONITORADOS = Collections.synchronizedSet(new LinkedHashSet<Long>());
    private static final AtomicBoolean HOOK_REGISTRADO = new AtomicBoolean(false);
    private static final AtomicBoolean ENCERRADO = new AtomicBoolean(false);

    private CtfProcessManager() {
    }

    public static long getMeuPid() {
        return MEU_PID;
    }

    /**
     * Garante que nenhum processo CTFClient órfão (de uma sessão anterior interrompida à força
     * no meio de uma transação, ex.: Stop Debug no IDE) esteja bloqueando a porta TCP antes de {@code iniciaClientCTF}.
     */
    public static int encerrarProcessosOrfaosAntesDeIniciar() {
        ENCERRADO.set(false);
        int porta = descobrirPortaCtfClient(resolverCtfClientHome(System.getenv("CTFCLIENT_HOME"), Platform.isWindows()));
        Set<Long> orfaos = descobrirPidsCtfClient(porta);
        orfaos.remove(MEU_PID);
        orfaos.removeAll(PIDS_MONITORADOS);
        int encerrados = 0;
        for (Long pid : orfaos) {
            if (pid == null || pid <= 0 || pid == MEU_PID) {
                continue;
            }
            boolean ok = Platform.isWindows()
                    ? encerrarPidWindows(pid)
                    : encerrarPidLinux(pid);
            if (ok) {
                encerrados++;
                TefAuditLogger.log("init", "Processo CTFClient orfao finalizado antes de iniciar",
                        String.format("pid:[%d] porta:[%d]", pid, porta));
            }
        }
        return encerrados;
    }

    /**
     * Captura os PIDs do CTFClient em execução logo após {@code iniciaClientCTF}
     * e registra automaticamente o {@code ShutdownHook} na JVM.
     */
    public static Set<Long> registrarMonitoramentoInicial() {
        ENCERRADO.set(false);
        registrarShutdownHook();

        int porta = descobrirPortaCtfClient(resolverCtfClientHome(System.getenv("CTFCLIENT_HOME"), Platform.isWindows()));
        Set<Long> detectados = descobrirPidsCtfClient(porta);
        PIDS_MONITORADOS.addAll(detectados);

        if (!detectados.isEmpty()) {
            TefAuditLogger.log("init", "Processo CTFClient monitorado",
                    String.format("pids:%s porta:[%d]", detectados, porta));
        }
        return new LinkedHashSet<>(detectados);
    }

    /** Registra o hook de desligamento da JVM (idempotente). */
    public static void registrarShutdownHook() {
        if (HOOK_REGISTRADO.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    encerrarProcessoCtfClient();
                }
            }, "ctfclient-shutdown-hook"));
        }
    }

    /**
     * Finaliza de forma idempotente e segura o processo do CTFClient no Windows e no Linux.
     *
     * @return Quantidade de processos CTFClient encerrados.
     */
    public static int encerrarProcessoCtfClient() {
        if (!ENCERRADO.compareAndSet(false, true)) {
            return 0;
        }

        int porta = descobrirPortaCtfClient(resolverCtfClientHome(System.getenv("CTFCLIENT_HOME"), Platform.isWindows()));
        Set<Long> alvos = new LinkedHashSet<>(PIDS_MONITORADOS);
        alvos.addAll(descobrirPidsCtfClient(porta));
        alvos.remove(MEU_PID);

        if (alvos.isEmpty()) {
            TefAuditLogger.log("shutdown", "Nenhum processo CTFClient ativo para encerrar.");
            return 0;
        }

        int encerrados = 0;
        for (Long pid : alvos) {
            if (pid == null || pid <= 0 || pid == MEU_PID) {
                continue;
            }
            boolean ok = Platform.isWindows()
                    ? encerrarPidWindows(pid)
                    : encerrarPidLinux(pid);
            if (ok) {
                encerrados++;
                TefAuditLogger.log("shutdown", "Processo CTFClient finalizado",
                        String.format("pid:[%d]", pid));
            }
        }
        PIDS_MONITORADOS.clear();
        return encerrados;
    }

    /** Resolve o diretório base do CTFClient usando {@code CTFCLIENT_HOME} ou caminhos padrão do SO. */
    static String resolverCtfClientHome(String envHome, boolean isWindows) {
        if (BufferNativoUtil.naoVazio(envHome)) {
            return envHome.trim();
        }
        String[] candidatos = isWindows
                ? new String[] {
                        "C:\\CTFClient",
                        "C:\\Program Files (x86)\\Auttar\\CTFClient",
                        "C:\\Program Files\\Auttar\\CTFClient"
                }
                : new String[] { "/opt/CTFClient" };

        for (String caminho : candidatos) {
            if (new File(caminho).isDirectory()) {
                return caminho;
            }
        }
        return null;
    }

    /** Lê a porta TCP configurada em {@code <CTFCLIENT_HOME>/bin/configCTFClient.xml} (padrão 2500). */
    public static int descobrirPortaCtfClient(String ctfClientHome) {
        if (BufferNativoUtil.naoVazio(ctfClientHome)) {
            File xml = new File(new File(ctfClientHome.trim(), "bin"), "configCTFClient.xml");
            int porta = extrairPortaDeXml(xml);
            if (porta > 0) {
                return porta;
            }
        }
        return PORTA_PADRAO_CTFCLIENT;
    }

    static int extrairPortaDeXml(File arquivoXml) {
        if (arquivoXml == null || !arquivoXml.isFile()) {
            return -1;
        }
        try {
            String conteudo = new String(Files.readAllBytes(arquivoXml.toPath()), StandardCharsets.UTF_8);
            Matcher matcher = PADRAO_PORTA_XML.matcher(conteudo);
            if (matcher.find()) {
                return BufferNativoUtil.parseIntSeguro(matcher.group(1), -1);
            }
        } catch (IOException ignored) {
            // Usa porta padrão
        }
        return -1;
    }

    /** Descobre os PIDs do CTFClient combinando porta TCP local e assinatura do processo. */
    public static Set<Long> descobrirPidsCtfClient(int portaTcp) {
        Set<Long> pids = new LinkedHashSet<>();
        if (Platform.isWindows()) {
            pids.addAll(descobrirPidsPorNetstatWindows(portaTcp));
        } else {
            pids.addAll(descobrirPidsProcfsLinux());
            pids.addAll(descobrirPidsPgrepLinux());
        }
        pids.remove(MEU_PID);
        return pids;
    }

    static Set<Long> parseNetstatListeningPids(List<String> linhasNetstat, int porta) {
        Set<Long> pids = new LinkedHashSet<>();
        if (linhasNetstat == null || porta <= 0) {
            return pids;
        }
        String sufixoPorta = ":" + porta;
        for (String linha : linhasNetstat) {
            if (linha == null) {
                continue;
            }
            String limpa = linha.trim();
            if (!limpa.startsWith("TCP")) {
                continue;
            }
            String[] partes = limpa.split("\\s+");
            if (partes.length < 5) {
                continue;
            }
            String enderecoLocal = partes[1];
            String estado = partes[3];
            String pidStr = partes[4];
            if (enderecoLocal.endsWith(sufixoPorta) && "LISTENING".equalsIgnoreCase(estado)) {
                long pid = BufferNativoUtil.parseIntSeguro(pidStr, -1);
                if (pid > 0 && pid != MEU_PID) {
                    pids.add(pid);
                }
            }
        }
        return pids;
    }

    private static Set<Long> descobrirPidsPorNetstatWindows(int porta) {
        List<String> linhas = executarComando("cmd.exe", "/c", "netstat -ano -p tcp");
        return parseNetstatListeningPids(linhas, porta);
    }

    private static Set<Long> descobrirPidsProcfsLinux() {
        Set<Long> pids = new LinkedHashSet<>();
        File procDir = new File("/proc");
        File[] entradas = procDir.listFiles();
        if (entradas == null) {
            return pids;
        }
        for (File entrada : entradas) {
            String nome = entrada.getName();
            if (! entrada.isDirectory() || !nome.matches("\\d+")) {
                continue;
            }
            long pid = BufferNativoUtil.parseIntSeguro(nome, -1);
            if (pid <= 0 || pid == MEU_PID) {
                continue;
            }
            File cmdlineFile = new File(entrada, "cmdline");
            try {
                if (cmdlineFile.isFile() && cmdlineFile.canRead()) {
                    byte[] bytes = Files.readAllBytes(cmdlineFile.toPath());
                    String cmdline = new String(bytes, StandardCharsets.UTF_8).toLowerCase();
                    if (cmdline.contains("ctf-client-desktop") || cmdline.contains("clientctf")) {
                        pids.add(pid);
                    }
                }
            } catch (IOException ignored) {
                // Processo pode ter encerrado durante a leitura
            }
        }
        return pids;
    }

    private static Set<Long> descobrirPidsPgrepLinux() {
        Set<Long> pids = new LinkedHashSet<>();
        List<String> linhas = executarComando("pgrep", "-f", "ctf-client-desktop|ClientCTF");
        for (String linha : linhas) {
            long pid = BufferNativoUtil.parseIntSeguro(linha, -1);
            if (pid > 0 && pid != MEU_PID) {
                pids.add(pid);
            }
        }
        return pids;
    }

    private static boolean encerrarPidWindows(long pid) {
        try {
            Process proc = new ProcessBuilder("taskkill", "/PID", String.valueOf(pid), "/T", "/F")
                    .redirectErrorStream(true)
                    .start();
            boolean terminou = proc.waitFor(5, TimeUnit.SECONDS);
            return terminou && proc.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean encerrarPidLinux(long pid) {
        try {
            new ProcessBuilder("kill", "-15", String.valueOf(pid)).start().waitFor(2, TimeUnit.SECONDS);
            File procPid = new File("/proc/" + pid);
            long limite = System.currentTimeMillis() + 1500;
            while (procPid.exists() && System.currentTimeMillis() < limite) {
                Thread.sleep(100);
            }
            if (procPid.exists()) {
                new ProcessBuilder("kill", "-9", String.valueOf(pid)).start().waitFor(2, TimeUnit.SECONDS);
            }
            return !procPid.exists();
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> executarComando(String... comando) {
        try {
            Process proc = new ProcessBuilder(comando)
                    .redirectErrorStream(true)
                    .start();
            Set<String> linhas = new LinkedHashSet<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String linha;
                while ((linha = reader.readLine()) != null) {
                    linhas.add(linha);
                }
            }
            proc.waitFor(3, TimeUnit.SECONDS);
            return new java.util.ArrayList<>(linhas);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static long extrairMeuPid() {
        try {
            String jvmName = ManagementFactory.getRuntimeMXBean().getName();
            int idx = jvmName != null ? jvmName.indexOf('@') : -1;
            if (idx > 0) {
                return Long.parseLong(jvmName.substring(0, idx));
            }
        } catch (Exception ignored) {
            // Fallback
        }
        return -1L;
    }
}
