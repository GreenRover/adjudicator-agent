package ch.adjudicator.client;

import chess_contest.ChessContest;
import chess_contest.ChessGameGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main client for connecting to the Adjudicator Chess Contest Platform.
 * <p>
 * This client handles gRPC communication with the server and manages the
 * bidirectional streaming connection for playing chess games.
 */
public class AdjudicatorClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdjudicatorClient.class);

    private final String serverAddress;
    private final String apiKey;
    private final boolean useTls;

    /**
     * Create a new Adjudicator client.
     *
     * @param serverAddress Server address in format "host:port"
     * @param apiKey        API key for authentication
     * @param useTls        Whether to use TLS encryption
     */
    public AdjudicatorClient(String serverAddress, String apiKey, boolean useTls) {
        this.serverAddress = serverAddress;
        this.apiKey = apiKey;
        this.useTls = useTls;
    }

    /**
     * Connect to the server via gRPC and play a game.
     * <p>
     * This method blocks the current thread until the game is concluded.
     *
     * @param agent       Agent instance implementing the Agent interface
     * @param mode        Game mode (TRAINING, OPEN, or RANKED)
     * @param timeControl Time control string (e.g., "180+2" for 3 minutes + 2s increment)
     * @throws Exception If a connection or game error occurs
     */
    public void playGame(Agent agent, GameMode mode, String timeControl) throws Exception {
        // Create channel
        ManagedChannel channel;
        if (useTls) {
            LOGGER.info("Connecting with SSL to {}", serverAddress);
            // Use TLS with default system trust store
            channel = ManagedChannelBuilder.forAddress(serverAddress, 443)
                    .useTransportSecurity()
                    .build();
        } else {
            LOGGER.info("Connecting plain text to {}", serverAddress);
            channel = ManagedChannelBuilder.forAddress(serverAddress, 80)
                    .usePlaintext()
                    .build();
        }

        try {
            ChessGameGrpc.ChessGameStub stub = ChessGameGrpc.newStub(channel);

            // Latch to wait for game completion
            CountDownLatch finishLatch = new CountDownLatch(1);
            AtomicReference<StreamObserver<ChessContest.ClientToServerMessage>> requestObserver = new AtomicReference<>();
            // Create response observer
            requestObserver.set(stub.playGame(
                    new StreamObserver<>() {
                        private String gameId;

                        @Override
                        public void onNext(ChessContest.ServerToClientMessage message) {
                            try {
                                switch (message.getMessageCase()) {
                                    case GAME_STARTED:
                                        gameId = message.getGameStarted().getGameId();
                                        agent.onGameStart(new GameInfo(
                                                message.getGameStarted().getGameId(),
                                                Color.valueOf(message.getGameStarted().getColor().toUpperCase()),
                                                message.getGameStarted().getInitialTimeMs(),
                                                message.getGameStarted().getIncrementMs()
                                        ));
                                        break;

                                    case MOVE_REQUEST:
                                        String move = agent.getMove(new MoveRequest(
                                                message.getMoveRequest().getOpponentMoveLan(),
                                                message.getMoveRequest().getYourRemainingTimeMs(),
                                                message.getMoveRequest().getOpponentRemainingTimeMs()
                                        ));

                                        // Send move response
                                        ChessContest.MoveResponse moveResponse = ChessContest.MoveResponse.newBuilder()
                                                .setGameId(gameId)
                                                .setMoveLan(move)
                                                .build();
                                        requestObserver.get().onNext(ChessContest.ClientToServerMessage.newBuilder()
                                                .setMoveResponse(moveResponse)
                                                .build());
                                        break;

                                    case GAME_OVER:
                                        agent.onGameOver(new GameOverInfo(
                                                GameResult.valueOf(message.getGameOver().getResult().toUpperCase()),
                                                message.getGameOver().getReason(),
                                                message.getGameOver().getFinalPgn()
                                        ));
                                        // Give the logging a moment to complete before closing the stream
                                        try {
                                            Thread.sleep(100);
                                        } catch (InterruptedException ignored) {
                                        }
                                        requestObserver.get().onCompleted();
                                        finishLatch.countDown();
                                        break;

                                    case ERROR:
                                        agent.onError(message.getError().getMessage(), null);
                                        break;

                                    default:
                                        break;
                                }
                            } catch (Exception e) {
                                LOGGER.error("Exception in onNext handling message type: {}", message.getMessageCase(), e);
                                requestObserver.get().onError(e);
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            agent.onError("Stream error: " + t.getClass().getName() + ": " + t.getMessage(), t);
                            // Give the logging a moment to complete before closing
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ignored) {
                            }
                            finishLatch.countDown();
                        }

                        @Override
                        public void onCompleted() {
                            finishLatch.countDown();
                        }
                    }
            ));

            // Send join request
            ChessContest.JoinRequest joinRequest = ChessContest.JoinRequest.newBuilder()
                    .setApiKey(apiKey)
                    .setGameMode(mode.toString())
                    .setTimeControl(timeControl)
                    .build();

            requestObserver.get().onNext(ChessContest.ClientToServerMessage.newBuilder()
                    .setJoinRequest(joinRequest)
                    .build());

            // Wait for game to complete
            finishLatch.await();

        } finally {
            channel.shutdown();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
