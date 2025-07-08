package com.votechain.backend.blockchain.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

/**
 * Configuración de Web3j para interacciones con la blockchain Ethereum
 */
@Configuration
public class Web3jConfig {

    @Value("${blockchain.provider.url}")
    private String providerUrl;

    @Value("${blockchain.wallet.private-key}")
    private String privateKey;

    @Value("${blockchain.contract.address}")
    private String contractAddress;

    @Value("${blockchain.gas-limit}")
    private Long gasLimit;

    @Value("${blockchain.gas-price}")
    private Long gasPrice;

    @Value("${blockchain.connection.timeout:10000}")
    private Long connectionTimeout;

    /**
     * Configura la instancia de Web3j para conectarse al nodo Ethereum
     */
    @Bean
    public Web3j web3j() {
        // Crear un cliente HTTP con timeouts configurados
        OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(connectionTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(connectionTimeout, TimeUnit.MILLISECONDS)
            .writeTimeout(connectionTimeout, TimeUnit.MILLISECONDS)
            .build();

        // Crear un HttpService con el cliente HTTP configurado
        HttpService httpService = new HttpService(providerUrl, httpClient);

        return Web3j.build(httpService);
    }

    /**
     * Configura las credenciales de la wallet para firmar transacciones
     */
    @Bean
    public Credentials credentials() {
        return Credentials.create(privateKey);
    }

    /**
     * Configura el proveedor de gas para las transacciones
     */
    @Bean
    public ContractGasProvider gasProvider() {
        return new StaticGasProvider(
            BigInteger.valueOf(gasPrice),
            BigInteger.valueOf(gasLimit)
        );
    }
}
