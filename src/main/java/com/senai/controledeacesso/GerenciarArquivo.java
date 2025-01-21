package com.senai.controledeacesso;

import java.io.File;
import java.io.IOException;

public class GerenciarArquivo {

    private final File pastaControleDeAcesso;
    private final File arquivoBancoDeDados;

    public GerenciarArquivo() {
        this.pastaControleDeAcesso = new File(System.getProperty("user.home"), "ControleDeAcesso");
        this.arquivoBancoDeDados = new File(pastaControleDeAcesso, "bancoDeDados.txt");
    }

    public void verificarEstruturaDeDiretorios() {
        if (!pastaControleDeAcesso.exists() && !pastaControleDeAcesso.mkdirs()) {
            throw new RuntimeException("Erro ao criar pasta ControleDeAcesso");
        }
        if (!arquivoBancoDeDados.exists()) {
            try {
                if (!arquivoBancoDeDados.createNewFile()) {
                    throw new RuntimeException("Erro ao criar arquivo bancoDeDados.txt");
                }
            } catch (IOException e) {
                throw new RuntimeException("Erro ao criar arquivo bancoDeDados.txt", e);
            }
        }
    }

    public File getArquivoBancoDeDados() {
        return arquivoBancoDeDados;
    }
}
