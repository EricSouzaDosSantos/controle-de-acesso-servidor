package com.senai.controledeacesso;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

public class RegistroDeAcesso {

    private Usuario usuario;
    private LocalDateTime dataHora;
    private static List<RegistroDeAcesso> listaDeRegistroDeAcesso = new ArrayList<>();

    public static List<RegistroDeAcesso> getListaDeRegistroDeAcesso() {
        return listaDeRegistroDeAcesso;
    }

    public RegistroDeAcesso(Usuario usuario, LocalDateTime dataHora) {
        this.usuario = usuario;
        this.dataHora = dataHora;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    @Override
    public String toString() {
        return "AcessoDosUsuarios{" +
                "usuario='" + usuario.getNome() + '\'' +
                ", dataHora=" + dataHora.format(DateTimeFormatter.ofPattern("HH:mm:ss")) +
                ", nomeImagem='" + usuario.getCaminhoImagem() + '\'' +
                '}';
    }



    public static void criarNovoRegistroDeAcesso(String idAcessoRecebido, List<Usuario> listaDeUsuarios) {
        boolean usuarioEncontrado = false;
        GerenciarArquivo gerenciarArquivo = new GerenciarArquivo();

        for (Usuario usuario : listaDeUsuarios) {
            if (usuario.getIdAcesso().toString().equals(idAcessoRecebido)) {
                RegistroDeAcesso novoAcesso = new RegistroDeAcesso(
                        usuario,
                        LocalDateTime.now()
                );
                listaDeRegistroDeAcesso.add(novoAcesso);
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


        for (RegistroDeAcesso registro : listaDeRegistroDeAcesso) {
            if (registro.getUsuario().getNome().equalsIgnoreCase(nomeUsuario)) {
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
                    String[] dados = linha.split("\\s*\\|\\s*");
                    Usuario usuario = new Usuario(
                            Long.parseLong(dados[0]),
                            dados[1].equals("null") ? null : UUID.fromString(dados[1]),
                            dados[2],
                            dados[3],
                            dados[4],
                            dados[6].equals("null") ? null : dados[6]
                    );
                    LocalDateTime dataHora = LocalDateTime.parse(dados[5], DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                    listaDeRegistroDeAcesso.add(new RegistroDeAcesso(usuario, dataHora));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao carregar os dados do arquivo: " + e.getMessage());
        }
    }

    // Método para salvar os dados no arquivo
    private static void salvarDadosDeAcessoNoArquivo(File arquivoRegistroDeAcesso) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(arquivoRegistroDeAcesso))) {
            for (RegistroDeAcesso registro : listaDeRegistroDeAcesso) {
                System.out.println("salvou os dados no arquivo");
                writer.write(String.format("%s | %s | %s\n",
                        registro.getUsuario(),
                        registro.getDataHora().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        registro.getUsuario().getCaminhoImagem()
                ));
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar os dados no arquivo: " + e.getMessage());
        }
    }
}
