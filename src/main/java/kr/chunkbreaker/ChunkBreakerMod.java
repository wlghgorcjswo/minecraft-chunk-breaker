package kr.chunkbreaker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ChunkBreakerMod implements ModInitializer {
    private static final int SLICE_HEIGHT = 16;
    private static final int WARNING_TICKS = 60; // 3 seconds at 20 TPS
    private static final Deque<BreakJob> JOBS = new ArrayDeque<>();
    private static final DustParticleOptions RED_DUST =
            new DustParticleOptions(0xFF0000, 1.5F);

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel serverLevel)) return;

            ChunkPos chunk = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
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

            if (!job.tick()) {
                JOBS.addLast(job);
            }
        }
    }

    private static final class BreakJob {
        private final ServerLevel level;
        private final ChunkPos chunk;
        private final int bottomY;
        private int topY;
        private int warningTicks = WARNING_TICKS;

        private BreakJob(ServerLevel level, ChunkPos chunk, int topY, int bottomY) {
            this.level = level;
            this.chunk = chunk;
            this.topY = topY;
            this.bottomY = bottomY;
        }

        private boolean tick() {
            if (warningTicks > 0) {
                showWarningBorder();
                warningTicks--;
                return false;
            }

            return eraseNextSlice();
        }

        private void showWarningBorder() {
            int minX = chunk.getMinBlockX();
            int minZ = chunk.getMinBlockZ();
            int maxX = chunk.getMaxBlockX() + 1;
            int maxZ = chunk.getMaxBlockZ() + 1;

            // Draw a visible red rectangle around the target chunk near the surface/player area.
            // Multiple horizontal layers make the warning easier to see from different heights.
            int centerX = minX + 8;
            int centerZ = minZ + 8;
            int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    centerX, centerZ) + 1;

            for (int dy = 0; dy <= 6; dy += 3) {
                double y = surfaceY + dy + 0.1;
                for (int i = 0; i <= 16; i++) {
                    spawnRed(minX + i, y, minZ);
                    spawnRed(minX + i, y, maxZ);
                    spawnRed(minX, y, minZ + i);
                    spawnRed(maxX, y, minZ + i);
                }
            }
        }

        private void spawnRed(double x, double y, double z) {
            level.sendParticles(RED_DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
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
