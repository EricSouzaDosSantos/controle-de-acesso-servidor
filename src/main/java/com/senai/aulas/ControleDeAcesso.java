package com.senai.aulas;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ControleDeAcesso {

    private final static File BANCO_DE_DADOS = new File("src/main/resources/bancoDeDados.txt");
    private final static String RAIZ_WEBAPP = "src/main/webapp";

    static String[] cabecalho = {"ID", "IdAcesso", "Nome", "Telefone", "Email"};
    static String[][] matrizCadastro;
    public static String[][] matrizRegistrosDeAcesso = {{"", ""}};// inicia a matriz com uma linha e duas colunas com "" para que na primeira vez não apareça null na tabela de registros

    static volatile boolean modoCadastrarIdAcesso = false;
    static int idUsuarioRecebidoPorHTTP = 0;
    static String dispositivoRecebidoPorHTTP = "Disp1";

    static String brokerUrl = "tcp://localhost:1883";  // Exemplo de
    static String topico = "IoTKIT1/UID";

    static CLienteMQTT conexaoMQTT;
    static ServidorHTTP servidorHTTP = new ServidorHTTP(RAIZ_WEBAPP);
    static Scanner scanner = new Scanner(System.in);
    static ExecutorService executorIdentificarAcessos = Executors.newFixedThreadPool(4);
    static ExecutorService executorCadastroIdAcesso = Executors.newSingleThreadExecutor();

    public static void main(String[] args) {
        conexaoMQTT = new CLienteMQTT(brokerUrl, topico, ControleDeAcesso::processarMensagemMQTTRecebida);
        carregarDadosDoArquivo();
        servidorHTTP.iniciarServidorHTTP(); // Inicia o servidor HTTP
        menuPrincipal();

        // Finaliza o todos os processos abertos ao sair do programa
        scanner.close();
        executorIdentificarAcessos.shutdown();
        executorCadastroIdAcesso.shutdown();
        conexaoMQTT.desconectar();
        servidorHTTP.pararServidorHTTP();
    }

    private static void menuPrincipal() {
        int opcao;
        do {
            String menu = """
                    _________________________________________________________
                    |   Escolha uma opção:                                  |
                    |       1- Exibir cadastro completo                     |
                    |       2- Inserir novo cadastro                        |
                    |       3- Atualizar cadastro por id                    |
                    |       4- Deletar um cadastro por id                   |
                    |       5- Associar TAG ou cartão de acesso ao usuário  |
                    |       6- Sair                                         |
                    _________________________________________________________
                    """;
            System.out.println(menu);
            opcao = scanner.nextInt();
            scanner.nextLine();

            switch (opcao) {
                case 1:
                    exibirCadastro();
                    break;
                case 2:
                    cadastrarUsuario();
                    break;
                case 3:
                    atualizarUsuario();
                    break;
                case 4:
                    deletarUsuario();
                    break;
                case 5:
                    aguardarCadastroDeIdAcesso();
                    break;
                case 6:
                    System.out.println("Fim do programa!");
                    break;
                default:
                    System.out.println("Opção inválida!");
            }

        } while (opcao != 6);
    }

    private static void aguardarCadastroDeIdAcesso() {
        modoCadastrarIdAcesso = true;
        System.out.println("Aguardando nova tag ou cartão para associar ao usuário");
        // Usar Future para aguardar até que o cadastro de ID seja concluído
        Future<?> future = executorCadastroIdAcesso.submit(() -> {
            while (modoCadastrarIdAcesso) {
                // Loop em execução enquanto o modoCadastrarIdAcesso estiver ativo
                try {
                    Thread.sleep(100); // Evita uso excessivo de CPU
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        try {
            future.get(); // Espera até que o cadastro termine
        } catch (Exception e) {
            System.err.println("Erro ao aguardar cadastro: " + e.getMessage());
        }
    }

    private static void processarMensagemMQTTRecebida(String mensagem) {
        if (!modoCadastrarIdAcesso) {
            executorIdentificarAcessos.submit(() -> criarNovoRegistroDeAcesso(mensagem)); // Processa em thread separada
        } else {
            cadastrarNovoIdAcesso(mensagem); // Processa em thread separada
            modoCadastrarIdAcesso = false;
            idUsuarioRecebidoPorHTTP = 0;
        }
    }

    // Função que busca e atualiza a tabela com o ID recebido
    private static void criarNovoRegistroDeAcesso(String idAcessoRecebido) {
        boolean usuarioEncontrado = false; // Variável para verificar se o usuário foi encontrado
        String[][] novaMatrizRegistro = new String[matrizRegistrosDeAcesso.length][2];
        int linhaNovoRegistro = 0;

        if (!matrizRegistrosDeAcesso[0][0].isEmpty()) {//testa se o valor da primeira posição da matriz está diferente de vazia ou "".
            novaMatrizRegistro = new String[matrizRegistrosDeAcesso.length + 1][2];
            linhaNovoRegistro = matrizRegistrosDeAcesso.length;
            for (int linhas = 0; linhas < matrizRegistrosDeAcesso.length; linhas++) {
                novaMatrizRegistro[linhas] = Arrays.copyOf(matrizRegistrosDeAcesso[linhas], matrizRegistrosDeAcesso[linhas].length);
            }
        }
        // Loop para percorrer a matriz e buscar o idAcesso
        for (int linhas = 1; linhas < matrizCadastro.length; linhas++) { // Começa de 1 para ignorar o cabeçalho
            String idAcessoNaMatriz = matrizCadastro[linhas][1]; // A coluna do idAcesso é a segunda coluna (índice 1)

            // Verifica se o idAcesso da matriz corresponde ao idAcesso recebido
            if (idAcessoNaMatriz.equals(idAcessoRecebido)) {
                novaMatrizRegistro[linhaNovoRegistro][0] = matrizCadastro[linhas][2]; // Assume que o nome do usuário está na coluna 3
                novaMatrizRegistro[linhaNovoRegistro][1] = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                System.out.println("Usuário encontrado: " +
                        novaMatrizRegistro[linhaNovoRegistro][0] + " - " +
                        novaMatrizRegistro[linhaNovoRegistro][1]);
                usuarioEncontrado = true; // Marca que o usuário foi encontrado
                matrizRegistrosDeAcesso = novaMatrizRegistro;
                break; // Sai do loop, pois já encontrou o usuário
            }
        }
        // Se não encontrou o usuário, imprime uma mensagem
        if (!usuarioEncontrado) {
            System.out.println("Id de Acesso " + idAcessoRecebido + " não cadastrado.");
        }
    }

    private static void cadastrarNovoIdAcesso(String novoIdAcesso) {
        boolean encontrado = false; // Variável para verificar se o usuário foi encontrado
        String idUsuarioEscolhido = String.valueOf(idUsuarioRecebidoPorHTTP);
        String dispositivoEscolhido = dispositivoRecebidoPorHTTP;

        if (idUsuarioRecebidoPorHTTP == 0) {
            // Exibe a lista de usuários para o administrador escolher
            for (String[] usuario : matrizCadastro) {
                System.out.println(usuario[0] + " - " + usuario[2]); // Exibe ID e Nome do usuário
            }
            // Pede ao administrador que escolha o ID do usuário
            System.out.print("Digite o ID do usuário para associar ao novo idAcesso: ");
            idUsuarioEscolhido = scanner.nextLine();
            conexaoMQTT.publicarMensagem(topico, dispositivoEscolhido);
        }

        modoCadastrarIdAcesso = true;
        // Verifica se o ID do usuário existe na matriz
        for (int linhas = 1; linhas < matrizCadastro.length; linhas++) {
            if (matrizCadastro[linhas][0].equals(idUsuarioEscolhido)) { // Coluna 0 é o idUsuario
                matrizCadastro[linhas][1] = novoIdAcesso; // Atualiza a coluna 1 com o novo idAcesso
                System.out.println("id de acesso " + novoIdAcesso + " associado ao usuário " + matrizCadastro[linhas][2]);
                conexaoMQTT.publicarMensagem("cadastro/disp", "CadastroConcluido");
                encontrado = true;
                salvaDadosNoArquivo();
                break;
            }
        }

        // Se não encontrou o usuário, imprime uma mensagem
        if (!encontrado) {
            System.out.println("Usuário com id" + idUsuarioEscolhido + " não encontrado.");
        }
    }

    // Funções de CRUD
    private static void exibirCadastro() {
        StringBuilder tabelaCadastro = new StringBuilder();

        for (String[] usuarioLinha : matrizCadastro) {
            for (int colunas = 0; colunas < matrizCadastro[0].length; colunas++) {
                int largura = colunas < 2 ? (colunas == 0 ? 4 : 8) : 25;
                tabelaCadastro.append(String.format("%-" + largura + "s | ", usuarioLinha[colunas]));
            }
            tabelaCadastro.append("\n");
        }
        System.out.println(tabelaCadastro);
    }

    private static void cadastrarUsuario() {
        System.out.print("Digite a quantidade de usuarios que deseja cadastrar:");
        int qtdUsuarios = scanner.nextInt();
        scanner.nextLine();

        String[][] novaMatriz = new String[matrizCadastro.length + qtdUsuarios][matrizCadastro[0].length];

        for (int linhas = 0; linhas < matrizCadastro.length; linhas++) {
            novaMatriz[linhas] = Arrays.copyOf(matrizCadastro[linhas], matrizCadastro[linhas].length);
        }

        System.out.println("\nPreencha os dados a seguir:");
        for (int linhas = matrizCadastro.length; linhas < novaMatriz.length; linhas++) {
            System.out.println(matrizCadastro[0][0] + "- " + linhas);
            novaMatriz[linhas][0] = String.valueOf(linhas);
            novaMatriz[linhas][1] = "Não Cadastrado";
            for (int colunas = 2; colunas < matrizCadastro[0].length; colunas++) {
                System.out.print(matrizCadastro[0][colunas] + ": ");
                novaMatriz[linhas][colunas] = scanner.nextLine();
            }
            System.out.println("-----------------------Inserido com sucesso------------------------\n");
        }
        matrizCadastro = novaMatriz;
        salvaDadosNoArquivo();
    }

    private static void atualizarUsuario() {
        exibirCadastro();
        System.out.println("Escolha um id para atualizar o cadastro:");
        int idUsuario = scanner.nextInt();
        scanner.nextLine();
        System.out.println("\nAtualize os dados a seguir:");

        System.out.println(matrizCadastro[0][0] + "- " + idUsuario);
        for (int dados = 2; dados < matrizCadastro[0].length; dados++) {
            System.out.print(matrizCadastro[0][dados] + ": ");
            matrizCadastro[idUsuario][dados] = scanner.nextLine();
        }

        System.out.println("---------Atualizado com sucesso-----------");
        exibirCadastro();
        salvaDadosNoArquivo();
    }

    private static void deletarUsuario() {
        String[][] novaMatriz = new String[matrizCadastro.length - 1][matrizCadastro[0].length];

        exibirCadastro();
        System.out.println("Escolha um id para deletar o cadastro:");
        int idUsuario = scanner.nextInt();
        scanner.nextLine();

        for (int i = 0, j = 0; i < matrizCadastro.length; i++) {
            if (i == idUsuario)
                continue;
            novaMatriz[j++] = matrizCadastro[i];
        }

        matrizCadastro = novaMatriz;
        salvaDadosNoArquivo();
        System.out.println("-----------------------Deletado com sucesso------------------------\n");
    }

    // Funções para persistência de dados
    private static void carregarDadosDoArquivo() {
        try (BufferedReader reader = new BufferedReader(new FileReader(BANCO_DE_DADOS))) {
            if (!BANCO_DE_DADOS.exists()) {
                return;
            }
            String linha;
            StringBuilder conteudo = new StringBuilder();
            while ((linha = reader.readLine()) != null) {
                conteudo.append(linha).append("\n");
            }

            String[] linhas = conteudo.toString().split("\n");
            matrizCadastro = new String[linhas.length][cabecalho.length];

            for (int i = 0; i < linhas.length; i++) {
                matrizCadastro[i] = linhas[i].split(",");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void salvaDadosNoArquivo() {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(BANCO_DE_DADOS));
            for (String[] linha : matrizCadastro) {
                writer.write(String.join(",", linha) + "\n");
            }
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
