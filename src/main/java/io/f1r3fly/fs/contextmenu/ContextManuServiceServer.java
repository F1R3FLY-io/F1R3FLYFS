package io.f1r3fly.fs.contextmenu;

import generic.ContextMenuService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.longrunning.Operation.ResultCase;

import generic.ContextManuServiceGrpc;
import generic.ContextMenuService.ActionRequest;
import generic.ContextMenuService.ActionResponse;
import generic.ContextMenuService.EmptyResponse;

import java.io.IOException;

public class ContextManuServiceServer {
    private static final Logger logger = LoggerFactory.getLogger(ContextManuServiceServer.class);
    private Server server;

    public ContextManuServiceServer(java.util.function.Consumer<String> onExchange, int port) {
        server = ServerBuilder.forPort(port)
            .addService(new ContextManuServiceImpl(onExchange))
            .build();
    }

    public void start() throws IOException {
        server.start();
        logger.info("Server started, listening on {}", server.getPort());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down gRPC server since JVM is shutting down");
            ContextManuServiceServer.this.stop();
            logger.info("Server shut down");
        }));
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    static class ContextManuServiceImpl extends ContextManuServiceGrpc.ContextManuServiceImplBase {
        private final java.util.function.Consumer<String> onExchange;

        public ContextManuServiceImpl(java.util.function.Consumer<String> onExchange) {
            this.onExchange = onExchange;
        }

        @Override
        public void submitAction(ActionRequest request, StreamObserver<ActionResponse> responseObserver) {
            switch(request.getAction()) {
                case EXCHANGE:
                    if (request.getPathCount() != 1) {
                        responseObserver.onNext(ActionResponse.newBuilder().setError(
                            ContextMenuService.ErrorResponse.newBuilder().setErrorMessage("Path should be one").build()
                        ).build());
                        return;
                    } else {
                        logger.info("Received Change: path={}, action={}", request.getPath(0), request.getAction());

                        onExchange.accept(request.getPath(0));

                        responseObserver.onNext(ActionResponse.newBuilder().setSuccess(
                            EmptyResponse.newBuilder().build()
                        ).build());
                    }
                    break;

               

                default:
                    responseObserver.onError(new IllegalArgumentException("Unknown action: " + request.getAction()));
            }

            responseObserver.onCompleted();
        }
    }
} 