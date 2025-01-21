package com.senai.controledeacesso;

import java.io.File;
import java.io.IOException;

public class GerenciarArquivo {

    private final File pastaControleDeAcesso;
    private final File arquivoBancoDeDados;
    private final File arquivoRegistroDeAcesso;
    private final File arquivoImagens;

    public GerenciarArquivo() {
        this.pastaControleDeAcesso = new File(System.getProperty("user.home"), "ControleDeAcesso");
        this.arquivoBancoDeDados = new File(pastaControleDeAcesso, "bancoDeDados.txt");
        this.arquivoRegistroDeAcesso = new File(pastaControleDeAcesso, "registroDeAcesso.txt");
        this.arquivoImagens = new File(pastaControleDeAcesso, "imagens");
    }

    public void verificarEstruturaDeDiretorios() {
        if (!pastaControleDeAcesso.exists() && !pastaControleDeAcesso.mkdirs()) {
            throw new RuntimeException("Erro ao criar pasta ControleDeAcesso");
        }
        if (!arquivoBancoDeDados.exists()) {
            try {
                if (!arquivoBancoDeDados.createNewFile() && !arquivoRegistroDeAcesso.createNewFile() && !arquivoImagens.mkdirs()) {
                    throw new RuntimeException("Erro ao criar bancos de dados");
                }
            } catch (IOException e) {
                throw new RuntimeException("Erro ao criar arquivo bancoDeDados.txt", e);
            }
        }
    }

    public File getArquivoBancoDeDados() {
        return arquivoBancoDeDados;
    }
    public File getArquivoRegistroDeAcesso() {
        return arquivoRegistroDeAcesso;
    }
    public File getArquivoImagens() {
        return arquivoImagens;
    }
}
