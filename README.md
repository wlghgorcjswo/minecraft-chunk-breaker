# Chunk Breaker 26.3

Minecraft Java 26.3 Fabric mod.

## Behavior
- Break one block normally.
- The mod uses the exact broken block position, not the player's position.
- The entire chunk containing that block is erased from top to bottom.
- 16 blocks of height are processed per tick.
- Blocks removed by the mod do not drop items.
- Bedrock is also removed.

## Build
GitHub Actions builds automatically after a push to `main`.

Download the artifact from:
**Actions → Build Chunk Breaker → latest successful run → Artifacts**

Then put the built mod JAR and Fabric API for Minecraft 26.3 into your `mods` folder.
