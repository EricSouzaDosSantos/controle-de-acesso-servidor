package com.senai.controledeacesso;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class AcessoDosUsuarios {

    private String nome;
    private LocalDateTime dataHora;
    private String nomeImagem;

    public AcessoDosUsuarios(String nome, LocalDateTime dataHora, String nomeImagem) {
        this.nome = nome;
        this.dataHora = dataHora;
        this.nomeImagem = nomeImagem;
    }

    public String getNome() {
        return nome;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getNomeImagem() {
        return nomeImagem;
    }

    @Override
    public String toString() {
        return "AcessoDosUsuarios{" +
                "nome='" + nome + '\'' +
                ", dataHora=" + dataHora.format(DateTimeFormatter.ofPattern("HH:mm:ss")) +
                ", nomeImagem='" + nomeImagem + '\'' +
                '}';
    }

    private static List<AcessoDosUsuarios> registrosDeAcesso = new ArrayList<>();

    public static void criarNovoRegistroDeAcesso(String idAcessoRecebido, List<Usuario> listaDeUsuarios) {
        boolean usuarioEncontrado = false;
        GerenciarArquivo gerenciarArquivo = new GerenciarArquivo();

        for (Usuario usuario : listaDeUsuarios) {
            if (usuario.getIdAcesso().equals(idAcessoRecebido)) {
                AcessoDosUsuarios novoAcesso = new AcessoDosUsuarios(
                        usuario.getNome(),
                        LocalDateTime.now(),
                        usuario.getNome()
                );
                registrosDeAcesso.add(novoAcesso);
                System.out.println("Usuário encontrado: " + novoAcesso);
                salvarDadosDeAcessoNoArquivo(gerenciarArquivo.getArquivoRegistroDeAcesso());
                usuarioEncontrado = true;
                break;
            }
        }

        if (!usuarioEncontrado) {
            System.out.println("Id de Acesso " + idAcessoRecebido + " não cadastrado.");
        }
    }

    // Método para exibir os registros de acesso de um usuário
    public static void exibirAcesso(Scanner scanner) {
        StringBuilder tabelaAcesso = new StringBuilder();

        System.out.println("\nQual o nome do usuário que você deseja ver o histórico de acesso?: ");
        String nomeUsuario = scanner.nextLine();


        for (AcessoDosUsuarios registro : registrosDeAcesso) {
            if (registro.getNome().equalsIgnoreCase(nomeUsuario)) {
                tabelaAcesso.append(registro).append("\n");
            }
        }

        if (tabelaAcesso.length() == 0) {
            System.out.println("Nenhum registro encontrado para o usuário: " + nomeUsuario);
        } else {
            System.out.println("Histórico de acesso de " + nomeUsuario + ":\n" + tabelaAcesso);
        }
    }

    // Método para carregar os dados do arquivo
    public static void carregarDadosDoArquivoRegistroDeAcesso(File arquivoRegistroDeAcesso) {
        try (BufferedReader reader = new BufferedReader(new FileReader(arquivoRegistroDeAcesso))) {
            String linha;

            while ((linha = reader.readLine()) != null) {
                if (!linha.trim().isEmpty()) {
                    String[] dados = linha.split(",");
                    String nome = dados[0];
                    LocalDateTime dataHora = LocalDateTime.parse(dados[1], DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    String nomeImagem = dados[2];

                    registrosDeAcesso.add(new AcessoDosUsuarios(nome, dataHora, nomeImagem));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao carregar os dados do arquivo: " + e.getMessage());
        }
    }

    // Método para salvar os dados no arquivo
    private static void salvarDadosDeAcessoNoArquivo(File arquivoRegistroDeAcesso) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(arquivoRegistroDeAcesso))) {
            for (AcessoDosUsuarios registro : registrosDeAcesso) {
                writer.write(String.format("%s,%s,%s\n",
                        registro.getNome(),
                        registro.getDataHora().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        registro.getNomeImagem()
                ));
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar os dados no arquivo: " + e.getMessage());
        }
    }
}
