package kr.chunkbreaker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ChunkBreakerMod implements ModInitializer {
    private static final int SLICE_HEIGHT = 16;
    private static final Deque<BreakJob> JOBS = new ArrayDeque<>();

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel serverLevel)) return;

            ChunkPos chunk = new ChunkPos(pos);
            int topY = serverLevel.getMaxY() - 1;
            int bottomY = serverLevel.getMinY();

            JOBS.addLast(new BreakJob(serverLevel, chunk, topY, bottomY));
        });

        ServerTickEvents.END_SERVER_TICK.register(ChunkBreakerMod::tickJobs);
    }

    private static void tickJobs(MinecraftServer server) {
        int count = JOBS.size();

        for (int i = 0; i < count; i++) {
            BreakJob job = JOBS.pollFirst();
            if (job == null) break;

            if (!job.eraseNextSlice()) {
                JOBS.addLast(job);
            }
        }
    }

    private static final class BreakJob {
        private final ServerLevel level;
        private final ChunkPos chunk;
        private final int bottomY;
        private int topY;

        private BreakJob(ServerLevel level, ChunkPos chunk, int topY, int bottomY) {
            this.level = level;
            this.chunk = chunk;
            this.topY = topY;
            this.bottomY = bottomY;
        }

        private boolean eraseNextSlice() {
            if (topY < bottomY) return true;

            int y0 = Math.max(bottomY, topY - SLICE_HEIGHT + 1);
            int minX = chunk.getMinBlockX();
            int minZ = chunk.getMinBlockZ();
            int maxX = chunk.getMaxBlockX();
            int maxZ = chunk.getMaxBlockZ();

            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

            for (int y = topY; y >= y0; y--) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        cursor.set(x, y, z);

                        if (!level.getBlockState(cursor).isAir()) {
                            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }

            topY = y0 - 1;
            return topY < bottomY;
        }
    }
}
