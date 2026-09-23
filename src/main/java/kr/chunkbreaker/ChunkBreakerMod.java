package kr.chunkbreaker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class ChunkBreakerMod implements ModInitializer {
    private static final int SLICE_HEIGHT = 16;
    private static final int WARNING_TICKS = 60;
    private static final Deque<BreakJob> JOBS = new ArrayDeque<>();

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel serverLevel)) return;

            ChunkPos chunk = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
            int topY = serverLevel.getMaxY() - 1;
            int bottomY = serverLevel.getMinY();

            JOBS.addLast(new BreakJob(serverLevel, chunk, topY, bottomY, pos.getY()));
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
        private final int warningY;
        private final List<Display.BlockDisplay> warningDisplays = new ArrayList<>();
        private int topY;
        private int warningTicks = WARNING_TICKS;
        private boolean warningSpawned;

        private BreakJob(ServerLevel level, ChunkPos chunk, int topY, int bottomY, int warningY) {
            this.level = level;
            this.chunk = chunk;
            this.topY = topY;
            this.bottomY = bottomY;
            this.warningY = warningY;
        }

        private boolean tick() {
            if (!warningSpawned) {
                spawnWarningBorder();
                playWarningSound();
                warningSpawned = true;
            }

            if (warningTicks > 0) {
                warningTicks--;
                if (warningTicks == 40) setWarningColor("minecraft:yellow_stained_glass");
                if (warningTicks == 20) setWarningColor("minecraft:red_stained_glass");
                if (warningTicks == 40 || warningTicks == 20) playWarningSound();
                return false;
            }

            if (!warningDisplays.isEmpty()) {
                removeWarningBorder();
                playBreakSound();
            }

            return eraseNextSlice();
        }

        private void playWarningSound() {
            var sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("minecraft:block.note_block.pling"));
            if (sound != null) {
                level.playSound(null, chunk.getMiddleBlockX(), warningY, chunk.getMiddleBlockZ(),
                        sound, SoundSource.BLOCKS, 1.2F, 0.8F);
            }
        }

        private void playBreakSound() {
            var sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("minecraft:entity.generic.explode"));
            if (sound != null) {
                level.playSound(null, chunk.getMiddleBlockX(), warningY, chunk.getMiddleBlockZ(),
                        sound, SoundSource.BLOCKS, 1.5F, 0.9F);
            }
        }

        private void spawnWarningBorder() {
            int minX = chunk.getMinBlockX();
            int minZ = chunk.getMinBlockZ();
            int maxX = chunk.getMaxBlockX();

            int maxZ = chunk.getMaxBlockZ();
            int height = topY - bottomY + 1;

            // Four huge, thin block displays form a world-border-like wall from world bottom to world top.
            spawnWall(minX, bottomY, minZ, 16.0F, height, 0.08F);
            spawnWall(minX, bottomY, maxZ + 0.92, 16.0F, height, 0.08F);
            spawnWall(minX, bottomY, minZ, 0.08F, height, 16.0F);
            spawnWall(maxX + 0.92, bottomY, minZ, 0.08F, height, 16.0F);
        }

        private void spawnWall(double x, double y, double z, float scaleX, float scaleY, float scaleZ) {
            var type = (EntityType<? extends Display.BlockDisplay>) BuiltInRegistries.ENTITY_TYPE
                    .getValue(Identifier.parse("minecraft:block_display"));
            if (type == null) return;

            Display.BlockDisplay display = new Display.BlockDisplay(type, level);
            display.setBlockState(BuiltInRegistries.BLOCK
                    .getValue(Identifier.parse("minecraft:green_stained_glass")).defaultBlockState());
            display.setPos(x, y, z);
            display.setTransformation(new com.mojang.math.Transformation(
                    new org.joml.Vector3f(0.0F, 0.0F, 0.0F),
                    null,
                    new org.joml.Vector3f(scaleX, scaleY, scaleZ),
                    null));
            level.addFreshEntity(display);
            warningDisplays.add(display);
        }

        private void setWarningColor(String blockId) {
            var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId));
            if (block == null) return;
            for (Display.BlockDisplay display : warningDisplays) {
                if (!display.isRemoved()) {
                    display.setBlockState(block.defaultBlockState());
                }
            }
        }

        private void removeWarningBorder() {
            for (Entity display : warningDisplays) {
                if (!display.isRemoved()) {
                    display.discard();
                }
            }
            warningDisplays.clear();
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
