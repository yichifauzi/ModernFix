package org.embeddedt.modernfix.common.mixin.bugfix.paper_chunk_patches;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.*;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.embeddedt.modernfix.duck.IPaperChunkHolder;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;


@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow @Final private BlockableEventLoop<Runnable> mainThreadExecutor;

    @Shadow @Final private ChunkMap.DistanceManager distanceManager;

    @Shadow protected abstract CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> protoChunkToFullChunk(ChunkHolder arg);

    @Shadow @Final private ServerLevel level;
    @Shadow @Final private ThreadedLevelLightEngine lightEngine;
    @Shadow @Final private ChunkProgressListener progressListener;

    @Shadow protected abstract CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> scheduleChunkGeneration(ChunkHolder chunkHolder, ChunkStatus chunkStatus);

    @Shadow @Final private StructureTemplateManager structureTemplateManager;
    private Executor mainInvokingExecutor;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void setup(CallbackInfo ci) {
        this.mainInvokingExecutor = (runnable) -> {
            if(ModernFixPlatformHooks.getCurrentServer().isSameThread())
                runnable.run();
            else
                this.mainThreadExecutor.execute(runnable);
        };
    }


    /* https://github.com/PaperMC/Paper/blob/ver/1.17.1/patches/server/0752-Fix-chunks-refusing-to-unload-at-low-TPS.patch */
    @ModifyArg(method = "prepareAccessibleChunk", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 1)
    private Executor useMainThreadExecutor(Executor executor) {
        return this.mainThreadExecutor;
    }

    /* https://github.com/PaperMC/Paper/blob/master/patches/removed/1.19.2-legacy-chunksystem/0482-Improve-Chunk-Status-Transition-Speed.patch */
    @ModifyArg(method = "prepareEntityTickingChunk", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 1)
    private Executor useMainInvokingExecutor(Executor executor) {
        return this.mainInvokingExecutor;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "scheduleChunkGeneration", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenComposeAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture skipWorkerIfPossible(CompletableFuture inputFuture, Function function, Executor executor, ChunkHolder holder) {
        Executor targetExecutor = (runnable) -> {
            if(((IPaperChunkHolder)holder).mfix$canAdvanceStatus()) {
                this.mainInvokingExecutor.execute(runnable);
                return;
            }
            executor.execute(runnable);
        };
        return inputFuture.thenComposeAsync(function, targetExecutor);
    }

    /**
     * @author embeddedt
     * @reason revert 1.17 chunk system changes, significantly reduces time and RAM needed to load chunks
     */
    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true)
    private void useLegacySchedulingLogic(ChunkHolder holder, ChunkStatus requiredStatus, CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {
        if(requiredStatus != ChunkStatus.EMPTY) {
            ChunkPos chunkpos = holder.getPos();
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = holder.getOrScheduleFuture(requiredStatus.getParent(), (ChunkMap)(Object)this);
            cir.setReturnValue(future.thenComposeAsync((either) -> {
                Optional<ChunkAccess> optional = either.left();
                if(!optional.isPresent())
                    return CompletableFuture.completedFuture(either);

                if (requiredStatus == ChunkStatus.LIGHT) {
                    this.distanceManager.addTicket(TicketType.LIGHT, chunkpos, 33 + ChunkStatus.getDistance(ChunkStatus.LIGHT), chunkpos);
                }

                // from original method
                if (optional.get().getStatus().isOrAfter(requiredStatus)) {
                    CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> completablefuture = requiredStatus.load(this.level, this.structureTemplateManager, this.lightEngine, (arg2) -> {
                        return this.protoChunkToFullChunk(holder);
                    }, (ChunkAccess)optional.get());
                    this.progressListener.onStatusChange(chunkpos, requiredStatus);
                    return completablefuture;
                } else {
                    return this.scheduleChunkGeneration(holder, requiredStatus);
                }
            }, this.mainThreadExecutor).thenComposeAsync(CompletableFuture::completedFuture, this.mainThreadExecutor));
        }
    }
}