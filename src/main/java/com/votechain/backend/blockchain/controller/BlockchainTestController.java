package com.votechain.backend.blockchain.controller;

import com.votechain.backend.auth.model.User;
import com.votechain.backend.auth.repository.UserRepository;
import com.votechain.backend.blockchain.model.BlockchainVerificationResult;
import com.votechain.backend.blockchain.service.BlockchainService;
import com.votechain.backend.vote.model.Vote;
import com.votechain.backend.vote.service.VoteService;
import com.votechain.backend.voting.model.Votacion;
import com.votechain.backend.voting.model.VotacionEstado;
import com.votechain.backend.voting.model.VotacionOpcion;
import com.votechain.backend.voting.repository.VotacionOpcionRepository;
import com.votechain.backend.voting.repository.VotacionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Controlador para probar la integración con blockchain
 */
@RestController
@RequestMapping("/test")  // ✅ Sin /api porque ya está en context-path
@Slf4j
public class BlockchainTestController {

    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private VoteService voteService;

    @Autowired
    private VotacionRepository votacionRepository;

    @Autowired
    private VotacionOpcionRepository votacionOpcionRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/vote")
    public ResponseEntity<?> testVote(@RequestParam Long votacionId,
                                     @RequestParam Long userId,
                                     @RequestParam Long opcionId) {
        try {
            // Crear un voto de prueba
            Vote vote = voteService.createVote(votacionId, userId, opcionId);

            // Registrar en blockchain
            CompletableFuture<String> future = blockchainService.registerVote(vote);
            String transactionHash = future.get(30, TimeUnit.SECONDS);

            // Verificar el voto
            BlockchainVerificationResult result = blockchainService.verifyVote(transactionHash);

            // Comprobar si el usuario ha votado
            boolean hasVoted = blockchainService.hasUserVoted(votacionId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("transactionHash", transactionHash);
            response.put("verification", result);
            response.put("hasVoted", hasVoted);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/contract-status")
    public ResponseEntity<?> getContractStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("contractLoaded", blockchainService.isContractLoaded());
            status.put("contractAddress", blockchainService.getContractAddress());
            status.put("walletAddress", blockchainService.getWalletAddress());
            status.put("networkVersion", blockchainService.getNetworkVersion());
            status.put("gasPrice", blockchainService.getCurrentGasPrice());

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al verificar el estado del contrato: " + e.getMessage());
        }
    }

    @PostMapping("/setup-and-vote")
    public ResponseEntity<?> setupAndVote() {
        try {
            // 1. Buscar o crear un usuario de prueba
            User usuario = null;
            Optional<User> userOptional = userRepository.findById(1L);

            if (userOptional.isPresent()) {
                usuario = userOptional.get();
            } else {
                // Si no se encuentra un usuario, buscar cualquier usuario en la base de datos
                usuario = userRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new RuntimeException("No se encontraron usuarios en la base de datos"));
            }
            log.info("Usuario seleccionado para prueba: ID={}, Nombre={}", usuario.getId(), usuario.getFullName());

            // 2. Crear una votación de prueba en la base de datos local
            Votacion votacion = new Votacion();
            votacion.setTitulo("Votación de prueba blockchain");
            votacion.setDescripcion("Esta es una votación de prueba para blockchain");
            votacion.setFechaInicio(LocalDateTime.now());
            votacion.setFechaFin(LocalDateTime.now().plusDays(1));
            votacion.setEstado(VotacionEstado.ABIERTA);
            votacion.setCreador(usuario);
            Votacion savedVotacion = votacionRepository.save(votacion);
            log.info("✅ Votación creada en base de datos: ID={}, Título={}", savedVotacion.getId(), savedVotacion.getTitulo());

            // 3. Crear la votación en blockchain con mejor manejo de errores
            log.info("Intentando crear votación {} en blockchain...", savedVotacion.getId());
            String txHashVotacion = null;
            Long blockchainVotingId = null;
            try {
                BlockchainService.VotingCreationResult result = blockchainService.createVotacionInBlockchain(
                    savedVotacion.getId(),
                    savedVotacion.getTitulo(),
                    savedVotacion.getFechaInicio(),
                    savedVotacion.getFechaFin()
                ).get(30, TimeUnit.SECONDS);

                txHashVotacion = result.getTransactionHash();
                blockchainVotingId = result.getBlockchainVotingId();

                log.info("✅ Votación creada en blockchain con hash: {}", txHashVotacion);
                log.info("🔑 ID real en blockchain: {} (ID local: {})", blockchainVotingId, savedVotacion.getId());

                // Verificar que la votación existe en la blockchain usando el ID correcto
                boolean votacionExiste = blockchainService.checkVotacionExistsSafe(blockchainVotingId)
                    .get(30, TimeUnit.SECONDS);
                if (!votacionExiste) {
                    log.warn("⚠️ La votación no se pudo verificar en la blockchain con ID {}, pero continuamos con el proceso", blockchainVotingId);
                } else {
                    log.info("✅ Verificación: La votación existe en la blockchain con ID {}", blockchainVotingId);
                }

                // Esperar unos segundos para asegurar que la transacción esté confirmada
                log.info("Esperando 3 segundos para asegurar confirmación de la transacción...");
                Thread.sleep(3000);

            } catch (Exception e) {
                log.error("❌ Error al crear votación en blockchain", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear votación en blockchain: " + e.getMessage());
            }

            // 4. Crear opciones de voto
            VotacionOpcion opcion1 = new VotacionOpcion();
            opcion1.setTitulo("Opción 1");
            opcion1.setDescripcion("Primera opción de prueba");
            opcion1.setVotacion(savedVotacion);
            opcion1.setOrden(1);
            VotacionOpcion savedOpcion1 = votacionOpcionRepository.save(opcion1);
            log.info("✅ Opción 1 creada: ID={}", savedOpcion1.getId());

            VotacionOpcion opcion2 = new VotacionOpcion();
            opcion2.setTitulo("Opción 2");
            opcion2.setDescripcion("Segunda opción de prueba");
            opcion2.setVotacion(savedVotacion);
            opcion2.setOrden(2);
            VotacionOpcion savedOpcion2 = votacionOpcionRepository.save(opcion2);
            log.info("✅ Opción 2 creada: ID={}", savedOpcion2.getId());

            // 5. Ahora sí registrar el voto usando el ID correcto del blockchain
            log.info("Registrando voto para usuario {} en votación blockchain ID {} (local ID {}), opción {}...",
                usuario.getId(), blockchainVotingId, savedVotacion.getId(), savedOpcion1.getId());

            // Crear el voto pero temporalmente modificar el ID de votación para que coincida con blockchain
            Vote vote = voteService.createVote(savedVotacion.getId(), usuario.getId(), savedOpcion1.getId());
            log.info("✅ Voto creado en base de datos: ID={}", vote.getId());

            // Crear una copia del voto con el ID correcto del blockchain para el registro
            Vote blockchainVote = new Vote();
            blockchainVote.setId(vote.getId());
            blockchainVote.setUser(vote.getUser());
            blockchainVote.setOpcionSeleccionada(vote.getOpcionSeleccionada());
            blockchainVote.setVoteHash(vote.getVoteHash());
            blockchainVote.setCreatedAt(vote.getCreatedAt());
            blockchainVote.setIpAddress(vote.getIpAddress());
            blockchainVote.setStatus(vote.getStatus());

            // Crear una votación temporal con el ID del blockchain
            Votacion tempVotacion = new Votacion();
            tempVotacion.setId(blockchainVotingId);
            tempVotacion.setTitulo(savedVotacion.getTitulo());
            tempVotacion.setDescripcion(savedVotacion.getDescripcion());
            tempVotacion.setFechaInicio(savedVotacion.getFechaInicio());
            tempVotacion.setFechaFin(savedVotacion.getFechaFin());
            tempVotacion.setEstado(savedVotacion.getEstado());
            tempVotacion.setCreador(savedVotacion.getCreador());

            blockchainVote.setVotacion(tempVotacion);

            // Registrar en blockchain usando el ID correcto
            log.info("Registrando voto en blockchain con ID de votación correcto: {}", blockchainVotingId);
            CompletableFuture<String> future = blockchainService.registerVote(blockchainVote);
            String transactionHash = future.get(30, TimeUnit.SECONDS);
            log.info("✅ Voto registrado en blockchain con hash: {}", transactionHash);

            // Actualizar el voto original en la base de datos con la información de blockchain
            vote.setBlockchainTransactionHash(transactionHash);
            vote.setBlockchainVerified(true);
            vote.setBlockchainVerifiedAt(LocalDateTime.now());
            // El VoteService debería tener un método para actualizar, pero por ahora seguimos

            // Verificar el voto
            log.info("Verificando voto en blockchain...");
            BlockchainVerificationResult result = blockchainService.verifyVote(transactionHash);
            log.info("✅ Verificación blockchain: {}", result.isVerified() ? "Exitosa" : "Fallida");

            // Comprobar si el usuario ha votado usando el ID correcto del blockchain
            log.info("Verificando si usuario ha votado usando ID blockchain {}...", blockchainVotingId);
            boolean hasVoted = blockchainService.hasUserVoted(blockchainVotingId, usuario.getId());
            log.info("✅ Usuario ha votado según blockchain: {}", hasVoted);

            // 6. Construir respuesta con información completa incluyendo ambos IDs
            log.info("Preparando respuesta...");
            Map<String, Object> response = new HashMap<>();
            response.put("votacion", Map.of(
                "idLocal", savedVotacion.getId(),
                "idBlockchain", blockchainVotingId,
                "titulo", savedVotacion.getTitulo(),
                "estado", savedVotacion.getEstado(),
                "blockchainTxHash", txHashVotacion
            ));
            response.put("opciones", Map.of(
                "opcion1", Map.of("id", savedOpcion1.getId(), "titulo", savedOpcion1.getTitulo()),
                "opcion2", Map.of("id", savedOpcion2.getId(), "titulo", savedOpcion2.getTitulo())
            ));
            response.put("usuario", Map.of(
                "id", usuario.getId(),
                "nombre", usuario.getFullName()
            ));
            response.put("voto", Map.of(
                "id", vote.getId(),
                "hash", vote.getVoteHash(),
                "blockchainTxHash", transactionHash
            ));
            response.put("blockchain", Map.of(
                "creacionVotacionTxHash", txHashVotacion,
                "votoTxHash", transactionHash,
                "verificacion", result,
                "usuarioHaVotado", hasVoted
            ));
            log.info("✅ Proceso completo exitoso");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error general en setup-and-vote", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + e.getMessage() + (e.getCause() != null ? ", Causa: " + e.getCause().getMessage() : ""));
        }
    }
}
