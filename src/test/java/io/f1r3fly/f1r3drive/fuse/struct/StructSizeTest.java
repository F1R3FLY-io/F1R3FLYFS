package io.f1r3fly.f1r3drive.fuse.struct;

import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import jnr.posix.util.Platform;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static io.f1r3fly.f1r3drive.fuse.struct.PlatformSize.platformSize;
import static jnr.ffi.Platform.OS.*;

class PlatformSize {
    public final int x32;
    public final int x64;

    PlatformSize(int x32, int x64) {
        this.x32 = x32;
        this.x64 = x64;
    }

    public static PlatformSize platformSize(int x32, int x64) {
        return new PlatformSize(x32, x64);
    }
}


/**
 * Test for the right struct size
 */
public class StructSizeTest {

    private static Map<Class<?>, Map<jnr.ffi.Platform.OS, PlatformSize>> sizes = Utils.asMap(
            Utils.Pair.pair(Statvfs.class, Utils.asMap(
                    Utils.Pair.pair(LINUX, platformSize(96, 112)), //
                    Utils.Pair.pair(WINDOWS, platformSize(88, 88)), //
                    Utils.Pair.pair(DARWIN, platformSize(64, 64)))),
            Utils.Pair.pair(FileStat.class, Utils.asMap(
                    Utils.Pair.pair(LINUX, platformSize(96, 144)), //
                    Utils.Pair.pair(WINDOWS, platformSize(128, 128)), //
                    Utils.Pair.pair(DARWIN, platformSize(96, 144)))),
            Utils.Pair.pair(FuseFileInfo.class, Utils.asMap(
                    Utils.Pair.pair(LINUX, platformSize(32, 40)), //
                    Utils.Pair.pair(WINDOWS, platformSize(32, 32)), //
                    Utils.Pair.pair(DARWIN, platformSize(32, 40)))),
            Utils.Pair.pair(FuseOperations.class, Utils.asMap(
                    Utils.Pair.pair(LINUX, platformSize(180, 360)), //
                    Utils.Pair.pair(WINDOWS, platformSize(180, 360)), //
                    Utils.Pair.pair(DARWIN, platformSize(232, 464)))),
            Utils.Pair.pair(Timespec.class, Utils.asMap(
                    Utils.Pair.pair(LINUX, platformSize(8, 16)), //
                    Utils.Pair.pair(WINDOWS, platformSize(8, 16)), //
                    Utils.Pair.pair(DARWIN, platformSize(8, 16)))),
            Utils.Pair.pair(Flock.class, Utils.asMap(
                    Utils.Pair.pair(LINUX, platformSize(24, 32)), //
                    Utils.Pair.pair(WINDOWS, platformSize(24, 32)), //
                    Utils.Pair.pair(DARWIN, platformSize(24, 24)))),
            Utils.Pair.pair(FuseBuf.class, Utils.asMap(
                    Utils.Pair.pair(LINUX, platformSize(24, 40)), //
                    Utils.Pair.pair(WINDOWS, platformSize(24, 40)), //
                    Utils.Pair.pair(DARWIN, platformSize(24, 40)))),
            Utils.Pair.pair(FuseBufvec.class, Utils.asMap(
                    Utils.Pair.pair(LINUX, platformSize(36, 64)), //
                    Utils.Pair.pair(WINDOWS, platformSize(36, 64)), //
                    Utils.Pair.pair(DARWIN, platformSize(36, 64)))),
            Utils.Pair.pair(FusePollhandle.class, Utils.asMap(
                    Utils.Pair.pair(LINUX, platformSize(16, 24)), //
                    Utils.Pair.pair(WINDOWS, platformSize(16, 24)), //
                    Utils.Pair.pair(DARWIN, platformSize(16, 24)))),
            Utils.Pair.pair(FuseContext.class, Utils.asMap(
                    // the real x64 size is 40 because of alignment
                    Utils.Pair.pair(LINUX, platformSize(24, 36)), //
                    Utils.Pair.pair(WINDOWS, platformSize(24, 34)), //
                    Utils.Pair.pair(DARWIN, platformSize(24, 34))))
    );

    @BeforeClass
    public static void init() {
        if (!Platform.IS_32_BIT && !Platform.IS_64_BIT) {
            throw new IllegalStateException("Unknown platform " + System.getProperty("sun.arch.data.model"));
        }
        System.out.println("Running struct size test\nPlatform: " + Platform.ARCH + "\nOS: " + Platform.OS_NAME);
    }

    private static void assertPlatfomValue(Function<jnr.ffi.Runtime, Struct> structFunction) {
        Struct struct = structFunction.apply(Runtime.getSystemRuntime());
        jnr.ffi.Platform.OS os = jnr.ffi.Platform.getNativePlatform().getOS();
        PlatformSize size = sizes.get(struct.getClass()).get(os);
        assertEquals(Platform.IS_32_BIT ? size.x32 : size.x64, Struct.size(struct));
    }


    @Test
    public void testStatvfs() {
        assertPlatfomValue(Statvfs::new);
    }

    @Test
    public void testFileStat() {
        assertPlatfomValue(FileStat::new);
    }

    @Test
    public void testFuseFileInfo() {
        assertPlatfomValue(FuseFileInfo::new);
    }

    @Test
    public void testFuseOperations() {
        assertPlatfomValue(FuseOperations::new);
    }

    @Test
    public void testTimeSpec() {
        assertPlatfomValue(Timespec::new);
    }

    @Test
    public void testFlock() {
        assertPlatfomValue(Flock::new);
    }

    @Test
    public void testFuseBuf() {
        assertPlatfomValue(FuseBuf::new);
    }

    @Test
    public void testFuseBufvec() {
        assertPlatfomValue(FuseBufvec::new);
    }

    @Test
    public void testFusePollhandle() {
        assertPlatfomValue(FusePollhandle::new);
    }

    @Test
    public void testFuseContext() {
        assertPlatfomValue(FuseContext::new);
    }
}
