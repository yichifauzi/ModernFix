package org.embeddedt.modernfix.common.mixin.bugfix.buffer_builder_leak;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.embeddedt.modernfix.ModernFix;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.ByteBuffer;

@Mixin(BufferBuilder.class)
public class BufferBuilderMixin {
    @Shadow private ByteBuffer buffer;

    private static final MemoryUtil.MemoryAllocator ALLOCATOR = MemoryUtil.getAllocator(false);
    private static boolean leakReported = false;

    @Override
    protected void finalize() throws Throwable {
        try {
            ByteBuffer buf = buffer;
            // can be null if a mod already tried to free the buffer
            if(buf != null) {
                if(!leakReported) {
                    leakReported = true;
                    ModernFix.LOGGER.warn("One or more BufferBuilders have been leaked, ModernFix will attempt to correct this.");
                }
                ALLOCATOR.free(MemoryUtil.memAddress0(buf));
                buffer = null;
            }
        } finally {
            super.finalize();
        }
    }
}