package com.senai.controledeacesso;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ControleDeAcesso {
    // Caminho para a pasta ControleDeAcesso no diretório do usuário
    private static final File pastaControleDeAcesso = new File(System.getProperty("user.home"), "ControleDeAcesso");


    // Caminho para o arquivo bancoDeDados.txt e para a pasta imagens
    private static final File arquivoBancoDeDados = new File(pastaControleDeAcesso, "bancoDeDados.txt");
    private static final File arquivoRegistroDeAcesso = new File(pastaControleDeAcesso, "RegistrosDeAcesso.txt");
    public static final File pastaImagens = new File(pastaControleDeAcesso, "imagens");

    static ArrayList<RegistroDeAcesso>registroDeAcessos=new ArrayList<>();
    static String[] cabecalho = {"ID", "IdAcesso", "Nome", "Telefone", "Email", "Imagem"};
    static String[][] matrizCadastro = {{"", ""}};
    public static String[][] matrizRegistrosDeAcesso = {{"", "", ""}};// inicia a matriz com uma linha e duas colunas com "" para que na primeira vez não apareça null na tabela de registros

    static volatile boolean modoCadastrarIdAcesso = false;
    static int idUsuarioRecebidoPorHTTP = 0;
    static String dispositivoRecebidoPorHTTP = "Disp1";

    static String brokerUrl = "tcp://localhost:1883";  // Exemplo de
    static String topico = "IoTKIT1/UID";

    static CLienteMQTT conexaoMQTT;
    static ServidorHTTPS servidorHTTPS;
    static Scanner scanner = new Scanner(System.in);
    static ExecutorService executorIdentificarAcessos = Executors.newFixedThreadPool(4);
    static ExecutorService executorCadastroIdAcesso = Executors.newSingleThreadExecutor();

    public static void main(String[] args) {
        verificarEstruturaDeDiretorios();
        carregarDadosDoArquivo();
        carregarDadosDoArquivoRegistroDeAcesso();
        conexaoMQTT = new CLienteMQTT(brokerUrl, topico, ControleDeAcesso::processarMensagemMQTTRecebida);
        servidorHTTPS = new ServidorHTTPS(); // Inicia o servidor HTTPS
        menuPrincipal();

        // Finaliza o todos os processos abertos ao sair do programa
        scanner.close();
        executorIdentificarAcessos.shutdown();
        executorCadastroIdAcesso.shutdown();
        conexaoMQTT.desconectar();
        servidorHTTPS.pararServidorHTTPS();
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
                    |       5- Exibir horários de acesso                    |
                    |       6- Associar TAG ou cartão de acesso ao usuário  |
                    |       7- Sair                                         |
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
                    exibirAcesso();
                    break;
                case 6:
                    aguardarCadastroDeIdAcesso();
                    break;
                case 7:
                    System.out.println("Fim do programa!");
                    break;
                default:
                    System.out.println("Opção inválida!");
            }

        } while (opcao != 7);
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
        String[][] novaMatrizRegistro = new String[matrizRegistrosDeAcesso.length][matrizRegistrosDeAcesso[0].length];
        int linhaNovoRegistro = 0;

        if (!matrizRegistrosDeAcesso[0][0].isEmpty()) {//testa se o valor da primeira posição da matriz está diferente de vazia ou "".
            novaMatrizRegistro = new String[matrizRegistrosDeAcesso.length + 1][matrizRegistrosDeAcesso[0].length];
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
                novaMatrizRegistro[linhaNovoRegistro][2] = matrizCadastro[linhas][5];
                System.out.println("Usuário encontrado: " +
                        novaMatrizRegistro[linhaNovoRegistro][0] + " - " +
                        novaMatrizRegistro[linhaNovoRegistro][1]);
                usuarioEncontrado = true; // Marca que o usuário foi encontrado
                matrizRegistrosDeAcesso = novaMatrizRegistro;
                salvarDadosDeAcessoNoArquivo();
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
                salvarDadosNoArquivo();
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

    private static void exibirAcesso() {

        StringBuilder tabelaAcesso = new StringBuilder();

        String nome = "";

        exibirCadastro();

        System.out.println("Qua o id do usuário que você deseja ver o histórico de acesso");

        int idDoUsuario = scanner.nextInt();

        for (int row = 1; row < matrizCadastro.length; row++) {
            for (int column = 0; column < cabecalho.length; column++) {
                if (row == idDoUsuario) {
                    nome = matrizCadastro[row][2];
                    break;
                }
            }
        }

        for (String[] usuarioLinha : matrizRegistrosDeAcesso) {
            if (usuarioLinha[0].equals(nome)) {
                tabelaAcesso.append(String.join( ",", usuarioLinha)).append("\n");
            }
        }
        System.out.println(tabelaAcesso);
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
            novaMatriz[linhas][0] = String.valueOf(linhas);// preenche o campo id com o numero gerado pelo for
            novaMatriz[linhas][1] = "-"; //preenche o campo idCadastro com "-"

            for (int colunas = 2; colunas < matrizCadastro[0].length - 1; colunas++) {
                System.out.print(matrizCadastro[0][colunas] + ": ");
                novaMatriz[linhas][colunas] = scanner.nextLine();
            }
            novaMatriz[linhas][matrizCadastro[0].length - 1] = "-";//preenche o campo imagem com "-"

            System.out.println("-----------------------Inserido com sucesso------------------------\n");
        }
        matrizCadastro = novaMatriz;
        salvarDadosNoArquivo();
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
        salvarDadosNoArquivo();
    }

    public static void deletarUsuario() {
        String[][] novaMatriz = new String[matrizCadastro.length - 1][matrizCadastro[0].length];
        int idUsuario = idUsuarioRecebidoPorHTTP;
        if (idUsuarioRecebidoPorHTTP == 0) {
            exibirCadastro();
            System.out.println("Escolha um id para deletar o cadastro:");
            idUsuario = scanner.nextInt();
            scanner.nextLine();
        }

        for (int i = 1, j = 1; i < matrizCadastro.length; i++) {
            if (i == idUsuario)
                continue;
            novaMatriz[j] = matrizCadastro[i];
            novaMatriz[j][0]= String.valueOf(j);
            j++;
        }

        matrizCadastro = novaMatriz;
        matrizCadastro[0]=cabecalho;
        salvarDadosNoArquivo();
        System.out.println("-----------------------Deletado com sucesso------------------------\n");
        idUsuarioRecebidoPorHTTP = 0;
    }

    // Funções para persistência de dados
    private static void carregarDadosDoArquivo() {

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivoBancoDeDados))) {
            String linha;
            StringBuilder conteudo = new StringBuilder();

            while ((linha = reader.readLine()) != null) {
                if (!linha.trim().isEmpty()) {
                    conteudo.append(linha).append("\n");
                }
            }

            if (!conteudo.toString().trim().isEmpty()) {
                String[] linhasDaTabela = conteudo.toString().split("\n");
                matrizCadastro = new String[linhasDaTabela.length][cabecalho.length];
                for (int i = 0; i < linhasDaTabela.length; i++) {
                    matrizCadastro[i] = linhasDaTabela[i].split(",");
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        matrizCadastro[0] = cabecalho;
    }

    public static void carregarDadosDoArquivoRegistroDeAcesso(){

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivoRegistroDeAcesso))) {
            String linha;
            StringBuilder conteudo = new StringBuilder();

            while ((linha = reader.readLine()) != null) {
                if (!linha.trim().isEmpty()) {
                    conteudo.append(linha).append("\n");
                }
            }

            if (!conteudo.toString().trim().isEmpty()) {
                String[] linhasDaTabela = conteudo.toString().split("\n");
                matrizRegistrosDeAcesso = new String[linhasDaTabela.length][2];
                for (int i = 0; i < linhasDaTabela.length; i++) {
                    matrizRegistrosDeAcesso[i] = linhasDaTabela[i].split(",");
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void salvarDadosDeAcessoNoArquivo(){
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(arquivoRegistroDeAcesso))){
            for (String[] linha : matrizRegistrosDeAcesso){
                writer.write(String.join(",", linha) + "\n");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void salvarDadosNoArquivo() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(arquivoBancoDeDados))) {
            for (String[] linha : matrizCadastro) {
                writer.write(String.join(",", linha) + "\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void verificarEstruturaDeDiretorios() {
        // Verifica se a pasta ControleDeAcesso existe, caso contrário, cria
        if (!pastaControleDeAcesso.exists()) {
            if (pastaControleDeAcesso.mkdir()) {
                System.out.println("Pasta ControleDeAcesso criada com sucesso.");
            } else {
                System.out.println("Falha ao criar a pasta ControleDeAcesso.");
            }
        }

        // Verifica se o arquivo bancoDeDados.txt existe, caso contrário, cria
        if (!arquivoBancoDeDados.exists()) {
            try {
                if (arquivoBancoDeDados.createNewFile()) {
                    System.out.println("Arquivo bancoDeDados.txt criado com sucesso.");
                } else {
                    System.out.println("Falha ao criar o arquivo bancoDeDados.txt.");
                }
            } catch (IOException e) {
                System.out.println("Erro ao criar arquivo bancoDeDados.txt: " + e.getMessage());
            }
        }

        if (!arquivoRegistroDeAcesso.exists()){
            try {
                if (arquivoRegistroDeAcesso.createNewFile()){
                    System.out.println("Arquivo RegistrosDeAcesso.txt criado com sucesso.");
                }else {
                    System.out.println("Falha ao criar o arquivo bancoDeDados.txt.");
                }
            } catch (IOException e) {
                System.out.println("Erro ao criar arquivo RegistrosDeAcesso.txt: " + e.getMessage());
            }
        }

        // Verifica se a pasta imagens existe, caso contrário, cria
        if (!pastaImagens.exists()) {
            if (pastaImagens.mkdir()) {
                System.out.println("Pasta imagens criada com sucesso.");
            } else {
                System.out.println("Falha ao criar a pasta imagens.");
            }
        }
    }
}
