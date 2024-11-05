package com.senai;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class CLienteMQTT {

    private MqttClient cliente;
    private final String brokerUrl;
    private String topicoAtual;
    private MensagemListener listenerAtual;

    public CLienteMQTT(String brokerUrl, String topico, MensagemListener listener) {
        this.brokerUrl = brokerUrl;
        this.topicoAtual = topico;
        this.listenerAtual = listener;
        this.cliente = conectar();
        if (cliente != null && cliente.isConnected()) {
            assinarTopicoEIniciarEscuta(topico, listener);
        }
    }

    private MqttClient conectar() {
        try {
            MqttClient cliente = new MqttClient(brokerUrl, MqttClient.generateClientId(), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(5);
            System.out.println("Conectando ao broker MQTT: " + brokerUrl);
            cliente.connect(options);
            System.out.println("Conectado.");

            // Configura o callback para gerenciar eventos de conexão e mensagens recebidas
            configurarCallback(cliente);

            return cliente;

        } catch (MqttException e) {
            System.err.println("Erro ao conectar: " + e.getMessage());
            return null;
        }
    }

    private void configurarCallback(MqttClient cliente) {
        cliente.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("Conexão perdida: " + cause.getMessage());
                // Tenta reconectar quando a conexão é perdida
                reconectar();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String mensagemRecebida = new String(message.getPayload(), StandardCharsets.UTF_8);
                System.out.println("Mensagem recebida no tópico " + topic + ": " + mensagemRecebida);
                if (listenerAtual != null) {
                    listenerAtual.onMensagemRecebida(mensagemRecebida);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Não utilizado para assinatura de tópicos
            }
        });
    }

    private void reconectar() {
        System.out.println("Tentando reconectar ao broker MQTT...");
        while (!cliente.isConnected()) {
            try {
                TimeUnit.SECONDS.sleep(5);
                if (cliente != null) {
                    cliente.close();
                }
                cliente = conectar();
                if (cliente != null && cliente.isConnected()) {
                    System.out.println("Reconexão bem-sucedida.");
                    if (topicoAtual != null) {
                        assinarTopicoEIniciarEscuta(topicoAtual, listenerAtual);
                    }
                }
            } catch (Exception e) {
                System.err.println("Erro ao tentar reconectar: " + e.getMessage());
            }
        }
    }

    public void desconectar() {
        if (cliente.isConnected()) {
            try {
                cliente.disconnect();
                System.out.println("Desconectado do broker MQTT.");
            } catch (MqttException e) {
                System.err.println("Erro ao desconectar: " + e.getMessage());
            }
        }
    }

    public void publicarMensagem(String topico, String mensagem) {
        if (cliente.isConnected()) {
            MqttMessage mqttMessage = new MqttMessage(mensagem.getBytes(StandardCharsets.UTF_8));
            mqttMessage.setQos(1);
            try {
                cliente.publish(topico, mqttMessage);
                System.out.println("Mensagem publicada no tópico " + topico + ": " + mensagem);
            } catch (MqttException e) {
                System.err.println("Erro ao publicar mensagem: " + e.getMessage());
            }
        } else {
            System.err.println("Erro: cliente desconectado.");
        }
    }

    public void assinarTopicoEIniciarEscuta(String topico, MensagemListener listener) {
        try {
            topicoAtual = topico;
            listenerAtual = listener;
            cliente.subscribe(topico, (topic, message) -> {
                String mensagemRecebida = new String(message.getPayload(), StandardCharsets.UTF_8).trim();
                System.out.println("Mensagem recebida no tópico " + topic + ": " + mensagemRecebida);
                listener.onMensagemRecebida(mensagemRecebida);
            });
        } catch (MqttException e) {
            System.err.println("Erro ao assinar tópico: " + e.getMessage());
        }
    }

    public interface MensagemListener {
        void onMensagemRecebida(String mensagem);
    }
}
