package com.senai.controledeacesso;

import com.sun.net.httpserver.*;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ServidorHTTPS {

    private HttpsServer server;

    private static boolean imageUpdate = false;

    public ServidorHTTPS() {
        iniciarServidorHTTPS();
    }

    public void iniciarServidorHTTPS() {
        try {
            System.out.println("Iniciando o servidor HTTPS...");

            // Configuração do SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // Configuração do Keystore
            char[] password = "1234abcd".toCharArray();  // Defina a senha do seu keystore
            KeyStore ks = KeyStore.getInstance("JKS");
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("security/keystoreSenai.jks");

            if (inputStream == null) {
                System.out.println("Erro: Keystore não encontrado no caminho especificado em 'src/main/resources/security/keystoreSenai.jks'.");
                throw new FileNotFoundException("Keystore não encontrado.");
            }

            ks.load(inputStream, password);
            inputStream.close();

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(ks, password);

            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

            // Configuração do HttpsServer com endereço "0.0.0.0" para permitir acesso externo
            server = HttpsServer.create(new InetSocketAddress("0.0.0.0", 8000), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                public void configure(HttpsParameters params) {
                    try {
                        SSLContext context = getSSLContext();
                        SSLEngine engine = context.createSSLEngine();
                        params.setNeedClientAuth(false);
                        params.setCipherSuites(engine.getEnabledCipherSuites());
                        params.setProtocols(engine.getEnabledProtocols());

                        SSLParameters sslParameters = context.getDefaultSSLParameters();
                        params.setSSLParameters(sslParameters);
                    } catch (Exception ex) {
                        System.out.println("Erro ao configurar o SSLContext para HTTPS.");
                        ex.printStackTrace();
                    }
                }
            });

            // Configurar Handlers para rotas
            server.createContext("/", new HomeHandler());
            server.createContext("/server-ip", new ServerIpHandler());
            server.createContext("/atualizacao", new AtualizacaoHandler());
            server.createContext("/cadastros", new CadastroListHandler());
            server.createContext("/cadastro", new CadastroHandler());
            server.createContext("/iniciarRegistroTag", new IniciarRegistroTagHandler());
            server.createContext("/verificarStatusTag", new VerificarStatusTagHandler());
            server.createContext("/imagens", new ImagemHandler());
            server.createContext("/cadastro/atualizar/", new UpdateCadastroHandler());
            server.createContext("/cadastro/deletar/", new DeleteCadastroHandler());
            server.createContext("/imagens/deletar/", new ImagemDeleteHandler());


            server.setExecutor(null); // Sem thread pool personalizado
            server.start();
            System.out.println("Servidor HTTPS iniciado na porta 8000 e acessível em todas as interfaces de rede.");

        } catch (Exception e) {
            System.out.println("Erro ao iniciar o servidor HTTPS.");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    // Método para parar o servidor
    public void pararServidorHTTPS() {
        if (server != null) {
            server.stop(0);
            System.out.println("Servidor HTTP parado.");
        }
    }

    // Handler para servir arquivos HTML, CSS e JS do diretório especificado
    private class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("Requisição recebida na rota: " + exchange.getRequestURI().getPath());

            String caminhoRequisitado = exchange.getRequestURI().getPath();

            // Caminho relativo para a pasta webapp
            String caminhoRelativoWebapp = "webapp";
            String caminhoArquivoRequisitado;

            if ("/".equals(caminhoRequisitado) || caminhoRequisitado.startsWith("/index.html")) {
                caminhoArquivoRequisitado = caminhoRelativoWebapp + "/index.html";
            } else {
                caminhoArquivoRequisitado = caminhoRelativoWebapp + caminhoRequisitado;
            }

            // Tentativa de carregar o arquivo como um recurso de dentro do JAR
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(caminhoArquivoRequisitado);

            if (inputStream == null) {
                System.out.println("Arquivo não encontrado no classpath. Tentando carregar do sistema de arquivos.");

                // Caminho alternativo para o sistema de arquivos para ambiente de desenvolvimento
                File arquivoLocal = new File("src/main/" + caminhoArquivoRequisitado);
                if (arquivoLocal.exists() && arquivoLocal.isFile()) {
                    inputStream = new FileInputStream(arquivoLocal);
                    System.out.println("Arquivo encontrado no sistema de arquivos: " + arquivoLocal.getPath());
                }
            }

            if (inputStream == null) {
                System.out.println("Arquivo não encontrado, retornando página 404.");
                // Página não encontrada, serve uma página HTML de erro
                String html404 = """
                        <html>
                            <head>
                                <title>Página não encontrada</title>
                                <style>
                                    body { font-family: Arial, sans-serif; text-align: center; padding: 50px; }
                                    h1 { color: #ff0000; }
                                </style>
                            </head>
                            <body>
                                <h1>404 - Página não encontrada</h1>
                                <p>A página que você está tentando acessar não foi encontrada no servidor.</p>
                            </body>
                        </html>
                        """;
                byte[] bytesResposta = html404.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(404, bytesResposta.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytesResposta);
                }
            } else {
                // Lê o conteúdo do arquivo encontrado e envia a resposta
                byte[] bytesResposta = inputStream.readAllBytes();
                inputStream.close();

                // Determina o tipo MIME com base na extensão do arquivo
                String mimeType = Files.probeContentType(Paths.get(caminhoArquivoRequisitado));
                exchange.getResponseHeaders().set("Content-Type", mimeType != null ? mimeType : "application/octet-stream");
                exchange.sendResponseHeaders(200, bytesResposta.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytesResposta);
                }
            }
            exchange.close();
        }
    }

    // Handler para fornecer o IP do servidor ao frontend
    private class ServerIpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String serverIp = "https://" + exchange.getLocalAddress().getAddress().getHostAddress() + ":8000";

            String jsonResponse = "{ \"serverIp\": \"" + serverIp + "\" }";
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    // Handler para a rota "/atualizacao"
    private class AtualizacaoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            String jsonResponse = RegistroDeAcesso.getListaDeRegistroDeAcesso().isEmpty()
                    ? "[]"
                    : "[" +
                    RegistroDeAcesso.getListaDeRegistroDeAcesso().stream()
                            .map(registro -> String.format("{\"nome\":\"%s\",\"horario\":\"%s\",\"imagem\":\"%s\"}",
                                    registro.getUsuario().getNome(),
                                    registro.getDataHora().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                                    registro.getUsuario().getCaminhoImagem()))
                            .collect(Collectors.joining(",")) +
                    "]";

//            String jsonResponse = RegistroDeAcesso.getListaDeRegistroDeAcesso().isEmpty()
//                    ? "[]"
//                    : RegistroDeAcesso.getListaDeRegistroDeAcesso().stream()
//                    .map(registro -> String.format(
//                            "{\"nome\":\"%s\",\"horario\":\"%s\",\"imagem\":\"%s\"}",
//                            registro.getUsuario().getNome(),
//                            registro.getDataHora().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
//                            registro.getUsuario().getCaminhoImagem()
//                    ))
//                    .collect(Collectors.joining(",", "[", "]"));

            byte[] bytesResposta = jsonResponse.getBytes();
            exchange.sendResponseHeaders(200, bytesResposta.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytesResposta);
            os.close();
        }
    }

    // Handler para listar todos os cadastros
    private class CadastroListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            JSONArray jsonArray = new JSONArray();


            // Percorre a matrizCadastr a partir da segunda linha (ignora cabeçalho)
            for (Usuario usuario : Usuario.getListaUsuarios()) {
//                String[] registro = ControleDeAcesso.matrizCadastr[i];
                if (usuario != null) { // Verifica se a linha está preenchida
                    JSONObject json = new JSONObject();
                    json.put("id", usuario.getId());
                    json.put("idAcesso", usuario.getIdAcesso() != null ? usuario.getIdAcesso().toString() : "-");
                    json.put("nome", usuario.getNome());
                    json.put("telefone", usuario.getTelefone());
                    json.put("email", usuario.getEmail());
                    json.put("imagem", usuario.getCaminhoImagem() != null ? usuario.getCaminhoImagem().replace(" ", "-") : "-");
                    jsonArray.put(json);
                    System.out.println("Listei os usuários");
                }
            }

            // Envia a resposta como JSON
            byte[] response = jsonArray.toString().getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }


    // Handler para cadastrar um novo usuário
    private class CadastroHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                InputStreamReader inputStreamReader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                StringBuilder corpoDaRequisicao = new StringBuilder();
                String linha;
                GerenciarArquivo gerenciarArquivo = new GerenciarArquivo();
                while ((linha = bufferedReader.readLine()) != null) {
                    corpoDaRequisicao.append(linha);
                }

                // Gera novo ID e cria o registro
                int novoID = Usuario.getListaUsuarios().size() + 1;

                JSONObject json = new JSONObject(corpoDaRequisicao.toString());
                String nome = json.getString("nome");
                String telefone = json.getString("telefone");
                String email = json.getString("email");
                String nomeImagem = novoID + nome;

                if (!json.getString("imagem").equals("-")) {
                    salvarImagem(json.getString("imagem").replace(" ", "-"), nomeImagem);
                } else {
                    nomeImagem = "-";
                }
                //Logs
                System.out.println("nome : " + nome + " | telefone : " + telefone + " | email : " + email);

//                String[] novoUsuario = {String.valueOf(novoID), "-", nome, telefone, email, nomeImagem};
//                String[][] novaMatriz = new String[novoID + 1][novoUsuario[0].length()];
//
//                for (int linhas = 0; linhas < ControleDeAcesso.matrizCadastro.length; linhas++) {
//                    novaMatriz[linhas] = Arrays.copyOf(ControleDeAcesso.matrizCadastro[linhas], ControleDeAcesso.matrizCadastro[linhas].length);
//                }

//                novaMatriz[novoID] = novoUsuario;
//                ControleDeAcesso.matrizCadastro = novaMatriz;
                Usuario usuario = new Usuario(novoID, null, nome, telefone, email, nomeImagem);
                Usuario.getListaUsuarios().add(usuario);
                Usuario.inserirUsuariosNoArquivo(gerenciarArquivo.getArquivoBancoDeDados());
//                ControleDeAcesso.salvarDadosNoArquivo();

                String responseMessage = "Cadastro recebido com sucesso!";
                exchange.sendResponseHeaders(200, responseMessage.length());
                exchange.getResponseBody().write(responseMessage.getBytes());
                exchange.close();
            } else {
                exchange.sendResponseHeaders(405, -1); // Método não permitido
            }
        }
    }


    // Handler para atualizar um cadastro existente (PUT)
    private class UpdateCadastroHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("PUT".equals(exchange.getRequestMethod())) {  // Mudando PATCH para PUT
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
                StringBuilder corpoDaRequisicao = new StringBuilder();
                GerenciarArquivo gerenciarArquivo = new GerenciarArquivo();
                String linha;
                while ((linha = bufferedReader.readLine()) != null) {
                    corpoDaRequisicao.append(linha);
                    // System.out.println(corpoDaRequisicao.append(linha));

                }

                if (corpoDaRequisicao.length() == 0) {
                    System.out.println("erro no atualizar 400");
                    exchange.sendResponseHeaders(400, -1); // Bad request

                    return;
                }

                JSONObject json = new JSONObject(corpoDaRequisicao.toString());
                System.out.println(corpoDaRequisicao.toString());
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                int id = Integer.parseInt(parts[parts.length - 1]);
                System.out.println(id);
                System.out.println(id - 1);

                if (id > 0 && id <= Usuario.getListaUsuarios().size()) {

                    // Verifica se o ID é válido e se o cadastro existe
//                if (id > 0) {
                    System.out.println("entrou no atualizar");
//                    System.out.println(Usuario.buscarUsuarioPorId(id).toString());
                    // Obtenha os dados do cadastro atual para substituir
//                    String[] registro = new String[6];
                    Usuario usuario = Usuario.buscarUsuarioPorId(id);
//                    usuario.setId(id);   // ID do cadastro
                    // Substitui os dados fornecidos no corpo da requisição
                    System.out.println("vai alterar uuid");
                    usuario.setIdAcesso(json.has("idAcesso") && !json.getString("idAcesso").equals("-") ? UUID.fromString(json.getString("idAcesso")) : null);
                    System.out.println("alterou o uuid");
                    usuario.setNome(json.has("nome") ? json.getString("nome") : "");   // Verifica se o nome foi enviado
                    usuario.setTelefone(json.has("telefone") ? json.getString("telefone") : "");   // Verifica se o telefone foi enviado
                    usuario.setEmail(json.has("email") ? json.getString("email") : "");   // Verifica se o email foi enviado
                    // Verifica se a imagem foi enviada
                    System.out.println("alterou os parametros");

                    String nomeImagem = json.has("nomeImagem") ? json.getString("nomeImagem").replace(" ", "-") : "-";
                    System.out.println("alterou os parametros e imagem");
//                    if (json.has("imagem") && !json.getString("imagem").equals("-")) {
//                        System.out.println("passou na imagem if");
//                        imageUpdate = true;
//                        salvarImagem(json.getString("imagem"), id + usuario.getNome());
//                        usuario.setCaminhoImagem(id + usuario.getNome().replace(" ", "-"));
//                        System.out.println("Imagem salva em: " + new File(ControleDeAcesso.pastaImagens, id + usuario.getNome() + ".png").getAbsolutePath());
//
//                    } else {
//                        usuario.setCaminhoImagem(Usuario.buscarUsuarioPorId(id).getNome().equals(usuario.getNome())
//                                ? Usuario.buscarUsuarioPorId(id).getCaminhoImagem()
//                                : nomeImagem);
//                        System.out.println("passou na imagem else");
//                    }
                    if (json.has("imagem") && !json.getString("imagem").equals("-")) {
                        String caminhoAntigo = usuario.getCaminhoImagem();
                        System.out.println(caminhoAntigo);

                        // Salvar a nova imagem
                        salvarImagem(json.getString("imagem"), id + usuario.getNome());
                        usuario.setCaminhoImagem(id + usuario.getNome().replace(" ", "-"));

                        // Excluir a imagem antiga, se necessário
                        if (caminhoAntigo != null && !caminhoAntigo.equals(usuario.getCaminhoImagem())) {
                            File imagemAntiga = new File(ControleDeAcesso.pastaImagens, caminhoAntigo + ".png");
                            if (imagemAntiga.exists()) {
                                boolean excluido = imagemAntiga.delete();
                                System.out.println(excluido ? "Imagem antiga excluída com sucesso." : "Falha ao excluir a imagem antiga.");
                            }
                        }
                        imageUpdate = true;

                        System.out.println("Imagem salva em: " + new File(ControleDeAcesso.pastaImagens, id + usuario.getNome() + ".png").getAbsolutePath());
                    } else {
                        // Caso não haja nova imagem, manter a antiga
                        usuario.setCaminhoImagem(Usuario.buscarUsuarioPorId(id).getNome().equals(usuario.getNome())
                                ? Usuario.buscarUsuarioPorId(id).getCaminhoImagem()
                                : nomeImagem);
                        System.out.println("Nenhuma nova imagem enviada. Mantendo a imagem existente.");
                    }

                    //Logs
                    System.out.println("Edição: nome : " + usuario.getNome() + " | telefone : " + usuario.getTelefone() + " | email : " + usuario.getEmail());

                    // Substitui o cadastro na matriz com os novos dados
                    long idUser = Usuario.getListaUsuarios().indexOf(Usuario.buscarUsuarioPorId(id));

                    if (idUser != -1) {
                        Usuario.getListaUsuarios().set((int) idUser, usuario);
                        System.out.println("Usuário atualizado com sucesso.");
                        Usuario.inserirUsuariosNoArquivo(gerenciarArquivo.getArquivoBancoDeDados());
                    } else {
                        System.out.println("Usuário não encontrado na lista.");
                    }


                    // Resposta de sucesso
                    String response = "{\"status\":\"Cadastro atualizado com sucesso.\"}";
                    byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    exchange.getResponseBody().write(responseBytes);
                } else {
                    System.out.println("não encontrado");
                    // Resposta de erro caso o ID não exista
                    String response = "{\"status\":\"ID não encontrado.\"}";
                    byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, responseBytes.length);
                    exchange.getResponseBody().write(responseBytes);
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Método não permitido
            }
            exchange.close();
        }
    }

    private void salvarImagem(String imagemBase64, String nomeImagem) throws IOException {
        byte[] dados = Base64.getDecoder().decode(imagemBase64);

        nomeImagem = nomeImagem.replace(" ", "-");

        // Caminho completo para salvar a imagem
        File arquivoNovaImagem = new File(ControleDeAcesso.pastaImagens, nomeImagem + ".png");
        try (FileOutputStream fileOutputStream = new FileOutputStream(arquivoNovaImagem)) {
            fileOutputStream.write(dados);
        }
    }

    // Handler para deletar um cadastro existente (DELETE)
    private class DeleteCadastroHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("Iniciando processamento no DeleteCadastroHandler");

            Scanner scanner = new Scanner(System.in);
            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                System.out.println("Método DELETE confirmado e CORS habilitado.");

                String response;
                int statusCode;
                GerenciarArquivo gerenciarArquivo = new GerenciarArquivo();

                try {
                    String idPath = exchange.getRequestURI().getPath().replaceFirst("/cadastro/deletar/", "").trim();
                    System.out.println("ID extraído da URI: " + idPath);

                    int id = Integer.parseInt(idPath);
                    System.out.println("ID convertido para inteiro: " + id);

                    long idUser = Usuario.getListaUsuarios().indexOf(Usuario.buscarUsuarioPorId(id));

                    if (idUser != -1 && Usuario.getListaUsuarios().get((int) idUser) != null) {
                        System.out.println("Entrou no delete");
                        ControleDeAcesso.idUsuarioRecebidoPorHTTP = id;
                        Usuario.deletarUsuario(id);

                        response = "{\"status\":\"Cadastro deletado com sucesso.\"}";
                        statusCode = 200;
                        System.out.println("Usuário deletado com sucesso.");
                    } else {
                        response = "{\"status\":\"ID não encontrado.\"}";
                        statusCode = 404;
                        System.out.println("ID não encontrado ou inválido.");
                    }
                } catch (NumberFormatException e) {
                    response = "{\"status\":\"ID inválido.\"}";
                    statusCode = 400;
                    System.err.println("Erro ao converter o ID para número: " + e.getMessage());
                }

                // Envia resposta
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close(); // Fecha o canal de resposta
                System.out.println("Processamento da requisição encerrado.");
            } else {
                System.out.println("Método não permitido: " + exchange.getRequestMethod());
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
            }
        }
    }


    // Classe para lidar com requisições de imagens
    private class ImagemHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Log para indicar o início do processamento da requisição
            System.out.println("Iniciando processamento no ImagemHandler");

            // Recupera o caminho completo da imagem solicitado pela URI
            String imagePath = Paths.get(ControleDeAcesso.pastaImagens.getAbsolutePath(),
                    exchange.getRequestURI().getPath().replace("/imagens/", "").replace(" ", "-") + ".png").toString();
            System.out.println("Caminho completo da imagem requisitada: " + imagePath);

            File imageFile = new File(imagePath);

            // Verifica se o arquivo da imagem existe e não é um diretório
            if (imageFile.exists() && !imageFile.isDirectory()) {
                // Define o tipo de conteúdo com base no tipo da imagem e ajusta o cabeçalho
                String contentType = Files.probeContentType(Paths.get(imagePath));
                exchange.getResponseHeaders().set("Content-Type", contentType);
                System.out.println("Tipo de conteúdo da imagem: " + contentType);

                // Configura o cabeçalho da resposta com o código 200 e o tamanho da imagem
                exchange.sendResponseHeaders(200, imageFile.length());
                System.out.println("Imagem encontrada. Enviando resposta com código 200 e tamanho: " + imageFile.length());

                // Envia a imagem para o cliente
                try (OutputStream os = exchange.getResponseBody();
                     FileInputStream fs = new FileInputStream(imageFile)) {
                    fs.transferTo(os);
                    System.out.println("Imagem enviada ao cliente com sucesso.");
                } catch (IOException e) {
                    System.err.println("Erro ao enviar a imagem ao cliente: " + e.getMessage());
                }
            } else {
                // Caso a imagem não seja encontrada, envia o código 404
                System.out.println("Imagem não encontrada. Enviando resposta 404.");
                exchange.sendResponseHeaders(404, -1);
            }
            // Fecha o canal de resposta
            exchange.close();
            System.out.println("Processamento da requisição encerrado.");
        }
    }

    public class ImagemDeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            System.out.println("Recebida uma requisição para o ImagemDeleteHandler");

            String response;
            int statusCode;


            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                System.out.println("Caminho da URI: " + path);

                String nomeImagem = path.replaceFirst("/imagens/deletar/", "") + ".png";
                nomeImagem = nomeImagem.replace(" ", "-");
                System.out.println("Nome da imagem extraído: " + nomeImagem);

                File arquivoImagem = new File(ControleDeAcesso.pastaImagens.getAbsolutePath(), nomeImagem);
                System.out.println("Caminho completo do arquivo: " + arquivoImagem.getAbsolutePath());

                if (arquivoImagem.exists() && arquivoImagem.isFile()) {
                    if (imageUpdate) {
                        response = "{ \"status\": \"sucesso\", \"mensagem\": \"Imagem excluídaRegistros de Acesso com sucesso.\" }";
                        statusCode = HttpURLConnection.HTTP_OK;
                        System.out.println("Imagem atualizada com sucesso.");
                    } else {
                        if (arquivoImagem.delete()) {
                            response = "{ \"status\": \"sucesso\", \"mensagem\": \"Imagem excluída com sucesso.\" }";
                            statusCode = HttpURLConnection.HTTP_OK;
                            System.out.println("Imagem excluída com sucesso.");
                        } else {
                            response = "{ \"status\": \"erro\", \"mensagem\": \"Falha ao excluir a imagem.\" }";
                            statusCode = HttpURLConnection.HTTP_INTERNAL_ERROR;
                            System.out.println("Erro: Falha ao excluir a imagem.");
                        }
                    }
                } else {
                    response = "{ \"status\": \"erro\", \"mensagem\": \"Imagem não encontrada.\" }";
                    statusCode = HttpURLConnection.HTTP_NOT_FOUND;
                    System.out.println("Erro: Imagem não encontrada.");
                }
            } else {
                response = "{ \"status\": \"erro\", \"mensagem\": \"Método não permitido.\" }";
                statusCode = HttpURLConnection.HTTP_BAD_METHOD;
                System.out.println("Erro: Método não permitido. Apenas DELETE é suportado.");
            }


            // Envia resposta
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.flush(); // Garante que todos os bytes sejam enviados antes de fechar
            os.close();
            exchange.close(); // Fecha o canal de resposta
            System.out.println("Resposta enviada ao cliente: " + response);
        }
    }


    private class IniciarRegistroTagHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                // Receber o ID do usuário e o dispositivo que solicitou o registro
                System.out.println("Entrou em registro da tag");
                String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))
                        .lines().collect(Collectors.joining());
                JSONObject json = new JSONObject(requestBody);
                int usuarioId = json.getInt("usuarioId");
                String dispositivo = json.getString("dispositivo"); // Novo campo do dispositivo

                // Iniciar o processo de registro da tag
                ControleDeAcesso.idUsuarioRecebidoPorHTTP = usuarioId;
                ControleDeAcesso.modoCadastrarIdAcesso = true;

                // Publicar no broker qual dispositivo foi habilitado
                ControleDeAcesso.conexaoMQTT.publicarMensagem("cadastro/disp", dispositivo);
                System.out.println("enviou a mensagem");

                // Criação da resposta JSON
                String response = new JSONObject()
                        .put("mensagem", "Registro de tag iniciado para o usuário " + usuarioId + " no " + dispositivo)
                        .toString();

                // Envio da resposta com cabeçalho de conteúdo JSON
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, response.getBytes().length);

                // Escrita e fechamento do corpo da resposta
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                    System.out.println("saindo do registro de tag");
                }
                exchange.close();
            }
        }
    }

    private class VerificarStatusTagHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int usuarioId = Integer.parseInt(exchange.getRequestURI().getPath().split("/")[2]);
//            String status = ControleDeAcesso.matrizCadastr[usuarioId][1].equals("-") ? "aguardando" : "sucesso";

            String status = Usuario.buscarUsuarioPorId(usuarioId).getIdAcesso() == null ? "aguardando" : "sucesso";

            String response = "{\"status\":\"" + status + "\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
        }
    }
}