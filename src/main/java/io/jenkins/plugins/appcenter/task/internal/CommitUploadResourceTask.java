package io.jenkins.plugins.appcenter.task.internal;

import hudson.model.TaskListener;
import io.jenkins.plugins.appcenter.AppCenterException;
import io.jenkins.plugins.appcenter.AppCenterLogger;
import io.jenkins.plugins.appcenter.api.AppCenterServiceFactory;
import io.jenkins.plugins.appcenter.model.appcenter.ReleaseUploadEndRequest;
import io.jenkins.plugins.appcenter.model.appcenter.SymbolUploadEndRequest;
import io.jenkins.plugins.appcenter.task.request.UploadRequest;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

@Singleton
public final class CommitUploadResourceTask implements AppCenterTask<UploadRequest>, AppCenterLogger {

    private static final long serialVersionUID = 1L;

    @Nonnull
    private final TaskListener taskListener;
    @Nonnull
    private final AppCenterServiceFactory factory;

    @Inject
    CommitUploadResourceTask(@Nonnull final TaskListener taskListener,
                             @Nonnull final AppCenterServiceFactory factory) {
        this.taskListener = taskListener;
        this.factory = factory;
    }

    @Nonnull
    @Override
    public CompletableFuture<UploadRequest> execute(@Nonnull UploadRequest request) {
        if (request.symbolUploadId == null) {
            return commitAppUpload(request);
        } else {
            return commitAppUpload(request)
                .thenCompose(this::commitSymbolsUpload);
        }
    }


    @Nonnull
    private CompletableFuture<UploadRequest> commitAppUpload(@Nonnull UploadRequest request) {
        final String uploadId = requireNonNull(request.uploadId, "uploadId cannot be null");

        log("Committing app resource.");

        final CompletableFuture<UploadRequest> future = new CompletableFuture<>();
        final ReleaseUploadEndRequest releaseUploadEndRequest = new ReleaseUploadEndRequest(ReleaseUploadEndRequest.StatusEnum.committed);

        factory.createAppCenterService()
            .releaseUploadsComplete(request.ownerName, request.appName, uploadId, releaseUploadEndRequest)
            .whenComplete((releaseUploadBeginResponse, throwable) -> {
                if (throwable != null) {
                    final AppCenterException exception = logFailure("Committing app resource unsuccessful: ", throwable);
                    future.completeExceptionally(exception);
                } else {
                    log("Committing app resource successful.");
                    final UploadRequest uploadRequest = request.newBuilder()
                        .setReleaseId(releaseUploadBeginResponse.release_id)
                        .build();
                    future.complete(uploadRequest);
                }
            });

        return future;
    }

    @Nonnull
    private CompletableFuture<UploadRequest> commitSymbolsUpload(@Nonnull UploadRequest request) {
        final String symbolUploadId = requireNonNull(request.symbolUploadId, "symbolUploadId cannot be null");

        log("Committing symbol resource.");

        final CompletableFuture<UploadRequest> future = new CompletableFuture<>();
        final SymbolUploadEndRequest symbolUploadEndRequest = new SymbolUploadEndRequest(SymbolUploadEndRequest.StatusEnum.committed);

        factory.createAppCenterService()
            .symbolUploadsComplete(request.ownerName, request.appName, symbolUploadId, symbolUploadEndRequest)
            .whenComplete((symbolUploadEndResponse, throwable) -> {
                if (throwable != null) {
                    final AppCenterException exception = logFailure("Committing symbol resource unsuccessful: ", throwable);
                    future.completeExceptionally(exception);
                } else {
                    log("Committing symbol resource successful.");
                    future.complete(request);
                }
            });

        return future;
    }

    @Override
    public PrintStream getLogger() {
        return taskListener.getLogger();
    }
}